#!/bin/bash
# Quarkus File Upload API - cURL Test Commands
#
# Usage:
#   chmod +x curl-tests.sh
#   ./curl-tests.sh
#
# Or run individual commands manually

BASE_URL="http://localhost:8080/whisper"
EMAIL="test@example.com"
PASSPHRASE="your-secret-passphrase"

echo "========================================"
echo "Quarkus File Upload API - cURL Tests"
echo "========================================"
echo ""

# Test 1: Validate Passphrase - SUCCESS
echo "Test 1: Validate Passphrase - SUCCESS"
curl -X POST "${BASE_URL}/api/validate-passphrase" \
  -H "Content-Type: application/json" \
  -d "{\"passphrase\": \"${PASSPHRASE}\"}" \
  -v
echo -e "\n\n"

# Test 2: Validate Passphrase - FAIL (wrong passphrase)
echo "Test 2: Validate Passphrase - FAIL (wrong passphrase)"
curl -X POST "${BASE_URL}/api/validate-passphrase" \
  -H "Content-Type: application/json" \
  -d '{"passphrase": "wrong-passphrase"}' \
  -v
echo -e "\n\n"

# Test 3: Upload - FAIL (plain text, not encrypted)
echo "Test 3: Upload File - FAIL (plain text, not encrypted)"
echo "This is plain text, not encrypted" > /tmp/plain-test.mp3
curl -X POST "${BASE_URL}/api/upload" \
  -F "email=${EMAIL}" \
  -F "file=@/tmp/plain-test.mp3;filename=test.mp3" \
  -v
echo -e "\n\n"

# Test 4: Upload - FAIL (too short to be valid encrypted data)
echo "Test 4: Upload File - FAIL (too short to be valid encrypted data)"
echo "Short" > /tmp/short-test.encrypted
curl -X POST "${BASE_URL}/api/upload" \
  -F "email=${EMAIL}" \
  -F "file=@/tmp/short-test.encrypted;filename=test.mp3.encrypted" \
  -v
echo -e "\n\n"

# Test 5: Upload - FAIL (missing email)
echo "Test 5: Upload File - FAIL (missing email)"
echo "FakeEncryptedDataHere" > /tmp/fake-encrypted.mp3.encrypted
curl -X POST "${BASE_URL}/api/upload" \
  -F "file=@/tmp/fake-encrypted.mp3.encrypted;filename=test.mp3.encrypted" \
  -v
echo -e "\n\n"

# Test 6: Upload - FAIL (empty email)
echo "Test 6: Upload File - FAIL (empty email)"
curl -X POST "${BASE_URL}/api/upload" \
  -F "email=" \
  -F "file=@/tmp/fake-encrypted.mp3.encrypted;filename=test.mp3.encrypted" \
  -v
echo -e "\n\n"

# Test 7: Upload - FAIL (invalid encryption - wrong format)
echo "Test 7: Upload File - FAIL (invalid encryption - random data)"
dd if=/dev/urandom of=/tmp/random-data.encrypted bs=1024 count=1 2>/dev/null
curl -X POST "${BASE_URL}/api/upload" \
  -F "email=${EMAIL}" \
  -F "file=@/tmp/random-data.encrypted;filename=test.mp3.encrypted" \
  -v
echo -e "\n\n"

# Test 8: Upload - FAIL (corrupted GCM tag)
echo "Test 8: Upload File - FAIL (corrupted encryption - should fail GCM verification)"
# Create a file that looks like encrypted data but has invalid GCM tag
printf '\x00\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f' > /tmp/fake-salt.bin
printf '\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b' >> /tmp/fake-salt.bin
printf 'InvalidCiphertextAndGCMTag' >> /tmp/fake-salt.bin
curl -X POST "${BASE_URL}/api/upload" \
  -F "email=${EMAIL}" \
  -F "file=@/tmp/fake-salt.bin;filename=corrupted.mp3.encrypted" \
  -v
echo -e "\n\n"

# Test 9: Health check (if available)
echo "Test 9: Health Check"
curl -X GET "${BASE_URL}/q/health" -v
echo -e "\n\n"

# Test 10: Swagger UI
echo "Test 10: OpenAPI/Swagger"
curl -X GET "${BASE_URL}/swagger" -v
echo -e "\n\n"

# Cleanup
rm -f /tmp/plain-test.mp3 /tmp/short-test.encrypted /tmp/fake-encrypted.mp3.encrypted /tmp/random-data.encrypted /tmp/fake-salt.bin

echo "========================================"
echo "Tests completed!"
echo "========================================"
