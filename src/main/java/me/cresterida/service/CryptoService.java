package me.cresterida.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cryptographic service for handling AES-GCM encryption and envelope encryption.
 *
 * This service:
 * 1. Verifies encrypted data from Angular frontend (PBKDF2 + AES-GCM)
 * 2. Implements envelope encryption for S3 storage
 * 3. Manages data keys and master key encryption
 */
@ApplicationScoped
public class CryptoService {

    private static final Logger logger = LoggerFactory.getLogger(CryptoService.class);

    // Encryption parameters matching Angular frontend
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // 128 bits = 16 bytes
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;

    @ConfigProperty(name = "encryption.master.key")
    String masterKeyBase64;

    @ConfigProperty(name = "encryption.verify.enabled", defaultValue = "true")
    boolean verificationEnabled;

    @Inject
    SessionManager sessionManager;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Result of decryption from Angular frontend (streaming version)
     */
    public static class StreamingDecryptionResult {
        public long size;                // Size of decrypted data
        public boolean verified;         // Whether decryption succeeded

        public StreamingDecryptionResult(long size, boolean verified) {
            this.size = size;
            this.verified = verified;
        }
    }

    /**
     * Result of data encryption with DEK (streaming version)
     */
    public static class StreamingDataEncryptionResult {
        public byte[] dek;               // The Data Encryption Key used (32 bytes, plaintext)
        public long encryptedSize;       // Size of encrypted data

        public StreamingDataEncryptionResult(byte[] dek, long encryptedSize) {
            this.dek = dek;
            this.encryptedSize = encryptedSize;
        }
    }

    /**
     * Result of decryption from Angular frontend
     */
    public static class DecryptionResult {
        public byte[] decryptedData;     // The plaintext data
        public long size;                // Size of decrypted data
        public boolean verified;         // Whether decryption succeeded

        public DecryptionResult(byte[] decryptedData, long size, boolean verified) {
            this.decryptedData = decryptedData;
            this.size = size;
            this.verified = verified;
        }
    }

    /**
     * Result of data encryption with DEK
     */
    public static class DataEncryptionResult {
        public byte[] encryptedData;     // Data encrypted with DEK
        public byte[] dek;               // The Data Encryption Key used (32 bytes, plaintext)
        public long encryptedSize;       // Size of encrypted data

        public DataEncryptionResult(byte[] encryptedData, byte[] dek, long encryptedSize) {
            this.encryptedData = encryptedData;
            this.dek = dek;
            this.encryptedSize = encryptedSize;
        }
    }

