# Quarks-Tigris: Secure File Upload Service

A Quarkus-based secure file upload service with Angular frontend, featuring envelope encryption and S3/Tigris storage.

## Features

✅ **Client-Side Encryption** - Files encrypted in browser with AES-GCM  
✅ **Envelope Encryption** - Server-side re-encryption with random DEK + master key  
✅ **Streaming Processing** - Memory-efficient handling of large files  
✅ **S3/Tigris Storage** - Scalable object storage  
✅ **Angular Frontend** - Modern SPA with authentication  
✅ **OpenAPI Documentation** - Auto-generated API docs  

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- Node.js 20+ (for frontend)
- Docker (optional, for LocalStack)

### Run in Development Mode

```bash
# Start with hot reload (backend + frontend)
./mvnw quarkus:dev

# Or use the convenience script
./dev-mode.sh
```

Access the application:
- **Web UI**: http://localhost:8080/whisper
- **API Docs**: http://localhost:8080/whisper/swagger-ui
- **Health Check**: http://localhost:8080/whisper/q/health

### Default Passphrase
```
your-secret-passphrase
```
(Change in `application.properties`)

## Project Structure

```
quarks-tigris/
├── src/main/java/me/cresterida/
│   ├── FileUploadResource.java      # REST endpoints
│   ├── dto/                          # Request/Response DTOs
│   │   ├── ErrorResponse.java
│   │   ├── PassphraseRequest.java
│   │   ├── PassphraseResponse.java
│   │   └── UploadResponse.java
│   ├── model/                        # Data models
│   │   └── EnvelopeMetadata.java
│   ├── service/                      # Business logic
│   │   └── CryptoService.java       # Encryption service
│   └── util/                         # Utilities
│       └── S3StorageService.java    # S3 operations
├── src/main/webui/                  # Angular frontend
│   └── src/app/
│       ├── passphrase/              # Authentication
│       ├── mp3-upload/              # File upload
│       └── auth.guard.ts            # Route protection
└── docs/                            # Additional documentation
    └── archive/                     # Historical docs
```

## Architecture

### System Overview

```ascii
┌──────────────────────────────────────────────────────────────────┐
│                         User's Browser                           │
└──────────────────────────────────────────────────────────────────┘
                               │
                               │ 1. Access Web UI
                               │    http://localhost:8080/whisper
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Angular Frontend (SPA)                      │
│  ┌────────────────┐  ┌────────────────┐  ┌───────────────────┐  │
│  │   Passphrase   │  │   Auth Guard   │  │   MP3 Upload      │  │
│  │   Component    │  │   & Service    │  │   Component       │  │
│  └────────────────┘  └────────────────┘  └───────────────────┘  │
│         │                                          │              │
│         │ 2. Validate Passphrase                  │              │
│         │    (AES-GCM client-side encryption)     │              │
│         └──────────────────┬───────────────────────┘              │
│                            │ 3. Upload Encrypted File             │
└────────────────────────────┼──────────────────────────────────────┘
                             │
                             │ Quinoa Integration
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Quarkus Backend                             │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              FileUploadResource                            │  │
│  │  • POST /api/validate-passphrase                          │  │
│  │  • POST /api/upload (100MB limit)                         │  │
│  └────────────────────────────────────────────────────────────┘  │
│                            │                                      │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              CryptoService (service/)                      │  │
│  │  • Verify & decrypt from Angular                          │  │
│  │  • Encrypt with random DEK (streaming)                    │  │
│  │  • Create envelope (encrypt DEK with master key)          │  │
│  └────────────────────────────────────────────────────────────┘  │
│                            │                                      │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │           S3StorageService (util/)                         │  │
│  │  • Generate S3 keys                                        │  │
│  │  • Upload encrypted file                                   │  │
│  │  • Upload envelope metadata                                │  │
│  └────────────────────────────────────────────────────────────┘  │
│                            │                                      │
│                            │ 4. Store Encrypted Data              │
└────────────────────────────┼──────────────────────────────────────┘
                             │
                             │ S3 Client
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                    S3/Tigris Object Storage                      │
│  • uploads/{email}/{uuid}/file.enc    (encrypted data)          │
│  • uploads/{email}/{uuid}/metadata.json (encrypted DEK)         │
│                                                                  │
│  Dev:  LocalStack (Docker container)                            │
│  Prod: Tigris or AWS S3                                         │
└──────────────────────────────────────────────────────────────────┘
```

