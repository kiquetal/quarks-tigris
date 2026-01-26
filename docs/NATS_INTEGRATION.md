# NATS JetStream Integration

This document provides comprehensive guidance for integrating with the NATS JetStream messaging system to consume file upload events.

## Overview

The Quarks-Tigris application publishes file upload events to NATS JetStream after successfully uploading and encrypting files. This enables asynchronous processing in any programming language that supports NATS clients.

## Architecture

```
File Upload → Quarkus Backend → S3 Storage
                     ↓
              NATS JetStream (FILE_UPLOADS stream)
                     ↓
         ┌───────────┴───────────┐
         ↓                       ↓
    .NET Consumer          Python Consumer
    (Pull-based)           (Pull-based)
         ↓                       ↓
    Process Files          Process Files
```

## NATS Stream Configuration

The application uses a JetStream stream with the following configuration:

| Setting | Value | Description |
|---------|-------|-------------|
| **Stream Name** | `FILE_UPLOADS` | Name of the JetStream stream |
| **Subject** | `file.uploads` | Subject pattern for messages |
| **Storage** | File-based | Persistent storage on disk |
| **Retention** | Limits (7 days) | Messages retained for 7 days |
| **Max Age** | 168 hours | Maximum message age before deletion |
| **Replicas** | 1 | Number of stream replicas |
| **Discard Policy** | Old | Discard old messages when limits reached |

## Message Format

Each file upload publishes a JSON message to the `file.uploads` subject:

```json
{
  "event_id": "4820c1bd-2753-4d70-bcae-43aa36a04889",
  "email": "user@example.com",
  "file_uuid": "8af4a599-089a-431a-833c-0c9a2fca372a",
  "s3_data_key": "uploads/user@example.com/8af4a599-089a-431a-833c-0c9a2fca372a/audio.mp3.enc",
  "s3_metadata_key": "uploads/user@example.com/8af4a599-089a-431a-833c-0c9a2fca372a/metadata.json",
  "bucket_name": "whispers-bucket-dev",
  "timestamp": 1769394113840
}
```

### Message Fields

| Field | Type | Description |
|-------|------|-------------|
| `event_id` | String (UUID) | Unique identifier for this upload event |
| `email` | String | Email address of the uploader |
| `file_uuid` | String (UUID) | Unique identifier for the uploaded file |
| `s3_data_key` | String | Full S3 object key for the encrypted file (`.enc`) |
| `s3_metadata_key` | String | Full S3 object key for the envelope metadata JSON |
| `bucket_name` | String | S3 bucket name where files are stored |
| `timestamp` | Long | Unix timestamp in milliseconds (epoch time) |

## Consumer Setup

### Prerequisites

- NATS Server with JetStream enabled
- NATS CLI tool (for management)
- NATS client library for your language

### Quick Setup with Docker

**1. Start NATS JetStream:**

```bash
# Using docker-compose (recommended)
docker-compose up -d

# Or manually with Docker
docker run -d --name nats-jetstream \
  -p 4222:4222 \
  -p 8222:8222 \
  nats:latest \
  -js \
  -m 8222 \
  --user guest \
  --pass guest
```

**2. Verify NATS is Running:**

```bash
# Check server info
nats server info --server nats://guest:guest@localhost:4222

# Check stream exists
nats stream info FILE_UPLOADS --server nats://guest:guest@localhost:4222
```

### Creating a Durable Consumer

A durable consumer allows your application to track its progress and resume from where it left off.

**Automated Setup:**

```bash
# Use the provided script
./setup-nats-consumer.sh
```

**Manual Setup:**

```bash
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
```

**Consumer Configuration Explained:**

- `FILE_UPLOADS` - Stream name
- `file_processor` - Consumer name (durable)
- `--filter "file.uploads"` - Only receive messages from this subject
- `--ack explicit` - Manual message acknowledgment required
- `--pull` - Pull-based consumer (application requests messages)
- `--deliver all` - Deliver all messages from the stream
- `--max-deliver=-1` - Unlimited delivery attempts
- `--max-pending=100` - Maximum unacknowledged messages
- `--wait=30s` - Wait timeout for fetch requests
- `--replay instant` - Deliver messages as fast as possible

**Verify Consumer:**

```bash
# Check consumer info
nats consumer info FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:4222

# Test fetching a message
nats consumer next FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:4222
```

## Consumer Implementation Examples

### F#/.NET Consumer

**Install NuGet Package:**

```bash
dotnet add package NATS.Client
```

**F# Implementation:**

