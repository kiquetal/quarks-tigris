# Docker Compose Setup Guide

## Overview

This guide explains how to run the application with Docker Compose instead of relying on Quarkus DevServices (Testcontainers).

## Architecture with Docker Compose

```ascii
┌─────────────────────────────────────────────────────────────┐
│                    Docker Compose Stack                     │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐  │
│  │  LocalStack  │  │     NATS     │  │  S3 Init        │  │
│  │  (S3)        │  │  JetStream   │  │  Container      │  │
│  │  Port: 4566  │  │  Port: 4222  │  │  (one-time)     │  │
│  └──────────────┘  └──────────────┘  └─────────────────┘  │
│         ▲                 ▲                                 │
└─────────┼─────────────────┼─────────────────────────────────┘
          │                 │
          │                 │
┌─────────┼─────────────────┼─────────────────────────────────┐
│         │                 │                                 │
│  ┌──────┴──────────┐      │      Quarkus Application       │
│  │  S3 Client      │      │      (runs on host)            │
│  │  Endpoint:      │      │                                │
│  │  localhost:4566 │      └──> (Future: NATS client)       │
│  └─────────────────┘                                        │
└─────────────────────────────────────────────────────────────┘
```

## Services Included

### 1. LocalStack (S3 Compatible Storage)
- **Port**: 4566
- **Purpose**: S3-compatible object storage for development
- **Persistence**: Data stored in `./localstack-data`
- **Health Check**: Available at `http://localhost:4566/_localstack/health`

### 2. NATS JetStream (Message Broker)
- **Port**: 4222 (client), 8222 (management)
- **Purpose**: Future event-driven architecture
- **Persistence**: Data stored in `./nats-data`
- **Management UI**: `http://localhost:8222`

### 3. LocalStack Initializer
- **Purpose**: Creates the S3 bucket on startup
- **Bucket**: `whisper-uploads`
- **Runs once**: Exits after bucket creation

## Quick Start

### Option 1: Using Docker Compose (Disable DevServices)

1. **Start Docker services**:
   ```bash
   docker-compose up -d
   ```

2. **Verify services are running**:
   ```bash
   docker-compose ps
   ```

3. **Set environment variable to disable DevServices**:
   ```bash
   export USE_DEVSERVICES=false
   ```

4. **Run Quarkus**:
   ```bash
   ./mvnw quarkus:dev
   ```

5. **Access the application**:
   ```
   http://localhost:8080/whisper
   ```

### Option 2: Using DevServices (Default - Testcontainers)

1. **Just run Quarkus** (DevServices will start containers automatically):
   ```bash
   ./mvnw quarkus:dev
   ```

2. **Access the application**:
   ```
   http://localhost:8080/whisper
   ```

## Environment Variables

The `.env` file contains configuration for Docker Compose:

```env
# LocalStack S3 Configuration
LOCALSTACK_SERVICES=s3
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
AWS_DEFAULT_REGION=us-east-1

# S3 Bucket Configuration
S3_BUCKET_NAME=whisper-uploads
S3_ENDPOINT_URL=http://localhost:4566

# NATS Configuration (for future use)
NATS_URL=nats://localhost:4222
```

## Docker Compose Commands

### Start Services
```bash
# Start all services in background
docker-compose up -d

# Start with logs visible
docker-compose up

# Start specific service
docker-compose up -d localstack
```

### Stop Services
```bash
# Stop all services
docker-compose down

# Stop and remove volumes (deletes data)
docker-compose down -v
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f localstack
docker-compose logs -f nats
```

### Check Status
```bash
# List running services
docker-compose ps

# Check health
docker-compose ps --services --filter "health=healthy"
```

### Restart Services
```bash
# Restart all
docker-compose restart

# Restart specific service
docker-compose restart localstack
```

## Verifying LocalStack S3

### List Buckets
```bash
aws --endpoint-url=http://localhost:4566 s3 ls
```

### List Objects in Bucket
```bash
aws --endpoint-url=http://localhost:4566 s3 ls s3://whisper-uploads --recursive
```

### Upload Test File
```bash
echo "test content" > test.txt
aws --endpoint-url=http://localhost:4566 s3 cp test.txt s3://whisper-uploads/test/test.txt
```

### Download File
```bash
aws --endpoint-url=http://localhost:4566 s3 cp s3://whisper-uploads/test/test.txt downloaded.txt
```

### Delete File
```bash
aws --endpoint-url=http://localhost:4566 s3 rm s3://whisper-uploads/test/test.txt
```

## NATS JetStream (Future Use)

### Access Management UI
Open browser: `http://localhost:8222`

### Using NATS CLI
```bash
# Install NATS CLI
# macOS
brew install nats-io/nats-tools/nats

# Linux
curl -sf https://binaries.nats.dev/nats-io/natscli/nats@latest | sh

# Check server info
nats server info

# Create stream (example for future use)
nats stream add UPLOADS \
  --subjects "uploads.*" \
  --storage file \
  --retention limits \
  --max-msgs=-1 \
  --max-age=1y
```

## Switching Between DevServices and Docker Compose

