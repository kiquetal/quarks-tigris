# Quarkus Container Usage & Dev Services

## Containerization with Quarkus

Quarkus provides first-class support for creating container images, offering multiple strategies:

1.  **Jib**: Builds container images without requiring a Docker daemon.
2.  **Docker**: Uses standard Dockerfiles (provided in `src/main/docker/`).
3.  **Source-to-Image (S2I)**: Useful for OpenShift environments.

### Building a JVM Image
```bash
./mvnw clean package -Dquarkus.container-image.build=true
```

### Building a Native Image
Native executables start faster and use less memory but take longer to build.
```bash
./mvnw clean package -Pnative -Dquarkus.native.container-build=true
```

---

## Quarkus Dev Services

Quarkus Dev Services automatically starts Docker containers for unconfigured dependencies (like databases, Kafka, Redis, or NATS) when running in dev mode (`./mvnw quarkus:dev`). This provides a "zero-config" experience for development.

### Using an Existing Running Container (e.g., NATS)

If you already have a service running (e.g., a local NATS instance started via Docker Compose or manually) and you **do not** want Quarkus to spin up a new container, you simply need to configure the connection URL in `src/main/resources/application.properties`.

Quarkus detects that the connection is manually configured and will automatically disable the Dev Service for that component.

#### Example: NATS

**Scenario:** You have a NATS container running on port `4222`.

1.  **Start your NATS container** (if not already running):
    ```bash
    docker run -d --name my-nats -p 4222:4222 nats:latest
    ```

2.  **Configure `application.properties`**:
    Add the NATS connection url. By explicitly setting this, Quarkus knows to connect here instead of starting a new ephemeral container.

    ```properties
    # Connect to the existing NATS instance on localhost
    quarkus.nats.servers=nats://localhost:4222
    ```

    *Note: If you want to explicitly disable Dev Services without setting the URL (e.g., if relying on defaults that happen to match your local setup, though setting the URL is safer), you can typically use:*
    ```properties
    quarkus.nats.devservices.enabled=false
    ```
