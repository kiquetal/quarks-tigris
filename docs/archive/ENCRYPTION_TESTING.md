# Testing the AES-GCM Encryption Implementation

## Manual Testing Steps

### 1. Start the Application
```bash
cd /mydata/codes/2026/quarks-tigris
./dev-mode.sh
```

### 2. Test the Flow

1. **Access the application**: Navigate to `http://localhost:8080/whisper`

2. **Enter Passphrase**: 
   - Default passphrase: `your-secret-passphrase`
   - This should redirect you to the upload page

3. **Upload a File**:
   - Enter your email address
   - Select an MP3 file
   - Click "Upload"
   - You should see "Encrypting..." message briefly
   - File will be encrypted and uploaded

### 3. Verification Points

#### Console Logs (Browser DevTools)
You should see these log messages:

```
File selected: { name: "...", size: ..., sizeInMB: "...", type: "..." }
Starting upload...
File: ... Size: ... bytes
Email: ...
Encrypting file with AES-GCM...
Encryption complete. Encrypted size: ... bytes
Upload successful: { message: "...", fileUrl: "..." }
```

#### Expected Behaviors
- ✅ File is encrypted before upload (check encrypted size > original size)
- ✅ Button shows "Encrypting..." during encryption
- ✅ Button is disabled during encryption
- ✅ Blue progress message appears during encryption
- ✅ Upload happens after encryption completes
- ✅ Success message shows file URL

#### File Size Change
The encrypted file should be slightly larger:
- **Original file**: X bytes
- **Encrypted file**: X + 28 bytes (16 bytes salt + 12 bytes IV) + 16 bytes (GCM tag)
- Total overhead: ~44 bytes

### 4. Error Cases to Test

#### No Passphrase
- Clear browser session/refresh page
- Try to navigate to `/upload` directly
- Should be redirected to passphrase page by auth guard

#### Empty Fields
- Don't enter email
- Button should be disabled
- Don't select file
- Button should be disabled

#### Large Files
- Try uploading a file > 100MB
- Should get "File is too large" error

## Automated Testing

### Unit Test for EncryptionService

Create a test file: `src/main/webui/src/app/encryption.service.spec.ts`

```typescript
import { TestBed } from '@angular/core/testing';
import { EncryptionService } from './encryption.service';

describe('EncryptionService', () => {
  let service: EncryptionService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(EncryptionService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should encrypt and decrypt a file', async () => {
    // Create a test file
    const testContent = 'This is test content for encryption';
    const testFile = new File([testContent], 'test.txt', { type: 'text/plain' });
    const passphrase = 'test-passphrase-123';

    // Encrypt the file
    const encryptedFile = await service.encryptFile(testFile, passphrase);

    // Verify encrypted file is larger (has salt + IV + tag overhead)
    expect(encryptedFile.size).toBeGreaterThan(testFile.size);
    expect(encryptedFile.name).toBe('test.txt.encrypted');

    // Decrypt the file
    const decryptedData = await service.decryptFile(encryptedFile, passphrase);

    // Verify decrypted content matches original
    const decoder = new TextDecoder();
    const decryptedContent = decoder.decode(decryptedData);
    expect(decryptedContent).toBe(testContent);
  });

  it('should fail to decrypt with wrong passphrase', async () => {
    const testFile = new File(['test content'], 'test.txt');
    const correctPassphrase = 'correct-passphrase';
    const wrongPassphrase = 'wrong-passphrase';

    const encryptedFile = await service.encryptFile(testFile, correctPassphrase);

    // Should throw error when decrypting with wrong passphrase
    await expectAsync(
      service.decryptFile(encryptedFile, wrongPassphrase)
    ).toBeRejected();
  });
});
```

### Run Tests
```bash
cd src/main/webui
npm test
```

## Security Verification

### Check Encryption Parameters
Open browser DevTools and run in console:

```javascript
// Verify Web Crypto API is available
console.log('Web Crypto available:', !!window.crypto.subtle);

// Check supported algorithms
crypto.subtle.generateKey(
  { name: 'AES-GCM', length: 256 },
  true,
  ['encrypt', 'decrypt']
).then(() => console.log('AES-GCM-256 supported: ✓'));
```

### Verify Encrypted File Content
1. Download an encrypted file from S3
2. Open it in a hex editor
3. Verify:
   - First 16 bytes are random (salt)
   - Next 12 bytes are random (IV)
   - Remaining bytes are encrypted data
   - Content is not readable

## Performance Testing

### Test with Different File Sizes
- Small file (1 MB): Encryption should be instant
- Medium file (10 MB): Encryption < 1 second
- Large file (50 MB): Encryption 2-5 seconds
- Max file (100 MB): Encryption 5-10 seconds

### Monitor Browser Performance
Open DevTools → Performance tab:
1. Start recording
2. Upload a file
3. Stop recording
4. Check encryption time in timeline

## Troubleshooting

### Issue: "No passphrase found. Please login again."
- **Cause**: Passphrase not stored in AuthService
- **Fix**: Check passphrase component is calling `authService.login(this.passphrase)`

### Issue: Build fails with type errors
- **Cause**: TypeScript strict type checking
- **Fix**: Ensure salt is cast to `BufferSource` in deriveKey

### Issue: Encryption takes too long
- **Cause**: Large file or slow device
- **Solution**: This is expected for large files; encryption happens on client

### Issue: Upload fails after encryption
- **Cause**: Network error or server issue
- **Fix**: Check server logs, verify S3 connection