### Use Docker Compose (Persistent Data)
```bash
# 1. Start Docker Compose services
docker-compose up -d

# 2. Disable DevServices
export USE_DEVSERVICES=false
export S3_ENDPOINT_URL=http://localhost:4566
export S3_BUCKET_NAME=whisper-uploads

# 3. Run Quarkus
./mvnw quarkus:dev
```

**Advantages**:
- ✅ Data persists between runs
- ✅ Faster startup (no container creation)
- ✅ Full control over services
- ✅ Can inspect/debug services independently

### Use DevServices (Default)
```bash
# Just run Quarkus (no need to start Docker Compose)
./mvnw quarkus:dev
```

**Advantages**:
- ✅ Zero configuration
- ✅ Automatic container management
- ✅ Clean state on each run
- ✅ No manual service management

## Troubleshooting

### Port Conflicts

**Issue**: Port 4566 already in use

**Solution**:
```bash
# Check what's using the port
lsof -i :4566

# Option 1: Stop conflicting service
docker-compose down

# Option 2: Change port in docker-compose.yml
# Edit: "4567:4566" instead of "4566:4566"
```

### LocalStack Not Starting

**Check logs**:
```bash
docker-compose logs localstack
```

**Common issues**:
- Docker daemon not running: `docker ps` to verify
- Insufficient permissions: Run with sudo or add user to docker group
- Old LocalStack image: `docker-compose pull localstack`

### Bucket Not Created

**Manually create bucket**:
```bash
aws --endpoint-url=http://localhost:4566 s3 mb s3://whisper-uploads
```

**Or restart init container**:
```bash
docker-compose up localstack-init
```

### Cannot Connect to S3 from Quarkus

**Check endpoint configuration**:
```bash
# Verify LocalStack is accessible
curl http://localhost:4566/_localstack/health

# Verify environment variable
echo $S3_ENDPOINT_URL

# Check Quarkus logs for S3 connection errors
```

**Common fixes**:
- Ensure `USE_DEVSERVICES=false` is set
- Verify LocalStack is running: `docker-compose ps`
- Check endpoint URL in application.properties

### Data Persistence Issues

**Check volume mounts**:
```bash
# List volumes
docker volume ls | grep quarks-tigris

# Inspect volume
docker volume inspect quarks-tigris_localstack-data
```

**Reset data**:
```bash
# Stop and remove volumes
docker-compose down -v

# Start fresh
docker-compose up -d
```

## Data Persistence

### LocalStack Data
- **Location**: `./localstack-data`
- **Contains**: S3 bucket data, objects
- **Persists**: Across container restarts

### NATS Data
- **Location**: `./nats-data`
- **Contains**: Streams, messages, subjects
- **Persists**: Across container restarts

### Clean Slate
```bash
# Remove all data
docker-compose down -v
rm -rf localstack-data nats-data

# Start fresh
docker-compose up -d
```

## Production Deployment

For production, replace LocalStack with real services:

### AWS S3
```properties
# In application.properties
%prod.quarkus.s3.endpoint-override=  # Remove this line
%prod.quarkus.s3.aws.region=us-east-1
%prod.quarkus.s3.aws.credentials.type=default
%prod.bucket.name=your-production-bucket
```

### Tigris Object Storage
```properties
# In application.properties
%prod.quarkus.s3.endpoint-override=https://fly.storage.tigris.dev
%prod.quarkus.s3.aws.region=auto
%prod.quarkus.s3.aws.credentials.type=static
%prod.quarkus.s3.aws.credentials.static-provider.access-key-id=${TIGRIS_ACCESS_KEY_ID}
%prod.quarkus.s3.aws.credentials.static-provider.secret-access-key=${TIGRIS_SECRET_ACCESS_KEY}
%prod.bucket.name=your-tigris-bucket
```

### NATS Cloud
Replace local NATS with NATS Cloud or self-hosted NATS cluster.

## Summary

### Docker Compose Mode
```bash
docker-compose up -d
export USE_DEVSERVICES=false
./mvnw quarkus:dev
```

**Use when**:
- You want persistent data
- Testing data retention
- Debugging S3 operations
- Inspecting uploaded files

### DevServices Mode (Default)
```bash
./mvnw quarkus:dev
```

**Use when**:
- Quick development
- Testing from scratch
- Clean state needed
- No data persistence required

## Quick Reference

| Service | Port | UI/Management | Purpose |
|---------|------|---------------|---------|
| LocalStack | 4566 | http://localhost:4566/_localstack/health | S3 Storage |
| NATS | 4222 | http://localhost:8222 | Messaging |
| NATS Admin | 8222 | http://localhost:8222 | Management |
| Quarkus | 8080 | http://localhost:8080/whisper | Application |
| Swagger UI | 8080 | http://localhost:8080/whisper/swagger-ui | API Docs |

## Files Created

- ✅ `docker-compose.yml` - Service definitions
- ✅ `.env` - Environment variables
- ✅ `DOCKER_COMPOSE.md` - This documentation

The application now supports both DevServices (automatic) and Docker Compose (manual) modes!
