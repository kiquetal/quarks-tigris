# Decrypt API Documentation

## Overview

The Decrypt API allows you to retrieve and decrypt files that were previously uploaded to the system. The decrypted files are streamed back to the client and, in development mode, also saved to a local folder.

## Endpoint

### GET `/api/decrypt`

Decrypt and download a file from S3/Tigris storage.

**Query Parameters:**
- `email` (required) - User email used during upload
- `uuid` (required) - Unique file identifier (UUID) from upload response

**Response:**
- **200 OK**: File decrypted successfully (binary stream)
- **400 Bad Request**: Missing or invalid parameters
- **404 Not Found**: File not found in storage
- **500 Internal Server Error**: Decryption failed

## Usage Examples

### cURL

```bash
# Download decrypted file
curl -X GET "http://localhost:8080/whisper/api/decrypt?email=user@example.com&uuid=abc-123-def" \
  -o decrypted-file.mp3
```

### HTTP Request

```http
GET http://localhost:8080/whisper/api/decrypt?email=user@example.com&uuid=abc-123-def
```

### Browser

Simply navigate to:
```
http://localhost:8080/whisper/api/decrypt?email=user@example.com&uuid=abc-123-def
```

The file will download automatically.

## How It Works

### Decryption Flow

```
1. Client requests file with email + UUID
   ↓
2. DecryptResource retrieves metadata from S3
   ↓
3. DecryptResource retrieves encrypted file data from S3
   ↓
4. CryptoService decrypts the file using envelope encryption:
   - Decrypt DEK with master key
   - Decrypt file data with DEK
   ↓
5. In DEV mode: Save to ./downloads/{email}/{uuid}/
   ↓
6. Stream decrypted file to client
```

### Storage Structure

Files are organized in S3 as:
```
uploads/{email}/{uuid}/
  ├── {filename}.enc          # Encrypted file data
  └── metadata.json           # Envelope metadata with encrypted DEK
```

## Development Mode

When running in **dev mode** (`quarkus.profile=dev`), decrypted files are automatically saved to the local filesystem:

```
downloads/
  └── {email}/
      └── {uuid}/
          └── {original-filename}
```

**Configuration:**

```properties
# application.properties
decrypt.download.path=./downloads
```

You can change the download path using environment variable:
```bash
DECRYPT_DOWNLOAD_PATH=/path/to/downloads ./mvnw quarkus:dev
```

## Security Considerations

### Envelope Encryption

The system uses **envelope encryption** for maximum security:

1. **Data Encryption**: Each file is encrypted with a unique random DEK (Data Encryption Key)
2. **Envelope**: The DEK is encrypted with the master key and stored in metadata
3. **Decryption**: 
   - Master key decrypts the DEK
   - DEK decrypts the file data

### Master Key

The master key is configured via:
```properties
encryption.master.key=${ENCRYPTION_MASTER_KEY}
```

⚠️ **Important**: The master key must be the same key used during upload. If you lose the master key, files cannot be decrypted.

### Access Control

Currently, the decrypt endpoint requires:
- Valid email
- Valid UUID

**Future Enhancement**: Add authentication/authorization to restrict access.

## Error Handling

### File Not Found (404)

```json
{
  "message": "File not found: NoSuchKey: The specified key does not exist"
}
```

**Possible causes:**
- Incorrect UUID
- Incorrect email
- File was deleted
- Wrong S3 bucket

### Decryption Failed (500)

```json
{
  "message": "Decryption failed: Authentication tag verification failed"
}
```

**Possible causes:**
- Wrong master key
- Corrupted file data
- Corrupted metadata
- File was modified after upload

### Invalid Parameters (400)

```json
{
  "message": "Email is required"
}
```

```json
{
  "message": "UUID is required"
}
```

## Testing

### 1. Upload a File First

```bash
# Encrypt a file using the Angular frontend
# Note the UUID from the response
```

### 2. Decrypt the File

```bash
curl -X GET "http://localhost:8080/whisper/api/decrypt?email=test@example.com&uuid=YOUR-UUID" \
  -o downloaded-file.mp3
```

### 3. Verify in Dev Mode

Check the downloads folder:
```bash
ls -lh downloads/test@example.com/YOUR-UUID/
```

## Integration with Frontend

You can add a download button in the Angular frontend:

```typescript
// In a component
downloadFile(email: string, uuid: string) {
  const url = `/whisper/api/decrypt?email=${email}&uuid=${uuid}`;
  window.location.href = url;
}
```

Or using HttpClient for more control:

```typescript
downloadFile(email: string, uuid: string) {
  this.http.get(`/whisper/api/decrypt`, {
    params: { email, uuid },
    responseType: 'blob'
  }).subscribe(blob => {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'decrypted-file.mp3';
    a.click();
  });
}
```

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `decrypt.download.path` | `./downloads` | Local folder for saving decrypted files (dev mode) |
| `quarkus.profile` | `dev` | Profile determines if local save is enabled |

## API Response Headers

Successful response includes:
```
Content-Disposition: attachment; filename="original-file.mp3"
Content-Type: application/octet-stream
Content-Length: <size-in-bytes>
```

## Limitations

- Maximum file size: Limited by available memory during decryption
- Concurrent downloads: Limited by server resources
- No resume support: Downloads must complete in one request

## Future Enhancements

- [ ] Add authentication/authorization
- [ ] Support streaming decryption for very large files
- [ ] Add download progress tracking
- [ ] Add file listing endpoint
- [ ] Add file deletion endpoint
- [ ] Support partial content (HTTP 206) for resume
