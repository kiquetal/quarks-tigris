# Envelope Encryption Architecture

## Overview

The encryption system uses **envelope encryption** with clearly separated responsibilities:
1. **Data Encryption**: Encrypt file data with a random DEK (Data Encryption Key)
2. **Envelope Creation**: Encrypt the DEK with a master key for secure storage

## Key Hierarchy

```
Master Key (AES-256, stored in config)
    ↓ encrypts
Data Encryption Key (DEK) - random per file
    ↓ encrypts
File Data (plaintext)
```

## Architecture Principles

### Separation of Concerns

The system separates data encryption from key encryption:

1. **`encryptWithDekStreaming()`** - Encrypts file data with random DEK
   - Generates random 256-bit DEK
   - Encrypts data in streaming mode (8KB chunks)
   - Returns plaintext DEK and encrypted size

2. **`createEnvelopeDek()`** - Creates envelope for the DEK
   - Encrypts DEK with master key
   - Returns encrypted DEK as base64
   - DEK is never stored in plaintext

### Why This Separation?

**Flexibility**: You can encrypt data and create envelope separately
**Streaming**: Data encryption happens in chunks without loading full file
**Security**: Explicit control over when to encrypt the DEK
**Clarity**: Each method has a single, clear responsibility

## Data Flow

### Upload Process

```
1. Angular Frontend (Encrypted with passphrase)
        ↓
2. Verify & Decrypt (verifyAndDecryptStreaming)
        ↓ writes to temp file 1
3. Encrypt with DEK (encryptWithDekStreaming)
        ↓ writes to temp file 2
        ↓ returns plaintext DEK
4. Create Envelope (createEnvelopeDek)
        ↓ encrypts DEK with master key
        ↓ returns encrypted DEK
5. Store to S3
        ↓ encrypted file data
        ↓ metadata.json with encrypted DEK
```

### Download/Decrypt Process

```
1. Fetch from S3
        ↓ encrypted file data
        ↓ metadata.json with encrypted DEK
2. Decrypt DEK (decryptKekWithMasterKey - internal)
        ↓ uses master key
        ↓ returns plaintext DEK
3. Decrypt File Data (decryptWithKek)
        ↓ uses plaintext DEK
        ↓ returns plaintext file
```

## CryptoService Methods

### Public Methods

#### 1. `verifyAndDecryptStreaming(InputStream, OutputStream)`
**Purpose**: Verify and decrypt data from Angular frontend

**Input**: 
- Encrypted data from Angular (format: `[salt][iv][ciphertext]`)

**Output**: 
- `StreamingDecryptionResult` (size, verified status)
- Decrypted data written to output stream

**Process**:
1. Read salt and IV from input stream
2. Derive key from passphrase using PBKDF2
3. Stream decrypt data in 8KB chunks
4. Verify GCM authentication tag

---

#### 2. `encryptWithDekStreaming(InputStream, OutputStream)`
**Purpose**: Encrypt plaintext data with a random DEK

**Input**:
- Plaintext data from input stream

**Output**:
- `StreamingDataEncryptionResult` containing:
  - `dek` (32 bytes, plaintext) - The encryption key used
  - `encryptedSize` - Size of encrypted output
- Encrypted data written to output stream

**Process**:
1. Generate random 256-bit DEK
2. Generate random IV
3. Write IV to output stream
4. Stream encrypt data in 8KB chunks
5. Return plaintext DEK (caller must encrypt it)

**Format**: `[12 bytes IV][encrypted data][16 bytes GCM tag]`

---

#### 3. `createEnvelopeDek(byte[] dek)`
**Purpose**: Create envelope by encrypting DEK with master key

**Input**:
- `dek` - The plaintext DEK to encrypt (32 bytes)

**Output**:
- Base64-encoded encrypted DEK

**Process**:
1. Load master key from config
2. Generate random IV
3. Encrypt DEK with master key using AES-GCM
4. Combine IV + encrypted DEK
5. Return as base64 string

**Format**: `[12 bytes IV][encrypted DEK][16 bytes GCM tag]` (base64-encoded)

---

#### 4. `decryptWithKek(byte[] encryptedData, String encryptedDekBase64)`
**Purpose**: Decrypt file data using encrypted DEK from metadata

**Input**:
- `encryptedData` - The encrypted file data
- `encryptedDekBase64` - Encrypted DEK from metadata.json

**Output**:
- Decrypted plaintext data

**Process**:
1. Decrypt DEK using master key (internal call)
2. Extract IV from encrypted data
3. Decrypt data with DEK
4. Clear sensitive keys from memory
5. Return plaintext data

---

### Private/Internal Methods

