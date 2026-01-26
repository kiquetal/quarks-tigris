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

## Durable Consumer Management

### Quick Start for .NET Developers

**TL;DR - Create a consumer for your .NET client:**

```bash
# 1. Create the durable pull consumer
nats consumer add FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:4222 \
  --filter "file.uploads" \
  --ack explicit \
  --pull \
  --deliver all \
  --max-deliver=-1 \
  --max-pending=100 \
  --wait=30s \
  --replay instant

# 2. Verify it was created
nats consumer info FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:4222

# 3. Test fetching a message
nats consumer next FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:4222 \
  --count 1
```

**Connection details for your .NET app:**
- **URL**: `nats://localhost:4222` (or `32871` if using DevServices)
- **User**: `guest`
- **Password**: `guest`
- **Stream**: `FILE_UPLOADS`
- **Consumer**: `file_processor`
- **Subject**: `file.uploads`

### Why Durable Consumers?

Durable consumers are essential for:
- **Message Persistence**: Consumer state survives restarts
- **Guaranteed Delivery**: Messages are tracked and redelivered if not acknowledged
- **.NET Client Integration**: Required for reliable message processing in .NET applications
- **Load Distribution**: Multiple .NET instances can share work using the same consumer

### Create Durable Consumer for .NET Client

#### Pull-Based Consumer (Recommended for .NET)

Pull-based consumers give your .NET application full control over when to fetch messages.

```bash
nats consumer add FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:32871 \
  --filter "file.uploads" \
  --ack explicit \
  --pull \
  --deliver all \
  --max-deliver=-1 \
  --max-pending=100 \
  --wait=30s \
  --replay instant
```

**Configuration Explained:**
- `FILE_UPLOADS` - Stream name
- `file_processor` - Durable consumer name (must be unique)
- `--filter "file.uploads"` - Only receive messages matching this subject
- `--ack explicit` - Messages must be manually acknowledged
- `--pull` - Pull-based consumer (client requests messages)
- `--deliver all` - Deliver all messages from the start
- `--max-deliver=-1` - Unlimited redelivery attempts
- `--max-pending=100` - Max unacknowledged messages (controls pending acks)
- `--wait=30s` - Wait time for acknowledgment
- `--replay instant` - Replay messages as fast as possible

#### Push-Based Consumer (Alternative)

Push consumers automatically deliver messages to your .NET client:

```bash
nats consumer add FILE_UPLOADS file_processor_push \
  --server nats://guest:guest@localhost:32871 \
  --filter "file.uploads" \
  --ack explicit \
  --deliver all \
  --target "file.processor.inbox" \
  --max-deliver=-1 \
  --replay instant
```

### Consumer Variants for Different Use Cases

#### 1. **Work Queue Consumer** (Multiple .NET instances sharing work)

```bash
nats consumer add FILE_UPLOADS work_queue \
  --server nats://guest:guest@localhost:32871 \
  --filter "file.uploads" \
  --ack explicit \
  --pull \
  --deliver all \
  --max-deliver=3 \
  --max-pending=10 \
  --wait=60s
```

This consumer supports:
- Load balancing across multiple .NET instances
- Automatic retry (up to 3 times)
- Max 3 delivery attempts before giving up

#### 2. **New Messages Only Consumer** (Process only new uploads)

```bash
nats consumer add FILE_UPLOADS new_files_only \
  --server nats://guest:guest@localhost:32871 \
  --filter "file.uploads" \
  --ack explicit \
  --pull \
  --deliver new \
  --max-deliver=-1 \
  --max-pending=50 \
  --wait=30s
```

Use `--deliver new` to only receive messages published after consumer creation.

#### 3. **Ordered Consumer** (Strict sequential processing)

```bash
nats consumer add FILE_UPLOADS ordered_processor \
  --server nats://guest:guest@localhost:32871 \
  --filter "file.uploads" \
  --pull \
  --deliver all \
  --replay original \
  --max-deliver=1 \
  --flow-control
```

Guarantees message order for sequential .NET processing.

### List All Consumers

```bash
nats consumer ls FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871
```

### Get Consumer Details

```bash
nats consumer info FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:32871
```

Output includes:
- Message counts (delivered, acknowledged, pending)
- Consumer configuration
- Redelivery statistics
- Last activity timestamp

### Monitor Consumer Health

```bash
# Get consumer statistics
nats consumer report FILE_UPLOADS \
  --server nats://guest:guest@localhost:32871

# Watch consumer in real-time
watch -n 2 'nats consumer info FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:32871'
```

### Delete Consumer

```bash
nats consumer rm FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:32871 \
  --force
```

### Reset Consumer Position

If your .NET client needs to reprocess messages:

```bash
# Delete and recreate consumer
nats consumer rm FILE_UPLOADS file_processor --force \
  --server nats://guest:guest@localhost:32871

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

## .NET Client Integration

### Connection Details for .NET

Your .NET NATS client needs these connection parameters:

```csharp
// Connection URL
string natsUrl = "nats://localhost:4222";  // or 32871 for DevServices

// Credentials
string username = "guest";
string password = "guest";

// Stream configuration
string streamName = "FILE_UPLOADS";
string consumerName = "file_processor";
string subject = "file.uploads";
```

### .NET Consumer Example (Pseudocode)

```csharp
// 1. Connect to NATS
var opts = ConnectionFactory.GetDefaultOptions();
opts.Url = "nats://localhost:4222";
opts.User = "guest";
opts.Password = "guest";

var connection = new ConnectionFactory().CreateConnection(opts);
var jsm = connection.CreateJetStreamManagementContext();
var js = connection.CreateJetStreamContext();

// 2. Get the durable consumer
var consumer = js.GetConsumerContext("FILE_UPLOADS", "file_processor");

// 3. Pull messages
while (true)
{
    var messages = consumer.Fetch(10, 5000); // Fetch 10 msgs, 5s timeout
    
    foreach (var msg in messages)
    {
        try
        {
            // Process message
            ProcessFileUploadEvent(msg.Data);
            
            // Acknowledge successful processing
            msg.Ack();
        }
        catch (Exception ex)
        {
            // Negative acknowledgment - will be redelivered
            msg.Nak();
        }
    }
}
```

### Required .NET NuGet Package

```bash
dotnet add package NATS.Client
# or
dotnet add package NATS.Client.JetStream
```

### Consumer Configuration Best Practices for .NET

1. **Use Pull Consumers**: Better control and error handling
2. **Set Reasonable Timeouts**: `--wait=30s` to `--wait=60s`
3. **Limit Pending Messages**: `--max-pending=100` prevents overwhelming your app
4. **Enable Retries**: `--max-deliver=3` for limited retries, or `-1` for unlimited
5. **Use Explicit Ack**: Always acknowledge messages after successful processing
6. **Monitor Consumer Lag**: Check pending messages regularly

### Testing Consumer with CLI

Before integrating with .NET, test the consumer:

```bash
# Subscribe using the durable consumer
nats consumer next FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:32871 \
  --count 1

# Fetch multiple messages
nats consumer next FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:32871 \
  --count 10 \
  --no-wait
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
