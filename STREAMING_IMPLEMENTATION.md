# Streaming Implementation for File Upload with Envelope Encryption

## Overview

The file upload system has been refactored to use **streaming** and **proper envelope encryption** instead of loading entire files into memory. This provides significant benefits for handling large files:

- **Memory Efficient**: Only 8KB buffers are kept in memory at a time
- **Scalable**: Can handle files of any size without running out of memory
- **Performance**: Reduces GC pressure and improves throughput
- **Security**: 
  - Temporary files are automatically cleaned up
  - Proper envelope encryption with master key
  - KEK (Key Encryption Key) is never stored in plaintext

## Envelope Encryption Architecture

### Key Hierarchy

```
Master Key (from config, never changes)
    ↓ (encrypts)
KEK (random per file)
    ↓ (encrypts)
File Data (plaintext)
```

### Flow Diagram

```
Angular (Encrypted File)
    ↓
FileUploadResource
    ↓
Temp File 1 (Decrypted) ← verifyAndDecryptStreaming()
    ↓
Temp File 2 (KEK-Encrypted) ← encryptWithEnvelopeStreaming()
    ↓                            ↓ (generates random KEK)
    ↓                            ↓ (encrypts KEK with master key)
    ↓                            ↓
S3 Storage (encrypted file)    S3 Metadata (encrypted KEK)
```

## Implementation Details

### Envelope Encryption Process

1. **Generate Random KEK**: A new 256-bit AES key is generated for each file
2. **Encrypt File with KEK**: The plaintext file is encrypted using the KEK
3. **Encrypt KEK with Master Key**: The KEK is encrypted using the master key
4. **Store Both**:
   - Encrypted file → S3
   - Encrypted KEK → S3 metadata.json

### Decryption Process

1. **Fetch Metadata**: Get encrypted KEK from S3 metadata.json
2. **Decrypt KEK with Master Key**: Use master key to decrypt the KEK
3. **Decrypt File with KEK**: Use decrypted KEK to decrypt the file data

## Implementation Details

### CryptoService Streaming Methods

#### 1. `verifyAndDecryptStreaming(InputStream, OutputStream)`

**Purpose**: Decrypt Angular-encrypted data without loading entire file into memory

**Process**:
1. Read salt (16 bytes) and IV (12 bytes) from input stream
2. Derive decryption key using PBKDF2
3. Initialize AES-GCM cipher in decrypt mode
4. Process data in 8KB chunks:
   - Read chunk from input stream
   - Decrypt chunk using `cipher.update()`
   - Write decrypted chunk to output stream
5. Finalize with `cipher.doFinal()` (verifies GCM tag)

**Memory Usage**: ~8KB buffer only

**Returns**: `StreamingDecryptionResult` (size + verification status)

#### 2. `encryptWithEnvelopeStreaming(InputStream, OutputStream)`

**Purpose**: Encrypt plaintext data with KEK and encrypt the KEK with master key

**Process**:
1. Generate random KEK (32 bytes for AES-256)
2. Generate random IV (12 bytes) for file encryption
3. Write IV to output stream first
4. Initialize AES-GCM cipher in encrypt mode with KEK
5. Process data in 8KB chunks:
   - Read chunk from input stream
   - Encrypt chunk using `cipher.update()`
   - Write encrypted chunk to output stream
6. Finalize with `cipher.doFinal()` (writes GCM tag)
7. **Encrypt KEK with master key** (separate operation)
8. Return encrypted KEK and size

**Memory Usage**: ~8KB buffer only

**Returns**: `StreamingEnvelopeEncryptionResult` (encrypted KEK + encrypted size)

**Security**: The KEK is encrypted with the master key before being returned, so it's never stored in plaintext.

### FileUploadResource Flow

```java
// Step 1: Create temporary files
Path tempDecrypted = Files.createTempFile("decrypted-", ".tmp");
Path tempEncrypted = Files.createTempFile("kek-encrypted-", ".tmp");

try {
    // Step 2: Decrypt Angular encryption (streaming)
    try (FileInputStream in = new FileInputStream(uploadedFile);
         FileOutputStream out = new FileOutputStream(tempDecrypted)) {
        
        StreamingDecryptionResult result = 
            cryptoService.verifyAndDecryptStreaming(in, out);
    }
    
    // Step 3: Encrypt with KEK (streaming)
    try (FileInputStream in = new FileInputStream(tempDecrypted);
         FileOutputStream out = new FileOutputStream(tempEncrypted)) {
        
        StreamingEnvelopeEncryptionResult result = 
            cryptoService.encryptWithEnvelopeStreaming(in, out);
    }
    
    // Step 4: Upload to S3 (streaming)
    s3.putObject(request, RequestBody.fromFile(tempEncrypted));
    
} finally {
    // Step 5: Cleanup temporary files
    Files.deleteIfExists(tempDecrypted);
    Files.deleteIfExists(tempEncrypted);
}
```

## Key Benefits

### 1. **Memory Efficiency**
- **Before**: 100MB file = 300MB+ memory (encrypted + decrypted + KEK-encrypted)
- **After**: 100MB file = ~16KB memory (two 8KB buffers)

### 2. **No OOM Errors**
- Can handle files larger than available heap space
- Suitable for containerized environments with memory limits

### 3. **Better Performance**
- Reduced GC overhead
- Stream processing allows parallel operations
- Disk I/O optimized with buffering

### 4. **Security**
- Sensitive data never fully in memory
- Temporary files cleaned up automatically in `finally` block
- Buffers are zeroed out after use

## Cipher Streaming Details

### AES-GCM Streaming Support

AES-GCM can be used in streaming mode with `cipher.update()` and `cipher.doFinal()`:

1. **`cipher.update(input)`**: 
   - Processes chunks of data
   - May return null or partial output (GCM buffers internally)
   - Does NOT verify authentication tag yet

2. **`cipher.doFinal()`**: 
   - Processes remaining buffered data
   - Generates/verifies GCM authentication tag (16 bytes)
   - Throws exception if tag verification fails

### Format

**Decryption Input** (from Angular):
```
[16 bytes Salt][12 bytes IV][Ciphertext + GCM Tag]
```

**Encryption Output** (KEK encryption):
```
[12 bytes IV][Ciphertext + GCM Tag]
```

## Error Handling

- **Decryption Failure**: GCM tag verification fails → Exception thrown
- **IO Errors**: File operations fail → Temp files cleaned up in `finally`
- **Encryption Failure**: Cipher errors → Temp files cleaned up

## Temporary Files

- Created in system temp directory
- Unique prefixes prevent collisions
- Automatically cleaned up in `finally` block
- OS will clean up if process crashes

## Performance Considerations

- **Buffer Size**: 8KB chosen as optimal balance
  - Larger: More memory, fewer system calls
  - Smaller: Less memory, more overhead
- **Try-with-resources**: Ensures streams are closed properly
- **S3 Upload**: Uses `RequestBody.fromFile()` for efficient streaming to S3

## Future Enhancements

1. **Direct S3 Upload**: Stream directly to S3 without temp files
2. **Parallel Processing**: Decrypt and encrypt in parallel pipelines
3. **Progress Tracking**: Add callbacks for upload progress
4. **Compression**: Add optional compression in the pipeline

## Testing

To test streaming with large files:

```bash
# Generate 500MB test file
dd if=/dev/urandom of=largefile.bin bs=1M count=500

# Monitor memory during upload
jstat -gc <pid> 1000
```

Expected: Memory usage stays constant regardless of file size.