#### `encryptKekWithMasterKey(byte[] kek)`
Encrypts a DEK/KEK with the master key. Used internally by `createEnvelopeDek()`.

#### `decryptKekWithMasterKey(String encryptedKekBase64)`
Decrypts a DEK/KEK using the master key. Used internally by `decryptWithKek()`.

#### `encryptWithDataKey(byte[] data, byte[] dataKey)`
Low-level encryption with a given key. Used internally.

#### `decryptWithPassphrase(byte[] ciphertext, String passphrase, byte[] salt, byte[] iv)`
Decrypts Angular-encrypted data. Used internally.

## Usage Example

### Encrypting and Storing a File

```java
// Step 1: Decrypt from Angular
StreamingDecryptionResult decryptResult = 
    cryptoService.verifyAndDecryptStreaming(angularEncryptedInput, tempDecryptedOutput);

// Step 2: Encrypt with random DEK (streaming)
StreamingDataEncryptionResult dekResult = 
    cryptoService.encryptWithDekStreaming(tempDecryptedInput, encryptedOutput);

// Step 3: Create envelope (encrypt DEK with master key)
String encryptedDek = cryptoService.createEnvelopeDek(dekResult.dek);

// Step 4: Clear sensitive DEK from memory
Arrays.fill(dekResult.dek, (byte) 0);

// Step 5: Store encrypted data and encrypted DEK
s3.putObject(dataRequest, encryptedOutput);
EnvelopeMetadata metadata = new EnvelopeMetadata(encryptedDek, ...);
s3.putObject(metadataRequest, metadata);
```

### Decrypting a File

```java
// Step 1: Fetch encrypted data and metadata from S3
byte[] encryptedData = s3.getObject(dataKey);
EnvelopeMetadata metadata = s3.getObject(metadataKey);

// Step 2: Decrypt file using encrypted DEK from metadata
byte[] plaintextData = cryptoService.decryptWithKek(
    encryptedData, 
    metadata.kek  // This is the encrypted DEK
);
```

## Security Features

### Key Management
- **Master Key**: Stored in config, never changes, encrypts DEKs
- **DEK**: Random per file, encrypts file data
- **DEK Storage**: Always encrypted with master key, never in plaintext

### Memory Security
- Sensitive keys cleared from memory after use (`Arrays.fill`)
- Temporary files automatically cleaned up
- Streaming prevents full data in memory

### Encryption Strength
- **Algorithm**: AES-256-GCM
- **Key Size**: 256 bits (32 bytes)
- **IV**: Random 12 bytes per encryption
- **Authentication**: GCM tag (16 bytes) prevents tampering

## Format Specifications

### Angular Encrypted Data (Input)
```
[16 bytes Salt][12 bytes IV][Ciphertext + 16 bytes GCM Tag]
```

### DEK-Encrypted Data (Stored in S3)
```
[12 bytes IV][Ciphertext + 16 bytes GCM Tag]
```

### Encrypted DEK (Stored in metadata.json)
```
Base64([12 bytes IV][Encrypted DEK + 16 bytes GCM Tag])
```

### Metadata JSON Structure
```json
{
  "version": "1.0",
  "kek": "base64-encoded encrypted DEK",
  "algorithm": "AES-GCM-256",
  "original_filename": "file.mp3",
  "original_size": 1048576,
  "encrypted_size": 1048604,
  "verification_status": "VERIFIED",
  "timestamp": 1706140800000
}
```

## Benefits

### 1. **Explicit Control**
You decide exactly when to encrypt the DEK:
- Can encrypt data first, then decide whether to create envelope
- Can re-encrypt DEK with different master key without re-encrypting data

### 2. **Streaming Efficiency**
- Only 8KB buffer in memory at any time
- Can handle files larger than available RAM
- Suitable for containerized environments

### 3. **Security**
- DEK never stored in plaintext
- Keys cleared from memory after use
- Master key rotation possible without re-encrypting all data

### 4. **Testability**
- Each method can be tested independently
- Clear separation of concerns
- Easy to mock for unit tests

## Configuration

### Master Key Generation

Generate a secure master key:

```bash
# Generate 256-bit key (32 bytes)
openssl rand -base64 32
```

Set in `application.properties`:
```properties
encryption.master.key=YOUR_BASE64_MASTER_KEY_HERE
```

### Important Notes

⚠️ **Master Key Security**:
- Never commit master key to version control
- Use environment variables or secret management
- Rotate periodically and re-encrypt DEKs
- Backup securely - lost master key = lost data access

⚠️ **DEK Lifecycle**:
- Generate new DEK per file
- Never reuse DEKs across files
- Clear from memory immediately after use
- Store only in encrypted form
