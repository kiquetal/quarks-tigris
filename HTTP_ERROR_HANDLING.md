# HTTP Error Handling Guide

## Overview

The Angular frontend now properly distinguishes between different HTTP error status codes, treating only 5xx errors as server errors.

## Status Code Classification

### 1xx - Informational
Not typically handled in error callbacks.

### 2xx - Success
Handled in the `next` callback.

### 3xx - Redirection
Usually handled automatically by HttpClient.

### 4xx - Client Errors
These are **NOT server errors**. They indicate issues with the request itself.

#### Common 4xx Codes:
- **400 Bad Request**: Invalid request format
- **401 Unauthorized**: Authentication required
- **403 Forbidden**: Valid request but access denied (e.g., wrong passphrase)
- **404 Not Found**: Resource doesn't exist
- **413 Payload Too Large**: File too big
- **422 Unprocessable Entity**: Validation errors

**Handling**: Show specific user-friendly messages based on the error type.

### 5xx - Server Errors
These indicate problems on the server side.

#### Common 5xx Codes:
- **500 Internal Server Error**: Generic server error
- **502 Bad Gateway**: Server is down or unreachable
- **503 Service Unavailable**: Server temporarily unavailable
- **504 Gateway Timeout**: Server took too long to respond

**Handling**: Show generic "Server error" message and suggest trying again later.

### 0 - Network Errors
Status code 0 typically indicates:
- Cannot connect to server
- Network connectivity issues
- CORS errors (in development)

**Handling**: Show connection error message.

## Implementation

### Passphrase Component

```typescript
error: (err: HttpErrorResponse) => {
  console.error('Error validating passphrase:', err);
  
  if (err.status === 403) {
    // 403 Forbidden - invalid passphrase
    alert('Invalid passphrase');
  } else if (err.status >= 500) {
    // 5xx - Server error
    alert('Server error. Please try again later.');
  } else if (err.status === 0) {
    // Network error
    alert('Cannot connect to server. Please check your connection.');
  } else {
    // Other client errors (4xx)
    alert('Request failed. Please try again.');
  }
}
```

### Upload Component

```typescript
error: (err: HttpErrorResponse) => {
  console.error('Upload failed:', err);
  
  if (err.status === 403) {
    // 403 Forbidden - unauthorized
    alert('You are not authorized to upload files.');
  } else if (err.status === 413) {
    // 413 Payload Too Large
    alert('File is too large. Please select a smaller file.');
  } else if (err.status >= 500) {
    // 5xx - Server error
    alert('Server error. Please try again later.');
  } else if (err.status === 0) {
    // Network error
    alert('Cannot connect to server. Please check your connection.');
  } else if (err.status >= 400 && err.status < 500) {
    // Other client errors (4xx)
    alert('Upload failed: ' + (err.error?.message || 'Invalid request'));
  } else {
    alert('Upload failed. Please try again.');
  }
}
```

## Error Response Structure

Angular's `HttpErrorResponse` provides:

```typescript
{
  status: number,        // HTTP status code
  statusText: string,    // Status text (e.g., "Forbidden")
  error: any,           // Response body (if any)
  message: string,      // Error message
  name: string,         // "HttpErrorResponse"
  ok: false,           // Always false for errors
  url: string | null   // Request URL
}
```

## Best Practices

### ✅ DO

1. **Check status code explicitly**:
   ```typescript
   if (err.status === 403) { }
   ```

2. **Use specific ranges for server errors**:
   ```typescript
   if (err.status >= 500) { }
   ```

3. **Provide user-friendly messages**:
   ```typescript
   alert('Invalid passphrase'); // Clear and actionable
   ```

4. **Log errors for debugging**:
   ```typescript
   console.error('Error details:', err);
   ```

5. **Handle network errors (status 0)**:
   ```typescript
   if (err.status === 0) { }
   ```

### ❌ DON'T

1. **Don't treat all errors as server errors**:
   ```typescript
   // ❌ Wrong
   alert('Server error'); // for 403
   ```

2. **Don't ignore the status code**:
   ```typescript
   // ❌ Wrong
   alert('Error occurred'); // Generic, not helpful
   ```

3. **Don't show technical error messages to users**:
   ```typescript
   // ❌ Wrong
   alert(err.message); // Shows technical details
   ```

## Error Scenarios and Responses

### Scenario 1: Wrong Passphrase (403)
**Server Response**: `403 Forbidden`  
**User Message**: "Invalid passphrase"  
**Classification**: Client error (not server error)

### Scenario 2: Server Crash (500)
**Server Response**: `500 Internal Server Error`  
**User Message**: "Server error. Please try again later."  
**Classification**: Server error

### Scenario 3: File Too Large (413)
**Server Response**: `413 Payload Too Large`  
**User Message**: "File is too large. Please select a smaller file."  
**Classification**: Client error (not server error)

### Scenario 4: Network Offline (0)
**Server Response**: Status 0  
**User Message**: "Cannot connect to server. Please check your connection."  
**Classification**: Network error (not server error)

### Scenario 5: Bad Request (400)
**Server Response**: `400 Bad Request`  
**User Message**: "Request failed. Please try again."  
**Classification**: Client error (not server error)

## Testing Error Handling

### Test 403 Response
```bash
# Backend returns 403 for wrong passphrase
curl -X POST http://localhost:8080/whisper/api/validate-passphrase \
  -H "Content-Type: application/json" \
  -d '{"passphrase":"wrong"}' \
  -v
```

Expected: Angular shows "Invalid passphrase" (not "Server error")

### Test 500 Response
Modify backend to return 500:
```java
return Response.status(500).build();
```

Expected: Angular shows "Server error. Please try again later."

### Test Network Error
Turn off backend server or disconnect network.

Expected: Angular shows "Cannot connect to server. Please check your connection."

## Summary

✅ **403 Forbidden**: Client error - shows "Invalid passphrase"  
✅ **4xx errors**: Client errors - shows specific messages  
✅ **5xx errors**: Server errors - shows "Server error"  
✅ **Status 0**: Network errors - shows "Cannot connect"  

The frontend now correctly distinguishes between client errors (user's fault), server errors (backend's fault), and network errors (connection issues).

## Files Modified

- ✅ `src/main/webui/src/app/passphrase/passphrase.ts`
- ✅ `src/main/webui/src/app/mp3-upload/mp3-upload.ts`

Both components now use `HttpErrorResponse` and check status codes explicitly.
