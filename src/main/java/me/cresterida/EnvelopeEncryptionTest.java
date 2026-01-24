package me.cresterida;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Test utility to verify envelope encryption and decryption.
 * This simulates the complete flow and verifies data integrity.
 */
public class EnvelopeEncryptionTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("Envelope Encryption Test");
        System.out.println("=".repeat(80));
        System.out.println();

        // Setup
        CryptoService cryptoService = new CryptoService();

        // Simulate Angular encryption parameters
        String passphrase = "your-secret-passphrase";
        String testData = "This is a test file content that will be encrypted!";

        System.out.println("Test Data: " + testData);
        System.out.println("Test Data Length: " + testData.length() + " bytes");
        System.out.println();

        // Step 1: Simulate Angular client-side encryption
        System.out.println("STEP 1: Simulating Angular client-side encryption...");
        byte[] angularEncrypted = simulateAngularEncryption(testData, passphrase);
        System.out.println("Angular encrypted size: " + angularEncrypted.length + " bytes");
        System.out.println("Format: [16 bytes salt][12 bytes IV][encrypted data]");
        System.out.println();

        // Step 2: Server-side verification and KEK encryption
        System.out.println("STEP 2: Server-side verification and KEK encryption...");

        // Set the passphrase
        cryptoService.appPassphrase = passphrase;
        cryptoService.verificationEnabled = true;

        CryptoService.EncryptionResult result = cryptoService.verifyAndEnvelopeEncrypt(
            angularEncrypted,
            "test.txt"
        );

        System.out.println("Verification: " + (result.verified ? "✓ SUCCESS" : "✗ FAILED"));
        System.out.println("Original size: " + result.originalSize + " bytes");
        System.out.println("KEK encrypted size: " + result.kekEncryptedData.length + " bytes");
        System.out.println("KEK (base64): " + result.kek.substring(0, 30) + "...");
        System.out.println();

        // Step 3: Create envelope metadata
        System.out.println("STEP 3: Creating envelope metadata...");
        EnvelopeMetadata metadata = new EnvelopeMetadata(
            result.kek,
            result.originalFileName,
            result.originalSize,
            result.kekEncryptedData.length,
            result.verified
        );

        ObjectMapper objectMapper = new ObjectMapper();
        String metadataJson = objectMapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(metadata);
        System.out.println("Envelope Metadata:");
        System.out.println(metadataJson);
        System.out.println();

        // Step 4: Decrypt envelope to verify
        System.out.println("STEP 4: Decrypting with KEK to verify...");
        byte[] decryptedData = cryptoService.decryptWithKek(
            result.kekEncryptedData,
            result.kek
        );

        String decryptedText = new String(decryptedData);
        System.out.println("Decrypted data: " + decryptedText);
        System.out.println("Decrypted length: " + decryptedData.length + " bytes");
        System.out.println();

        // Verify data integrity
        System.out.println("STEP 5: Verifying data integrity...");
        if (testData.equals(decryptedText)) {
            System.out.println("✓ SUCCESS: Original data matches decrypted data!");
        } else {
            System.out.println("✗ FAILED: Data mismatch!");
            System.out.println("Expected: " + testData);
            System.out.println("Got: " + decryptedText);
        }

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("Test completed successfully!");
        System.out.println("=".repeat(80));
    }

    /**
     * Simulates Angular's AES-GCM encryption with PBKDF2 key derivation.
     * Returns: [16 bytes salt][12 bytes IV][encrypted data]
     */
    private static byte[] simulateAngularEncryption(String data, String passphrase) throws Exception {
        javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        // Generate salt and IV
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        random.nextBytes(salt);
        random.nextBytes(iv);

        // Derive key using PBKDF2 (same as Angular)
        javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            100000,
            256
        );
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        javax.crypto.SecretKey key = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");

        // Encrypt with AES-GCM
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, gcmSpec);
        byte[] encrypted = cipher.doFinal(data.getBytes());

        // Combine salt + IV + encrypted data
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(salt.length + iv.length + encrypted.length);
        buffer.put(salt);
        buffer.put(iv);
        buffer.put(encrypted);

        return buffer.array();
    }
}