```fsharp
open System
open System.Text
open System.Text.Json
open NATS.Client

// Message type
type FileUploadEvent = {
    event_id: string
    email: string
    file_uuid: string
    s3_data_key: string
    s3_metadata_key: string
    bucket_name: string
    timestamp: int64
}

// Connect to NATS
let connectionFactory = ConnectionFactory()
let options = ConnectionFactory.GetDefaultOptions()
options.Url <- "nats://localhost:4222"
options.User <- "guest"
options.Password <- "guest"

use connection = connectionFactory.CreateConnection(options)
let js = connection.CreateJetStreamContext()

// Get consumer context
let consumer = js.GetConsumerContext("FILE_UPLOADS", "file_processor")

printfn "🚀 NATS Consumer started. Listening for file upload events..."

// Pull messages in a loop
while true do
    try
        // Fetch up to 10 messages with 5 second timeout
        let messages = consumer.Fetch(10, 5000)
        
        for msg in messages do
            try
                // Deserialize message
                let json = Encoding.UTF8.GetString(msg.Data)
                let event = JsonSerializer.Deserialize<FileUploadEvent>(json)
                
                printfn "\n📨 Received file upload event:"
                printfn "   Event ID: %s" event.event_id
                printfn "   Email: %s" event.email
                printfn "   File UUID: %s" event.file_uuid
                printfn "   S3 Data Key: %s" event.s3_data_key
                printfn "   Bucket: %s" event.bucket_name
                
                // Process the file here
                // - Download from S3
                // - Decrypt if needed
                // - Process/transform
                
                // Acknowledge message
                msg.Ack()
                printfn "   ✅ Message acknowledged"
                
            with ex ->
                printfn "   ❌ Error processing message: %s" ex.Message
                msg.Nak() // Negative acknowledgment
                
    with ex ->
        printfn "Error fetching messages: %s" ex.Message
        System.Threading.Thread.Sleep(1000)
```

**C# Implementation:**

```csharp
using System;
using System.Text;
using System.Text.Json;
using NATS.Client;
using NATS.Client.JetStream;

public class FileUploadEvent
{
    public string event_id { get; set; }
    public string email { get; set; }
    public string file_uuid { get; set; }
    public string s3_data_key { get; set; }
    public string s3_metadata_key { get; set; }
    public string bucket_name { get; set; }
    public long timestamp { get; set; }
}

class Program
{
    static void Main(string[] args)
    {
        var opts = ConnectionFactory.GetDefaultOptions();
        opts.Url = "nats://localhost:4222";
        opts.User = "guest";
        opts.Password = "guest";

        var cf = new ConnectionFactory();
        using var connection = cf.CreateConnection(opts);
        var js = connection.CreateJetStreamContext();

        var consumer = js.GetConsumerContext("FILE_UPLOADS", "file_processor");

        Console.WriteLine("🚀 NATS Consumer started. Listening for file upload events...");

        while (true)
        {
            try
            {
                var messages = consumer.Fetch(10, 5000);

                foreach (var msg in messages)
                {
                    try
                    {
                        var json = Encoding.UTF8.GetString(msg.Data);
                        var evt = JsonSerializer.Deserialize<FileUploadEvent>(json);

                        Console.WriteLine($"\n📨 Received file upload event:");
                        Console.WriteLine($"   Event ID: {evt.event_id}");
                        Console.WriteLine($"   Email: {evt.email}");
                        Console.WriteLine($"   S3 Data Key: {evt.s3_data_key}");

                        // Process the file here

                        msg.Ack();
                        Console.WriteLine("   ✅ Message acknowledged");
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"   ❌ Error: {ex.Message}");
                        msg.Nak();
                    }
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error fetching messages: {ex.Message}");
                System.Threading.Thread.Sleep(1000);
            }
        }
    }
}
```

### Python Consumer

**Install Package:**

```bash
pip install nats-py
```

**Python Implementation:**

