# S3 Storage Service Refactoring

## Overview

The S3-related code has been refactored from `FileUploadResource` into a dedicated utility class `S3StorageService` in the `me.cresterida.util` package.

## New Structure

```
me.cresterida/
├── FileUploadResource.java     (refactored - uses S3StorageService)
├── CryptoService.java
├── EnvelopeMetadata.java
├── dto/
│   ├── ErrorResponse.java
│   ├── PassphraseRequest.java
│   ├── PassphraseResponse.java
│   └── UploadResponse.java
└── util/                        (NEW PACKAGE)
    └── S3StorageService.java    (NEW CLASS)
```

## S3StorageService Class

### Purpose
Centralized utility class for all S3/Tigris storage operations.

### Features

#### 1. **Key Generation**
```java
public UploadResult generateKeys(String email, String fileName, String fileId)
```
Generates S3 object keys for data and metadata following the pattern:
- Data: `uploads/{email}/{fileId}/{fileName}.enc`
- Metadata: `uploads/{email}/{fileId}/metadata.json`

#### 2. **Upload Encrypted File**
```java
public void uploadEncryptedFile(String key, Path filePath, long fileSize)
```
Uploads an encrypted file to S3 with:
- Content type: `application/octet-stream`
- Streaming from file path
- Content length specified

#### 3. **Upload Metadata**
```java
public void uploadMetadata(String key, String metadataJson)
```
Uploads metadata JSON to S3 with:
- Content type: `application/json`
- String content

#### 4. **Combined Upload**
```java
public UploadResult uploadFileAndMetadata(
    String email, 
    String fileName, 
    String fileId,
    Path encryptedFilePath, 
    long encryptedFileSize,
    String metadataJson)
```
Convenience method that:
1. Generates keys
2. Uploads encrypted file
3. Uploads metadata
4. Returns the UploadResult with keys used

### Inner Class: UploadResult

```java
public static class UploadResult {
    public String dataKey;
    public String metadataKey;
}
```

Holds the S3 keys for uploaded objects.

## FileUploadResource Changes

### Before (Direct S3 Operations)

```java
@Inject
S3Client s3;

@ConfigProperty(name = "bucket.name")
String bucketName;

// ... in upload method ...

String dataKey = "uploads/" + email + "/" + fileId + "/" + baseFileName + ".enc";
String metadataKey = "uploads/" + email + "/" + fileId + "/metadata.json";

PutObjectRequest dataRequest = PutObjectRequest.builder()
    .bucket(bucketName)
    .key(dataKey)
    .contentType("application/octet-stream")
    .contentLength(dekResult.encryptedSize)
    .build();
s3.putObject(dataRequest, RequestBody.fromFile(tempEncrypted));

PutObjectRequest metadataRequest = PutObjectRequest.builder()
    .bucket(bucketName)
    .key(metadataKey)
    .contentType("application/json")
    .build();
s3.putObject(metadataRequest, RequestBody.fromString(metadataJson));
```

### After (Using S3StorageService)

```java
@Inject
S3StorageService s3StorageService;

// ... in upload method ...

S3StorageService.UploadResult uploadResult = s3StorageService.uploadFileAndMetadata(
    email,
    file.fileName(),
    fileId,
    tempEncrypted,
    dekResult.encryptedSize,
    metadataJson
);

// Use uploadResult.dataKey and uploadResult.metadataKey
```

## Benefits

### 1. **Separation of Concerns**
- S3 logic separated from REST endpoint logic
- Resource class focuses on request handling
- Storage service focuses on S3 operations

### 2. **Reusability**
- S3 operations can be used from multiple places
- Can be used in batch operations, scheduled jobs, etc.
- Easy to add more storage-related methods

### 3. **Testability**
- S3StorageService can be mocked easily
- Unit tests can test upload logic without S3
- Integration tests can test S3 operations separately

### 4. **Maintainability**
- All S3 configuration in one place
- Easier to change S3 key patterns
- Easier to add features like versioning, tagging, etc.

### 5. **Consistency**
- All S3 uploads use the same patterns
- Content types are consistently set
- Logging is centralized

## Usage Examples

### Basic Upload

