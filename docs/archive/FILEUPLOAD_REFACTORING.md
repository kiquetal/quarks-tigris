# FileUploadResource Refactoring Summary

## What Changed

The `FileUploadResource` class has been refactored to follow better package organization by extracting all request/response DTOs into a separate package.

## New Package Structure

```
me.cresterida/
├── FileUploadResource.java (refactored - removed inner classes)
├── CryptoService.java
├── EnvelopeMetadata.java
└── dto/                     (NEW PACKAGE)
    ├── ErrorResponse.java
    ├── PassphraseRequest.java
    ├── PassphraseResponse.java
    └── UploadResponse.java
```

## New DTO Classes

### 1. `dto/ErrorResponse.java`
```java
public class ErrorResponse {
    public String message;
    public ErrorResponse(String message)
}
```
**Usage**: Error responses for API failures

---

### 2. `dto/PassphraseRequest.java`
```java
public class PassphraseRequest {
    public String passphrase;
}
```
**Usage**: Request body for `/api/validate-passphrase`

---

### 3. `dto/PassphraseResponse.java`
```java
public class PassphraseResponse {
    public boolean validated;
    public PassphraseResponse(boolean validated)
}
```
**Usage**: Response for passphrase validation

---

### 4. `dto/UploadResponse.java`
```java
public class UploadResponse {
    public String message;
    public String key;
    public boolean verified;
    public long originalSize;
    public UploadResponse(...)
}
```
**Usage**: Success response for `/api/upload`

---

## FileUploadResource Changes

### Before (Inner Classes)
```java
public class FileUploadResource {
    // ... methods ...
    
    public static class PassphraseRequest { ... }
    public static class PassphraseResponse { ... }
    public static class UploadResponse { ... }
    public static class ErrorResponse { ... }
}
```

### After (Clean Separation)
```java
// Imports from dto package
import me.cresterida.dto.ErrorResponse;
import me.cresterida.dto.PassphraseRequest;
import me.cresterida.dto.PassphraseResponse;
import me.cresterida.dto.UploadResponse;

public class FileUploadResource {
    // ... only methods, no inner classes ...
}
```

## Benefits

### 1. **Better Organization**
- DTOs are in their own package
- Easy to find and maintain
- Clear separation of concerns

### 2. **Reusability**
- DTOs can be reused across multiple resources
- Can be used in other packages/modules

### 3. **Testability**
- DTOs can be tested independently
- Easier to create test fixtures

### 4. **OpenAPI/Swagger**
- Schema annotations are preserved
- DTOs show up cleanly in API documentation

### 5. **Maintainability**
- Changes to DTOs don't clutter the resource class
- Each DTO is in its own file

## API Endpoints (Unchanged)

The API endpoints remain the same:

### POST `/api/validate-passphrase`
**Request**: `PassphraseRequest`
```json
{
  "passphrase": "your-secret-passphrase"
}
```

**Response**: `PassphraseResponse`
```json
{
  "validated": true
}
```

---

### POST `/api/upload`
**Request**: Multipart form data
- `file`: File upload
- `email`: String

**Success Response**: `UploadResponse`
```json
{
  "message": "File uploaded successfully with envelope encryption",
  "key": "uploads/email@test.com/uuid/file.enc",
  "verified": true,
  "originalSize": 1048576
}
```

**Error Response**: `ErrorResponse`
```json
{
  "message": "Error description"
}
```

## Migration Guide

If you have any code referencing the old inner classes:

### Old Way
```java
FileUploadResource.PassphraseRequest request = ...
FileUploadResource.UploadResponse response = ...
```

### New Way
```java
import me.cresterida.dto.PassphraseRequest;
import me.cresterida.dto.UploadResponse;

PassphraseRequest request = ...
UploadResponse response = ...
```

## Compilation Status

✅ **All code compiles successfully**
✅ **No errors, only minor warnings**
✅ **API functionality unchanged**

## Next Steps (Optional Improvements)

1. **Add validation annotations**
   ```java
   @NotNull
   @Email
   public String email;
   ```

2. **Add Jackson annotations for JSON customization**
   ```java
   @JsonProperty("file_key")
   public String key;
   ```

3. **Add builder pattern for complex DTOs**
   ```java
   UploadResponse.builder()
       .message("...")
       .key("...")
       .build();
   ```

4. **Add equals/hashCode/toString methods**
   - Consider using Lombok's `@Data` annotation

## Files Created

1. `/src/main/java/me/cresterida/dto/ErrorResponse.java`
2. `/src/main/java/me/cresterida/dto/PassphraseRequest.java`
3. `/src/main/java/me/cresterida/dto/PassphraseResponse.java`
4. `/src/main/java/me/cresterida/dto/UploadResponse.java`

## Files Modified

1. `/src/main/java/me/cresterida/FileUploadResource.java`
   - Added imports for dto classes
   - Removed inner class definitions
   - No functional changes to methods
