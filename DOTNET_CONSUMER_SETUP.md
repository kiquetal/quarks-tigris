# .NET Consumer Setup Guide

Quick guide for setting up a .NET client to consume FILE_UPLOADS messages from NATS JetStream.

## Prerequisites

- NATS server with JetStream enabled
- NATS CLI installed (for setup)
- .NET 6+ SDK

## Step 1: Start NATS Server

### Using Docker Compose (Recommended)

```bash
# Start NATS + LocalStack
docker-compose up -d

# Verify NATS is running
docker-compose ps
curl http://localhost:8222/healthz
```

NATS will be available at:
- **Client Port**: `nats://localhost:4222`
- **Monitoring**: `http://localhost:8222`
- **Credentials**: `guest:guest`

### Or Run NATS Manually

```bash
# With JetStream enabled
nats-server -js -m 8222 --user guest --pass guest
```

## Step 2: Create the Stream (Automatic)

The Quarkus application **automatically creates** the `FILE_UPLOADS` stream on startup. You'll see:

```
🔧 Attempting to create NATS stream...
   ✓ Connected to NATS server
   ✅ Stream FILE_UPLOADS created successfully!
```

### Or Create Manually

```bash
nats stream add FILE_UPLOADS \
  --server nats://guest:guest@localhost:4222 \
  --subjects "file.uploads" \
  --storage file \
  --retention limits \
  --max-age 7d
```

## Step 3: Create Durable Consumer

This is **required** for your .NET client. Run this command once:

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

### Verify Consumer

```bash
nats consumer info FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:4222
```

Expected output:
```
Information for Consumer FILE_UPLOADS > file_processor created 2026-01-25 15:30:00

Configuration:

             Durable Name: file_processor
          Filter Subject: file.uploads
        Deliver Subject: _INBOX.xxx
            Ack Policy: explicit
              Ack Wait: 30s
         Replay Policy: instant
   Maximum Deliveries: unlimited
      Max Ack Pending: 100
  Maximum Batch Size: 100

State:

  Last Delivered Message: Consumer sequence: 0 Stream sequence: 0
    Acknowledgment floor: Consumer sequence: 0 Stream sequence: 0
        Outstanding Acks: 0 out of maximum 100
    Redelivered Messages: 0
    Unprocessed Messages: 0
           Waiting Pulls: 0
```

## Step 4: Install .NET NuGet Package

```bash
dotnet add package NATS.Client
```

Or add to your `.csproj`:

```xml
<ItemGroup>
  <PackageReference Include="NATS.Client" Version="1.1.4" />
</ItemGroup>
```

## Step 5: .NET Consumer Code

### Basic Pull Consumer

```csharp
using NATS.Client;
using NATS.Client.JetStream;
using System;
using System.Text;
using System.Text.Json;

public class FileUploadConsumer
{
    private IConnection connection;
    private IJetStream jetStream;
    
    public void Connect()
    {
        var opts = ConnectionFactory.GetDefaultOptions();
        opts.Url = "nats://localhost:4222";
        opts.User = "guest";
        opts.Password = "guest";
        
        connection = new ConnectionFactory().CreateConnection(opts);
        jetStream = connection.CreateJetStreamContext();
        
        Console.WriteLine("✓ Connected to NATS JetStream");
    }
    
    public void StartConsuming()
    {
        // Get the durable consumer
        var pullSubscribeOptions = PullSubscribeOptions.Builder()
            .WithStream("FILE_UPLOADS")
            .WithDurable("file_processor")
            .Build();
            
        var subscription = jetStream.PullSubscribe("file.uploads", pullSubscribeOptions);
        
        Console.WriteLine("✓ Subscribed to file_processor consumer");
        Console.WriteLine("Waiting for messages...\n");
        
        while (true)
        {
            try
            {
                // Fetch up to 10 messages with 5 second timeout
                var messages = subscription.Fetch(10, 5000);
                
                foreach (var msg in messages)
                {
                    ProcessMessage(msg);
                }
            }
            catch (NATSTimeoutException)
            {
                // No messages available, continue polling
                Console.WriteLine("No messages, waiting...");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error: {ex.Message}");
                System.Threading.Thread.Sleep(1000);
            }
        }
    }
    
    private void ProcessMessage(Msg msg)
    {
        try
        {
            // Parse the JSON message
            var json = Encoding.UTF8.GetString(msg.Data);
            var fileEvent = JsonSerializer.Deserialize<FileUploadEvent>(json);
            
            Console.WriteLine($"📥 Received: {fileEvent.FileName}");
            Console.WriteLine($"   User: {fileEvent.Email}");
            Console.WriteLine($"   S3 Key: {fileEvent.S3Key}");
            Console.WriteLine($"   Upload Time: {fileEvent.UploadTime}");
            
            // TODO: Your processing logic here
            // Example: Download from S3, process file, etc.
            
            // Acknowledge successful processing
            msg.Ack();
            Console.WriteLine("   ✓ Message acknowledged\n");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"   ✗ Error processing message: {ex.Message}");
            
            // Negative acknowledgment - message will be redelivered
            msg.Nak();
            Console.WriteLine("   ⚠ Message will be redelivered\n");
        }
    }
    
    public void Dispose()
    {
        connection?.Close();
        connection?.Dispose();
    }
}

// Message DTO matching the Java FileUploadEvent
public class FileUploadEvent
{
    public string FileName { get; set; }
    public string S3Key { get; set; }
    public string Email { get; set; }
    public long FileSize { get; set; }
    public string ContentType { get; set; }
    public DateTime UploadTime { get; set; }
    public string Status { get; set; }
}
```