    /**
     * Verifies and decrypts data from Angular frontend.
     *
     * Process:
     * 1. Extract salt, IV, and encrypted data from Angular encryption
     * 2. Decrypt using the user's passphrase to verify and get plaintext
     *
     * @param encryptedData The encrypted data from Angular (salt + IV + encrypted content)
     * @param email The user's email to retrieve their passphrase
     * @return DecryptionResult containing plaintext data
     */
    public DecryptionResult verifyAndDecrypt(byte[] encryptedData, String email)
            throws GeneralSecurityException, IOException {

        logger.debug("Starting verification and decryption for user: {}", email);
        logger.debug("Input data size: {} bytes", encryptedData.length);

        // Get the user's passphrase from SessionManager
        String passphrase = getPassphraseForEmail(email);
        if (passphrase == null) {
            throw new GeneralSecurityException("No passphrase found for email: " + email);
        }

        // Parse the encrypted data structure from Angular
        if (encryptedData.length < SALT_LENGTH + IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted data: too short");
        }

        byte[] salt = Arrays.copyOfRange(encryptedData, 0, SALT_LENGTH);
        byte[] iv = Arrays.copyOfRange(encryptedData, SALT_LENGTH, SALT_LENGTH + IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(encryptedData, SALT_LENGTH + IV_LENGTH, encryptedData.length);

        logger.debug("Extracted - Salt: {} bytes, IV: {} bytes, Ciphertext: {} bytes",
                SALT_LENGTH, IV_LENGTH, ciphertext.length);

        // Decrypt the data using the user's passphrase to verify integrity
        byte[] decryptedData;
        boolean verified = false;

        try {
            logger.debug("Decrypting with user passphrase for verification...");
            decryptedData = decryptWithPassphrase(ciphertext, passphrase, salt, iv);
            verified = true;
            logger.info("Decryption successful! Original file size: {} bytes", decryptedData.length);
        } catch (Exception e) {
            logger.error("Decryption failed: {}", e.getMessage(), e);
            throw new GeneralSecurityException("Failed to decrypt and verify data: " + e.getMessage(), e);
        }

        return new DecryptionResult(decryptedData, decryptedData.length, verified);
    }

    /**
     * Encrypts plaintext data with a randomly generated DEK (Data Encryption Key).
     * Does NOT perform envelope encryption - that's done separately with createEnvelopeDek().
     *
     * @param plaintextData The plaintext data to encrypt
     * @return DataEncryptionResult containing encrypted data and the plaintext DEK
     */
    public DataEncryptionResult encryptWithDek(byte[] plaintextData)
            throws GeneralSecurityException {

        System.out.println("Starting data encryption with DEK...");
        System.out.println("Plaintext data size: " + plaintextData.length + " bytes");

        // Generate a new random DEK (Data Encryption Key) for this file
        byte[] dek = new byte[32]; // 256 bits for AES-256
        secureRandom.nextBytes(dek);
        System.out.println("Generated new DEK (Data Encryption Key): 32 bytes");

        // Encrypt the plaintext data with the DEK
        byte[] encryptedData = encryptWithDataKey(plaintextData, dek);
        System.out.println("DEK encryption complete. Size: " + encryptedData.length + " bytes");

        return new DataEncryptionResult(encryptedData, dek, encryptedData.length);
    }

    /**
     * Creates an envelope by encrypting a DEK with the master key.
     * This is the "envelope" part of envelope encryption.
     *
     * @param dek The Data Encryption Key to encrypt (32 bytes)
     * @return Base64-encoded encrypted DEK (format: [12 bytes IV][encrypted DEK + GCM tag])
     */
    public String createEnvelopeDek(byte[] dek) throws GeneralSecurityException {
        System.out.println("Creating envelope: encrypting DEK with master key...");

        String encryptedDek = encryptDekWithMasterKey(dek);

        System.out.println("DEK encrypted with master key for storage");
        return encryptedDek;
    }

    /**
     * Encrypts a DEK (Data Encryption Key) with the master key.
     * This is the core of envelope encryption - the DEK is encrypted before storage.
     * The encrypted result becomes the KEK (Key Encryption Key) stored in metadata.
     *
     * @param dek The DEK to encrypt (32 bytes)
     * @return Base64-encoded encrypted DEK/KEK (format: [12 bytes IV][encrypted DEK + GCM tag])
     */
    private String encryptDekWithMasterKey(byte[] dek) throws GeneralSecurityException {
        // Decode the master key
        byte[] masterKey = Base64.getDecoder().decode(masterKeyBase64);

        // Generate random IV for this encryption
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        // Encrypt the DEK with the master key
        SecretKey masterSecretKey = new SecretKeySpec(masterKey, "AES");
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, masterSecretKey, gcmSpec);

        byte[] encryptedDek = cipher.doFinal(dek);

        // Combine IV + encrypted DEK
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encryptedDek.length);
        buffer.put(iv);
        buffer.put(encryptedDek);

        // Clear sensitive data
        Arrays.fill(masterKey, (byte) 0);

