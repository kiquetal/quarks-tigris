# Design Document: Voice-to-Text Pipeline

## Overview

The Voice-to-Text Pipeline is a distributed, security-focused system that enables users to upload audio files through a web interface, have them encrypted with multiple layers of protection, stored in S3-compatible object storage, and asynchronously transcribed using OpenAI's Whisper model. The system prioritizes data security through triple-layer encryption (client-side, server-side with unique DEKs, and envelope encryption for key protection) and reliability through persistent messaging with NATS JetStream.

The system consists of two independently deployable services:
1. **quarks-tigris**: A Quarkus backend with Angular frontend handling authentication, file upload, encryption, and storage
2. **FsNatsWhisper**: An F# consumer service that processes encrypted files and performs speech-to-text transcription

These services communicate asynchronously via NATS JetStream, allowing for horizontal scaling, fault tolerance, and decoupled deployment lifecycles.

## Architecture

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              USER'S BROWSER                                 │
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                      Angular Frontend (SPA)                          │  │
│  │  • Passphrase Authentication                                         │  │
│  │  • File Selection & Upload UI                                        │  │
│  │  • Client-Side AES-256-GCM Encryption (PBKDF2)                      │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      │ HTTPS
                                      │ Encrypted MP3 Upload
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         QUARKUS BACKEND (quarks-tigris)                     │
│                                                                             │
│  ┌────────────────────────────────────────────────────────────────┐        │
│  │  FileUploadResource (REST API)                                 │        │
│  │  • POST /api/validate-passphrase                               │        │
│  │  • POST /api/upload (100MB limit)                              │        │
│  │  • GET /api/files                                              │        │
│  │  • DELETE /api/files                                           │        │
│  └────────────────────────────────────────────────────────────────┘        │
│                              │                                              │
│  ┌────────────────────────────────────────────────────────────────┐        │
│  │  CryptoService                                                 │        │
│  │  • Verify client passphrase                                    │        │
│  │  • Decrypt client-encrypted file (streaming)                   │        │
│  │  • Generate random 256-bit DEK per file                        │        │
│  │  • Encrypt file with DEK (AES-256-GCM)                        │        │
│  │  • Encrypt DEK with master key → KEK (envelope encryption)    │        │
│  └────────────────────────────────────────────────────────────────┘        │
│                              │                                              │
│  ┌────────────────────────────────────────────────────────────────┐        │
│  │  S3StorageService                                              │        │
│  │  • Upload encrypted file: uploads/{email}/{uuid}/{file}.enc    │        │
│  │  • Upload metadata: uploads/{email}/{uuid}/metadata.json       │        │
│  └────────────────────────────────────────────────────────────────┘        │
│                              │                                              │
│  ┌────────────────────────────────────────────────────────────────┐        │
│  │  NatsService                                                   │        │
│  │  • Publish file upload event to JetStream                      │        │
│  │  • Subject: file.uploads                                       │        │
│  │  • Stream: FILE_UPLOADS                                        │        │
│  └────────────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
                    │                                    │
                    │                                    │
                    ▼                                    ▼
    ┌───────────────────────────┐      ┌────────────────────────────┐
    │   S3/Tigris Storage       │      │   NATS JetStream Server    │
    │                           │      │                            │
    │  • Encrypted files (.enc) │      │  Stream: FILE_UPLOADS      │
    │  • Envelope metadata      │      │  Subject: file.uploads     │
    │  • Bucket: whisper-*      │      │  Retention: 7 days         │
    └───────────────────────────┘      └────────────────────────────┘
                    │                                    │
                    │                                    │
                    │                                    │ Subscribe
                    │                                    │ (Pull Consumer)
                    │                                    ▼
                    │              ┌─────────────────────────────────────────┐
                    │              │  FsNatsWhisper Service (F# Consumer)    │
                    │              │                                         │
                    │              │  ┌───────────────────────────────────┐  │
                    │              │  │  NATS Consumer                    │  │
                    │              │  │  • Subscribe to file.uploads      │  │
                    │              │  │  • Durable consumer: file_processor│ │
                    │              │  │  • Explicit ACK                   │  │
                    │              │  └───────────────────────────────────┘  │
                    │              │                │                        │
                    │              │                ▼                        │
                    │              │  ┌───────────────────────────────────┐  │
                    │              │  │  S3 Download Module               │  │
                    │◄─────────────┼──┤  • Download metadata.json         │  │
                    │              │  │  • Download encrypted file        │  │
                    │              │  └───────────────────────────────────┘  │
                    │              │                │                        │
                    │              │                ▼                        │
                    │              │  ┌───────────────────────────────────┐  │
                    │              │  │  Crypto Module                    │  │
                    │              │  │  • Decrypt KEK with master key    │  │
                    │              │  │  • Extract DEK from KEK           │  │
                    │              │  │  • Decrypt file with DEK          │  │
                    │              │  │  • AES-256-GCM decryption         │  │
                    │              │  └───────────────────────────────────┘  │
                    │              │                │                        │
                    │              │                ▼                        │
                    │              │  ┌───────────────────────────────────┐  │
                    │              │  │  Audio Processing Module          │  │
                    │              │  │  • FFmpeg format conversion       │  │
                    │              │  │  • Audio normalization            │  │
                    │              │  └───────────────────────────────────┘  │
                    │              │                │                        │
                    │              │                ▼                        │
                    │              │  ┌───────────────────────────────────┐  │
                    │              │  │  Whisper Transcription Engine     │  │
                    │              │  │  • Speech-to-text conversion      │  │
                    │              │  │  • Language detection             │  │
                    │              │  │  • Timestamp generation           │  │
                    │              │  └───────────────────────────────────┘  │
                    │              │                │                        │
                    │              │                ▼                        │
                    │              │  ┌───────────────────────────────────┐  │
                    │              │  │  Result Publisher                 │  │
                    │              │  │  • Save to local file (current)   │  │
                    │              │  │  • Publish to NATS (future)       │  │
                    │              │  └───────────────────────────────────┘  │
                    │              └─────────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────┐
    │   Local Downloads Folder  │
    │   • Decrypted audio files │
    │   • Transcription .txt    │
    └───────────────────────────┘
```

### Security Architecture

The system implements defense-in-depth through three layers of encryption:

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: Client-Side Encryption (Browser)                  │
│ • Algorithm: AES-256-GCM                                    │
│ • Key Derivation: PBKDF2 (100k iterations, SHA-256)        │
│ • Input: User passphrase + random salt                     │
│ • Purpose: Protect data in transit, zero-trust transport   │
│ • IV: Random 12 bytes per file                             │
│ • Output: [IV][ciphertext][GCM tag]                        │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 2: Server-Side Encryption (Quarkus)                  │
│ • Algorithm: AES-256-GCM                                    │
│ • Key: Random 256-bit DEK (unique per file)                │
│ • Purpose: Protect data at rest, key isolation             │
│ • IV: Random 12 bytes per file                             │
│ • Output: [IV][ciphertext][GCM tag]                        │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: Envelope Encryption (Quarkus)                     │
│ • Algorithm: AES-256-GCM                                    │
│ • Key: Master key (from environment)                        │
│ • Encrypts: DEK → KEK                                       │
│ • Purpose: Secure key storage, key rotation capability     │
│ • IV: Random 12 bytes per KEK                              │
│ • Output: base64([IV][encrypted_DEK][GCM tag])             │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow Sequence

#### Upload Flow (quarks-tigris)

```
1. User Authentication
   User → Angular: Enter passphrase
   Angular → Quarkus: POST /api/validate-passphrase
   Quarkus → Angular: Session token

2. Client-Side Encryption
   User → Angular: Select MP3 file
   Angular: Generate cryptographically random salt (16 bytes using crypto.getRandomValues)
   Angular: Derive key = PBKDF2(passphrase, salt, 100000, SHA-256)
   Note: Salt is required for PBKDF2 to prevent rainbow table attacks and ensure unique keys
   Angular: Generate random IV (12 bytes)
   Angular: Encrypt file = AES-256-GCM(file, key, IV)
   Angular: Format = [salt][IV][ciphertext][GCM_tag]
   Note: Salt must be transmitted with the encrypted file so the server can derive the same key

3. File Upload
   Angular → Quarkus: POST /api/upload with encrypted file + passphrase
   Quarkus: Verify passphrase
   Quarkus: Extract salt from encrypted file (first 16 bytes)
   Quarkus: Derive key = PBKDF2(passphrase, salt, 100000, SHA-256)
   Note: Server uses the same salt from the client to derive the identical decryption key
   Quarkus: Decrypt client encryption (streaming, 8KB buffers)
   Quarkus: Generate cryptographically random DEK (32 bytes using SecureRandom)
   Quarkus: Generate cryptographically random IV (12 bytes using SecureRandom)
   Quarkus: Encrypt file = AES-256-GCM(decrypted_file, DEK, IV)
   Quarkus: Format encrypted file = [IV][ciphertext][GCM_tag]
   Note: Each file gets a unique, randomly generated DEK that is never reused

4. Envelope Encryption
   Quarkus: Load master_key from environment
   Quarkus: Generate cryptographically random IV (12 bytes using SecureRandom)
   Quarkus: Encrypt KEK = AES-256-GCM(DEK, master_key, IV)
   Quarkus: Format KEK = base64([IV][encrypted_DEK][GCM_tag])
   Note: The random DEK is encrypted with the master key for secure storage

5. S3 Storage
   Quarkus: Generate UUID for file
   Quarkus → S3: Upload encrypted file to uploads/{email}/{uuid}/{filename}.enc
   Quarkus → S3: Upload metadata to uploads/{email}/{uuid}/metadata.json
   Metadata = {
     version: "1.0",
     kek: base64_encoded_kek,
     algorithm: "AES-GCM-256",
     original_filename: "audio.mp3",
     original_size: bytes,
     encrypted_size: bytes,
     verification_status: "VERIFIED",
     timestamp: unix_ms
   }

6. NATS Event Publishing
   Quarkus: Generate event_id (UUID)
   Quarkus → NATS: Publish to file.uploads subject
   Event = {
     event_id: uuid,
     email: user_email,
     file_uuid: file_uuid,
     s3_data_key: "uploads/{email}/{uuid}/{filename}.enc",
     s3_metadata_key: "uploads/{email}/{uuid}/metadata.json",
     bucket_name: "whisper-uploads",
     timestamp: unix_ms
   }

7. Response
   Quarkus → Angular: Success response with file_uuid
```

#### Transcription Flow (FsNatsWhisper)

```
1. Message Consumption
   NATS → F# Service: Pull message from file.uploads
   F# Service: Parse JSON event payload
   F# Service: Extract s3_data_key, s3_metadata_key, bucket_name

2. S3 Download
   F# Service → S3: Download metadata.json using s3_metadata_key
   F# Service → S3: Download encrypted file using s3_data_key
   F# Service: Parse metadata JSON

3. Envelope Decryption
   F# Service: Extract KEK from metadata (base64 string)
   F# Service: Decode KEK from base64 to bytes
   F# Service: Parse KEK = [IV (12)][encrypted_DEK][GCM_tag (16)]
   F# Service: Load master_key from environment
   F# Service: Decrypt DEK = AES-256-GCM-Decrypt(encrypted_DEK, master_key, IV, GCM_tag)

4. File Decryption
   F# Service: Parse encrypted file = [IV (12)][ciphertext][GCM_tag (16)]
   F# Service: Decrypt file = AES-256-GCM-Decrypt(ciphertext, DEK, IV, GCM_tag)
   F# Service: Verify GCM tag for authenticity

5. Audio Processing
   F# Service: Save decrypted file to temp location
   F# Service → FFmpeg: Convert audio format (if needed)
   F# Service → FFmpeg: Normalize audio levels

6. Transcription
   F# Service: Load Whisper model
   F# Service → Whisper: Transcribe audio
   Whisper → F# Service: Return transcription text, language, timestamps

7. Result Storage
   F# Service: Save transcription to downloads/{email}/{filename}.txt
   F# Service: Save decrypted audio to downloads/{email}/{filename}.mp3 (debug)

8. Acknowledgment
   F# Service → NATS: ACK message (success)
   OR
   F# Service → NATS: NACK message (failure, allow redelivery)
```

## Components and Interfaces

### quarks-tigris Components

#### FileUploadResource (REST Controller)

**Responsibilities:**
- Handle HTTP requests for file operations
- Validate request parameters and file sizes
- Coordinate between services for upload workflow
- Return appropriate HTTP responses

**Interface:**
```java
@Path("/api")
public class FileUploadResource {
    
    @POST
    @Path("/validate-passphrase")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response validatePassphrase(PassphraseRequest request);
    
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFile(
        @MultipartForm FileUploadForm form
    );
    
    @GET
    @Path("/files")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listFiles(@QueryParam("email") String email);
    
    @DELETE
    @Path("/files")
    public Response deleteFile(
        @QueryParam("email") String email,
        @QueryParam("uuid") String uuid
    );
}
```

**Dependencies:**
- CryptoService (for encryption/decryption)
- S3StorageService (for file storage)
- NatsService (for event publishing)

#### CryptoService

**Responsibilities:**
- Perform AES-256-GCM encryption and decryption
- Generate cryptographically secure random keys and IVs
- Implement PBKDF2 key derivation
- Handle streaming encryption/decryption for large files
- Implement envelope encryption (DEK → KEK)

**Interface:**
```java
public class CryptoService {
    
    // Client encryption handling
    public byte[] deriveKeyFromPassphrase(String passphrase, byte[] salt, int iterations);
    public byte[] extractSalt(InputStream encryptedStream);  // Extract first 16 bytes
    public InputStream decryptClientStream(InputStream encryptedStream, String passphrase);
    
    // Server encryption
    public byte[] generateDEK();
    public byte[] generateIV();
    public byte[] generateSalt();  // 16 bytes for PBKDF2
    public EncryptionResult encryptStream(InputStream plaintext, byte[] dek);
    public InputStream decryptStream(InputStream ciphertext, byte[] dek, byte[] iv);
    
    // Envelope encryption
    public String encryptDEK(byte[] dek, byte[] masterKey);
    public byte[] decryptKEK(String kekBase64, byte[] masterKey);
    
    // Verification
    public boolean verifyGCMTag(byte[] ciphertext, byte[] tag, byte[] key, byte[] iv);
}

public class EncryptionResult {
    public byte[] iv;
    public InputStream ciphertext;
    public byte[] gcmTag;
}
```

**Key Generation:**
- Use `SecureRandom` with system entropy for DEK and IV generation
- DEK: 32 bytes (256 bits) of cryptographically secure random data, generated uniquely per file
- IV: 12 bytes (96 bits) of cryptographically secure random data per encryption operation
- Salt: 16 bytes (128 bits) of cryptographically secure random data for PBKDF2
- Each file upload generates a new random DEK that is never reused across files
- DEK randomness ensures that even identical files produce different ciphertexts
- Salt randomness ensures that the same passphrase produces different keys for different files

**Streaming Processing:**
- Use 8KB buffer size for streaming operations
- Process encryption/decryption in chunks to limit memory usage
- Support files up to 100MB without loading entire file into memory

#### S3StorageService

**Responsibilities:**
- Upload encrypted files and metadata to S3
- Download files and metadata from S3
- Organize files by user email and UUID
- Handle S3 connection and credential management

**Interface:**
```java
public class S3StorageService {
    
    public void uploadEncryptedFile(
        String email,
        String uuid,
        String filename,
        InputStream encryptedData,
        long size
    );
    
    public void uploadMetadata(
        String email,
        String uuid,
        EnvelopeMetadata metadata
    );
    
    public InputStream downloadFile(String s3Key);
    
    public EnvelopeMetadata downloadMetadata(String s3Key);
    
    public void deleteFile(String email, String uuid);
    
    public List<FileInfo> listFiles(String email);
    
    public String buildDataKey(String email, String uuid, String filename);
    
    public String buildMetadataKey(String email, String uuid);
}
```

**Path Structure:**
- Data: `uploads/{email}/{uuid}/{filename}.enc`
- Metadata: `uploads/{email}/{uuid}/metadata.json`

#### NatsService

**Responsibilities:**
- Establish connection to NATS JetStream
- Configure FILE_UPLOADS stream
- Publish file upload events
- Handle connection failures and retries

**Interface:**
```java
public class NatsService {
    
    public void initialize();
    
    public void configureStream(
        String streamName,
        String subject,
        Duration retention
    );
    
    public void publishFileUploadEvent(FileUploadEvent event);
    
    public void close();
}

public class FileUploadEvent {
    public String eventId;
    public String email;
    public String fileUuid;
    public String s3DataKey;
    public String s3MetadataKey;
    public String bucketName;
    public long timestamp;
}
```

**Stream Configuration:**
- Stream name: FILE_UPLOADS
- Subject: file.uploads
- Retention: 7 days
- Storage: File (persistent)
- Max message age: 7 days

#### Angular Frontend Components

**AuthService:**
```typescript
export class AuthService {
  validatePassphrase(passphrase: string): Observable<AuthResponse>;
  isAuthenticated(): boolean;
  getSessionToken(): string;
  logout(): void;
}
```

**CryptoService:**
```typescript
export class CryptoService {
  generateSalt(): Uint8Array;  // 16 bytes for PBKDF2
  async deriveKey(passphrase: string, salt: Uint8Array): Promise<CryptoKey>;
  async encryptFile(file: File, passphrase: string): Promise<EncryptedFile>;
  generateIV(): Uint8Array;
}

export interface EncryptedFile {
  data: ArrayBuffer;  // [salt][IV][ciphertext][GCM_tag]
  salt: Uint8Array;   // 16 bytes, needed for PBKDF2 key derivation
}
```

**UploadService:**
```typescript
export class UploadService {
  uploadFile(
    encryptedFile: EncryptedFile,
    originalFilename: string,
    passphrase: string
  ): Observable<UploadResponse>;
  
  listFiles(): Observable<FileInfo[]>;
  deleteFile(uuid: string): Observable<void>;
}
```

### FsNatsWhisper Components

#### NatsConsumer Module

**Responsibilities:**
- Subscribe to NATS JetStream file.uploads subject
- Create durable consumer
- Pull messages and parse JSON payloads
- Handle acknowledgments and redelivery

**Interface:**
```fsharp
module NatsConsumer =
    
    type FileUploadEvent = {
        EventId: string
        Email: string
        FileUuid: string
        S3DataKey: string
        S3MetadataKey: string
        BucketName: string
        Timestamp: int64
    }
    
    val connect : natsUrl:string -> Connection
    val createDurableConsumer : connection:Connection -> streamName:string -> consumerName:string -> Consumer
    val pullMessage : consumer:Consumer -> Async<Message option>
    val parseEvent : message:Message -> Result<FileUploadEvent, string>
    val ackMessage : message:Message -> unit
    val nackMessage : message:Message -> unit
```

#### S3Download Module

**Responsibilities:**
- Download encrypted files from S3
- Download metadata JSON from S3
- Parse metadata structure
- Handle S3 connection errors

**Interface:**
```fsharp
module S3Download =
    
    type EnvelopeMetadata = {
        Version: string
        Kek: string  // base64-encoded
        Algorithm: string
        OriginalFilename: string
        OriginalSize: int64
        EncryptedSize: int64
        VerificationStatus: string
        Timestamp: int64
    }
    
    val downloadFile : bucketName:string -> s3Key:string -> Async<byte[]>
    val downloadMetadata : bucketName:string -> s3Key:string -> Async<EnvelopeMetadata>
    val parseMetadata : json:string -> Result<EnvelopeMetadata, string>
```

#### Crypto Module

**Responsibilities:**
- Decrypt KEK using master key
- Decrypt file using DEK
- Verify GCM authentication tags
- Handle decryption errors

**Interface:**
```fsharp
module Crypto =
    
    type DecryptionResult = {
        Plaintext: byte[]
        Verified: bool
    }
    
    val decryptKEK : kekBase64:string -> masterKey:byte[] -> Result<byte[], string>
    val decryptFile : encryptedData:byte[] -> dek:byte[] -> Result<DecryptionResult, string>
    val parseEncryptedFormat : data:byte[] -> (byte[] * byte[] * byte[])  // (IV, ciphertext, tag)
    val verifyGCMTag : ciphertext:byte[] -> tag:byte[] -> key:byte[] -> iv:byte[] -> bool
```

#### AudioProcessing Module

**Responsibilities:**
- Invoke FFmpeg for format conversion
- Normalize audio levels
- Handle FFmpeg errors
- Manage temporary files

**Interface:**
```fsharp
module AudioProcessing =
    
    type ProcessingOptions = {
        OutputFormat: string
        Normalize: bool
        SampleRate: int option
    }
    
    val convertAudio : inputPath:string -> outputPath:string -> options:ProcessingOptions -> Async<Result<unit, string>>
    val normalizeAudio : audioPath:string -> Async<Result<unit, string>>
```

#### WhisperTranscription Module

**Responsibilities:**
- Load Whisper model
- Perform speech-to-text transcription
- Detect language
- Generate timestamps
- Handle transcription errors

**Interface:**
```fsharp
module WhisperTranscription =
    
    type TranscriptionResult = {
        Text: string
        Language: string
        DurationSeconds: float
        WordCount: int
        Segments: TranscriptionSegment list
    }
    
    and TranscriptionSegment = {
        StartTime: float
        EndTime: float
        Text: string
    }
    
    val loadModel : modelPath:string -> Model
    val transcribe : model:Model -> audioPath:string -> Async<Result<TranscriptionResult, string>>
    val detectLanguage : model:Model -> audioPath:string -> Async<string>
```

#### ResultPublisher Module

**Responsibilities:**
- Save transcription to local file
- Save decrypted audio for debugging
- (Future) Publish results to NATS

**Interface:**
```fsharp
module ResultPublisher =
    
    val saveTranscription : email:string -> filename:string -> transcription:string -> Async<unit>
    val saveAudio : email:string -> filename:string -> audioData:byte[] -> Async<unit>
    val publishToNats : connection:Connection -> result:TranscriptionResult -> fileUuid:string -> Async<unit>
```

## Data Models

### EnvelopeMetadata (JSON)

```json
{
  "version": "1.0",
  "kek": "base64(IV + encrypted_DEK + GCM_tag)",
  "algorithm": "AES-GCM-256",
  "original_filename": "audio.mp3",
  "original_size": 1048576,
  "encrypted_size": 1048604,
  "verification_status": "VERIFIED",
  "timestamp": 1706140800000
}
```

**Field Descriptions:**
- `version`: Metadata format version for future compatibility
- `kek`: Base64-encoded Key Encryption Key (encrypted DEK)
- `algorithm`: Encryption algorithm identifier
- `original_filename`: Original name of the uploaded file
- `original_size`: Size of the original file in bytes
- `encrypted_size`: Size of the encrypted file in bytes (includes IV and GCM tag)
- `verification_status`: Status of encryption verification ("VERIFIED", "FAILED")
- `timestamp`: Unix timestamp in milliseconds when file was uploaded

### FileUploadEvent (NATS Message)

```json
{
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "file_uuid": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "s3_data_key": "uploads/user@example.com/7c9e6679-7425-40de-944b-e07fc1f90ae7/audio.mp3.enc",
  "s3_metadata_key": "uploads/user@example.com/7c9e6679-7425-40de-944b-e07fc1f90ae7/metadata.json",
  "bucket_name": "whisper-uploads",
  "timestamp": 1769394113840
}
```

**Field Descriptions:**
- `event_id`: Unique identifier for this upload event (UUID v4)
- `email`: User's email address (used for file organization)
- `file_uuid`: Unique identifier for the uploaded file (UUID v4)
- `s3_data_key`: S3 object key for the encrypted file
- `s3_metadata_key`: S3 object key for the metadata JSON
- `bucket_name`: S3 bucket name where files are stored
- `timestamp`: Unix timestamp in milliseconds when event was published

### Encrypted File Format (Binary)

```
[12 bytes IV][variable length ciphertext][16 bytes GCM tag]
```

**Structure:**
- **IV (Initialization Vector)**: 12 bytes (96 bits) of random data
- **Ciphertext**: AES-256-GCM encrypted file content
- **GCM Tag**: 16 bytes (128 bits) authentication tag for integrity verification

### KEK Format (Binary, then Base64-encoded)

```
[12 bytes IV][32 bytes encrypted DEK][16 bytes GCM tag]
```

**Structure:**
- **IV**: 12 bytes of random data for KEK encryption
- **Encrypted DEK**: 32 bytes (256 bits) of encrypted Data Encryption Key
- **GCM Tag**: 16 bytes authentication tag
- **Encoding**: Entire structure is base64-encoded for JSON storage

### TranscriptionResult (Future NATS Message)

```json
{
  "event_id": "550e8400-e29b-41d4-a716-446655440001",
  "file_uuid": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "transcription": "This is the full text transcription of the audio file...",
  "language": "en",
  "duration_seconds": 120.5,
  "word_count": 250,
  "segments": [
    {
      "start_time": 0.0,
      "end_time": 5.2,
      "text": "This is the first segment."
    }
  ],
  "timestamp": 1769394200000,
  "status": "SUCCESS"
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*


### Property Reflection

After analyzing all acceptance criteria, I've identified the following consolidations to eliminate redundancy:

**Consolidated Properties:**
1. **IV Uniqueness** (2.3, 3.6, 4.2) → Single property covering all IV generation
2. **DEK Uniqueness** (3.3, 3.5, 18.2) → Single property covering DEK uniqueness
3. **Round-Trip Encryption** (3.1, 3.4, 10.4, 10.6, 17.1, 17.2, 17.3) → Three distinct round-trip properties (client, file, envelope)
4. **Binary Format** (2.4, 3.7, 4.3) → Three separate properties (client format, file format, KEK format) - kept separate as they validate different structures
5. **Metadata Structure** (6.1, 6.2, 6.3) → Single comprehensive property
6. **Event Structure** (7.4, 7.5) → Single comprehensive property
7. **Path Format** (5.1, 5.2) → Two separate properties - kept separate as they validate different path types
8. **PBKDF2 Key Derivation** (1.4, 2.2) → Single property (duplicate removed)
9. **GCM Tag Verification** (17.4, 17.5) → Single property covering verification and rejection

**Properties Marked as Examples/Edge Cases:**
- Configuration loading (15.x) → Integration examples
- Error handling (16.x) → Integration examples
- API endpoints (14.x) → Integration examples
- FFmpeg/Whisper integration (11.x, 12.x) → Integration examples
- File size limits (5.5, 14.5) → Edge cases

### Core Correctness Properties

#### Property 1: File Encryption Round-Trip

*For any* valid audio file and randomly generated DEK, encrypting the file with the DEK using AES-256-GCM and then decrypting with the same DEK should produce the original file content exactly.

**Validates: Requirements 3.1, 3.4, 10.6, 17.1**

#### Property 2: Envelope Encryption Round-Trip

*For any* valid DEK and master key, encrypting the DEK with the master key using AES-256-GCM (creating a KEK) and then decrypting the KEK with the same master key should produce the original DEK exactly.

**Validates: Requirements 4.1, 10.4, 17.2**

#### Property 3: Complete Encryption Pipeline Round-Trip

*For any* valid audio file, master key, and user passphrase, the complete encryption pipeline (client encryption → server decryption → DEK encryption → envelope encryption) followed by the complete decryption pipeline (envelope decryption → DEK decryption → file decryption) should produce the original file content exactly.

**Validates: Requirements 17.3**

#### Property 4: DEK Uniqueness

*For any* sequence of file uploads, all generated DEKs should be unique with negligible collision probability (less than 2^-128), verified by generating a large number of DEKs and ensuring no duplicates exist.

**Validates: Requirements 3.3, 3.5, 18.1, 18.2**

#### Property 5: IV Uniqueness

*For any* sequence of encryption operations (client, file, or KEK), all generated initialization vectors (IVs) should be unique, verified by generating a large number of IVs and ensuring no duplicates exist.

**Validates: Requirements 2.3, 3.6, 4.2, 18.4**

#### Property 6: Client Encrypted File Format

*For any* file encrypted by the client, the encrypted output should follow the format [16 bytes salt][12 bytes IV][ciphertext][16 bytes GCM tag], verified by parsing the output and confirming the salt is 16 bytes, the IV is 12 bytes, the GCM tag is 16 bytes, and the ciphertext length matches expected size.

**Validates: Requirements 2.4**

**Note:** The salt is required for PBKDF2 key derivation and must be transmitted with the encrypted file so the server can derive the same decryption key from the passphrase.

#### Property 7: Server Encrypted File Format

*For any* file encrypted by the server with a DEK, the encrypted output should follow the format [12 bytes IV][ciphertext][16 bytes GCM tag], verified by parsing the output and confirming the IV is 12 bytes, the GCM tag is 16 bytes, and the ciphertext length matches expected size.

**Validates: Requirements 3.7, 10.5**

#### Property 8: KEK Format

*For any* KEK generated by envelope encryption, the KEK should follow the format [12 bytes IV][32 bytes encrypted DEK][16 bytes GCM tag] before base64 encoding, verified by decoding from base64 and parsing the binary structure.

**Validates: Requirements 4.3, 10.3**

#### Property 9: KEK Base64 Encoding

*For any* KEK stored in metadata, the KEK should be valid base64-encoded data that can be decoded to exactly 60 bytes (12 + 32 + 16), verified by decoding and checking the length.

**Validates: Requirements 4.4, 10.2**

#### Property 10: Metadata Never Contains Plaintext DEK

*For any* metadata JSON stored in S3, the metadata should never contain the plaintext DEK, verified by parsing the metadata and confirming only the base64-encoded KEK is present, not the raw DEK.

**Validates: Requirements 4.5**

#### Property 11: S3 Data Path Format

*For any* encrypted file stored in S3, the S3 key should follow the format "uploads/{email}/{uuid}/{filename}.enc", verified by parsing the key and confirming it matches the pattern with valid email, UUID v4, and .enc extension.

**Validates: Requirements 5.1**

#### Property 12: S3 Metadata Path Format

*For any* metadata file stored in S3, the S3 key should follow the format "uploads/{email}/{uuid}/metadata.json", verified by parsing the key and confirming it matches the pattern with valid email, UUID v4, and metadata.json filename.

**Validates: Requirements 5.2**

#### Property 13: UUID v4 Generation

*For any* uploaded file, the generated file UUID should be a valid UUID v4, verified by parsing the UUID and confirming it matches the v4 format (version bits = 0100, variant bits = 10xx).

**Validates: Requirements 5.3**

#### Property 14: Metadata Contains Original Filename

*For any* file upload, the metadata stored in S3 should contain the original filename in the "original_filename" field, verified by comparing the metadata field with the uploaded filename.

**Validates: Requirements 5.4**

#### Property 15: Failed Upload Cleanup

*For any* file upload that fails after partial S3 upload, no orphaned files should remain in S3 storage, verified by checking that the S3 keys for both data and metadata do not exist after a failed upload.

**Validates: Requirements 5.6**

#### Property 16: Metadata Structure Completeness

*For any* metadata JSON created by the upload service, the metadata should contain all required fields: version="1.0", kek (base64 string), algorithm="AES-GCM-256", original_filename (string), original_size (positive integer), encrypted_size (positive integer), verification_status (string), and timestamp (positive integer), verified by parsing the JSON and checking all fields exist with correct types and values.

**Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6**

#### Property 17: NATS Event Structure Completeness

*For any* file upload event published to NATS, the event should contain all required fields: event_id (UUID v4), email (string), file_uuid (UUID v4), s3_data_key (string), s3_metadata_key (string), bucket_name (string), and timestamp (positive integer), verified by parsing the JSON and checking all fields exist with correct types.

**Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5**

#### Property 18: Event ID Uniqueness

*For any* sequence of file upload events, all generated event_ids should be unique UUID v4 values, verified by collecting event_ids from multiple uploads and ensuring no duplicates exist.

**Validates: Requirements 7.5**

#### Property 19: NATS Message Parsing

*For any* valid NATS message JSON payload conforming to the FileUploadEvent schema, the transcription service should successfully parse all fields without errors, verified by generating random valid payloads and confirming successful parsing.

**Validates: Requirements 8.4, 9.1, 9.4**

#### Property 20: Message Acknowledgment on Success

*For any* file that is successfully transcribed and saved, the transcription service should send an ACK to NATS for the corresponding message, verified by tracking ACK calls after successful processing.

**Validates: Requirements 8.5, 13.5**

#### Property 21: Message Non-Acknowledgment on Failure

*For any* file processing that fails (download, decryption, transcription, or storage), the transcription service should not send an ACK to NATS, allowing message redelivery, verified by tracking that no ACK is sent when errors occur.

**Validates: Requirements 8.6**

#### Property 22: Idempotent Message Processing

*For any* NATS message processed multiple times (due to redelivery), the transcription service should produce the same result without duplicate side effects, verified by processing the same message twice and confirming identical output files and no duplicate transcriptions.

**Validates: Requirements 8.7**

#### Property 23: GCM Tag Verification

*For any* encrypted data with a GCM authentication tag, decryption should verify the tag before returning plaintext, and if the tag is invalid (data tampered), decryption should fail with an error, verified by tampering with ciphertext and confirming decryption rejection.

**Validates: Requirements 17.4, 17.5**

#### Property 24: PBKDF2 Key Derivation Determinism

*For any* passphrase and salt combination, deriving a key using PBKDF2 with 100,000 iterations and SHA-256 should produce the same key every time, verified by deriving keys multiple times with the same inputs and confirming identical outputs.

**Validates: Requirements 1.4, 2.2**

#### Property 25: Authentication Error Message Uniformity

*For any* authentication failure (invalid passphrase or invalid username), the error message should be generic and not reveal which credential was incorrect, verified by testing various failure scenarios and confirming all return the same generic error message.

**Validates: Requirements 1.3**

#### Property 26: Session Token Generation on Success

*For any* successful authentication, the upload service should return a non-empty session token, verified by authenticating with valid credentials and confirming a token is returned.

**Validates: Requirements 1.2**

#### Property 27: Transcription File Organization

*For any* completed transcription, the saved files (transcription text and decrypted audio) should be organized in the downloads directory under the user's email subdirectory with the original filename preserved, verified by checking file paths after transcription.

**Validates: Requirements 13.1, 13.2, 13.3, 13.4**

#### Property 28: Unauthenticated Request Rejection

*For any* API endpoint except /api/validate-passphrase, requests without valid authentication should be rejected with an error, verified by sending unauthenticated requests to protected endpoints and confirming rejection.

**Validates: Requirements 14.6**

## Error Handling

### Upload Service Error Handling

**Client Encryption Errors:**
- Invalid passphrase: Return 401 Unauthorized with generic error message
- Encryption failure: Return 500 Internal Server Error, log error without exposing key material
- File too large: Return 413 Payload Too Large before processing

**Server Encryption Errors:**
- DEK generation failure: Return 500 Internal Server Error, log error, do not store partial data
- Encryption failure: Return 500 Internal Server Error, log error with context (email, filename)
- GCM tag generation failure: Return 500 Internal Server Error, abort upload

**S3 Storage Errors:**
- Upload failure: Return 500 Internal Server Error, clean up any partial uploads, log error with S3 key
- Connection timeout: Retry up to 3 times with exponential backoff, then return 503 Service Unavailable
- Insufficient permissions: Return 500 Internal Server Error, log error with bucket name

**NATS Publishing Errors:**
- Connection failure: Return 500 Internal Server Error, log error with NATS URL
- Publish timeout: Retry up to 3 times, then return 500 Internal Server Error
- Stream not configured: Log error and fail fast at startup

**General Error Handling:**
- All errors logged with correlation ID for tracing
- Sensitive data (keys, passphrases) never logged
- User-facing errors are generic to prevent information leakage
- Failed uploads trigger cleanup of S3 resources

### Transcription Service Error Handling

**NATS Consumption Errors:**
- Connection failure: Retry connection with exponential backoff (1s, 2s, 4s, 8s, max 60s)
- Message parsing failure: Log error with message ID, NACK message for redelivery
- Consumer not found: Recreate durable consumer, resume processing

**S3 Download Errors:**
- File not found: Log error with S3 key and file UUID, NACK message
- Download timeout: Retry up to 3 times with exponential backoff, then NACK message
- Insufficient permissions: Log error, NACK message, alert administrator

**Decryption Errors:**
- Invalid KEK format: Log error with file UUID, NACK message
- Master key mismatch: Log error, NACK message, alert administrator
- GCM tag verification failure: Log error indicating possible tampering, NACK message, alert security team
- Invalid encrypted file format: Log error with file UUID, NACK message

**Audio Processing Errors:**
- FFmpeg not found: Log error, fail fast at startup
- Unsupported format: Log error with file UUID and format, NACK message
- Conversion failure: Log error with FFmpeg output, NACK message
- Normalization failure: Log warning, continue with unnormalized audio

**Transcription Errors:**
- Whisper model not found: Log error, fail fast at startup
- Transcription timeout: Log error with file UUID, NACK message
- Out of memory: Log error, NACK message, alert administrator
- Language detection failure: Log warning, use default language (English)

**Storage Errors:**
- File write failure: Log error with file path, NACK message
- Insufficient disk space: Log error, NACK message, alert administrator
- Permission denied: Log error, NACK message, alert administrator

**General Error Handling:**
- All errors logged with file UUID for tracing
- Sensitive data (keys, DEKs) never logged
- Failed processing results in NACK to allow redelivery
- Service continues processing other messages after individual failures
- Health check endpoint reports service status

## Testing Strategy

### Dual Testing Approach

The system requires both unit testing and property-based testing for comprehensive coverage:

**Unit Tests:**
- Specific examples demonstrating correct behavior
- Edge cases (empty files, maximum size files, special characters in filenames)
- Error conditions (invalid credentials, network failures, corrupted data)
- Integration points between components (REST API, S3 client, NATS client)
- Configuration loading and validation

**Property-Based Tests:**
- Universal properties that hold for all inputs
- Comprehensive input coverage through randomization
- Encryption/decryption correctness across all possible files
- Data format validation across all possible inputs
- Minimum 100 iterations per property test

### Property-Based Testing Configuration

**Library Selection:**
- **Java/Quarkus**: Use jqwik for property-based testing
- **F#**: Use FsCheck for property-based testing
- **TypeScript/Angular**: Use fast-check for property-based testing

**Test Configuration:**
- Each property test runs minimum 100 iterations
- Each test tagged with: **Feature: voice-to-text-pipeline, Property {number}: {property_text}**
- Each correctness property implemented by a SINGLE property-based test
- Tests organized by component (CryptoService, S3StorageService, NatsService, etc.)

**Example Test Structure (Java/jqwik):**
```java
@Property
@Label("Feature: voice-to-text-pipeline, Property 1: File Encryption Round-Trip")
void fileEncryptionRoundTrip(@ForAll byte[] fileContent, @ForAll byte[] dek) {
    // Arrange
    byte[] iv = cryptoService.generateIV();
    
    // Act
    byte[] encrypted = cryptoService.encrypt(fileContent, dek, iv);
    byte[] decrypted = cryptoService.decrypt(encrypted, dek);
    
    // Assert
    assertArrayEquals(fileContent, decrypted);
}
```

**Example Test Structure (F#/FsCheck):**
```fsharp
[<Property(Arbitrary = [| typeof<Generators> |])>]
let ``Feature: voice-to-text-pipeline, Property 2: Envelope Encryption Round-Trip``
    (dek: byte[]) (masterKey: byte[]) =
    // Arrange
    let iv = Crypto.generateIV()
    
    // Act
    let kek = Crypto.encryptDEK dek masterKey iv
    let decryptedDEK = Crypto.decryptKEK kek masterKey
    
    // Assert
    dek = decryptedDEK
```

### Test Coverage Requirements

**quarks-tigris (Java/Quarkus):**
- CryptoService: Properties 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 23, 24
- S3StorageService: Properties 11, 12, 13, 14, 15
- NatsService: Properties 17, 18
- FileUploadResource: Properties 25, 26, 28
- Integration tests: API endpoints, configuration loading, error handling

**FsNatsWhisper (F#):**
- Crypto module: Properties 2, 8, 9, 23 (decryption side)
- NatsConsumer module: Properties 19, 20, 21, 22
- ResultPublisher module: Property 27
- Integration tests: NATS consumption, S3 download, FFmpeg processing, Whisper transcription

**Angular Frontend (TypeScript):**
- CryptoService: Properties 6, 24 (client-side)
- Unit tests: Authentication flow, file upload UI, error handling

### Testing Best Practices

**Balance Unit and Property Tests:**
- Don't write too many unit tests - property tests handle input coverage
- Focus unit tests on specific examples, edge cases, and integration points
- Use property tests for universal correctness guarantees

**Property Test Design:**
- Generate random valid inputs (files, keys, IVs, UUIDs)
- Test round-trip properties for all encryption/decryption operations
- Test format properties for all binary and JSON structures
- Test uniqueness properties for all generated identifiers
- Test error handling with invalid inputs (tampered data, wrong keys)

**Integration Test Design:**
- Test complete upload flow: client → server → S3 → NATS
- Test complete transcription flow: NATS → S3 → decrypt → transcribe → save
- Test error scenarios: network failures, invalid credentials, corrupted files
- Test configuration: environment variables, missing config, invalid config

**Performance Testing:**
- Test streaming with large files (up to 100MB)
- Test memory usage during encryption/decryption
- Test NATS throughput with multiple concurrent uploads
- Test transcription performance with various audio lengths

## Deployment Considerations

### Environment Variables

**quarks-tigris:**
```bash
# S3 Configuration
S3_ENDPOINT=https://fly.storage.tigris.dev
S3_ACCESS_KEY=tid_xxx
S3_SECRET_KEY=tsec_xxx
S3_BUCKET_NAME=whisper-uploads
S3_REGION=auto

# NATS Configuration
NATS_URL=nats://nats.fly.dev:4222
NATS_STREAM=FILE_UPLOADS
NATS_SUBJECT=file.uploads

# Encryption Configuration
MASTER_KEY=base64_encoded_256_bit_key

# Application Configuration
MAX_FILE_SIZE_MB=100
PASSPHRASE_HASH=bcrypt_hash_of_passphrase
```

**FsNatsWhisper:**
```bash
# S3 Configuration
S3_ENDPOINT=https://fly.storage.tigris.dev
S3_ACCESS_KEY=tid_xxx
S3_SECRET_KEY=tsec_xxx
S3_REGION=auto

# NATS Configuration
NATS_URL=nats://nats.fly.dev:4222
NATS_STREAM=FILE_UPLOADS
NATS_SUBJECT=file.uploads
NATS_CONSUMER=file_processor

# Encryption Configuration
MASTER_KEY=base64_encoded_256_bit_key

# Whisper Configuration
WHISPER_MODEL_PATH=/app/models/whisper-base
WHISPER_MODEL_SIZE=base

# Storage Configuration
DOWNLOADS_PATH=/app/downloads
```

### Security Considerations

**Key Management:**
- Master key must be identical between quarks-tigris and FsNatsWhisper
- Master key should be rotated periodically (requires re-encryption of all KEKs)
- Master key should never be committed to version control
- Use secrets management service (Fly.io secrets, AWS Secrets Manager, etc.)

**Network Security:**
- All HTTP traffic over HTTPS/TLS
- NATS connection over TLS in production
- S3 connection over HTTPS
- Restrict S3 bucket access to service IPs only

**Data Security:**
- Files encrypted at rest in S3
- Files encrypted in transit (HTTPS)
- Keys never logged or exposed in error messages
- Temporary files cleaned up after processing

### Scalability

**Horizontal Scaling:**
- Multiple quarks-tigris instances behind load balancer
- Multiple FsNatsWhisper consumers processing in parallel
- NATS JetStream handles message distribution
- S3 provides unlimited storage capacity

**Performance Optimization:**
- Streaming processing for large files
- Connection pooling for S3 and NATS
- Whisper model loaded once at startup
- FFmpeg processing optimized for speed

### Monitoring and Observability

**Metrics to Track:**
- Upload success/failure rate
- Transcription success/failure rate
- Average upload time
- Average transcription time
- NATS message queue depth
- S3 storage usage
- Error rates by type

**Logging:**
- Structured logging with correlation IDs
- Log levels: DEBUG, INFO, WARN, ERROR
- Sensitive data never logged
- Centralized log aggregation (Fly.io logs, CloudWatch, etc.)

**Health Checks:**
- quarks-tigris: /health endpoint checking S3 and NATS connectivity
- FsNatsWhisper: Health check endpoint checking NATS, S3, and Whisper model availability

## Future Enhancements

### Phase 1: Result Publishing to NATS

**Objective:** Publish transcription results back to NATS for frontend consumption

**Changes:**
- Add new NATS subject: audio.transcription.result
- Modify ResultPublisher to publish TranscriptionResult to NATS
- Add NATS consumer in Angular frontend to receive results
- Display transcriptions in web UI

**Benefits:**
- Real-time transcription updates
- Decoupled frontend from file system
- Enables multiple consumers of transcription results

### Phase 2: Multi-Language Support

**Objective:** Support transcription in multiple languages

**Changes:**
- Add language selection in Angular UI
- Pass language preference in NATS event
- Configure Whisper with language parameter
- Store detected language in transcription result

**Benefits:**
- Broader user base
- Improved transcription accuracy for non-English audio

### Phase 3: Real-Time Progress Updates

**Objective:** Show transcription progress to users

**Changes:**
- Add WebSocket connection from Angular to backend
- Publish progress events during transcription
- Display progress bar in UI
- Estimate time remaining

**Benefits:**
- Better user experience
- Transparency into processing status

### Phase 4: Batch Processing

**Objective:** Allow users to upload multiple files at once

**Changes:**
- Support multiple file selection in Angular
- Batch upload API endpoint
- Parallel transcription processing
- Batch result display

**Benefits:**
- Improved efficiency for power users
- Reduced upload overhead

### Phase 5: Transcription Storage and Querying

**Objective:** Store transcriptions in database for searching and retrieval

**Changes:**
- Add PostgreSQL database
- Store transcriptions with metadata
- Add search API endpoint
- Add search UI in Angular

**Benefits:**
- Persistent transcription history
- Full-text search capability
- Analytics on transcription data
