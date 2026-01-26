# Delete File Functionality - Implementation Summary

## Overview
Implemented complete delete functionality for encrypted files in the Quarkus-Tigris application, ensuring both backend and frontend properly handle file deletion with proper validation.

## Changes Made

### 1. Backend Changes

#### A. EnvelopeMetadata Model (`src/main/java/me/cresterida/model/EnvelopeMetadata.java`)
- **Added `fileId` field**: Already existed but now properly utilized
- This field stores the UUID folder name from S3 structure: `uploads/{email}/{fileId}/`

#### B. FileListResource (`src/main/java/me/cresterida/FileListResource.java`)

**Updated `fetchUserMetadata()` method:**
- Extracts `fileId` from S3 key structure
- Populates `fileId` in metadata before returning to frontend
- S3 key format: `uploads/{email}/{fileId}/metadata.json`
- Extracts UUID from position [2] in split array

**Enhanced `deleteFile()` endpoint:**
- **Path**: `DELETE /whisper/api/files`
- **Required Query Parameters**:
  - `fileId`: UUID of the file folder in S3
  - `fileName`: Original filename with extension
- **Required Header**:
  - `X-Session-Token`: Valid session token
  
**Validation Flow**:
1. ✅ Validates `fileId` parameter (400 Bad Request if missing)
2. ✅ Validates `fileName` parameter (400 Bad Request if missing)
3. ✅ Validates session token (401 Unauthorized if missing/invalid)
4. ✅ Generates S3 key using: `uploads/{email}/{fileId}/{fileName}`
5. ✅ Deletes encrypted file from S3
6. ✅ Deletes metadata file from S3
7. ✅ Returns structured response with DeleteFileResponse DTO

**Error Handling**:
- Specific S3Exception handling with proper error messages
- Generic Exception fallback
- Proper HTTP status codes (400, 401, 500)

#### C. DeleteFileResponse DTO (`src/main/java/me/cresterida/dto/DeleteFileResponse.java`)
```java
{
  "message": "File deleted successfully",
  "fileId": "8485e2b5-8edf-49aa-8cdd-930a51327dbe",
  "fileName": "zeno.mp3",
  "deleted": true,
  "s3Key": "uploads/user@example.com/8485e2b5-8edf-49aa-8cdd-930a51327dbe/zeno.mp3"
}
```

### 2. Frontend Changes

#### A. API Service (`src/main/webui/src/app/api.service.ts`)

**Updated FileMetadata interface:**
```typescript
export interface FileMetadata {
  version: string;
  kek: string;
  algorithm: string;
  original_filename: string;
  file_id: string;  // NEW: UUID from S3 folder structure
  original_size: number;
  encrypted_size: number;
  verification_status: string;
  timestamp: number;
}
```

**Added `deleteFile()` method:**
```typescript
deleteFile(sessionToken: string, fileId: string, fileName: string): Observable<any>
```
- Sends DELETE request to `/whisper/api/files`
- Includes session token in `X-Session-Token` header
- Passes `fileId` and `fileName` as query parameters

#### B. File List Component (`src/main/webui/src/app/file-list/file-list.ts`)

**Updated local FileMetadata interface:**
- Added `file_id: string` field

**Implemented `deleteFile()` method:**
1. ✅ Validates `file_id` exists
2. ✅ Validates `original_filename` exists
3. ✅ Shows confirmation dialog
4. ✅ Validates session token
5. ✅ Calls API service to delete file
6. ✅ On success:
   - Shows success alert
   - Removes file from local list
   - Updates UI automatically
7. ✅ On error:
   - Handles 401 (session expired) with redirect
   - Shows appropriate error messages
   - Displays error in UI

## API Specification

### DELETE /whisper/api/files

**Request:**
```bash
DELETE /whisper/api/files?fileId={uuid}&fileName={name}
Headers:
  X-Session-Token: {session-token}
```

**Query Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| fileId | string | Yes | UUID of the file folder in S3 |
| fileName | string | Yes | Original filename with extension |

**Headers:**
| Header | Type | Required | Description |
|--------|------|----------|-------------|
| X-Session-Token | string | Yes | Valid session token from login |

**Success Response (200):**
```json
{
  "message": "File deleted successfully",
  "fileId": "8485e2b5-8edf-49aa-8cdd-930a51327dbe",
  "fileName": "zeno.mp3",
  "deleted": true,
  "s3Key": "uploads/user@example.com/8485e2b5-8edf-49aa-8cdd-930a51327dbe/zeno.mp3"
}
```

**Error Responses:**

**400 Bad Request** - Missing required parameter:
```json
{
  "error": "fileId parameter is required"
}
```
or
```json
{
  "error": "fileName parameter is required"
}
```

**401 Unauthorized** - Invalid/missing session:
```json
{
  "error": "Session token required"
}
```
or
```json
{
  "error": "Invalid or expired session"
}
```

**500 Internal Server Error** - S3 or server error:
```json
{
  "error": "Failed to delete file from storage"
}
```

## S3 File Structure

