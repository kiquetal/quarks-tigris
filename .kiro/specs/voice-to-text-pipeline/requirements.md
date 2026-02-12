# Requirements Document: Voice-to-Text Pipeline

## Introduction

The Voice-to-Text Pipeline is a secure, distributed system for uploading, encrypting, storing, and transcribing audio files. The system consists of two main components: a Quarkus/Angular web application (quarks-tigris) for secure file upload with triple-layer encryption, and an F# transcription service (FsNatsWhisper) that processes encrypted audio files using Whisper speech-to-text technology. The components communicate asynchronously via NATS JetStream messaging, with encrypted files stored in S3-compatible object storage.

## Glossary

- **Upload_Service**: The Quarkus backend and Angular frontend (quarks-tigris) responsible for file upload, encryption, and storage
- **Transcription_Service**: The F# consumer service (FsNatsWhisper) that processes and transcribes audio files
- **Client_Encryption**: AES-256-GCM encryption performed in the browser before upload
- **Envelope_Encryption**: Two-layer encryption where a Data Encryption Key (DEK) encrypts the file, and a master key encrypts the DEK to produce a Key Encryption Key (KEK)
- **DEK**: Data Encryption Key - a random 256-bit key generated per file to encrypt the file content
- **KEK**: Key Encryption Key - the encrypted form of the DEK, stored in metadata
- **Master_Key**: A shared secret key used to encrypt/decrypt DEKs, stored in environment variables
- **NATS_JetStream**: A persistent messaging system for asynchronous communication between services
- **S3_Storage**: S3-compatible object storage (Tigris, AWS S3, or LocalStack) for encrypted files and metadata
- **Whisper**: OpenAI's speech-to-text transcription model
- **Passphrase**: User-provided secret used for client-side encryption key derivation

## Requirements

### Requirement 1: User Authentication

**User Story:** As a user, I want to authenticate with a passphrase, so that I can securely upload audio files.

#### Acceptance Criteria

1. WHEN a user submits a passphrase, THE Upload_Service SHALL validate it against stored credentials
2. WHEN authentication succeeds, THE Upload_Service SHALL return a session token
3. WHEN authentication fails, THE Upload_Service SHALL return an error without revealing whether the passphrase or username was incorrect
4. THE Upload_Service SHALL use the passphrase for client-side encryption key derivation via PBKDF2 with 100,000 iterations

### Requirement 2: Client-Side File Encryption

**User Story:** As a user, I want my audio files encrypted in the browser before upload, so that my data is protected in transit.

#### Acceptance Criteria

1. WHEN a user selects an audio file, THE Upload_Service SHALL encrypt it using AES-256-GCM before transmission
2. THE Upload_Service SHALL derive the encryption key from the user's passphrase using PBKDF2 with 100,000 iterations
3. WHEN encrypting, THE Upload_Service SHALL generate a random 12-byte initialization vector (IV) per file
4. THE Upload_Service SHALL prepend the IV to the encrypted data and append the 16-byte GCM authentication tag
5. THE Upload_Service SHALL never transmit the user's passphrase or derived key to the server

### Requirement 3: Server-Side File Re-encryption

**User Story:** As a system administrator, I want files re-encrypted on the server with unique keys, so that data at rest is protected independently of client credentials.

#### Acceptance Criteria

1. WHEN the Upload_Service receives an encrypted file, THE Upload_Service SHALL decrypt the client encryption using the provided passphrase
2. WHEN decrypting client encryption, THE Upload_Service SHALL use streaming processing with 8KB buffers
3. WHEN client decryption succeeds, THE Upload_Service SHALL generate a random 256-bit DEK unique to that file
4. THE Upload_Service SHALL encrypt the decrypted file content using the DEK with AES-256-GCM
5. THE Upload_Service SHALL never reuse a DEK across multiple files
6. WHEN encrypting with the DEK, THE Upload_Service SHALL generate a random 12-byte IV
7. THE Upload_Service SHALL format the encrypted file as: [12 bytes IV][encrypted data][16 bytes GCM tag]

### Requirement 4: Envelope Encryption for Key Protection