        // Return as base64 for storage in metadata
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    /**
     * Decrypts a KEK that was encrypted with the master key.
     *
     * @param encryptedKekBase64 The encrypted KEK (base64-encoded: [IV][encrypted KEK + tag])
     * @return The decrypted KEK (32 bytes)
     */
    private byte[] decryptKekWithMasterKey(String encryptedKekBase64) throws GeneralSecurityException {
        // Decode the encrypted KEK
        byte[] encryptedKekData = Base64.getDecoder().decode(encryptedKekBase64);

        // Extract IV and ciphertext
        byte[] iv = Arrays.copyOfRange(encryptedKekData, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(encryptedKekData, IV_LENGTH, encryptedKekData.length);

        // Decode the master key
        byte[] masterKey = Base64.getDecoder().decode(masterKeyBase64);

        // Decrypt the KEK with the master key
        SecretKey masterSecretKey = new SecretKeySpec(masterKey, "AES");
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, masterSecretKey, gcmSpec);

        byte[] kek = cipher.doFinal(ciphertext);

        // Clear sensitive data
        Arrays.fill(masterKey, (byte) 0);

        return kek;
    }

    /**
     * Decrypts data using passphrase (for verification).
     * Matches the Angular encryption parameters.
     */
    private byte[] decryptWithPassphrase(byte[] ciphertext, String passphrase, byte[] salt, byte[] iv)
            throws GeneralSecurityException {

        // Derive key from passphrase using PBKDF2 (matching Angular)
        SecretKey key = deriveKeyFromPassphrase(passphrase, salt);

        // Decrypt using AES-GCM
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        return cipher.doFinal(ciphertext);
    }

    /**
     * Derives an AES key from a passphrase using PBKDF2.
     * Parameters match the Angular implementation.
     */
    private SecretKey deriveKeyFromPassphrase(String passphrase, byte[] salt)
            throws GeneralSecurityException {

        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();

        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts data with a data encryption key (DEK) using AES-GCM.
     * Returns: [12 bytes IV][encrypted data + GCM tag]
     */
    private byte[] encryptWithDataKey(byte[] data, byte[] dataKey) throws GeneralSecurityException {
        SecretKey key = new SecretKeySpec(dataKey, "AES");

        // Generate random IV
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        // Encrypt
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        byte[] ciphertext = cipher.doFinal(data);

        // Combine IV + ciphertext
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
        buffer.put(iv);
        buffer.put(ciphertext);

        return buffer.array();
    }

    /**
     * Decrypts KEK-encrypted data using the KEK from envelope metadata.
     *
     * @param kekEncryptedData The encrypted data (format: [12 bytes IV][encrypted data + GCM tag])
     * @param encryptedKekBase64 The encrypted KEK as base64 string (from metadata.json)
     * @return The decrypted plaintext data
     */
    public byte[] decryptWithKek(byte[] kekEncryptedData, String encryptedKekBase64)
            throws GeneralSecurityException {

        // First, decrypt the KEK using the master key
        byte[] kek = decryptKekWithMasterKey(encryptedKekBase64);
        System.out.println("KEK decrypted with master key");

        // Extract IV and ciphertext
        byte[] iv = Arrays.copyOfRange(kekEncryptedData, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(kekEncryptedData, IV_LENGTH, kekEncryptedData.length);

        // Decrypt data with KEK
        SecretKey key = new SecretKeySpec(kek, "AES");
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        byte[] decryptedData = cipher.doFinal(ciphertext);

        // Clear sensitive data
        Arrays.fill(kek, (byte) 0);

        return decryptedData;
    }

    /**
     * Verifies and decrypts data from Angular frontend using streaming.
     * Writes decrypted data directly to output stream without loading all in memory.
     *
     * @param inputStream The input stream with encrypted data from Angular
     * @param outputStream The output stream to write decrypted data to
     * @param email The user's email to retrieve their passphrase
     * @return StreamingDecryptionResult with size and verification status
     */
    public StreamingDecryptionResult verifyAndDecryptStreaming(InputStream inputStream, OutputStream outputStream, String email)
            throws GeneralSecurityException, IOException {

        logger.debug("Starting streaming verification and decryption for user: {}", email);

        // Get the user's passphrase from SessionManager
        String passphrase = getPassphraseForEmail(email);
        if (passphrase == null) {
            throw new GeneralSecurityException("No passphrase found for email: " + email);
        }

        // Read salt and IV from the beginning of the stream
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];

        int saltRead = inputStream.readNBytes(salt, 0, SALT_LENGTH);
        int ivRead = inputStream.readNBytes(iv, 0, IV_LENGTH);

        if (saltRead < SALT_LENGTH || ivRead < IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted data: too short");
        }

        logger.debug("Extracted - Salt: {} bytes, IV: {} bytes", SALT_LENGTH, IV_LENGTH);

        // Derive key from passphrase
        SecretKey key = deriveKeyFromPassphrase(passphrase, salt);

        // Initialize cipher for decryption
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        // Stream decryption in chunks
        byte[] buffer = new byte[8192]; // 8KB buffer
        long totalDecrypted = 0;
        int bytesRead;
        boolean verified = false;

        try {
            logger.debug("Decrypting in streaming mode...");
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] decryptedChunk = cipher.update(buffer, 0, bytesRead);
                if (decryptedChunk != null && decryptedChunk.length > 0) {
                    outputStream.write(decryptedChunk);
                    totalDecrypted += decryptedChunk.length;
                }
            }

            // Finalize decryption (handles GCM tag verification)
            byte[] finalChunk = cipher.doFinal();
            if (finalChunk != null && finalChunk.length > 0) {
                outputStream.write(finalChunk);
                totalDecrypted += finalChunk.length;
            }

            verified = true;
            logger.info("Streaming decryption successful! Total decrypted: {} bytes", totalDecrypted);
        } catch (Exception e) {
            logger.error("Streaming decryption failed: {}", e.getMessage(), e);
            throw new GeneralSecurityException("Failed to decrypt and verify data: " + e.getMessage(), e);
        } finally {
            Arrays.fill(buffer, (byte) 0);
        }