The application uses this S3 folder structure:
```
bucket-name/
└── uploads/
    └── {email}/
        └── {fileId}/  (UUID)
            ├── {originalFileName}.enc  (encrypted file - NOTE: .enc extension added)
            └── metadata.json           (envelope metadata)
```

**Example:**
- Original file: `zeno.mp3`
- UUID: `7f864ee8-f081-4eba-81ea-b2ce03bab271`
- Stored as: `uploads/user@example.com/7f864ee8-f081-4eba-81ea-b2ce03bab271/zeno.mp3.enc`
- Metadata: `uploads/user@example.com/7f864ee8-f081-4eba-81ea-b2ce03bab271/metadata.json`

## Testing

### Manual Testing

1. **Start the application:**
   ```bash
   ./dev-mode.sh
   ```

2. **Login and get session token**

3. **List files to get fileId:**
   ```bash
   curl -X GET "http://localhost:8080/whisper/api/files" \
     -H "X-Session-Token: YOUR_TOKEN"
   ```

4. **Delete a file:**
   ```bash
   curl -X DELETE "http://localhost:8080/whisper/api/files?fileId=UUID&fileName=example.mp3" \
     -H "X-Session-Token: YOUR_TOKEN"
   ```

5. **Use the test script:**
   ```bash
   ./test-delete.sh
   ```
   (Update SESSION_TOKEN variable in the script first)

### Frontend Testing

1. Navigate to the file list page
2. Login with valid passphrase
3. Click "Load My Files"
4. Click "Delete" button on any file
5. Confirm deletion in dialog
6. Verify file is removed from list

## Key Implementation Details

### Why Both fileId and fileName?

1. **Security**: The fileId (UUID) prevents guessing file locations
2. **Structure**: S3 uses nested folder structure: `uploads/{email}/{fileId}/{fileName}`
3. **Validation**: Both are needed to construct the correct S3 key
4. **Metadata**: Files are tracked by both UUID and original name

**Important Note on File Extensions:**
- Frontend sends the **original filename** (e.g., `zeno.mp3`)
- Backend automatically appends `.enc` extension in `generateS3Key()` method
- Actual S3 storage: `uploads/{email}/{fileId}/zeno.mp3.enc`
- This ensures the correct encrypted file is deleted from S3

### What Gets Deleted?

When deleting a file, the backend removes:
1. **Encrypted file**: `uploads/{email}/{fileId}/{fileName}`
2. **Metadata file**: `uploads/{email}/{fileId}/metadata.json`

This ensures complete cleanup of all file-related data.

### Session Validation

All delete operations require:
- Valid session token in `X-Session-Token` header
- Token validation through SessionManager
- Email extraction from token to ensure user owns the file

## OpenAPI Documentation

The delete endpoint is fully documented with OpenAPI annotations:
- Visible in Swagger UI at `/q/swagger-ui`
- Shows request parameters, response schema, and error codes
- Documents the DeleteFileResponse DTO structure

## Error Handling Flow

```
Frontend Delete Request
    ↓
Validate file_id exists → Show error alert
    ↓
Validate filename exists → Show error alert
    ↓
Show confirmation dialog → User cancels
    ↓
Check session token → Redirect to login
    ↓
API Service DELETE call
    ↓
Backend validates fileId → 400 Bad Request
    ↓
Backend validates fileName → 400 Bad Request
    ↓
Backend validates session → 401 Unauthorized
    ↓
Generate S3 key
    ↓
Delete from S3 → S3Exception → 500 Error
    ↓
Success → 200 OK with DeleteFileResponse
    ↓
Frontend removes from list
    ↓
UI updates automatically
```

## Benefits

✅ **Type-safe**: Proper DTOs throughout the stack
✅ **Well-validated**: Multiple validation layers
✅ **User-friendly**: Clear error messages and confirmations
✅ **Complete cleanup**: Deletes both data and metadata
✅ **Secure**: Requires valid session token
✅ **Documented**: Full OpenAPI/Swagger documentation
✅ **Reactive**: UI updates immediately after deletion
✅ **Error resilient**: Handles all error scenarios gracefully

## Future Enhancements

Possible improvements:
- Bulk delete functionality
- Soft delete with recovery option
- Delete confirmation via email
- Audit log of deletions
- Recycle bin feature

## Troubleshooting

### Angular List Not Updating After Delete

If the file list doesn't update after deletion:

1. **Verify console logs** - Check if delete succeeds in browser console
2. **Check trackBy** - Ensure template uses `trackBy: trackByFileId`
3. **New array reference** - Code uses spread operator: `[...array.filter()]`
4. **Zone execution** - Update wrapped in `NgZone.run()`
5. **Change detection** - `cdr.detectChanges()` called after update

**The implementation includes ALL these fixes:**
- ✅ Creates new array reference with spread operator
- ✅ Wraps update in NgZone.run() for proper zone execution
- ✅ Calls detectChanges() explicitly
- ✅ Uses trackBy in template for proper DOM updates

See detailed troubleshooting guide: [ANGULAR_LIST_UPDATE_FIX.md](ANGULAR_LIST_UPDATE_FIX.md)