### Encryption Flow

```
User Browser
    ↓ (Client encrypts with passphrase: AES-256-GCM + PBKDF2)
Encrypted File → Quarkus Backend
    ↓ (Verify passphrase & decrypt: streaming)
Plaintext Data → CryptoService
    ↓ (Encrypt with random DEK: streaming)
DEK-Encrypted Data
    ↓ (Encrypt DEK with master key: envelope)
S3/Tigris Storage
    ├─ Encrypted File Data
    └─ Metadata (with encrypted DEK)
```

### Security Layers

1. **Client-Side**: AES-256-GCM with PBKDF2 (100k iterations)
2. **Server-Side**: Random DEK per file for data encryption
3. **Envelope**: DEK encrypted with master key for secure storage
4. **Streaming**: Memory-efficient processing (8KB buffers)

## Data Formats

### Envelope Encryption Format

The envelope metadata is stored as JSON in S3 alongside the encrypted file:

**S3 Storage Structure:**
```
uploads/{email}/{uuid}/
├── {filename}.enc          # Encrypted file data (binary)
└── metadata.json           # Envelope metadata (JSON)
```

**Envelope Metadata JSON Schema:**

```json
{
  "encryptedDEK": "base64-encoded-encrypted-data-encryption-key",
  "dekIV": "base64-encoded-initialization-vector-for-dek",
  "dataIV": "base64-encoded-initialization-vector-for-file",
  "originalFileName": "audio.mp3",
  "encryptedFileName": "audio.mp3.enc",
  "fileSize": 1048576,
  "encryptedSize": 1048592,
  "contentType": "audio/mpeg",
  "uploadTime": "2026-01-26T15:30:00Z",
  "email": "user@example.com"
}
```

**Field Descriptions:**

| Field | Type | Description |
|-------|------|-------------|
| `encryptedDEK` | String (Base64) | The Data Encryption Key (DEK) encrypted with the master key using AES-256-GCM |
| `dekIV` | String (Base64) | 12-byte initialization vector used to encrypt the DEK |
| `dataIV` | String (Base64) | 12-byte initialization vector used to encrypt the file data with the DEK |
| `originalFileName` | String | Original file name before encryption |
| `encryptedFileName` | String | File name with `.enc` extension |
| `fileSize` | Long | Original file size in bytes |
| `encryptedSize` | Long | Encrypted file size in bytes (includes GCM tag) |
| `contentType` | String | MIME type of the original file |
| `uploadTime` | String (ISO-8601) | Timestamp when file was uploaded |
| `email` | String | Email address of the uploader |

**Encryption Process:**

1. Generate random 256-bit DEK (Data Encryption Key)
2. Encrypt file data with DEK using AES-256-GCM (streaming)
3. Encrypt DEK with master key using AES-256-GCM
4. Store encrypted file and metadata separately in S3

**Decryption Process:**

1. Retrieve envelope metadata from S3
2. Decrypt DEK using master key and `dekIV`
3. Use decrypted DEK to decrypt file data with `dataIV`
4. Return original file

### NATS JetStream Message Format

After successful file upload, a message is published to the `FILE_UPLOADS` stream on subject `file.uploads`.

**NATS Message JSON Schema:**

```json
{
  "event_id": "4820c1bd-2753-4d70-bcae-43aa36a04889",
  "email": "kiquetal@gmail.com",
  "file_uuid": "8af4a599-089a-431a-833c-0c9a2fca372a",
  "s3_data_key": "uploads/kiquetal@gmail.com/8af4a599-089a-431a-833c-0c9a2fca372a/zeno.mp3.enc",
  "s3_metadata_key": "uploads/kiquetal@gmail.com/8af4a599-089a-431a-833c-0c9a2fca372a/metadata.json",
  "bucket_name": "whispers-bucket-dev",
  "timestamp": 1769394113840
}
```

