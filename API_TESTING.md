# API Testing Guide

This directory contains test files for the Quarkus File Upload API with various scenarios including requests that should be rejected due to improper encryption.

## Test Files

### 1. `http-requests.http`
HTTP request file compatible with:
- **IntelliJ IDEA** (built-in HTTP Client)
- **VSCode** with [REST Client extension](https://marketplace.visualstudio.com/items?itemName=humao.rest-client)
- Other REST clients supporting `.http` files

**Usage:**
- Open the file in IntelliJ IDEA or VSCode
- Click the "Run" button next to each request
- View responses inline

### 2. `curl-tests.sh`
Automated bash script that runs all test cases sequentially.

**Usage:**
```bash
# Make executable (already done)
chmod +x curl-tests.sh

# Run all tests
./curl-tests.sh

# Run in background and save output
./curl-tests.sh > test-results.log 2>&1
```

### 3. `CURL_COMMANDS.md`
Individual curl commands that you can copy/paste for manual testing.

**Usage:**
- Open the file
- Copy any command
- Paste in terminal and run
- Modify as needed for your tests

## Test Scenarios

### ✅ Should SUCCEED
1. **Valid passphrase validation** - Correct passphrase from config
2. **Properly encrypted file** - File encrypted by Angular frontend

### ❌ Should FAIL (Expected Rejections)

| Test | Reason | Expected Error |
|------|--------|----------------|
| Plain text file | Not encrypted | Decryption failed / Invalid format |
| Too short data | Less than 28 bytes (salt+iv minimum) | Invalid encrypted data: too short |
| Missing email | Required field missing | Email is required |
| Empty email | Required field empty | Email is required |
| Random data | Invalid encryption format | Decryption failed |
| Corrupted GCM tag | Failed authentication | Failed to decrypt and verify data |
| Wrong passphrase | Invalid credentials | Invalid passphrase |
| File > 100MB | Exceeds size limit | File is too large |

## Prerequisites

Before running tests:

1. **Start Quarkus in dev mode:**
   ```bash
   ./mvnw quarkus:dev
   # Or use the dev script
   ./dev-mode.sh
   ```

2. **Verify the service is running:**
   ```bash
   curl http://localhost:8080/whisper/q/health
   ```

3. **Check your configuration:**
   - Open `src/main/resources/application.properties`
   - Note the `app.passphrase` value
   - Update test files if using a different passphrase

## Configuration

Update these variables in the test files to match your environment:

```bash
BASE_URL="http://localhost:8080/whisper"
EMAIL="test@example.com"
PASSPHRASE="your-secret-passphrase"  # Must match app.passphrase in application.properties
```

## Example: Running Individual Tests

### Test 1: Invalid Passphrase (Should Fail)
```bash
curl -X POST "http://localhost:8080/whisper/api/validate-passphrase" \
  -H "Content-Type: application/json" \
  -d '{"passphrase": "wrong-password"}'
```

**Expected Response:**
```json
HTTP/1.1 401 Unauthorized
{
  "message": "Invalid passphrase"
}
```

### Test 2: Upload Plain Text (Should Fail)
```bash
echo "This is not encrypted" > /tmp/plain.mp3
curl -X POST "http://localhost:8080/whisper/api/upload" \
  -F "email=test@example.com" \
  -F "file=@/tmp/plain.mp3;filename=test.mp3.encrypted" \
  -v
```

**Expected Response:**
```
HTTP/1.1 500 Internal Server Error
(Encryption verification error)
```

### Test 3: Upload Without Email (Should Fail)
```bash
echo "FakeData" > /tmp/fake.encrypted
curl -X POST "http://localhost:8080/whisper/api/upload" \
  -F "file=@/tmp/fake.encrypted;filename=test.mp3.encrypted" \
  -v
```

**Expected Response:**
```json
HTTP/1.1 400 Bad Request
{
  "message": "Email is required"
}
```

## Testing with Valid Encrypted Files

To test with a properly encrypted file, you need to:

1. **Use the Angular frontend:**
   - Navigate to `http://localhost:4200` (if running in dev mode)
   - Enter passphrase and encrypt a file
   - The file will be properly encrypted with AES-GCM

2. **Or manually encrypt a file** using the same algorithm:
   - PBKDF2 with 100,000 iterations
   - AES-256-GCM encryption
   - Format: `[16 bytes salt][12 bytes IV][ciphertext + GCM tag]`

## Interpreting Results

### Success Indicators
- HTTP 200 OK
- Response contains `"message": "File uploaded successfully..."`
- File appears in S3/Tigris bucket

### Failure Indicators (Expected for rejection tests)
- HTTP 400 Bad Request - Invalid input
- HTTP 401 Unauthorized - Invalid passphrase
- HTTP 413 Payload Too Large - File too big
- HTTP 500 Internal Server Error - Encryption/decryption failure

## Troubleshooting

### Port Already in Use
If port 8080 is in use, update `BASE_URL` in test files to match your Quarkus port.

### Connection Refused
Ensure Quarkus is running:
```bash
./mvnw quarkus:dev
```

### Wrong Passphrase
Update the `PASSPHRASE` variable to match your `application.properties`:
```properties
app.passphrase=your-secret-passphrase
```

### S3/Tigris Not Configured
Check S3 configuration in `application.properties` or environment variables.

## Advanced Testing

### Load Testing
Use `ab` (Apache Bench) or `wrk` for load testing:
```bash
# Install Apache Bench
sudo apt-get install apache2-utils

# Test with 100 requests, 10 concurrent
ab -n 100 -c 10 -p /tmp/test.encrypted \
  -T "multipart/form-data" \
  http://localhost:8080/whisper/api/upload
```

### Automated CI/CD Testing
Add to your CI pipeline:
```yaml
# Example GitHub Actions
- name: Run API Tests
  run: |
    ./mvnw quarkus:dev &
    sleep 10
    ./curl-tests.sh
```

## See Also

- [ENCRYPTION_IMPLEMENTATION.md](ENCRYPTION_IMPLEMENTATION.md) - Encryption details
- [ENVELOPE_ENCRYPTION_ARCHITECTURE.md](ENVELOPE_ENCRYPTION_ARCHITECTURE.md) - Architecture
- [STREAMING_IMPLEMENTATION.md](STREAMING_IMPLEMENTATION.md) - Streaming approach
- [API_CONFIGURATION.md](API_CONFIGURATION.md) - API configuration
