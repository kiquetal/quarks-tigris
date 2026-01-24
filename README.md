# quarkus-tigris

This project uses Quarkus, the Supersonic Subatomic Java Framework, to create a backend service that handles file uploads to Amazon S3. It also includes an Angular frontend for user interaction.

## System Architecture

### Current Implementation

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
│         │ 2. POST /api/validate-passphrase        │              │
│         └──────────────────┬───────────────────────┘              │
│                            │ 3. POST /api/upload (multipart)      │
└────────────────────────────┼──────────────────────────────────────┘
                             │
                             │ Quinoa Integration
                             │ (Serves Angular + Routes API calls)
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                      Quarkus Backend (REST)                      │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              FileUploadResource.java                       │  │
│  │  • POST /api/validate-passphrase (403 on invalid)         │  │
│  │  • POST /api/upload (50MB limit, multipart)               │  │
│  │  • Validates: file size, email, file presence             │  │
│  │  • Returns: 200 (success), 400 (validation), 413 (size)   │  │
│  └────────────────────────────────────────────────────────────┘  │
│                            │                                      │
│                            │ 4. Upload File                       │
│                            │    PutObjectRequest                  │
└────────────────────────────┼──────────────────────────────────────┘
                             │
                             │ S3 Client (AWS SDK)
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                    Amazon S3 / LocalStack                        │
│  • Bucket: configurable (default: "your-bucket-name")           │
│  • Path: uploads/{email}/{uuid}-{filename}                      │
│  • Dev Mode: LocalStack container (S3 emulation)                │
│  • Prod: Real AWS S3 or Tigris                                  │
└──────────────────────────────────────────────────────────────────┘
```

### Architecture Details

**Frontend (Angular 19)**
- Single Page Application (SPA) served via Quinoa
- Passphrase authentication with session management
- Route guards protect upload page
- File upload with progress and error handling
- HTTP error handling (403 → invalid passphrase, 413 → file too large)

**Backend (Quarkus 3.30.7)**
- RESTful API with OpenAPI/Swagger documentation
- Multipart file upload handling (max 50MB per file)
- Input validation (file size, email, file presence)
- S3 integration via AWS SDK
- DevServices: LocalStack for S3 emulation in dev mode

**Storage (S3-Compatible)**
- Development: LocalStack (containerized S3 emulation)
- Production: AWS S3 or Tigris Object Storage
- File organization: `uploads/{email}/{uuid}-{filename}`

**Key Features**
- ✅ Passphrase-protected file upload
- ✅ File size validation (50MB limit)
- ✅ Email-based file organization
- ✅ HTTP error handling with user-friendly messages
- ✅ Hot reload for both frontend and backend
- ✅ OpenAPI documentation

### Future Enhancements (Planned)

The following components are included in dependencies but not yet implemented:

```ascii
[Quarkus Backend] ---> [NATS JetStream] ---> [F# Worker]
                                                   │
                                                   └──> Voice-to-Text Processing
                                                   └──> Notification Service
```

**Planned Features:**
- 🔲 NATS JetStream event publishing after file upload
- 🔲 F# worker for voice-to-text transcription
- 🔲 Event-driven notification system
- 🔲 Asynchronous processing pipeline

## Technologies Used

*   **Quarkus**: A full-stack, Kubernetes-native Java framework tailored for GraalVM and OpenJDK HotSpot, crafted from the best of breed Java libraries and standards.
*   **Angular**: A platform for building mobile and desktop web applications.
*   **Quinoa**: A Quarkus extension that simplifies the integration of a web UI, like one built with Angular, into a Quarkus application.
*   **Amazon S3**: A scalable object storage service used to store the uploaded MP3 files.

## Project Structure

The project is organized as follows:

-   `src/main/java`: Contains the Java source code for the Quarkus backend.
    -   API paths are controlled via `@Path` annotations for maximum flexibility
    -   See [API_ORGANIZATION.md](./API_ORGANIZATION.md) for path organization guidelines
-   `src/main/resources`: Contains the configuration files for the Quarkus application, such as `application.properties`.
-   `src/main/webui`: Contains the Angular frontend application.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

This command will start both the Quarkus backend and the Angular frontend in development mode. 

**Important**: Access the application at `http://localhost:8080/whisper` (not port 4200). 

### Development Modes

The application supports two development modes:

#### Option 1: DevServices Mode (Default - Automatic)

Uses Quarkus DevServices with Testcontainers for automatic container management:

```shell script
./mvnw quarkus:dev
```

**Advantages:**
- ✅ Zero configuration - containers start automatically
- ✅ Clean state on each run
- ✅ No manual service management

#### Option 2: Docker Compose Mode (Manual - Persistent Data)

Uses Docker Compose for manual container management with data persistence:

```shell script
# 1. Start services
docker-compose up -d

# 2. Run Quarkus with Docker Compose backend
USE_DEVSERVICES=false ./mvnw quarkus:dev

# Or use the helper script
./dev-mode.sh
```

**Advantages:**
- ✅ Persistent data between runs
- ✅ Full control over services
- ✅ Can inspect/debug services independently
- ✅ Faster startup (no container creation)

**Services included:**
- LocalStack (S3) on port 4566
- NATS JetStream on port 4222

For detailed Docker Compose instructions, see [DOCKER_COMPOSE.md](./DOCKER_COMPOSE.md).

### URL Structure

The application uses `/whisper` as the base path. Quinoa automatically routes requests based on the URL path:

- **Frontend Routes** (Angular SPA):
  - `http://localhost:8080/whisper/` → Angular app (redirects to `/passphrase`)
  - `http://localhost:8080/whisper/passphrase` → Passphrase page
  - `http://localhost:8080/whisper/upload` → Upload page

- **Backend API Routes** (Quarkus REST):
  - `http://localhost:8080/whisper/api/validate-passphrase` → Passphrase validation endpoint
  - `http://localhost:8080/whisper/api/upload` → File upload endpoint

- **OpenAPI/Swagger Documentation**:
  - `http://localhost:8080/whisper/swagger` → OpenAPI specification (JSON)
  - `http://localhost:8080/whisper/swagger-ui` → Swagger UI (interactive API documentation)

Quinoa intelligently proxies the Angular dev server through Quarkus, so:
- Any path starting with `/whisper/api` is handled by your Quarkus backend
- All other paths under `/whisper` are served by the Angular application
- No additional proxy configuration is needed!

## API Configuration

The Angular frontend uses a centralized API service that reads the backend URL from environment configuration files. This allows you to easily change the API endpoint for different environments.

### Changing the Backend URL

To change where the frontend sends API requests:

1. **For Development**: Edit `src/main/webui/src/environments/environment.ts`
2. **For Production**: Edit `src/main/webui/src/environments/environment.prod.ts`

Update the `apiUrl` property:
```typescript
export const environment = {
  production: false,
  apiUrl: '/whisper/api',  // Change this to your backend URL
};
```

**Examples**:
- Relative URL (default): `'/whisper/api'`
- Full URL for standalone Angular: `'http://localhost:8080/whisper/api'`
- Production: `'https://api.your-domain.com/whisper/api'`

For detailed instructions, see [API_CONFIGURATION.md](./API_CONFIGURATION.md).

## Usage

1. **Access the application**: Navigate to `http://localhost:8080/whisper`
2. **Enter passphrase**: The default passphrase is `your-secret-passphrase`
3. **Upload MP3 file**: After successful validation, enter your email and select an MP3 file to upload

### Default Configuration

- **Passphrase**: `your-secret-passphrase` (change this in `FileUploadResource.java`)
- **S3 Bucket**: `your-bucket-name` (configurable via `S3_BUCKET_NAME` env var, defaults to `your-bucket-name`)
- **Upload path**: Files are stored as `uploads/{email}/{uuid}-{filename}`

### Verifying S3 Uploads (LocalStack)

When running in dev mode, Quarkus starts a LocalStack container. To verify uploads or list objects:

1.  **Find the LocalStack port**:
    ```bash
    docker ps
    # Look for the port mapping for 4566/tcp (e.g., 0.0.0.0:32790->4566/tcp)
    ```

2.  **List objects in the bucket**:
    ```bash
    # Replace 32790 with your actual port
    aws --endpoint-url=http://localhost:32790 s3api list-objects --bucket your-bucket-name
    ```

> **_NOTE:_** Quarkus now ships with a Dev UI, which is available in dev mode only at `http://localhost:8080/q/dev/`.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/quarkus-tigris-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): A Jakarta REST implementation utilizing build time processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it
- Reactive Messaging Nats Jetstream ([guide](https://docs.quarkiverse.io/quarkus-reactive-messaging-nats-jetstream/dev/)): Easily integrate to nats.io JetStream.
- Amazon S3 ([guide](https://docs.quarkiverse.io/quarkus-amazon-services/dev/amazon-s3.html)): Connect to Amazon S3 cloud storage

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