```python
import asyncio
import json
from nats.aio.client import Client as NATS
from nats.js.api import ConsumerConfig

async def consume_messages():
    nc = NATS()
    
    # Connect to NATS
    await nc.connect("nats://guest:guest@localhost:4222")
    
    # Get JetStream context
    js = nc.jetstream()
    
    print("🚀 NATS Consumer started. Listening for file upload events...")
    
    # Pull subscribe
    psub = await js.pull_subscribe(
        subject="file.uploads",
        durable="file_processor",
        stream="FILE_UPLOADS"
    )
    
    while True:
        try:
            # Fetch up to 10 messages with 5 second timeout
            messages = await psub.fetch(batch=10, timeout=5)
            
            for msg in messages:
                try:
                    # Deserialize message
                    event = json.loads(msg.data.decode())
                    
                    print(f"\n📨 Received file upload event:")
                    print(f"   Event ID: {event['event_id']}")
                    print(f"   Email: {event['email']}")
                    print(f"   File UUID: {event['file_uuid']}")
                    print(f"   S3 Data Key: {event['s3_data_key']}")
                    print(f"   Bucket: {event['bucket_name']}")
                    
                    # Process the file here
                    # - Download from S3
                    # - Process/transform
                    
                    # Acknowledge message
                    await msg.ack()
                    print("   ✅ Message acknowledged")
                    
                except Exception as ex:
                    print(f"   ❌ Error processing message: {ex}")
                    await msg.nak()
                    
        except asyncio.TimeoutError:
            # No messages available, continue polling
            pass
        except Exception as ex:
            print(f"Error fetching messages: {ex}")
            await asyncio.sleep(1)

if __name__ == '__main__':
    asyncio.run(consume_messages())
```

### Go Consumer

**Install Package:**

```bash
go get github.com/nats-io/nats.go
```

**Go Implementation:**

```go
package main

import (
    "encoding/json"
    "fmt"
    "log"
    "time"

    "github.com/nats-io/nats.go"
)

type FileUploadEvent struct {
    EventID        string `json:"event_id"`
    Email          string `json:"email"`
    FileUUID       string `json:"file_uuid"`
    S3DataKey      string `json:"s3_data_key"`
    S3MetadataKey  string `json:"s3_metadata_key"`
    BucketName     string `json:"bucket_name"`
    Timestamp      int64  `json:"timestamp"`
}

func main() {
    // Connect to NATS
    nc, err := nats.Connect("nats://guest:guest@localhost:4222")
    if err != nil {
        log.Fatal(err)
    }
    defer nc.Close()

    // Get JetStream context
    js, err := nc.JetStream()
    if err != nil {
        log.Fatal(err)
    }

    // Create pull subscription
    sub, err := js.PullSubscribe("file.uploads", "file_processor")
    if err != nil {
        log.Fatal(err)
    }

    fmt.Println("🚀 NATS Consumer started. Listening for file upload events...")

    for {
        // Fetch up to 10 messages with 5 second timeout
        messages, err := sub.Fetch(10, nats.MaxWait(5*time.Second))
        if err != nil {
            if err == nats.ErrTimeout {
                continue
            }
            log.Printf("Error fetching messages: %v", err)
            time.Sleep(1 * time.Second)
            continue
        }

        for _, msg := range messages {
            var event FileUploadEvent
            if err := json.Unmarshal(msg.Data, &event); err != nil {
                log.Printf("❌ Error unmarshaling message: %v", err)
                msg.Nak()
                continue
            }

            fmt.Printf("\n📨 Received file upload event:\n")
            fmt.Printf("   Event ID: %s\n", event.EventID)
            fmt.Printf("   Email: %s\n", event.Email)
            fmt.Printf("   File UUID: %s\n", event.FileUUID)
            fmt.Printf("   S3 Data Key: %s\n", event.S3DataKey)
            fmt.Printf("   Bucket: %s\n", event.BucketName)

            // Process the file here

            // Acknowledge message
            msg.Ack()
            fmt.Println("   ✅ Message acknowledged")
        }
    }
}
```

## Supported Languages

The following languages have official NATS client libraries:

- ✅ **F#** - NATS.Client NuGet package
- ✅ **C#/.NET** - NATS.Client NuGet package
- ✅ **Python** - nats-py (pip package)
- ✅ **Go** - nats.go library
- ✅ **Node.js/TypeScript** - nats.js (npm package)
- ✅ **Rust** - async-nats crate
- ✅ **Java** - nats.java library
- ✅ **Ruby** - nats-pure gem

All languages can consume from the same `FILE_UPLOADS` stream and work in parallel!

## Use Cases

### 1. Asynchronous File Processing

Process uploaded files in the background:
- Audio transcoding
- Video processing
- Image optimization
- File analysis/scanning

### 2. Notifications

Send notifications when files are uploaded:
- Email notifications
- Webhook callbacks
- Slack/Discord notifications
- SMS alerts

### 3. Audit Logging

Track all file upload events:
- Centralized logging
- Compliance tracking
- Usage analytics
- Security monitoring

### 4. Multi-Service Integration

Connect multiple services to the same stream:
- .NET service for transcoding
- Python service for ML analysis
- Go service for file validation
- Node.js service for notifications

