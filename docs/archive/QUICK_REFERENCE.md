# Quick Reference: Envelope Encryption

## 🚀 Quick Start

### 1. Generate Master Key (First Time Only)
```bash
./mvnw compile exec:java -Dexec.mainClass="me.cresterida.MasterKeyGenerator"
```
Copy the generated key to `application.properties`:
```properties
encryption.master.key=YOUR_GENERATED_KEY_HERE
```

### 2. Configure Passphrase
In `application.properties`:
```properties
app.passphrase=your-secret-passphrase
```

### 3. Run Application
```bash
./dev-mode.sh
```

### 4. Test Upload
1. Navigate to: http://localhost:8080/whisper
2. Enter passphrase: `your-secret-passphrase`
3. Upload a file
4. Check console logs for verification

## 📊 How It Works

```
┌─────────────────┐
│  Angular Client │
└────────┬────────┘
         │ 1. Encrypt with passphrase (AES-GCM)
         ↓
┌─────────────────┐
│  Upload to API  │
└────────┬────────┘
         │ 2. NO passphrase sent
         ↓
┌─────────────────┐
│ Quarkus Backend │
├─────────────────┤
│ • Decrypt (verify)
│ • Generate DEK
│ • Re-encrypt data
│ • Encrypt DEK with Master Key
└────────┬────────┘
         │ 3. Store in S3
         ↓
┌─────────────────┐
│  S3 Storage     │
├─────────────────┤
│ • file.enc      │ ← Encrypted with DEK
│ • metadata.json │ ← Contains encrypted DEK
└─────────────────┘
```

## 🔐 Encryption Layers

| Layer | Component | Key Used | Purpose |
|-------|-----------|----------|---------|
| 1 | Angular | User Passphrase | Client-side encryption |
| 2 | Quarkus | App Passphrase | Verification |
| 3 | Quarkus | Random DEK | Envelope encryption |
| 4 | Quarkus | Master Key (KEK) | DEK encryption |

## 📝 Key Concepts

### DEK (Data Encryption Key)
- **What**: Random 256-bit key generated per file
- **Purpose**: Encrypts the actual file data
- **Storage**: Encrypted with Master Key in metadata.json
- **Benefit**: Each file has unique encryption

### KEK (Key Encryption Key / Master Key)
- **What**: Your configured master key
- **Purpose**: Encrypts all DEKs
- **Storage**: In application.properties (or vault)
- **Benefit**: Single key rotation point

### Envelope
- **What**: Pattern of encrypting keys with keys
- **Structure**: Data encrypted with DEK, DEK encrypted with KEK
- **Benefit**: Easy key rotation without re-encrypting data

## 🔧 Configuration Reference

### Required Properties
```properties
# Backend passphrase (must match frontend validation)
app.passphrase=your-secret-passphrase

# Master key for envelope encryption (256-bit, base64)
encryption.master.key=+hMMpH56z4xefxKZI1cp6PbIFeY1fRFbNiECMfkYy/U=

# Enable verification (recommended: true)
encryption.verify.enabled=true
```

### Environment Variables (Production)
```bash
export APP_PASSPHRASE="production-passphrase"
export ENCRYPTION_MASTER_KEY="base64-master-key"
export ENCRYPTION_VERIFY_ENABLED=true
```

## 📂 S3 Storage Structure

```
uploads/
  └── {email}/
      └── {uuid}/
          ├── {filename}.enc       ← Encrypted file (DEK)
          └── metadata.json         ← Envelope metadata (encrypted DEK)
```

### metadata.json Structure
```json
{
  "version": "1.0",
  "encrypted_dek": "base64-encoded-encrypted-dek",
  "algorithm": "AES-GCM-256",
  "kek_algorithm": "AES-GCM-256",
  "original_filename": "song.mp3",
  "original_size": 5242880,
  "encrypted_size": 5242912,
  "verification_status": "verified",
  "timestamp": 1706112000000
}
```

## 🧪 Testing

### Test Encryption/Decryption
```bash
./mvnw compile exec:java -Dexec.mainClass="me.cresterida.EnvelopeEncryptionTest"
```

Expected output:
```
✓ Decryption successful! Original file size: 51 bytes
✓ SUCCESS: Original data matches decrypted data!
```

### Manual Test
```bash
# 1. Start app
./dev-mode.sh

# 2. Open browser
http://localhost:8080/whisper

# 3. Upload file and check logs
```

## 🔍 Verification Logs

Look for these in console:
```
Starting verification and envelope encryption...
Extracted - Salt: 16 bytes, IV: 12 bytes, Ciphertext: 5242880 bytes
Decrypting with application passphrase for verification...
✓ Decryption successful! Original file size: 5242880 bytes
Generated new Data Encryption Key (DEK): 32 bytes
Envelope encryption complete. Size: 5242912 bytes
Data key encrypted with master key (KEK)
✓ Encrypted data uploaded to S3: uploads/.../file.enc
✓ Envelope metadata uploaded to S3: uploads/.../metadata.json
```

## 🔓 Later Decryption

```java
// 1. Get metadata
EnvelopeMetadata metadata = objectMapper.readValue(
    s3.getObjectAsString(metadataKey), 
    EnvelopeMetadata.class
);

// 2. Get encrypted data
byte[] encryptedData = s3.getObjectAsBytes(dataKey);

// 3. Decrypt
byte[] plaintext = cryptoService.decryptEnvelopeData(
    encryptedData, 
    metadata.encryptedDek
);
```

## ⚠️ Security Reminders

- ✅ Passphrase NEVER sent over network
- ✅ Master key NEVER committed to git
- ✅ Each file has unique DEK
- ✅ DEKs encrypted with Master Key
- ✅ Verification ensures data integrity
- ✅ Use environment variables in production
- ✅ Store master key in secure vault
- ✅ Rotate master key periodically

## 🐛 Troubleshooting

### Issue: "Failed to decrypt and verify data"
**Cause**: Passphrase mismatch between Angular and backend
**Fix**: Ensure `app.passphrase` matches the passphrase used in `/validate-passphrase` endpoint

### Issue: "No such algorithm"
**Cause**: Java crypto provider not available
**Fix**: Ensure Java 11+ with full crypto support

### Issue: "Invalid encrypted data: too short"
**Cause**: File not encrypted by Angular or corrupted
**Fix**: Check Angular encryption is working, verify file upload

### Issue: Master key not found
**Cause**: `encryption.master.key` not configured
**Fix**: Generate key with MasterKeyGenerator and add to properties

## 📚 Documentation Files

- `IMPLEMENTATION_SUMMARY.md` - Complete implementation details
- `ENVELOPE_ENCRYPTION.md` - Detailed encryption architecture
- `ENCRYPTION_IMPLEMENTATION.md` - Original encryption setup
- `ENCRYPTION_TESTING.md` - Testing procedures

## 🎯 Key Classes

| Class | Purpose |
|-------|---------|
| `CryptoService.java` | Core encryption/decryption logic |
| `EnvelopeMetadata.java` | Metadata structure |
| `MasterKeyGenerator.java` | Generate master keys |
| `EnvelopeEncryptionTest.java` | Test utility |
| `FileUploadResource.java` | Upload API endpoint |
| `EncryptionService` (Angular) | Client-side encryption |

## 🚦 Status Indicators

### Success
```
✓ Decryption successful!
✓ Encrypted data uploaded to S3
✓ Envelope metadata uploaded to S3
```

### Warning
```
⚠ Verification: not_verified (verification disabled)
```

### Error
```
✗ Decryption failed: Invalid passphrase
✗ Failed to verify encrypted data
```

---

**Ready to use!** 🎉
