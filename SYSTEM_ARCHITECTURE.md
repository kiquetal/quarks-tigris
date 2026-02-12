# Voice-to-Text Pipeline System Architecture

## System Overview

This document describes the complete architecture of the secure voice-to-text pipeline system, consisting of two main components: **quarks-tigris** (upload service) and **FsNatsWhisper** (transcription service).

## High-Level Architecture Diagram

```ascii
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
                    │              │  │  • Publish to NATS (future)       │  │
                    │              │  │  • Save to local file (current)   │  │
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

## Data Flow Sequence

### Phase 1: File Upload (quarks-tigris)

```
1. User authenticates with passphrase
   └─> Angular validates and stores session token

2. User selects MP3 file
   └─> Angular encrypts file client-side (AES-256-GCM + PBKDF2)
   └─> Uploads encrypted file to Quarkus backend

3. Quarkus receives encrypted file
   └─> Verifies passphrase
   └─> Decrypts client encryption (streaming)
   └─> Generates random 256-bit DEK
   └─> Re-encrypts file with DEK (streaming)
   └─> Encrypts DEK with master key → KEK
   └─> Generates UUID for file

4. Quarkus stores in S3
   └─> uploads/{email}/{uuid}/{filename}.enc (encrypted file)
   └─> uploads/{email}/{uuid}/metadata.json (envelope metadata with KEK)

5. Quarkus publishes NATS event
   └─> Stream: FILE_UPLOADS
   └─> Subject: file.uploads
   └─> Payload: {event_id, email, file_uuid, s3_data_key, s3_metadata_key, bucket_name, timestamp}

6. Quarkus returns success response to Angular
```

### Phase 2: Transcription (FsNatsWhisper)

```
1. F# service receives NATS message
   └─> Parses event payload
   └─> Extracts S3 keys and bucket name

2. Download from S3
   └─> Download metadata.json
   └─> Download encrypted file (.enc)

3. Decrypt envelope
   └─> Parse metadata.json
   └─> Extract KEK (base64-encoded encrypted DEK)
   └─> Decrypt KEK with master key → DEK
   └─> Decrypt file with DEK → original MP3

4. Process audio
   └─> Convert format with FFmpeg (if needed)
   └─> Normalize audio

5. Transcribe with Whisper
   └─> Load Whisper model
   └─> Perform speech-to-text
   └─> Generate timestamps

6. Save results
   └─> Save decrypted audio to downloads/ (debugging)
   └─> Save transcription to .txt file
   └─> (Future) Publish result to NATS
```

## Security Architecture

### Encryption Layers

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: Client-Side Encryption (Browser)                  │
│ • Algorithm: AES-256-GCM                                    │
│ • Key Derivation: PBKDF2 (100k iterations)                 │
│ • Input: User passphrase                                    │
│ • Purpose: Protect data in transit                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 2: Server-Side Encryption (Quarkus)                  │
│ • Algorithm: AES-256-GCM                                    │
│ • Key: Random 256-bit DEK (unique per file)                │
│ • Purpose: Protect data at rest                            │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: Envelope Encryption (Quarkus)                     │
│ • Algorithm: AES-256-GCM                                    │
│ • Key: Master key (from environment)                        │
│ • Encrypts: DEK → KEK                                       │
│ • Purpose: Secure key storage                              │
└─────────────────────────────────────────────────────────────┘
```

### Key Management

- **Client Passphrase**: User-provided, never stored
- **DEK (Data Encryption Key)**: Random 256-bit, unique per file, never stored in plaintext
- **KEK (Key Encryption Key)**: Encrypted DEK, stored in metadata.json
- **Master Key**: Base64-encoded, stored in environment variables, shared between Quarkus and F# service

## Technology Stack

### quarks-tigris
- **Backend**: Quarkus 3.30.7, Java 21
- **Frontend**: Angular 19, TypeScript
- **Storage**: S3-compatible (Tigris, AWS S3, LocalStack)
- **Messaging**: NATS JetStream
- **Encryption**: AES-256-GCM, PBKDF2
- **Build**: Maven, npm

### FsNatsWhisper
- **Runtime**: .NET 8+, F#
- **Audio Processing**: FFmpeg
- **Transcription**: Whisper model
- **Storage Client**: AWS SDK for .NET
- **Messaging**: NATS.Client
- **Build**: dotnet CLI

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Production Deployment                    │
└─────────────────────────────────────────────────────────────┘

┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  Fly.io / Cloud  │     │  Tigris Storage  │     │  NATS Cluster    │
│                  │     │                  │     │                  │
│  • quarks-tigris │────▶│  • Encrypted     │     │  • JetStream     │
│    (Quarkus)     │     │    files         │     │  • Persistence   │
│  • Angular SPA   │     │  • Metadata      │     │  • 7-day retain  │
└──────────────────┘     └──────────────────┘     └──────────────────┘
                                                            │
                                                            │
                                                            ▼
                         ┌──────────────────────────────────────────┐
                         │  Fly.io / Cloud                          │
                         │                                          │
                         │  • FsNatsWhisper (F# Consumer)          │
                         │  • Whisper model                         │
                         │  • FFmpeg                                │
                         │  • Multi-arch (amd64/arm64)             │
                         └──────────────────────────────────────────┘
```

## Message Formats

### NATS Upload Event (file.uploads)

```json
{
  "event_id": "uuid",
  "email": "user@example.com",
  "file_uuid": "uuid",
  "s3_data_key": "uploads/{email}/{uuid}/{filename}.enc",
  "s3_metadata_key": "uploads/{email}/{uuid}/metadata.json",
  "bucket_name": "whisper-uploads",
  "timestamp": 1769394113840
}
```

### S3 Envelope Metadata (metadata.json)

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

### Transcription Result (future - audio.transcription.result)

```json
{
  "event_id": "uuid",
  "file_uuid": "uuid",
  "transcription": "Full text transcription...",
  "language": "en",
  "duration_seconds": 120.5,
  "word_count": 250,
  "timestamp": 1769394200000,
  "status": "SUCCESS"
}
```

## Performance Characteristics

- **Upload**: Streaming processing, 8KB buffers, supports files up to 100MB
- **Storage**: S3-compatible, scalable object storage
- **Messaging**: NATS JetStream with persistence, 7-day retention
- **Transcription**: Depends on Whisper model size and audio duration
- **Concurrency**: Multiple F# consumers can process files in parallel

## Future Enhancements

1. **Result Publishing**: Publish transcription results back to NATS
2. **Web UI for Results**: Display transcriptions in Angular frontend
3. **Multiple Languages**: Support for multi-language transcription
4. **Real-time Processing**: WebSocket updates for transcription progress
5. **Batch Processing**: Process multiple files in parallel
6. **Result Storage**: Store transcriptions in database or S3
7. **API for Results**: REST API to query transcription results