**User Story:** As a security engineer, I want data encryption keys protected by a master key, so that key material is never stored in plaintext.

#### Acceptance Criteria

1. WHEN a DEK is generated, THE Upload_Service SHALL encrypt it using the Master_Key with AES-256-GCM
2. THE Upload_Service SHALL generate a random 12-byte IV for KEK encryption
3. THE Upload_Service SHALL format the KEK as: [12 bytes IV][encrypted DEK][16 bytes GCM tag]
4. THE Upload_Service SHALL encode the KEK in base64 format for storage
5. THE Upload_Service SHALL store the KEK in metadata, never the plaintext DEK
6. THE Upload_Service SHALL load the Master_Key from environment variables at startup
7. THE Transcription_Service SHALL use the same Master_Key to decrypt KEKs

### Requirement 5: S3 Storage Organization

**User Story:** As a system administrator, I want files organized by user and UUID in S3, so that storage is structured and scalable.

#### Acceptance Criteria

1. WHEN storing an encrypted file, THE Upload_Service SHALL use the path format: uploads/{email}/{uuid}/{filename}.enc
2. WHEN storing metadata, THE Upload_Service SHALL use the path format: uploads/{email}/{uuid}/metadata.json
3. THE Upload_Service SHALL generate a UUID v4 for each uploaded file
4. THE Upload_Service SHALL store the original filename in the metadata
5. THE Upload_Service SHALL support file uploads up to 100MB in size
6. WHEN upload fails, THE Upload_Service SHALL clean up any partially uploaded files from S3_Storage

### Requirement 6: Envelope Metadata Format

**User Story:** As a developer, I want standardized metadata for encrypted files, so that decryption is reliable and auditable.

#### Acceptance Criteria

1. THE Upload_Service SHALL create metadata containing: version, kek, algorithm, original_filename, original_size, encrypted_size, verification_status, timestamp
2. THE Upload_Service SHALL set the version field to "1.0"
3. THE Upload_Service SHALL set the algorithm field to "AES-GCM-256"
4. THE Upload_Service SHALL set verification_status to "VERIFIED" after successful encryption
5. THE Upload_Service SHALL store the metadata as valid JSON
6. THE Upload_Service SHALL include Unix timestamp in milliseconds for the upload time

### Requirement 7: NATS Event Publishing

**User Story:** As a system architect, I want upload events published to NATS, so that transcription can be processed asynchronously.

#### Acceptance Criteria

1. WHEN a file upload completes successfully, THE Upload_Service SHALL publish an event to NATS_JetStream
2. THE Upload_Service SHALL publish to the subject "file.uploads"
3. THE Upload_Service SHALL publish to the stream "FILE_UPLOADS"
4. THE Upload_Service SHALL include in the event: event_id, email, file_uuid, s3_data_key, s3_metadata_key, bucket_name, timestamp
5. THE Upload_Service SHALL generate a unique event_id (UUID v4) for each event
6. WHEN NATS publishing fails, THE Upload_Service SHALL log the error and return a failure response to the client
7. THE Upload_Service SHALL configure the FILE_UPLOADS stream with 7-day message retention

### Requirement 8: NATS Message Consumption

**User Story:** As a transcription service, I want to consume file upload events reliably, so that no files are missed for processing.

#### Acceptance Criteria

1. THE Transcription_Service SHALL subscribe to the "file.uploads" subject on NATS_JetStream
2. THE Transcription_Service SHALL use a durable consumer named "file_processor"
3. THE Transcription_Service SHALL use pull-based consumption for message retrieval
4. WHEN a message is received, THE Transcription_Service SHALL parse the JSON payload
5. THE Transcription_Service SHALL explicitly acknowledge messages after successful processing
6. WHEN processing fails, THE Transcription_Service SHALL not acknowledge the message to allow redelivery
7. THE Transcription_Service SHALL handle message redelivery without duplicate processing

### Requirement 9: S3 File Download

**User Story:** As a transcription service, I want to download encrypted files and metadata from S3, so that I can decrypt and process them.

#### Acceptance Criteria