**Field Descriptions:**

| Field | Type | Description |
|-------|------|-------------|
| `event_id` | String (UUID) | Unique identifier for this upload event |
| `email` | String | Email address of the uploader |
| `file_uuid` | String (UUID) | Unique identifier for the uploaded file |
| `s3_data_key` | String | Full S3 object key for the encrypted file (`.enc`) |
| `s3_metadata_key` | String | Full S3 object key for the envelope metadata JSON |
| `bucket_name` | String | S3 bucket name where files are stored |
| `timestamp` | Long | Unix timestamp in milliseconds (epoch time) |

**NATS Stream Configuration:**

| Setting | Value |
|---------|-------|
| **Stream Name** | `FILE_UPLOADS` |
| **Subject** | `file.uploads` |
| **Storage** | File-based (persistent) |
| **Retention** | 7 days (limits policy) |
| **Max Age** | 168 hours |
| **Replicas** | 1 (default) |

**Consumer Configuration (.NET Client):**

```bash
nats consumer add FILE_UPLOADS file_processor \
  --filter "file.uploads" \
  --ack explicit \
  --pull \
  --deliver all \
  --max-deliver=-1 \
  --max-pending=100 \
  --wait=30s
```

**Message Flow:**

```
File Upload → CryptoService → S3 Storage → NatsService → JetStream
                                                            ↓
                                              .NET Consumer (Pull)
```

**Use Cases for NATS Messages:**

1. **Asynchronous Processing**: Trigger background jobs (transcoding, analysis)
2. **Notifications**: Send email/webhook notifications on upload
3. **Audit Logging**: Track all file upload events
4. **Integration**: Connect to .NET/Python/Go consumers for downstream processing
5. **Monitoring**: Track upload metrics and statistics

**Related Documentation:**
- [NATS_COMMANDS.md](NATS_COMMANDS.md) - NATS CLI commands and consumer setup
- [DOTNET_CONSUMER_SETUP.md](DOTNET_CONSUMER_SETUP.md) - Complete .NET consumer guide with code examples
- [NATS_INTEGRATION.md](docs/NATS_INTEGRATION.md) - NATS integration architecture

### Complete Example: File Upload Flow

**Step 1: User Uploads File**
- File: `my-audio.mp3` (1 MB)
- User: `alice@example.com`
- Client encrypts with passphrase before upload

**Step 2: Server Processes Upload**

Generated UUID: `550e8400-e29b-41d4-a716-446655440000`

S3 Keys created:
```
uploads/alice@example.com/550e8400-e29b-41d4-a716-446655440000/my-audio.mp3.enc
uploads/alice@example.com/550e8400-e29b-41d4-a716-446655440000/metadata.json
```

**Step 3: Envelope Metadata Stored**

`metadata.json`:
```json
{
  "encryptedDEK": "kL8vN2mR5tY9wB3xF6jP1qS4uH7zC0eI=",
  "dekIV": "a1b2c3d4e5f6g7h8i9j0k1l2",
  "dataIV": "m1n2o3p4q5r6s7t8u9v0w1x2",
  "originalFileName": "my-audio.mp3",
  "encryptedFileName": "my-audio.mp3.enc",
  "fileSize": 1048576,
  "encryptedSize": 1048592,
  "contentType": "audio/mpeg",
  "uploadTime": "2026-01-26T15:30:00.123Z",
  "email": "alice@example.com"
}
```

**Step 4: NATS Message Published**

Published to: `FILE_UPLOADS` stream, subject `file.uploads`

```json
{
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "alice@example.com",
  "file_uuid": "550e8400-e29b-41d4-a716-446655440000",
  "s3_data_key": "uploads/alice@example.com/550e8400-e29b-41d4-a716-446655440000/my-audio.mp3.enc",
  "s3_metadata_key": "uploads/alice@example.com/550e8400-e29b-41d4-a716-446655440000/metadata.json",
  "bucket_name": "whisper-uploads",
  "timestamp": 1737904200123
}
```

**Step 5: .NET Consumer Processes Message**

