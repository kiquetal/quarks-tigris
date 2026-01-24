# Docker Compose Setup - Quick Reference

## Files Created

✅ `docker-compose.yml` - Service definitions for LocalStack and NATS  
✅ `.env` - Environment variables for Docker Compose  
✅ `DOCKER_COMPOSE.md` - Comprehensive documentation  
✅ `dev-mode.sh` - Interactive script to switch between modes  
✅ Updated `application.properties` - Support for both modes  
✅ Updated `.gitignore` - Exclude data directories  
✅ Updated `README.md` - Documentation for both modes  

## Quick Start

### Method 1: Manual Docker Compose

```bash
# Start Docker services
docker-compose up -d

# Verify services are running
docker-compose ps

# Run Quarkus with Docker Compose backend
USE_DEVSERVICES=false ./mvnw quarkus:dev
```

### Method 2: Interactive Script

```bash
# Make script executable (one time)
chmod +x dev-mode.sh

# Run the script
./dev-mode.sh

# Select option 2 for Docker Compose mode
```

### Method 3: DevServices (Default)

```bash
# Just run Quarkus - containers managed automatically
./mvnw quarkus:dev
```

## Services Overview

| Service | Port | Purpose | Data Persistence |
|---------|------|---------|------------------|
| LocalStack (S3) | 4566 | S3-compatible storage | `./localstack-data/` |
| NATS JetStream | 4222 | Message broker | `./nats-data/` |
| NATS Management | 8222 | NATS admin UI | - |

## Key Commands

### Docker Compose

```bash
# Start services
docker-compose up -d

# Stop services
docker-compose down

# View logs
docker-compose logs -f

# Check status
docker-compose ps

# Clean everything (including data)
docker-compose down -v
rm -rf localstack-data nats-data
```

### AWS CLI (LocalStack)

```bash
# List buckets
aws --endpoint-url=http://localhost:4566 s3 ls

# List files in bucket
aws --endpoint-url=http://localhost:4566 s3 ls s3://whisper-uploads --recursive

# Download file
aws --endpoint-url=http://localhost:4566 s3 cp s3://whisper-uploads/uploads/email@example.com/file.mp3 ./
```

## Environment Variables

When using Docker Compose mode, set these variables:

```bash
export USE_DEVSERVICES=false
export S3_ENDPOINT_URL=http://localhost:4566
export S3_BUCKET_NAME=whisper-uploads
```

Or use the `.env` file (already configured).

## Switching Modes

### From DevServices to Docker Compose

1. Stop Quarkus (Ctrl+C)
2. Start Docker Compose: `docker-compose up -d`
3. Run with env vars: `USE_DEVSERVICES=false ./mvnw quarkus:dev`

### From Docker Compose to DevServices

1. Stop Quarkus (Ctrl+C)
2. Stop Docker Compose: `docker-compose down`
3. Run normally: `./mvnw quarkus:dev`

## Configuration

### application.properties

The app now supports both modes via environment variables:

```properties
# Toggle between DevServices and external Docker Compose
quarkus.s3.devservices.enabled=${USE_DEVSERVICES:true}

# S3 Endpoint (only when DevServices disabled)
%dev.quarkus.s3.endpoint-override=${S3_ENDPOINT_URL:http://localhost:4566}

# Bucket name
bucket.name=${S3_BUCKET_NAME:whisper-uploads}
```

## Verifying Setup

### Check LocalStack

```bash
# Health check
curl http://localhost:4566/_localstack/health

# List buckets
aws --endpoint-url=http://localhost:4566 s3 ls
```

### Check NATS

```bash
# Health check
curl http://localhost:8222/healthz

# Management UI
open http://localhost:8222
```

### Check Quarkus

```bash
# Application
curl http://localhost:8080/whisper

# Health
curl http://localhost:8080/q/health
```

## Data Persistence

### Persistent Data Locations

- **LocalStack**: `./localstack-data/` (S3 buckets and objects)
- **NATS**: `./nats-data/` (streams and messages)

### Backup Data

```bash
# Create backup
tar -czf backup-$(date +%Y%m%d).tar.gz localstack-data nats-data

# Restore backup
tar -xzf backup-20260124.tar.gz
```

### Reset to Clean State

```bash
# Stop everything and remove data
docker-compose down -v
rm -rf localstack-data nats-data

# Start fresh
docker-compose up -d
```

## Troubleshooting

### Port Already in Use

```bash
# Check what's using port 4566
lsof -i :4566

# Kill the process or change port in docker-compose.yml
```

### Cannot Connect to LocalStack

```bash
# Check if running
docker-compose ps

# Check logs
docker-compose logs localstack

# Restart service
docker-compose restart localstack
```

### Bucket Not Created

```bash
# Manually create bucket
aws --endpoint-url=http://localhost:4566 s3 mb s3://whisper-uploads

# Or re-run init container
docker-compose up localstack-init
```

### Data Not Persisting

```bash
# Check volume mounts
docker volume ls | grep quarks

# Inspect volume
docker volume inspect quarks-tigris_localstack-data

# Verify data directory exists
ls -la localstack-data/
```

## Useful Links

- **Application**: http://localhost:8080/whisper
- **Swagger UI**: http://localhost:8080/whisper/swagger-ui
- **Dev UI**: http://localhost:8080/q/dev
- **LocalStack**: http://localhost:4566
- **NATS Management**: http://localhost:8222

## Documentation

- 📄 `README.md` - Main project documentation
- 📄 `DOCKER_COMPOSE.md` - Detailed Docker Compose guide
- 📄 `API_CONFIGURATION.md` - API configuration
- 📄 `API_ORGANIZATION.md` - API structure guide
- 📄 `FILE_UPLOAD_TROUBLESHOOTING.md` - Upload issues
- 📄 `HTTP_ERROR_HANDLING.md` - Error handling guide

## Summary

### DevServices (Default)
```bash
./mvnw quarkus:dev
```
- ✅ Automatic, zero config
- ✅ Clean state each run
- ❌ No data persistence

### Docker Compose
```bash
docker-compose up -d
USE_DEVSERVICES=false ./mvnw quarkus:dev
```
- ✅ Data persistence
- ✅ Full service control
- ✅ Debugging friendly
- ❌ Manual management

Use Docker Compose when you need persistent data and full control over services!