1. WHEN a NATS message is received, THE Transcription_Service SHALL extract the s3_data_key and s3_metadata_key
2. THE Transcription_Service SHALL download the metadata.json file from S3_Storage using the s3_metadata_key
3. THE Transcription_Service SHALL download the encrypted file from S3_Storage using the s3_data_key
4. THE Transcription_Service SHALL parse the metadata JSON to extract the KEK
5. WHEN S3 download fails, THE Transcription_Service SHALL log the error and not acknowledge the NATS message
6. THE Transcription_Service SHALL load S3 credentials from environment variables

### Requirement 10: Envelope Decryption

**User Story:** As a transcription service, I want to decrypt files using envelope encryption, so that I can access the original audio content.

#### Acceptance Criteria

1. WHEN metadata is downloaded, THE Transcription_Service SHALL extract the base64-encoded KEK
2. THE Transcription_Service SHALL decode the KEK from base64 to bytes
3. THE Transcription_Service SHALL parse the KEK format: [12 bytes IV][encrypted DEK][16 bytes GCM tag]
4. THE Transcription_Service SHALL decrypt the KEK using the Master_Key with AES-256-GCM to obtain the DEK
5. THE Transcription_Service SHALL parse the encrypted file format: [12 bytes IV][encrypted data][16 bytes GCM tag]
6. THE Transcription_Service SHALL decrypt the file using the DEK with AES-256-GCM to obtain the original audio
7. WHEN decryption fails, THE Transcription_Service SHALL log the error with the file UUID and not acknowledge the NATS message

### Requirement 11: Audio Format Processing

**User Story:** As a transcription service, I want to process audio files into the correct format, so that Whisper can transcribe them accurately.

#### Acceptance Criteria

1. WHEN audio is decrypted, THE Transcription_Service SHALL use FFmpeg to convert the audio format if needed
2. THE Transcription_Service SHALL normalize audio levels for optimal transcription quality
3. THE Transcription_Service SHALL support MP3 input format
4. WHEN FFmpeg processing fails, THE Transcription_Service SHALL log the error and not acknowledge the NATS message

### Requirement 12: Speech-to-Text Transcription

**User Story:** As a user, I want my audio files transcribed to text, so that I can read the spoken content.

#### Acceptance Criteria

1. WHEN audio processing completes, THE Transcription_Service SHALL load the Whisper model
2. THE Transcription_Service SHALL perform speech-to-text transcription on the processed audio
3. THE Transcription_Service SHALL detect the language of the audio
4. THE Transcription_Service SHALL generate timestamps for transcription segments
5. THE Transcription_Service SHALL produce a complete text transcription of the audio content
6. WHEN transcription fails, THE Transcription_Service SHALL log the error with the file UUID

### Requirement 13: Transcription Result Storage

**User Story:** As a user, I want transcription results saved, so that I can access them later.

#### Acceptance Criteria

1. WHEN transcription completes, THE Transcription_Service SHALL save the transcription text to a local file
2. THE Transcription_Service SHALL save the decrypted audio file to the downloads directory for debugging
3. THE Transcription_Service SHALL organize saved files by user email
4. THE Transcription_Service SHALL use the original filename in the saved transcription filename
5. THE Transcription_Service SHALL acknowledge the NATS message after successful transcription and storage

### Requirement 14: REST API Endpoints

**User Story:** As a frontend developer, I want REST API endpoints for file operations, so that users can manage their uploads.

#### Acceptance Criteria

1. THE Upload_Service SHALL provide a POST endpoint at /api/validate-passphrase for authentication
2. THE Upload_Service SHALL provide a POST endpoint at /api/upload for file uploads with a 100MB size limit
3. THE Upload_Service SHALL provide a GET endpoint at /api/files for listing user's uploaded files
4. THE Upload_Service SHALL provide a DELETE endpoint at /api/files for removing uploaded files
5. WHEN a request exceeds the 100MB limit, THE Upload_Service SHALL return an error response
6. THE Upload_Service SHALL require authentication for all endpoints except /api/validate-passphrase

### Requirement 15: Configuration Management

**User Story:** As a system administrator, I want configuration via environment variables, so that the system can be deployed in different environments.

