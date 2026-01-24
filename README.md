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

