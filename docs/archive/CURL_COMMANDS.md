# Quick cURL Commands Reference
# Copy and paste these commands to test the Quarkus API

## Configuration
BASE_URL="http://localhost:8080/whisper"
EMAIL="test@example.com"

## 1. Test Passphrase Validation (Valid)
curl -X POST "${BASE_URL}/api/validate-passphrase" \
  -H "Content-Type: application/json" \
  -d '{"passphrase": "your-secret-passphrase"}'

## 2. Test Passphrase Validation (Invalid)
curl -X POST "${BASE_URL}/api/validate-passphrase" \
  -H "Content-Type: application/json" \
  -d '{"passphrase": "wrong-password"}'

## 3. Upload Plain Text File (Should FAIL - not encrypted)
echo "This is plain text" > /tmp/test-plain.mp3
curl -X POST "${BASE_URL}/api/upload" \
  -F "email=${EMAIL}" \
  -F "file=@/tmp/test-plain.mp3;filename=test.mp3.encrypted"

## 4. Upload Too Short Data (Should FAIL - invalid format)
echo "Short" > /tmp/test-short.encrypted
curl -X POST "${BASE_URL}/api/upload" \
  -F "email=${EMAIL}" \
  -F "file=@/tmp/test-short.encrypted;filename=test.mp3.encrypted"

## 5. Upload Without Email (Should FAIL - missing email)
echo "FakeData" > /tmp/test-fake.encrypted
curl -X POST "${BASE_URL}/api/upload" \
  -F "file=@/tmp/test-fake.encrypted;filename=test.mp3.encrypted"

## 6. Upload With Empty Email (Should FAIL - empty email)
curl -X POST "${BASE_URL}/api/upload" \
  -F "email=" \
  -F "file=@/tmp/test-fake.encrypted;filename=test.mp3.encrypted"

## 7. Upload Random Data (Should FAIL - invalid encryption/GCM tag)
dd if=/dev/urandom of=/tmp/test-random.encrypted bs=1024 count=1 2>/dev/null
curl -X POST "${BASE_URL}/api/upload" \
  -F "email=${EMAIL}" \
  -F "file=@/tmp/test-random.encrypted;filename=test.mp3.encrypted"

## 8. Upload File with Invalid GCM Tag (Should FAIL - decryption error)
# Create fake encrypted structure: [16 bytes salt][12 bytes IV][invalid ciphertext]
printf '\x00\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f' > /tmp/test-invalid-gcm.bin
printf '\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b' >> /tmp/test-invalid-gcm.bin
printf 'InvalidCiphertextThatWillFailGCMVerification' >> /tmp/test-invalid-gcm.bin
curl -X POST "${BASE_URL}/api/upload" \
  -F "email=${EMAIL}" \
  -F "file=@/tmp/test-invalid-gcm.bin;filename=corrupted.mp3.encrypted"

## 9. Health Check
curl -X GET "${BASE_URL}/q/health"

## 10. Swagger UI
curl -X GET "${BASE_URL}/swagger"

## Cleanup temporary files
rm -f /tmp/test-*.mp3 /tmp/test-*.encrypted /tmp/test-*.bin

## To test with verbose output, add -v flag:
# curl -v -X POST "${BASE_URL}/api/upload" ...

## To see only HTTP status code:
# curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/api/upload" ...

## To test file size limit (create 101MB file):
# dd if=/dev/zero of=/tmp/large-file.bin bs=1M count=101
# curl -X POST "${BASE_URL}/api/upload" \
#   -F "email=${EMAIL}" \
#   -F "file=@/tmp/large-file.bin;filename=large.mp3.encrypted"