#### Acceptance Criteria

1. THE Upload_Service SHALL load S3 credentials (access key, secret key, endpoint) from environment variables
2. THE Upload_Service SHALL load the Master_Key from an environment variable as a base64-encoded string
3. THE Upload_Service SHALL load NATS server URL from an environment variable
4. THE Upload_Service SHALL load the S3 bucket name from an environment variable
5. THE Transcription_Service SHALL load the same Master_Key from an environment variable
6. THE Transcription_Service SHALL load S3 credentials from environment variables
7. THE Transcription_Service SHALL load NATS server URL from an environment variable
8. WHEN required environment variables are missing, THE Upload_Service SHALL fail to start with a clear error message
9. WHEN required environment variables are missing, THE Transcription_Service SHALL fail to start with a clear error message

### Requirement 16: Error Handling and Logging

**User Story:** As a system administrator, I want comprehensive error logging, so that I can troubleshoot issues effectively.

#### Acceptance Criteria

1. WHEN an error occurs during file upload, THE Upload_Service SHALL log the error with context (user email, filename, error type)
2. WHEN an error occurs during encryption, THE Upload_Service SHALL log the error without exposing key material
3. WHEN an error occurs during NATS publishing, THE Upload_Service SHALL log the error with the event details
4. WHEN an error occurs during transcription, THE Transcription_Service SHALL log the error with the file UUID and error type
5. WHEN an error occurs during decryption, THE Transcription_Service SHALL log the error without exposing key material
6. THE Upload_Service SHALL return user-friendly error messages to the client without exposing internal details
7. THE Transcription_Service SHALL continue processing other messages when one message fails

### Requirement 17: Encryption Round-Trip Correctness

**User Story:** As a security engineer, I want to verify that encryption and decryption are inverse operations, so that no data is lost or corrupted.

#### Acceptance Criteria

1. FOR ALL valid audio files, encrypting with a DEK then decrypting with the same DEK SHALL produce the original file content
2. FOR ALL valid DEKs, encrypting with the Master_Key then decrypting with the same Master_Key SHALL produce the original DEK
3. FOR ALL valid files, the complete envelope encryption and decryption process SHALL preserve the original file content exactly
4. WHEN encryption produces a GCM tag, decryption SHALL verify the tag before returning data
5. WHEN the GCM tag verification fails, THE system SHALL reject the decryption and return an error

### Requirement 18: Key Generation Uniqueness

**User Story:** As a security engineer, I want each file encrypted with a unique key, so that compromising one key doesn't compromise other files.

#### Acceptance Criteria

1. FOR ALL file uploads, the generated DEK SHALL be cryptographically random
2. FOR ALL file uploads, the probability of DEK collision SHALL be negligible (less than 2^-128)
3. THE Upload_Service SHALL use a cryptographically secure random number generator for DEK generation
4. FOR ALL file uploads, the IV SHALL be unique per encryption operation

### Requirement 19: Streaming Processing

**User Story:** As a system administrator, I want large files processed in streams, so that memory usage remains bounded.

#### Acceptance Criteria

1. WHEN processing files larger than 8KB, THE Upload_Service SHALL use streaming encryption with 8KB buffers
2. WHEN processing files larger than 8KB, THE Upload_Service SHALL use streaming decryption with 8KB buffers
3. THE Upload_Service SHALL not load entire files into memory during encryption or decryption
4. WHEN uploading to S3, THE Upload_Service SHALL use streaming upload for files larger than 8KB

### Requirement 20: NATS Stream Configuration

**User Story:** As a system administrator, I want NATS streams configured for reliability, so that messages are not lost.

#### Acceptance Criteria

1. THE Upload_Service SHALL configure the FILE_UPLOADS stream with persistence enabled
2. THE Upload_Service SHALL configure the FILE_UPLOADS stream with 7-day message retention
3. THE Upload_Service SHALL configure the FILE_UPLOADS stream to store messages on disk
4. THE Transcription_Service SHALL create a durable consumer that survives service restarts
5. WHEN the Transcription_Service restarts, THE Transcription_Service SHALL resume processing from the last acknowledged message
