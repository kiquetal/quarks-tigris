# Envelope Encryption Implementation

## Overview

This implementation provides **double encryption** with **envelope encryption** for maximum security:

1. **Client-Side Encryption (Angular)**: Files are encrypted with AES-GCM-256 using a passphrase-derived key (PBKDF2)
2. **Server-Side Verification**: Quarkus decrypts the file using the configured passphrase to verify integrity
3. **Envelope Encryption**: Quarkus re-encrypts the plaintext with a new random Data Encryption Key (DEK)
4. **Key Encryption**: The DEK is encrypted with a Master Key (KEK - Key Encryption Key)
5. **Storage**: Both the encrypted data and encrypted DEK are stored in S3

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Angular Frontend                          │
├─────────────────────────────────────────────────────────────────┤
│ 1. User enters passphrase                                       │
│ 2. File encrypted with AES-GCM (passphrase → PBKDF2 → AES key) │
│ 3. Upload encrypted file to backend                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      Quarkus Backend                             │
├─────────────────────────────────────────────────────────────────┤
│ 1. Receive encrypted file                                       │
│ 2. Decrypt with configured passphrase (verification)            │
│ 3. Generate random DEK (Data Encryption Key)                   │
│ 4. Encrypt plaintext with DEK → Encrypted Data                 │
│ 5. Encrypt DEK with Master Key → Encrypted DEK                 │
│ 6. Create envelope metadata JSON                                │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                        S3 Storage                                │
├─────────────────────────────────────────────────────────────────┤
│ uploads/{email}/{uuid}/                                         │
│   ├── {filename}.enc        (Encrypted data with DEK)          │
│   └── metadata.json          (Envelope metadata + encrypted DEK)│
└─────────────────────────────────────────────────────────────────┘
```

## Encryption Flow

### Phase 1: Client-Side Encryption (Angular)

```typescript
// Angular encrypts the file
const salt = crypto.getRandomValues(new Uint8Array(16));
const iv = crypto.getRandomValues(new Uint8Array(12));
const key = deriveKey(passphrase, salt); // PBKDF2-SHA256, 100k iterations
const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, fileData);

// Format: [16 bytes salt][12 bytes IV][encrypted data + GCM tag]
```

**Parameters:**
- Algorithm: AES-GCM-256
- Key Derivation: PBKDF2-SHA256
- Iterations: 100,000
- Salt: 16 bytes (random)
- IV: 12 bytes (random)
- Tag: 16 bytes (GCM authentication tag)

### Phase 2: Server-Side Decryption & Verification (Quarkus)

```java
// 1. Parse Angular's encrypted format
byte[] salt = Arrays.copyOfRange(data, 0, 16);
byte[] iv = Arrays.copyOfRange(data, 16, 28);
byte[] ciphertext = Arrays.copyOfRange(data, 28, data.length);

// 2. Derive the same key using configured passphrase
SecretKey key = deriveKeyFromPassphrase(appPassphrase, salt);

// 3. Decrypt and verify
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
// The '128' specifies a 128-bit (16-byte) authentication tag, the standard for GCM.
GCMParameterSpec spec = new GCMParameterSpec(128, iv);
cipher.init(Cipher.DECRYPT_MODE, key, spec); // <-- Key is used here
// doFinal automatically verifies the GCM authentication tag (the last 16 bytes of the ciphertext).
// If verification fails, it throws AEADBadTagException.
byte[] plaintext = cipher.doFinal(ciphertext);  // ✓ Verified!
```

### Phase 3: Envelope Encryption (Quarkus)

```java
// 1. Generate random Data Encryption Key (DEK)
byte[] dek = new byte[32];  // 256 bits
secureRandom.nextBytes(dek);

// 2. Encrypt plaintext with DEK
// This is handled by the encryptWithDekStreaming method in CryptoService
StreamingDataEncryptionResult encryptionResult = cryptoService.encryptWithDekStreaming(plaintextStream);
// encryptionResult.encryptedStream contains: [12 bytes IV][encrypted data + GCM tag]
// encryptionResult.dek contains the generated DEK

// 3. Encrypt DEK with Master Key (KEK)
// This is handled by the createEnvelopeDek method in CryptoService
String encryptedDEKBase64 = cryptoService.createEnvelopeDek(encryptionResult.dek);
// The result is a Base64 string containing: [12 bytes IV][encrypted DEK + GCM tag]
```

## Envelope Metadata Format

Stored as `metadata.json` in S3:

```json
{
  "version": "1.0",
  "encrypted_dek": "base64_encoded_encrypted_dek",
  "algorithm": "AES-GCM-256",
  "kek_algorithm": "AES-GCM-256",
  "original_filename": "song.mp3",
  "original_size": 5242880,
  "encrypted_size": 5243000,
  "verification_status": "verified",
  "timestamp": 1706112000000
}
```

## Configuration

### application.properties

```properties
# Application passphrase (must match the one used in validatePassphrase endpoint)
app.passphrase=${APP_PASSPHRASE:your-secret-passphrase}

# Master key for envelope encryption (base64-encoded 256-bit key)
encryption.master.key=${ENCRYPTION_MASTER_KEY:+hMMpH56z4xefxKZI1cp6PbIFeY1fRFbNiECMfkYy/U=}

