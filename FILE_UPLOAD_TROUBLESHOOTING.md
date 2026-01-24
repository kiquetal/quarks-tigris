# File Upload Troubleshooting Guide

## Issue: "File is too large" Error

If you're seeing this error, here's how to diagnose and fix it.

## Changes Applied

### 1. Backend Configuration (application.properties)

Added file size limits:
```properties
# Multipart/File Upload Configuration
quarkus.http.limits.max-body-size=100M
quarkus.resteasy-reactive.multipart.file-content-types=audio/mpeg,audio/mp3,application/octet-stream
quarkus.resteasy-reactive.multipart.input-part.default-content-type=application/octet-stream
```

**Max body size**: 100MB
**Allowed content types**: MP3 audio files

### 2. Backend Validation (FileUploadResource.java)

Added file size validation:
```java
// Check file size (50MB limit)
long maxFileSize = 50 * 1024 * 1024; // 50MB in bytes
if (file.size() > maxFileSize) {
    return Response.status(413)
            .entity(new ErrorResponse("File is too large. Maximum size is 50MB."))
            .build();
}
```

**File size limit**: 50MB per file

### 3. Frontend Logging (mp3-upload.ts)

Added detailed logging to help diagnose issues:
```typescript
onFileSelected(event: any) {
  this.selectedFile = event.target.files[0];
  if (this.selectedFile) {
    console.log('File selected:', {
      name: this.selectedFile.name,
      size: this.selectedFile.size,
      sizeInMB: (this.selectedFile.size / (1024 * 1024)).toFixed(2) + ' MB',
      type: this.selectedFile.type
    });
  }
}
```

## Diagnosing the Problem

### Step 1: Check Browser Console

Open browser console (F12) and look for logs when selecting a file:

```
File selected: {
  name: "my-audio.mp3",
  size: 5242880,
  sizeInMB: "5.00 MB",
  type: "audio/mpeg"
}
```

**Check**:
- ✅ File size in MB
- ✅ File type

### Step 2: Attempt Upload and Check Logs

When you click Upload, you should see:

```
Starting upload...
File: my-audio.mp3 Size: 5242880 bytes
Email: test@example.com
```

If error occurs:
```
Upload failed: HttpErrorResponse {...}
Error status: 413
Error status text: Payload Too Large
Error body: {message: "File is too large. Maximum size is 50MB."}
```

### Step 3: Check Backend Logs

In your Quarkus terminal, you should see:
```
Uploading file: my-audio.mp3 (5242880 bytes) for email: test@example.com
```

## File Size Limits

### Current Configuration

| Component | Limit | Reason |
|-----------|-------|--------|
| **Quarkus HTTP** | 100MB | Maximum request body size |
| **Backend Validation** | 50MB | Reasonable MP3 file size |
| **Browser** | No limit | Handled by backend |

### Why 50MB?

- ✅ Typical MP3 songs: 3-10 MB
- ✅ High-quality hour-long podcast: ~30-50 MB
- ✅ Prevents abuse
- ✅ S3 upload time reasonable

## Common Issues and Solutions

### Issue 1: File Larger Than 50MB

**Error**: "File is too large. Maximum size is 50MB."  
**Status Code**: 413

**Solution**: Either:
1. Compress the MP3 file (reduce bitrate)
2. Increase limit in `FileUploadResource.java`:
   ```java
   long maxFileSize = 100 * 1024 * 1024; // 100MB
   ```

### Issue 2: File Larger Than 100MB

**Error**: Request too large  
**Status Code**: 413 (from Quarkus)

**Solution**: Increase in `application.properties`:
```properties
quarkus.http.limits.max-body-size=200M
```

### Issue 3: Wrong File Type

**Error**: "Upload failed: Invalid request"  
**Status Code**: 400

**Frontend**: File input only accepts `.mp3` files:
```html
<input type="file" accept=".mp3,audio/mpeg" />
```

**Backend**: Accepts:
- `audio/mpeg`
- `audio/mp3`
- `application/octet-stream`

### Issue 4: Missing Email

**Error**: "Upload failed: Email is required"  
**Status Code**: 400

**Solution**: Ensure email field is filled before uploading.

### Issue 5: Network Timeout

**Error**: "Cannot connect to server"  
**Status Code**: 0

**Cause**: Upload taking too long (large file + slow connection)

**Solution**: Increase timeout or reduce file size.

## Testing Different Scenarios

### Test 1: Small File (< 50MB)

1. Select an MP3 file < 50MB
2. Enter email
3. Click Upload

**Expected**:
```
Console: Starting upload...
Console: File: song.mp3 Size: 3145728 bytes
Backend: Uploading file: song.mp3 (3145728 bytes)
Alert: File uploaded successfully!
```

### Test 2: Large File (> 50MB but < 100MB)

1. Select an MP3 file > 50MB
2. Enter email
3. Click Upload

**Expected**:
```
Console: Starting upload...
Console: Error status: 413
Alert: File is too large. Please select a smaller file.
```

### Test 3: Very Large File (> 100MB)

1. Select a file > 100MB
2. Enter email
3. Click Upload

**Expected**:
- Quarkus rejects before reaching backend
- Status: 413
- Alert: File is too large

### Test 4: No File Selected

1. Don't select a file
2. Button should be disabled

**Expected**: Upload button is disabled until file is selected

### Test 5: No Email

1. Select a file
2. Don't enter email
3. Button should be disabled

**Expected**: Upload button is disabled until email is entered

## Adjusting File Size Limits

### To Allow Larger Files

1. **Backend validation** (`FileUploadResource.java`):
   ```java
   long maxFileSize = 100 * 1024 * 1024; // Change to 100MB
   ```

2. **HTTP limit** (`application.properties`):
   ```properties
   quarkus.http.limits.max-body-size=200M  # Change to 200M
   ```

3. **Restart Quarkus**: Changes will be picked up by hot reload

### To Restrict Files Further

1. **Reduce backend limit**:
   ```java
   long maxFileSize = 10 * 1024 * 1024; // 10MB only
   ```

2. **Add frontend validation** (`mp3-upload.ts`):
   ```typescript
   onFileSelected(event: any) {
     this.selectedFile = event.target.files[0];
     if (this.selectedFile) {
       const maxSize = 10 * 1024 * 1024; // 10MB
       if (this.selectedFile.size > maxSize) {
         alert('File is too large. Maximum size is 10MB.');
         this.selectedFile = null;
         return;
       }
     }
   }
   ```

## Current Status

✅ **Backend**: Validates file size (50MB limit)  
✅ **Quarkus**: Accepts up to 100MB requests  
✅ **Frontend**: Logs file size for debugging  
✅ **Error handling**: Shows appropriate messages  

## Files Modified

1. ✅ `src/main/resources/application.properties` - Added HTTP limits
2. ✅ `src/main/java/me/cresterida/FileUploadResource.java` - Added validation
3. ✅ `src/main/webui/src/app/mp3-upload/mp3-upload.ts` - Added logging

## Next Steps

1. **Check console logs** when selecting a file
2. **Try uploading** and check the error status code
3. **Adjust limits** if needed based on your requirements
4. **Test with different file sizes** to verify limits work

## Summary

The application now has proper file size handling:

- **Frontend**: Logs file size for debugging
- **Backend**: Validates and rejects files > 50MB with clear error message
- **Quarkus**: Accepts requests up to 100MB
- **Error handling**: Returns 413 with descriptive message

If you're still seeing "File is too large", check the console logs to see the actual file size and adjust limits accordingly!
