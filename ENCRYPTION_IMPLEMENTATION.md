# AES-GCM Encryption Implementation

## Overview
This implementation adds client-side AES-GCM encryption to the file upload process, ensuring files are encrypted before being uploaded to the server.

## Components Modified/Created

### 1. **EncryptionService** (`src/main/webui/src/app/encryption.service.ts`)
A new service that handles AES-GCM encryption and decryption using the Web Crypto API.

**Key Features:**
- **PBKDF2 Key Derivation**: Derives a 256-bit AES key from the user's passphrase using PBKDF2 with 100,000 iterations
- **Random Salt & IV**: Generates cryptographically secure random values for each encryption
- **AES-GCM-256**: Uses AES-GCM (Galois/Counter Mode) for authenticated encryption
- **Metadata Format**: Encrypted files contain `[16 bytes salt][12 bytes IV][encrypted data]`

**Methods:**
- `encryptFile(file: File, passphrase: string): Promise<File>` - Encrypts a file and returns a new File object
- `decryptFile(encryptedFile: File, passphrase: string): Promise<ArrayBuffer>` - Decrypts an encrypted file (for future use)

### 2. **AuthService** (`src/main/webui/src/app/auth.service.ts`)
Updated to store the passphrase securely in memory during the user session.

**Changes:**
- Added `passphrase` property to store the user's passphrase
- Modified `login(passphrase: string)` to accept and store the passphrase
- Added `getPassphrase()` method to retrieve the stored passphrase
- Modified `logout()` to clear the passphrase from memory

### 3. **Passphrase Component** (`src/main/webui/src/app/passphrase/passphrase.ts`)
Updated to pass the passphrase to AuthService after successful validation.

**Changes:**
- Modified the `validatePassphrase()` method to call `this.authService.login(this.passphrase)` instead of `this.authService.login()`

### 4. **Mp3Upload Component** (`src/main/webui/src/app/mp3-upload/mp3-upload.ts`)
Updated to encrypt files before uploading.

**Changes:**
- Injected `EncryptionService` and `AuthService` dependencies
- Added `isEncrypting` flag to track encryption progress
- Modified `onUpload()` to:
  1. Retrieve the passphrase from AuthService
  2. Encrypt the file using AES-GCM
  3. Upload the encrypted file
  4. Handle encryption errors

### 5. **Mp3Upload Template** (`src/main/webui/src/app/mp3-upload/mp3-upload.html`)
Updated to show encryption progress.

**Changes:**
- Disabled upload button during encryption
- Changed button text to "Encrypting..." during encryption
- Added encryption progress message

### 6. **Mp3Upload Styles** (`src/main/webui/src/app/mp3-upload/mp3-upload.css`)
Added styling for the encryption progress message.

## Encryption Flow

1. **User enters passphrase** → Validated by backend → Stored in AuthService
2. **User selects file** → File loaded into memory
3. **User clicks upload** → Process begins:
   - Retrieve passphrase from AuthService
   - Generate random 16-byte salt
   - Generate random 12-byte IV (Initialization Vector)
   - Derive 256-bit AES key from passphrase using PBKDF2
   - Encrypt file data using AES-GCM
   - Prepend salt and IV to encrypted data
   - Create new File object with encrypted content
   - Upload encrypted file to server

## Security Features

1. **AES-GCM-256**: Industry-standard authenticated encryption
2. **PBKDF2**: Key derivation with 100,000 iterations (protects against brute force)
3. **Random Salt**: Each file gets a unique salt (prevents rainbow table attacks)
4. **Random IV**: Each file gets a unique IV (ensures unique ciphertext)
5. **Authenticated Encryption**: GCM mode provides both confidentiality and integrity

## File Format

Encrypted files follow this format:
```
[16 bytes: Salt][12 bytes: IV][Remaining: Encrypted Data]
```

To decrypt, you need:
1. The original passphrase
2. Extract salt from bytes 0-15
3. Extract IV from bytes 16-27
4. Derive key using passphrase + salt
5. Decrypt remaining bytes using AES-GCM with key + IV

## Usage

1. User navigates to the application
2. Enters passphrase at `/passphrase` route
3. After validation, redirected to `/upload` route
4. Selects MP3 file and enters email
5. Clicks "Upload" button
6. File is encrypted (shows "Encrypting..." message)
7. Encrypted file is uploaded to S3
8. Success/error message displayed

## Notes

- Passphrase is stored in memory only (cleared on logout/refresh)
- Original filename gets `.encrypted` extension
- File size increases slightly due to salt, IV, and GCM tag overhead
- Encryption happens entirely in the browser (client-side)
- Server receives only the encrypted file