```java
@Inject
S3StorageService s3StorageService;

// Upload with combined method
S3StorageService.UploadResult result = s3StorageService.uploadFileAndMetadata(
    "user@example.com",
    "song.mp3.encrypted",
    UUID.randomUUID().toString(),
    encryptedFilePath,
    fileSize,
    metadataJsonString
);

System.out.println("Data uploaded to: " + result.dataKey);
System.out.println("Metadata uploaded to: " + result.metadataKey);
```

### Step-by-Step Upload

```java
// Generate keys
S3StorageService.UploadResult keys = s3StorageService.generateKeys(
    email, fileName, fileId
);

// Upload data
s3StorageService.uploadEncryptedFile(
    keys.dataKey, 
    encryptedFilePath, 
    fileSize
);

// Upload metadata
s3StorageService.uploadMetadata(
    keys.metadataKey, 
    metadataJson
);
```

### Get Bucket Name

```java
String bucket = s3StorageService.getBucketName();
```

## Configuration

The S3StorageService uses the same configuration as before:

```properties
# application.properties
bucket.name=whisper-uploads

# S3/Tigris configuration
quarkus.s3.endpoint-override=...
quarkus.s3.aws.credentials.type=...
```

## S3 Key Pattern

Files are organized by email and file ID:

```
uploads/
  ├── user1@example.com/
  │   ├── uuid-1/
  │   │   ├── song.enc          (encrypted file data)
  │   │   └── metadata.json     (envelope metadata)
  │   └── uuid-2/
  │       ├── podcast.enc
  │       └── metadata.json
  └── user2@example.com/
      └── uuid-3/
          ├── audio.enc
          └── metadata.json
```

## Future Enhancements

The S3StorageService can be easily extended with:

1. **Download Operations**
   ```java
   public byte[] downloadEncryptedFile(String key)
   public String downloadMetadata(String key)
   ```

2. **List Operations**
   ```java
   public List<String> listUserFiles(String email)
   ```

3. **Delete Operations**
   ```java
   public void deleteFile(String dataKey, String metadataKey)
   ```

4. **Versioning Support**
   ```java
   public void enableVersioning()
   public List<Version> getFileVersions(String key)
   ```

5. **Pre-signed URLs**
   ```java
   public String generatePresignedUrl(String key, Duration expiration)
   ```

6. **Metadata Tags**
   ```java
   public void uploadWithTags(String key, Path file, Map<String, String> tags)
   ```

7. **Multipart Upload (for very large files)**
   ```java
   public void uploadLargeFile(String key, Path file)
   ```

## Testing

### Unit Test Example

```java
@QuarkusTest
class S3StorageServiceTest {
    
    @Inject
    S3StorageService s3StorageService;
    
    @Test
    void testKeyGeneration() {
        UploadResult result = s3StorageService.generateKeys(
            "test@example.com",
            "file.mp3.encrypted",
            "test-uuid"
        );
        
        assertEquals("uploads/test@example.com/test-uuid/file.enc", result.dataKey);
        assertEquals("uploads/test@example.com/test-uuid/metadata.json", result.metadataKey);
    }
}
```

### Mock for Unit Tests

```java
@Mock
S3StorageService mockS3Service;

@Test
void testUpload() {
    // Mock S3 operations
    UploadResult mockResult = new UploadResult("key1", "key2");
    when(mockS3Service.uploadFileAndMetadata(...)).thenReturn(mockResult);
    
    // Test your code
    // ...
}
```

## Migration Guide

If you have existing code that uses S3Client directly:

### Step 1: Inject S3StorageService
```java
@Inject
S3StorageService s3StorageService;
```

### Step 2: Replace Direct S3 Calls
```java
// OLD
s3.putObject(request, body);

// NEW
s3StorageService.uploadEncryptedFile(key, path, size);
```

### Step 3: Use Helper Methods
```java
// OLD
String key = "uploads/" + email + "/" + id + "/" + name;

// NEW
UploadResult result = s3StorageService.generateKeys(email, name, id);
String key = result.dataKey;
```

## Summary

✅ **Created**: `me.cresterida.util.S3StorageService`  
✅ **Refactored**: `FileUploadResource` to use S3StorageService  
✅ **Removed**: Direct S3Client usage from resource class  
✅ **Maintained**: All existing functionality  
✅ **Improved**: Code organization and testability  

The refactoring is complete and all code compiles successfully!