        return new StreamingDecryptionResult(totalDecrypted, verified);
    }

    /**
     * Retrieves the passphrase for a given email.
     * This looks up the passphrase from the SessionManager's in-memory map.
     *
     * @param email The user's email
     * @return The passphrase, or null if not found
     */
    private String getPassphraseForEmail(String email) {
        // We need to reverse lookup: find passphrase for this email
        // SessionManager has passphrase->email mapping, so we need to iterate
        return sessionManager.getPassphraseForEmail(email);
    }

    /**
     * Encrypts plaintext data with a randomly generated DEK using streaming.
     * Reads from input stream and writes encrypted data to output stream.
     * Does NOT perform envelope encryption - that's done separately with createEnvelopeDek().
     *
     * @param inputStream The input stream with plaintext data
     * @param outputStream The output stream to write DEK-encrypted data to
     * @return StreamingDataEncryptionResult with plaintext DEK and encrypted size
     */
    public StreamingDataEncryptionResult encryptWithDekStreaming(InputStream inputStream, OutputStream outputStream)
            throws GeneralSecurityException, IOException {

        System.out.println("Starting streaming data encryption with DEK...");

        // Generate a new random DEK (Data Encryption Key) for this file
        byte[] dek = new byte[32]; // 256 bits for AES-256
        secureRandom.nextBytes(dek);
        System.out.println("Generated new DEK (Data Encryption Key): 32 bytes");

        // Generate random IV for DEK encryption
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        // Write IV to output stream first
        outputStream.write(iv);
        long totalEncrypted = iv.length;

        // Initialize cipher for encryption
        SecretKey key = new SecretKeySpec(dek, "AES");
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

        // Stream encryption in chunks
        byte[] buffer = new byte[8192]; // 8KB buffer
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            byte[] encryptedChunk = cipher.update(buffer, 0, bytesRead);
            if (encryptedChunk != null && encryptedChunk.length > 0) {
                outputStream.write(encryptedChunk);
                totalEncrypted += encryptedChunk.length;
            }
        }

        // Finalize encryption (includes GCM tag)
        byte[] finalChunk = cipher.doFinal();
        if (finalChunk != null && finalChunk.length > 0) {
            outputStream.write(finalChunk);
            totalEncrypted += finalChunk.length;
        }

        System.out.println("DEK streaming encryption complete. Size: " + totalEncrypted + " bytes");

        // Clear buffer from memory
        Arrays.fill(buffer, (byte) 0);

        return new StreamingDataEncryptionResult(dek, totalEncrypted);
    }
}
