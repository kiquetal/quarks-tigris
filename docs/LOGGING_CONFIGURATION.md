# Application Logging Configuration

## Environment Variable Configuration

The application log level can now be controlled via the `LOG_LEVEL_APP` environment variable.

### Configuration in application.properties

```properties
# Logging Configuration
# Application log level (can be overridden with LOG_LEVEL_APP environment variable)
# Levels: TRACE, DEBUG, INFO, WARN, ERROR, OFF
quarkus.log.category."me.cresterida".level=${LOG_LEVEL_APP:INFO}
```

## Usage

### Default Behavior (INFO level)

Without setting the environment variable, the application logs at **INFO** level:

```bash
./mvnw quarkus:dev
```

This will log:
- ✅ INFO level and above (INFO, WARN, ERROR)
- ❌ DEBUG and TRACE messages are hidden

### Setting Log Level via Environment Variable

#### Option 1: Inline with command
```bash
LOG_LEVEL_APP=DEBUG ./mvnw quarkus:dev
```

#### Option 2: Export first
```bash
export LOG_LEVEL_APP=DEBUG
./mvnw quarkus:dev
```

#### Option 3: In .env file (if using)
```bash
# .env
LOG_LEVEL_APP=DEBUG
```

#### Option 4: Docker/Container
```bash
docker run -e LOG_LEVEL_APP=DEBUG my-app
```

#### Option 5: System property
```bash
./mvnw quarkus:dev -DLOG_LEVEL_APP=DEBUG
```

## Available Log Levels

| Level | Description | Use Case |
|-------|-------------|----------|
| **TRACE** | Most verbose | Extremely detailed debugging |
| **DEBUG** | Detailed info | Development and troubleshooting |
| **INFO** | General info | Production (default) |
| **WARN** | Warnings | Production |
| **ERROR** | Errors only | Minimal logging |
| **OFF** | No logging | Disable application logs |

## Examples

### Development (see everything)
```bash
LOG_LEVEL_APP=DEBUG ./mvnw quarkus:dev
```

Output includes:
```
DEBUG Validating passphrase...
DEBUG Decrypted size: 1048576 bytes
DEBUG DEK encrypted size: 1048604 bytes
DEBUG Envelope created: DEK encrypted with master key
DEBUG Created envelope metadata JSON:
{...}
INFO  Uploading encrypted file: audio.mp3 (1048576 bytes)
INFO  Decryption verification: SUCCESS
```

### Production (important events only)
```bash
LOG_LEVEL_APP=INFO ./mvnw quarkus:dev
```

Output includes:
```
INFO  Uploading encrypted file: audio.mp3 (1048576 bytes)
INFO  Email: user@example.com
INFO  Decryption verification: SUCCESS
INFO  Passphrase valid for email: user@example.com
```

### Troubleshooting (maximum detail)
```bash
LOG_LEVEL_APP=TRACE ./mvnw quarkus:dev
```

Shows TRACE, DEBUG, INFO, WARN, and ERROR messages.

### Production with minimal logs
```bash
LOG_LEVEL_APP=WARN ./mvnw quarkus:dev
```

Only shows:
```
WARN  Failed to delete temporary files: Permission denied
WARN  Failed to publish to NATS: Connection refused
ERROR IO Error: File not found
```

### Disable application logs
```bash
LOG_LEVEL_APP=OFF ./mvnw quarkus:dev
```

No logs from `me.cresterida` package (Quarkus framework logs still appear).

## Package Scope

The configuration applies to the entire `me.cresterida` package, which includes:
- `me.cresterida.FileUploadResource`
- `me.cresterida.FileListResource`
- `me.cresterida.DecryptResource`
- `me.cresterida.service.*`
- `me.cresterida.util.*`
- All other classes in the package

## Class-Specific Log Levels

If you need different log levels for specific classes, you can add additional configuration:

```properties
# Package-wide default
quarkus.log.category."me.cresterida".level=${LOG_LEVEL_APP:INFO}

# Override for specific class
quarkus.log.category."me.cresterida.FileUploadResource".level=DEBUG
quarkus.log.category."me.cresterida.service.CryptoService".level=TRACE
```

Or via environment variables:
```bash
export LOG_LEVEL_APP=INFO
export LOG_LEVEL_UPLOAD=DEBUG
```

Then in application.properties:
```properties
quarkus.log.category."me.cresterida".level=${LOG_LEVEL_APP:INFO}
quarkus.log.category."me.cresterida.FileUploadResource".level=${LOG_LEVEL_UPLOAD:INFO}
```

## Production Deployment

### Docker
```dockerfile
FROM quarkus/...
ENV LOG_LEVEL_APP=INFO
```

### Kubernetes
```yaml
env:
  - name: LOG_LEVEL_APP
    value: "INFO"
```

### systemd service
```ini
[Service]
Environment="LOG_LEVEL_APP=INFO"
```

### Fly.io
```bash
fly secrets set LOG_LEVEL_APP=INFO
```

## Troubleshooting

### Logs not showing?

1. **Check log level is not too high:**
   ```bash
   echo $LOG_LEVEL_APP
   # Should be INFO or DEBUG for normal logs
   ```

2. **Verify configuration:**
   ```bash
   grep "quarkus.log.category" src/main/resources/application.properties
   ```

3. **Check Quarkus is reading the property:**
   Look for this in startup logs:
   ```
   INFO  [io.quarkus] Profile dev activated.
   ```

### Too many logs?

Set to WARN or ERROR:
```bash
LOG_LEVEL_APP=WARN ./mvnw quarkus:dev
```

### Need different levels for different environments?

Use profile-specific configuration:

```properties
# Default
quarkus.log.category."me.cresterida".level=${LOG_LEVEL_APP:INFO}

# Development: verbose
%dev.quarkus.log.category."me.cresterida".level=${LOG_LEVEL_APP:DEBUG}

# Production: minimal
%prod.quarkus.log.category."me.cresterida".level=${LOG_LEVEL_APP:WARN}
```

## Summary

✅ **Default**: INFO level (production-ready)
✅ **Configurable**: Via `LOG_LEVEL_APP` environment variable
✅ **Flexible**: Change without rebuilding
✅ **Scoped**: Applies to `me.cresterida` package
✅ **Production-ready**: Easy to configure in any deployment

### Quick Reference

```bash
# Default (INFO)
./mvnw quarkus:dev

# Development (DEBUG)
LOG_LEVEL_APP=DEBUG ./mvnw quarkus:dev

# Troubleshooting (TRACE)
LOG_LEVEL_APP=TRACE ./mvnw quarkus:dev

# Production quiet (WARN)
LOG_LEVEL_APP=WARN ./mvnw quarkus:dev

# Silent application logs (OFF)
LOG_LEVEL_APP=OFF ./mvnw quarkus:dev
```