Your .NET consumer receives the message and can:
1. Parse the NATS message to get `s3_data_key` and `s3_metadata_key`
2. Download encrypted file from S3: `bucket_name` + `s3_data_key`
3. Download envelope metadata from S3: `bucket_name` + `s3_metadata_key`
4. Decrypt file using the envelope (requires master key)
5. Process the original file (transcoding, analysis, etc.)
6. Acknowledge message to NATS

**Example .NET Code:**

```csharp
var message = JsonSerializer.Deserialize<FileUploadEvent>(msgData);

// Download encrypted file
var encryptedFile = await s3Client.GetObjectAsync(
    message.bucket_name, 
    message.s3_data_key
);

// Download envelope metadata
var metadata = await s3Client.GetObjectAsync(
    message.bucket_name, 
    message.s3_metadata_key
);

// Decrypt and process file...
```

**Security Notes:**
- File is **double-encrypted**: once by client, once by server
- DEK is randomly generated per file (never reused)
- Master key never stored with the data
- Each encryption layer uses unique IVs
- .NET consumer needs master key to decrypt files

## Configuration

### Environment Variables

```bash
# S3/Tigris Configuration
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_REGION=auto
S3_ENDPOINT_OVERRIDE=https://fly.storage.tigris.dev
S3_BUCKET_NAME=your-bucket-name

# Encryption
APP_PASSPHRASE=your-secret-passphrase
ENCRYPTION_MASTER_KEY=base64-encoded-master-key
```

### Generate Master Key

```bash
openssl rand -base64 32
```

## API Endpoints

The application provides a RESTful API for passphrase validation and file upload.

**Interactive API Documentation:**
- **Swagger UI**: http://localhost:8080/whisper/swagger-ui
- **OpenAPI Spec**: http://localhost:8080/whisper/swagger

Use the Swagger UI to explore and test all available endpoints with interactive documentation.

## Documentation

- **[GETTING_STARTED.md](GETTING_STARTED.md)** - Detailed setup guide
- **[API_TESTING.md](API_TESTING.md)** - API testing guide with examples
- **[ENVELOPE_ENCRYPTION_ARCHITECTURE.md](ENVELOPE_ENCRYPTION_ARCHITECTURE.md)** - Encryption architecture details
- **[docs/archive/](docs/archive/)** - Historical documentation

## Development

### Hot Reload
Both backend and frontend support hot reload in dev mode:
- **Backend**: Automatic recompilation on Java changes
- **Frontend**: Automatic rebuild on TypeScript/HTML/CSS changes

### Project Packages

**Backend Packages:**
- `me.cresterida` - REST resources
- `me.cresterida.dto` - Data Transfer Objects
- `me.cresterida.model` - Domain models
- `me.cresterida.service` - Business logic (encryption)
- `me.cresterida.util` - Utilities (S3 storage)

**Frontend Structure:**
- `passphrase/` - Authentication component
- `mp3-upload/` - File upload component
- `auth.guard.ts` - Route protection
- `encryption.service.ts` - Client-side encryption

### Build for Production

```bash
# Build JVM-based JAR
./mvnw package

# Build native executable (requires GraalVM)
./mvnw package -Dnative

# Build container
docker build -f src/main/docker/Dockerfile.jvm -t quarks-tigris .
```

## Troubleshooting

### Port Already in Use
```bash
# Kill process on port 8080
lsof -ti:8080 | xargs kill -9
```

### S3 Connection Issues
Check your endpoint configuration in `application.properties`:
```properties
quarkus.s3.endpoint-override=${S3_ENDPOINT_OVERRIDE}
quarkus.s3.path-style-access=true
```

### Frontend Build Errors
```bash
cd src/main/webui
npm install
npm run build
```

## Technology Stack

- **Backend**: Quarkus 3.30.7, Java 21
- **Frontend**: Angular 19, TypeScript
- **Storage**: S3-compatible (Tigris, AWS S3, LocalStack)
- **Encryption**: AES-256-GCM, PBKDF2
- **API Docs**: OpenAPI/Swagger
- **Build**: Maven, npm

## License

Apache 2.0

---

**Built with ❤️ using Quarkus and Angular**