# Enable verification of encrypted data from Angular
encryption.verify.enabled=${ENCRYPTION_VERIFY_ENABLED:true}
```

### Environment Variables (Production)

```bash
export APP_PASSPHRASE="your-production-passphrase"
export ENCRYPTION_MASTER_KEY="$(java -cp target/classes me.cresterida.MasterKeyGenerator)"
export ENCRYPTION_VERIFY_ENABLED=true
```

## Generating Master Key

Run the key generator:

```bash
./mvnw compile exec:java -Dexec.mainClass="me.cresterida.MasterKeyGenerator"
```

Output:
```
================================================================================
Generated Master Key (base64-encoded 256-bit AES key):
+hMMpH56z4xefxKZI1cp6PbIFeY1fRFbNiECMfkYy/U=

Add this to your application.properties or environment variables:
encryption.master.key=+hMMpH56z4xefxKZI1cp6PbIFeY1fRFbNiECMfkYy/U=

IMPORTANT: Keep this key secure and never commit it to version control!
================================================================================
```

## Decryption Process (Later Processing)

To decrypt the file later:

```java
// 1. Download metadata.json from S3
EnvelopeMetadata metadata = objectMapper.readValue(metadataJson, EnvelopeMetadata.class);

// 2. Decrypt the DEK using Master Key
byte[] dek = cryptoService.decryptDataKey(metadata.encryptedDek);

// 3. Download encrypted data from S3
byte[] encryptedData = s3.getObject(dataKey);

// 4. Decrypt the data using DEK
byte[] plaintext = cryptoService.decryptEnvelopeData(encryptedData, dek);
```

## Security Features

### 1. **Defense in Depth**
- **Layer 1**: Client-side encryption (protects during transit)
- **Layer 2**: Server-side verification (ensures integrity)
- **Layer 3**: Envelope encryption (protects at rest)

### 2. **Key Separation**
- **User Passphrase**: Known only to user (used for initial encryption)
- **Application Passphrase**: Configured on server (never sent over network)
- **Data Encryption Keys (DEK)**: Random per file (ensures unique encryption)
- **Master Key (KEK)**: Protects all DEKs (single key rotation point)

### 3. **No Passphrase Transmission**
- Passphrase **never** sent from Angular to Quarkus
- Backend uses its own configured passphrase for verification
- Eliminates risk of passphrase interception

### 4. **Perfect Forward Secrecy**
- Each file has a unique DEK
- Compromise of one file doesn't affect others
- DEKs are never reused

### 5. **Key Rotation**
- Master Key can be rotated without re-encrypting all files
- Only need to re-encrypt the DEKs (small operation)
- Files remain encrypted with their original DEKs

## File Structure in S3

```
s3://bucket-name/
└── uploads/
    └── user@example.com/
        └── a1b2c3d4-e5f6-7890-abcd-ef1234567890/
            ├── song.mp3.enc          (Encrypted with DEK)
            └── metadata.json         (Contains encrypted DEK)
```

## Verification Status

The system verifies encryption integrity by decrypting the file from Angular:

- **verified**: File successfully decrypted with configured passphrase ✓
- **not_verified**: Verification disabled or failed ✗

## Encryption Parameters Summary

| Component | Algorithm | Key Size | Iterations | Salt | IV |
|-----------|-----------|----------|------------|------|-----|
| Angular Encryption | AES-GCM | 256-bit | 100,000 (PBKDF2) | 16 bytes | 12 bytes |
| DEK Encryption | AES-GCM | 256-bit | N/A | N/A | 12 bytes |
| KEK Encryption | AES-GCM | 256-bit | N/A | N/A | 12 bytes |

## Advantages of This Approach

1. **No passphrase over network**: Passphrase stays in Angular and backend config
2. **Verification**: Backend confirms Angular encrypted correctly
3. **Envelope encryption**: Industry-standard pattern for cloud storage
4. **Key rotation**: Easy to rotate master key
5. **Unique per-file keys**: Each file has unique encryption
6. **Metadata separation**: Encryption metadata stored separately
7. **Audit trail**: Verification status and timestamps recorded

## Testing

### 1. Upload Flow
```bash
# Start the application
./dev-mode.sh

# Access http://localhost:8080/whisper
# Enter passphrase: your-secret-passphrase
# Upload a file
```

### 2. Verify S3 Structure
```bash
aws s3 ls s3://whisper-uploads/uploads/ --recursive
# Should see: .../file.mp3.enc and .../metadata.json
```

### 3. Check Logs
```
Starting verification and envelope encryption...
Extracted - Salt: 16 bytes, IV: 12 bytes, Ciphertext: 5242880 bytes
Decrypting with application passphrase for verification...
✓ Decryption successful! Original file size: 5242880 bytes
Generated new Data Encryption Key (DEK): 32 bytes
Envelope encryption complete. Size: 5242912 bytes
Data key encrypted with master key (KEK)
✓ Encrypted data uploaded to S3: uploads/user@example.com/.../file.mp3.enc
✓ Envelope metadata uploaded to S3: uploads/user@example.com/.../metadata.json
```

## Future Enhancements

1. **Key Rotation Tool**: Utility to rotate master key and re-encrypt DEKs
2. **Decryption API**: Endpoint to decrypt and stream files
3. **Key Management Service**: Integration with AWS KMS or Vault
4. **Audit Logging**: Track all encryption/decryption operations
5. **Multi-tenant Keys**: Different master keys per tenant