### 5. Data Pipeline

Build a complete data pipeline:
```
Upload → JetStream → Consumer 1 (Process) → Consumer 2 (Notify) → Consumer 3 (Archive)
```

## Best Practices

### 1. Message Acknowledgment

Always acknowledge or negatively acknowledge messages:

```fsharp
try
    // Process message
    processFile event
    msg.Ack()  // Success
with ex ->
    msg.Nak()  // Failure - requeue message
```

### 2. Error Handling

Implement proper error handling:
- Catch exceptions during processing
- Use `Nak()` for retriable errors
- Use `Term()` for permanent failures
- Log errors for debugging

### 3. Idempotency

Make your consumers idempotent:
- Track processed event IDs
- Skip duplicate messages
- Use database transactions

### 4. Backpressure

Control message flow:
- Fetch small batches (e.g., 10 messages)
- Process before fetching more
- Use `--max-pending` to limit unacknowledged messages

### 5. Monitoring

Monitor your consumers:
- Track processing rate
- Monitor error rates
- Alert on consumer lag
- Check message age

### 6. Graceful Shutdown

Handle shutdown properly:
```fsharp
// Register shutdown handler
use cts = new CancellationTokenSource()
Console.CancelKeyPress.Add(fun _ -> 
    printfn "Shutting down..."
    cts.Cancel()
)

// Check cancellation in loop
while not cts.Token.IsCancellationRequested do
    // Process messages
```

## Monitoring & Operations

### Check Consumer Status

```bash
# View consumer info
nats consumer info FILE_UPLOADS file_processor

# Monitor in real-time
nats consumer info FILE_UPLOADS file_processor --watch

# List all consumers
nats consumer list FILE_UPLOADS
```

### View Stream Status

```bash
# Stream info
nats stream info FILE_UPLOADS

# View messages
nats stream view FILE_UPLOADS

# Monitor stream
nats stream info FILE_UPLOADS --watch
```

### Reset Consumer

```bash
# Delete and recreate consumer (resets position)
nats consumer rm FILE_UPLOADS file_processor
./setup-nats-consumer.sh
```

## Troubleshooting

### Consumer Not Receiving Messages

1. **Check stream exists:**
   ```bash
   nats stream ls
   ```

2. **Check consumer exists:**
   ```bash
   nats consumer ls FILE_UPLOADS
   ```

3. **Verify messages in stream:**
   ```bash
   nats stream info FILE_UPLOADS
   ```

4. **Check consumer configuration:**
   ```bash
   nats consumer info FILE_UPLOADS file_processor
   ```

### Connection Issues

1. **Verify NATS is running:**
   ```bash
   docker ps | grep nats
   ```

2. **Check NATS logs:**
   ```bash
   docker logs nats-jetstream
   ```

3. **Test connection:**
   ```bash
   nats server ping
   ```

### Message Processing Errors

1. **View pending messages:**
   ```bash
   nats consumer info FILE_UPLOADS file_processor | grep Pending
   ```

2. **Check for errors in consumer logs**

3. **Test with single message:**
   ```bash
   nats consumer next FILE_UPLOADS file_processor --count 1
   ```

## Security Considerations

### Authentication

In production, use proper authentication:

```bash
# Use credentials file
nats --creds /path/to/creds.file consumer info FILE_UPLOADS file_processor

# Use token
nats --token mytoken consumer info FILE_UPLOADS file_processor
```

### Encryption

For production deployments:
- Enable TLS for NATS connections
- Use encrypted credentials
- Secure S3 access keys
- Store master encryption key securely

### Access Control

- Use NATS authentication
- Implement stream-level permissions
- Restrict consumer creation
- Audit consumer access

## Related Documentation

- **[NATS_COMMANDS.md](../NATS_COMMANDS.md)** - NATS CLI commands reference
- **[DOTNET_CONSUMER_SETUP.md](../DOTNET_CONSUMER_SETUP.md)** - Detailed F#/.NET setup guide
- **[README.md](../README.md)** - Main project documentation
- **[ENVELOPE_ENCRYPTION_ARCHITECTURE.md](../ENVELOPE_ENCRYPTION_ARCHITECTURE.md)** - Encryption details

## Additional Resources

- [NATS Documentation](https://docs.nats.io/)
- [JetStream Overview](https://docs.nats.io/nats-concepts/jetstream)
- [NATS Client Libraries](https://nats.io/download/)
- [NATS CLI Tool](https://github.com/nats-io/natscli)

---

**Need Help?** Check the troubleshooting section or refer to the official NATS documentation.
