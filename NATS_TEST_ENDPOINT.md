# NATS Test Endpoint Usage Guide

## Quick Start

### Step 1: Create the NATS Stream

```bash
nats stream add FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871 \
  --subjects "file.uploads" \
  --storage file \
  --retention limits \
  --max-age 7d
```

### Step 2: Start a Subscriber (Optional - to see messages)

In a separate terminal:

```bash
nats sub "file.uploads" \
  --server nats://guest:guest@localhost:32871
```

### Step 3: Test Publishing via HTTP

```bash
# Simple test message
curl http://localhost:8080/whisper/api/nats-test/publish

# Or with custom parameters
curl -X POST "http://localhost:8080/whisper/api/nats-test/publish-custom?email=john@example.com&fileId=test-123"
```

## Available Endpoints

### 1. Ping - Health Check

```bash
curl http://localhost:8080/whisper/api/nats-test/ping
```

**Response:**
```
NATS test endpoint is alive! Use /api/nats-test/publish to send a test message.
```

---

### 2. Get Configuration

```bash
curl http://localhost:8080/whisper/api/nats-test/config
```

**Response:**
```json
{
  "channel": "file-uploads",
  "subject": "file.uploads",
  "stream": "FILE_UPLOADS",
  "expectedPort": "32871",
  "authentication": "guest:guest",
  "createStreamCommand": "nats stream add FILE_UPLOADS --server nats://guest:guest@localhost:32871 --subjects \"file.uploads\" --storage file --retention limits --max-age 7d",
  "subscribeCommand": "nats sub \"file.uploads\" --server nats://guest:guest@localhost:32871",
  "viewStreamCommand": "nats stream view FILE_UPLOADS --server nats://guest:guest@localhost:32871"
}
```

---

### 3. Publish Test Message (GET)

```bash
curl http://localhost:8080/whisper/api/nats-test/publish
```

**Response:**
```json
{
  "status": "success",
  "message": "Test message published to NATS",
  "testEmail": "test@example.com",
  "testFileUuid": "550e8400-e29b-41d4-a716-446655440000",
  "subject": "file.uploads",
  "stream": "FILE_UPLOADS",
  "note": "Check your NATS subscriber or run: nats stream view FILE_UPLOADS"
}
```

---

### 4. Publish Custom Message (POST)

```bash
# With custom email and file ID
curl -X POST "http://localhost:8080/whisper/api/nats-test/publish-custom?email=alice@test.com&fileId=my-file-123"

# With just email
curl -X POST "http://localhost:8080/whisper/api/nats-test/publish-custom?email=bob@example.com"

# No parameters (uses defaults)
curl -X POST http://localhost:8080/whisper/api/nats-test/publish-custom
```

**Response:**
```json
{
  "status": "success",
  "message": "Custom test message published to NATS",
  "email": "alice@test.com",
  "fileUuid": "my-file-123",
  "dataKey": "test/data/my-file-123/test.enc",
  "metadataKey": "test/data/my-file-123/metadata.json",
  "bucket": "whisper-uploads",
  "subject": "file.uploads",
  "stream": "FILE_UPLOADS"
}
```

---

## Complete Testing Workflow

### Terminal 1: Start NATS Subscriber
```bash
nats sub "file.uploads" \
  --server nats://guest:guest@localhost:32871
```

### Terminal 2: Publish Test Messages
```bash
# Test 1: Simple publish
curl http://localhost:8080/whisper/api/nats-test/publish

# Test 2: Custom email
curl -X POST "http://localhost:8080/whisper/api/nats-test/publish-custom?email=test1@example.com"

# Test 3: Custom email and file ID
curl -X POST "http://localhost:8080/whisper/api/nats-test/publish-custom?email=test2@example.com&fileId=file-456"
```

### Expected Output in Subscriber Terminal
```
[#1] Received on "file.uploads"
{"event_id":"123...","email":"test@example.com","file_uuid":"456...","s3_data_key":"test/data/456.../test.enc",...}

[#2] Received on "file.uploads"
{"event_id":"789...","email":"test1@example.com",...}

[#3] Received on "file.uploads"
{"event_id":"abc...","email":"test2@example.com","file_uuid":"file-456",...}
```

---

## Verify Messages in Stream

### View All Messages
```bash
nats stream view FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871
```

### Get Stream Info
```bash
nats stream info FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871
```

**Expected Output:**
```
Information for Stream FILE_UPLOADS

State:
  Messages: 3
  Bytes: 831 B
  FirstSeq: 1
  LastSeq: 3
```

---

## Troubleshooting

### Error: Connection Refused
- Make sure NATS is running on port 32871
- Check: `docker ps | grep nats`
- Check: `curl http://localhost:32901/healthz`

### Error: Authorization Violation
- Use correct credentials: `guest:guest`
- Update NATS URL: `nats://guest:guest@localhost:32871`

### Error: Stream Not Found
- Create the stream first (see Step 1)
- Verify: `nats stream ls --server nats://guest:guest@localhost:32871`

### No Messages Appearing
- Check subscriber is connected
- Check stream exists: `nats stream info FILE_UPLOADS --server nats://guest:guest@localhost:32871`
- Check Quarkus logs for errors

---

## Swagger UI

You can also test these endpoints via Swagger UI:

```
http://localhost:8080/whisper/swagger-ui
```

Look for the **"NATS Testing"** tag.

---

## Notes

- ✅ **No automatic test on startup** - Prevents errors before stream is created
- ✅ **Manual control** - You create the stream first, then test publishing
- ✅ **Multiple test options** - Simple GET or custom POST with parameters
- ✅ **Easy verification** - Use NATS subscriber to see messages in real-time
- ✅ **Safe testing** - Won't affect actual file uploads

---

## Next Steps

After successful testing:
1. Upload a real file via the web UI
2. Watch it appear in your subscriber
3. Verify in stream: `nats stream view FILE_UPLOADS`
4. Your F# consumer can use the same subscription pattern!
