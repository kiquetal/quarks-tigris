package me.cresterida;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
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

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Result of encryption verification and re-encryption
     */
    public static class EncryptionResult {
        public byte[] envelopeEncryptedData;
        public String encryptedDataKey;
        public String originalFileName;
        public long originalSize;
        public boolean verified;

        public EncryptionResult(byte[] envelopeEncryptedData, String encryptedDataKey,
                              String originalFileName, long originalSize, boolean verified) {
            this.envelopeEncryptedData = envelopeEncryptedData;
            this.encryptedDataKey = encryptedDataKey;
            this.originalFileName = originalFileName;
            this.originalSize = originalSize;
            this.verified = verified;
        }
    }

    /**
     * Verifies and re-encrypts file data using envelope encryption.
     *
     * Process:
     * 1. Extract salt, IV, and encrypted data from input
     * 2. Verify by decrypting with passphrase (optional)
     * 3. Generate new data key for envelope encryption
     * 4. Encrypt the already-encrypted data with the data key
     * 5. Encrypt the data key with master key
     *
     * @param encryptedData The encrypted data from Angular (salt + IV + encrypted content)
     * @param passphrase The user's passphrase for verification
     * @param originalFileName The original file name (before encryption)
     * @return EncryptionResult containing envelope-encrypted data and encrypted data key
     */
    public EncryptionResult verifyAndEnvelopeEncrypt(byte[] encryptedData, String passphrase,
                                                     String originalFileName)
            throws GeneralSecurityException, IOException {

        System.out.println("Starting verification and envelope encryption...");
        System.out.println("Input data size: " + encryptedData.length + " bytes");

        // Parse the encrypted data structure
        if (encryptedData.length < SALT_LENGTH + IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted data: too short");
        }

        byte[] salt = Arrays.copyOfRange(encryptedData, 0, SALT_LENGTH);
        byte[] iv = Arrays.copyOfRange(encryptedData, SALT_LENGTH, SALT_LENGTH + IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(encryptedData, SALT_LENGTH + IV_LENGTH, encryptedData.length);

        System.out.println("Extracted - Salt: " + SALT_LENGTH + " bytes, IV: " + IV_LENGTH +
                         " bytes, Ciphertext: " + ciphertext.length + " bytes");

        // Verify decryption (optional but recommended)
        byte[] decryptedData = null;
        boolean verified = false;

        if (verificationEnabled && passphrase != null && !passphrase.isEmpty()) {
            try {
                System.out.println("Verifying encryption with passphrase...");
                decryptedData = decryptWithPassphrase(ciphertext, passphrase, salt, iv);
                verified = true;
                System.out.println("Verification successful! Decrypted size: " + decryptedData.length + " bytes");
            } catch (Exception e) {
                System.err.println("Verification failed: " + e.getMessage());
                throw new GeneralSecurityException("Failed to verify encrypted data: " + e.getMessage());
            }
        } else {
            System.out.println("Verification skipped (disabled or no passphrase)");
            // If verification is disabled, we still process the encrypted data
            verified = false;
        }

        // Perform envelope encryption
        // Generate a random data encryption key (DEK)
        byte[] dataKey = new byte[32]; // 256 bits for AES-256
        secureRandom.nextBytes(dataKey);
        System.out.println("Generated data encryption key (DEK): 32 bytes");

        // If we verified, encrypt the decrypted data; otherwise, encrypt the ciphertext as-is
        byte[] dataToEncrypt = (verified && decryptedData != null) ? decryptedData : encryptedData;

        // Encrypt the data with the DEK
        byte[] envelopeEncrypted = encryptWithDataKey(dataToEncrypt, dataKey);
        System.out.println("Envelope encryption complete. Size: " + envelopeEncrypted.length + " bytes");

        // Encrypt the DEK with the master key
        String encryptedDataKey = encryptDataKey(dataKey);
        System.out.println("Data key encrypted with master key");

        // Clear sensitive data
        Arrays.fill(dataKey, (byte) 0);
        if (decryptedData != null) {
            Arrays.fill(decryptedData, (byte) 0);
        }

        long originalSize = verified && decryptedData != null ? decryptedData.length : 0;

        return new EncryptionResult(envelopeEncrypted, encryptedDataKey, originalFileName, originalSize, verified);
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
     * Encrypts a data encryption key (DEK) with the master key.
     * Returns base64-encoded encrypted key.
     */
    private String encryptDataKey(byte[] dataKey) throws GeneralSecurityException {
        byte[] masterKey = Base64.getDecoder().decode(masterKeyBase64);
        SecretKey key = new SecretKeySpec(masterKey, "AES");

        // Generate random IV for master key encryption
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        // Encrypt DEK with master key
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        byte[] encryptedDek = cipher.doFinal(dataKey);

        // Combine IV + encrypted DEK and encode as base64
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encryptedDek.length);
        buffer.put(iv);
        buffer.put(encryptedDek);

        return Base64.getEncoder().encodeToString(buffer.array());
    }

    /**
     * Decrypts a data encryption key (DEK) using the master key.
     */
    public byte[] decryptDataKey(String encryptedDataKeyBase64) throws GeneralSecurityException {
        byte[] encryptedDataKey = Base64.getDecoder().decode(encryptedDataKeyBase64);
        byte[] masterKey = Base64.getDecoder().decode(masterKeyBase64);
        SecretKey key = new SecretKeySpec(masterKey, "AES");

        // Extract IV and encrypted DEK
        byte[] iv = Arrays.copyOfRange(encryptedDataKey, 0, IV_LENGTH);
        byte[] encryptedDek = Arrays.copyOfRange(encryptedDataKey, IV_LENGTH, encryptedDataKey.length);

        // Decrypt
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        return cipher.doFinal(encryptedDek);
    }

    /**
     * Decrypts envelope-encrypted data.
     */
    public byte[] decryptEnvelopeData(byte[] envelopeEncryptedData, String encryptedDataKeyBase64)
            throws GeneralSecurityException {

        // Decrypt the data key
        byte[] dataKey = decryptDataKey(encryptedDataKeyBase64);

        // Extract IV and ciphertext
        byte[] iv = Arrays.copyOfRange(envelopeEncryptedData, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(envelopeEncryptedData, IV_LENGTH, envelopeEncryptedData.length);

        // Decrypt data
        SecretKey key = new SecretKeySpec(dataKey, "AES");
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        byte[] decryptedData = cipher.doFinal(ciphertext);

        // Clear sensitive data
        Arrays.fill(dataKey, (byte) 0);

        return decryptedData;
    }

    /**
     * Generates a new master key (for initial setup).
     * Returns base64-encoded 256-bit key.
     */
    public static String generateMasterKey() {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[32]; // 256 bits
        random.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
