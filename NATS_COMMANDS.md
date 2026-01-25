# NATS Commands Reference

## Quick Reference

This document contains common NATS CLI commands for managing the FILE_UPLOADS stream.

## Programmatic Stream Creation

The application **automatically creates** the `FILE_UPLOADS` stream on startup using the `NatsService` class.

### How It Works

1. On application startup, `NatsService.createStreamIfNotExists()` is called
2. It connects to NATS server using the configured URL and credentials
3. Checks if the `FILE_UPLOADS` stream already exists
4. If not, creates it with the following configuration:
   - **Stream Name**: `FILE_UPLOADS`
   - **Subject**: `file.uploads`
   - **Storage**: File-based
   - **Retention Policy**: Limits (old messages deleted)
   - **Max Age**: 7 days
   - **Credentials**: `guest:guest` (DevServices default)

### Startup Logs

You should see logs like this on startup:

```
🔧 Attempting to create NATS stream...
   Connecting to: nats://localhost:32871
   ✓ Connected to NATS server
   Stream FILE_UPLOADS does not exist, creating...
   ✅ Stream FILE_UPLOADS created successfully!
     Name: FILE_UPLOADS
     Subjects: [file.uploads]
     Storage: File
     Max Age: PT168H
   ✓ NATS connection closed
```

Or if the stream already exists:

```
🔧 Attempting to create NATS stream...
   Connecting to: nats://localhost:32871
   ✓ Connected to NATS server
   ✓ Stream FILE_UPLOADS already exists
     Messages: 0
     Subjects: [file.uploads]
   ✓ NATS connection closed
```

### Benefits

- **No manual setup required** - stream is created automatically
- **Idempotent** - safe to run multiple times, won't fail if stream exists
- **Fail-safe** - application continues even if NATS is not available
- **DevServices friendly** - works with Quarkus NATS DevServices

### Code Location

The stream creation code is in:
- **File**: `src/main/java/me/cresterida/service/NatsService.java`
- **Method**: `createStreamIfNotExists()`
- **Called from**: `onStart()` startup event observer

## Configuration

- **NATS Server**: `nats://localhost:32871`
- **Credentials**: `guest:guest`
- **Stream Name**: `FILE_UPLOADS`
- **Subject**: `file.uploads`
- **Storage**: File-based
- **Retention**: 7 days

## Create Stream

```bash
nats stream add FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871 \
  --subjects "file.uploads" \
  --storage file \
  --retention limits \
  --max-age 7d
```

### Create Stream (Detailed Options)

```bash
nats stream add FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871 \
  --subjects "file.uploads" \
  --storage file \
  --retention limits \
  --max-msgs=-1 \
  --max-bytes=-1 \
  --max-age=7d \
  --max-msg-size=-1 \
  --discard old \
  --dupe-window=2m \
  --replicas=1
```

## Verify Stream

### Check Stream Info

```bash
nats stream info FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871
```

### List All Streams

```bash
nats stream ls \
  --server nats://guest:guest@localhost:32871
```

### View Stream Statistics

```bash
nats stream report \
  --server nats://guest:guest@localhost:32871
```

## Monitor Messages

### View Messages in Stream

```bash
nats stream view FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871
```

### Subscribe to New Messages

```bash
nats sub "file.uploads" \
  --server nats://guest:guest@localhost:32871
```

### Subscribe and Show Message Details

```bash
nats sub "file.uploads" \
  --server nats://guest:guest@localhost:32871 \
  --translate "jq ."
```

## Test Publishing

### Publish Test Message

```bash
nats pub "file.uploads" \
  --server nats://guest:guest@localhost:32871 \
  '{"test":"message","timestamp":'"$(date +%s)"'}'
```

### Publish from File

```bash
nats pub "file.uploads" \
  --server nats://guest:guest@localhost:32871 \
  --file test-event.json
```

## Stream Management

### Get Specific Message

```bash
# Get first message
nats stream get FILE_UPLOADS 1 \
  --server nats://guest:guest@localhost:32871
```

### Purge Stream (Delete All Messages)

```bash
nats stream purge FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871 \
  --force
```

### Delete Stream

```bash
nats stream rm FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871 \
  --force
```

## Consumer Management

### Create Consumer

```bash
nats consumer add FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:32871 \
  --filter "file.uploads" \
  --ack explicit \
  --pull \
  --deliver all \
  --max-deliver=-1 \
  --max-pending=100 \
  --wait=30s
```

### List Consumers

```bash
nats consumer ls FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871
```

### Consumer Info

```bash
nats consumer info FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:32871
```

## Server Information

### Check Server Status

```bash
nats server ping \
  --server nats://guest:guest@localhost:32871
```

### Server Info

```bash
nats server info \
  --server nats://guest:guest@localhost:32871
```

### JetStream Info

```bash
nats server report jetstream \
  --server nats://guest:guest@localhost:32871
```

### Account Info

```bash
nats account info \
  --server nats://guest:guest@localhost:32871
```

## HTTP Monitoring Endpoints

### Server Variables

```bash
curl -s http://localhost:32901/varz | jq .
```

### Connections

```bash
curl -s http://localhost:32901/connz | jq .
```

### JetStream Stats

```bash
curl -s http://localhost:32901/jsz | jq .
```

### Account Stats

```bash
curl -s http://localhost:32901/accountz | jq .
```

### Health Check

```bash
curl http://localhost:32901/healthz
```

## Troubleshooting

### Check if Stream Exists

```bash
nats stream info FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871 2>&1 | \
  grep -q "does not exist" && echo "Stream does not exist" || echo "Stream exists"
```

### View Last 10 Messages

```bash
nats stream view FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871 \
  --last 10
```

### Check Message Count

```bash
nats stream info FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871 | \
  grep "Messages:"
```

## Environment Variables

You can set these to avoid repeating server URL:

```bash
export NATS_URL=nats://guest:guest@localhost:32871
export NATS_CONTEXT=quarks-tigris

# Then use commands without --server flag:
nats stream info FILE_UPLOADS
nats sub "file.uploads"
```

## Example: Complete Workflow

```bash
# 1. Create the stream
nats stream add FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871 \
  --subjects "file.uploads" \
  --storage file \
  --retention limits \
  --max-age 7d

# 2. Verify it was created
nats stream info FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871

# 3. Subscribe to messages in one terminal
nats sub "file.uploads" \
  --server nats://guest:guest@localhost:32871

# 4. Upload a file via the web UI (in another terminal)
# The message should appear in the subscriber

# 5. View all messages in the stream
nats stream view FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871

# 6. Check stream statistics
nats stream report \
  --server nats://guest:guest@localhost:32871
```

## Notes

- **Authentication**: Uses `guest:guest` credentials
- **Port**: NATS is on `32871`, monitoring on `32901` (32871 + 4000)
- **JetStream**: Must be enabled with `-js` flag when starting NATS server
- **Storage**: File-based storage in `/data` directory (mounted volume)
- **Retention**: Messages older than 7 days are automatically deleted