### Program.cs

```csharp
using System;

class Program
{
    static void Main(string[] args)
    {
        Console.WriteLine("FILE_UPLOADS Consumer - .NET Client");
        Console.WriteLine("====================================\n");
        
        var consumer = new FileUploadConsumer();
        
        try
        {
            consumer.Connect();
            consumer.StartConsuming();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Fatal error: {ex.Message}");
            Console.WriteLine(ex.StackTrace);
        }
        finally
        {
            consumer.Dispose();
        }
    }
}
```

## Step 6: Run and Test

### Start Your .NET Consumer

```bash
dotnet run
```

Expected output:
```
FILE_UPLOADS Consumer - .NET Client
====================================

✓ Connected to NATS JetStream
✓ Subscribed to file_processor consumer
Waiting for messages...
```

### Upload a File via Web UI

1. Open browser: http://localhost:8080/whisper
2. Enter passphrase: `your-secret-passphrase`
3. Upload an MP3 file

### See Message in .NET Consumer

```
📥 Received: my-audio.mp3
   User: user@example.com
   S3 Key: uploads/user@example.com/abc-123/my-audio.mp3
   Upload Time: 2026-01-25 15:45:30
   ✓ Message acknowledged
```

## Monitoring

### Check Consumer Status

```bash
# See pending messages
nats consumer info FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:4222

# Watch in real-time
watch -n 2 'nats consumer info FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:4222'
```

### View Messages Without Consuming

```bash
nats stream view FILE_UPLOADS \
  --server nats://guest:guest@localhost:4222
```

### Manually Fetch Next Message (Testing)

```bash
nats consumer next FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:4222
```

## Troubleshooting

### Consumer Not Receiving Messages

```bash
# 1. Check if stream exists
nats stream info FILE_UPLOADS --server nats://guest:guest@localhost:4222

# 2. Check if consumer exists
nats consumer info FILE_UPLOADS file_processor --server nats://guest:guest@localhost:4222

# 3. Check message count in stream
nats stream view FILE_UPLOADS --server nats://guest:guest@localhost:4222 --last 5

# 4. Check NATS server health
curl http://localhost:8222/healthz
```

### Reset Consumer (Reprocess All Messages)

```bash
# Delete consumer
nats consumer rm FILE_UPLOADS file_processor \
  --server nats://guest:guest@localhost:4222 \
  --force

# Recreate it
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

### Connection Refused

If you get "connection refused":

1. **Check NATS is running**:
   ```bash
   docker-compose ps
   # or
   curl http://localhost:8222/healthz
   ```

2. **Check port** - DevServices uses random port (e.g., 32871), docker-compose uses 4222:
   ```bash
   # For docker-compose
   opts.Url = "nats://localhost:4222";
   
   # For DevServices (check logs for port)
   opts.Url = "nats://localhost:32871";
   ```

3. **Verify credentials**:
   ```bash
   nats server ping --server nats://guest:guest@localhost:4222
   ```

## Production Considerations

### Multiple Consumer Instances

To scale horizontally, just run multiple .NET instances with the **same consumer name**. NATS will automatically load balance messages:

```bash
# Terminal 1
dotnet run

# Terminal 2
dotnet run

# Terminal 3
dotnet run
```

Messages will be distributed across all instances.

### Error Handling Strategy

```csharp
private void ProcessMessage(Msg msg)
{
    try
    {
        var json = Encoding.UTF8.GetString(msg.Data);
        var fileEvent = JsonSerializer.Deserialize<FileUploadEvent>(json);
        
        // Your processing logic
        ProcessFile(fileEvent);
        
        // Success
        msg.Ack();
    }
    catch (TransientException ex)
    {
        // Temporary error - retry
        Console.WriteLine($"Transient error: {ex.Message}");
        msg.Nak(5000); // Nak with 5 second delay
    }
    catch (PermanentException ex)
    {
        // Permanent error - don't retry
        Console.WriteLine($"Permanent error: {ex.Message}");
        msg.Term(); // Terminate - won't be redelivered
    }
}
```

### Graceful Shutdown

```csharp
class Program
{
    private static FileUploadConsumer consumer;
    private static bool isShuttingDown = false;
    
    static void Main(string[] args)
    {
        Console.CancelKeyPress += (sender, e) => {
            Console.WriteLine("\nShutting down gracefully...");
            isShuttingDown = true;
            e.Cancel = true;
        };
        
        consumer = new FileUploadConsumer();
        consumer.Connect();
        consumer.StartConsuming(() => isShuttingDown);
        consumer.Dispose();
        
        Console.WriteLine("Shutdown complete");
    }
}
```

## Summary

1. ✅ Start NATS with JetStream: `docker-compose up -d`
2. ✅ Create durable consumer: `nats consumer add FILE_UPLOADS file_processor ...`
3. ✅ Add NuGet package: `dotnet add package NATS.Client`
4. ✅ Connect and consume: Pull messages, process, acknowledge
5. ✅ Monitor: Use `nats consumer info` to check status

**Connection String**: `nats://guest:guest@localhost:4222`

For more details, see [NATS_COMMANDS.md](NATS_COMMANDS.md).
