# Storage Options Summary

## Overview

The application supports three storage backends for S3-compatible object storage:

1. **DevServices (LocalStack)** - Automatic, testcontainers-based
2. **Docker Compose (LocalStack)** - Manual, persistent local storage
3. **Tigris** - Cloud-based, production-ready storage

## Comparison Matrix

| Feature | DevServices | Docker Compose | Tigris |
|---------|-------------|----------------|--------|
| **Setup** | Zero config | One-time setup | Account + config |
| **Data Persistence** | ❌ Ephemeral | ✅ Persistent | ✅ Cloud storage |
| **Internet Required** | ❌ No | ❌ No | ✅ Yes |
| **Cost** | Free | Free | Free tier + usage |
| **Speed** | Fast (local) | Fast (local) | Variable (network) |
| **Use Case** | Quick dev | Local testing | Production/staging |
| **Global Access** | ❌ No | ❌ No | ✅ Yes (CDN) |
| **Scalability** | Limited | Limited | ✅ Unlimited |
| **Backup** | ❌ No | Manual | ✅ Automatic |
| **Monitoring** | Basic | Basic | ✅ Full dashboard |

## Quick Start Commands

### DevServices (Default)
```bash
./mvnw quarkus:dev
```

### Docker Compose
```bash
docker-compose up -d
USE_DEVSERVICES=false ./mvnw quarkus:dev
```

### Tigris
```bash
# Setup (one-time)
./setup-tigris.sh

# Run
source .env.tigris
./mvnw quarkus:dev
```

## Configuration Comparison

### DevServices Configuration
```properties
# application.properties (default)
quarkus.s3.devservices.enabled=true
bucket.name=whisper-uploads
# Everything else managed automatically
```

### Docker Compose Configuration
```bash
# Environment variables
export USE_DEVSERVICES=false
export S3_ENDPOINT_URL=http://localhost:4566
export S3_BUCKET_NAME=whisper-uploads
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
```

### Tigris Configuration
```bash
# Environment variables
export USE_DEVSERVICES=false
export AWS_ACCESS_KEY_ID=tid_your_key
export AWS_SECRET_ACCESS_KEY=tsec_your_secret
export AWS_REGION=auto
export S3_ENDPOINT_URL=https://fly.storage.tigris.dev
export S3_BUCKET_NAME=whisper-uploads
export S3_PATH_STYLE_ACCESS=true
```

## Use Case Recommendations

### Use DevServices When:
- ✅ Quick feature development
- ✅ Running unit tests
- ✅ Need clean state each run
- ✅ Zero configuration preferred
- ✅ CI/CD pipelines

### Use Docker Compose When:
- ✅ Testing data persistence
- ✅ Debugging upload issues
- ✅ Need to inspect files manually
- ✅ Offline development
- ✅ Local integration testing
- ✅ Working with realistic data sets

### Use Tigris When:
- ✅ Production deployment
- ✅ Staging environment
- ✅ Need global access
- ✅ Testing with real cloud storage
- ✅ Sharing files with team
- ✅ Performance testing at scale
- ✅ Demo environment

## S3 Endpoint URLs

| Mode | Endpoint | Location |
|------|----------|----------|
| DevServices | Auto-assigned | Docker container |
| Docker Compose | http://localhost:4566 | Local Docker |
| Tigris Global | https://fly.storage.tigris.dev | Global CDN |
| Tigris US East | https://us-east-1.storage.tigris.dev | US East |
| Tigris EU West | https://eu-west-1.storage.tigris.dev | EU West |
| Tigris Asia Pacific | https://ap-southeast-1.storage.tigris.dev | Asia Pacific |

## Data Persistence

### DevServices
- **Location**: Docker volume (temporary)
- **Lifecycle**: Deleted when Quarkus stops
- **Backup**: Not applicable
- **Restore**: Not applicable

### Docker Compose
- **Location**: `./localstack-data/`
- **Lifecycle**: Persists across restarts
- **Backup**: `tar -czf backup.tar.gz localstack-data/`
- **Restore**: `tar -xzf backup.tar.gz`

### Tigris
- **Location**: Tigris cloud infrastructure
- **Lifecycle**: Permanent (until deleted)
- **Backup**: Built-in redundancy
- **Restore**: S3 versioning/lifecycle policies

## Performance Comparison

### Upload Speed (5MB file)

| Mode | Average Time | Network |
|------|-------------|---------|
| DevServices | ~100ms | Local |
| Docker Compose | ~100ms | Local |
| Tigris (same region) | ~500ms | Internet |
| Tigris (global) | ~300-800ms | CDN |

### Download Speed

| Mode | Average Time | Network |
|------|-------------|---------|
| DevServices | ~50ms | Local |
| Docker Compose | ~50ms | Local |
| Tigris (CDN) | ~200-400ms | Internet |

## Cost Analysis

### DevServices
- **Cost**: $0.00
- **Infrastructure**: Local Docker
- **Storage**: Local disk space
- **Bandwidth**: None (local)

### Docker Compose
- **Cost**: $0.00
- **Infrastructure**: Local Docker
- **Storage**: Local disk space
- **Bandwidth**: None (local)

### Tigris (Free Tier)
- **Cost**: $0.00/month
- **Storage**: 25 GB included
- **Bandwidth**: 250 GB/month included
- **After Free Tier**:
  - Storage: $0.02/GB/month
  - Bandwidth: $0.09/GB
  - No per-request charges

### Example: 1000 Uploads/Month

Assuming 5MB average file size:

| Mode | Storage | Cost |
|------|---------|------|
| DevServices | 5 GB local | $0 |
| Docker Compose | 5 GB local | $0 |
| Tigris | 5 GB cloud | $0 (free tier) |

## Migration Between Modes

### LocalStack → Tigris

```bash
# 1. Export from LocalStack
mkdir -p migration-data
aws s3 sync s3://whisper-uploads ./migration-data/ \
  --endpoint-url=http://localhost:4566

# 2. Import to Tigris
source .env.tigris
aws s3 sync ./migration-data/ s3://whisper-uploads \
  --endpoint-url=https://fly.storage.tigris.dev
```

### Tigris → LocalStack

```bash
# 1. Export from Tigris
source .env.tigris
aws s3 sync s3://whisper-uploads ./migration-data/ \
  --endpoint-url=https://fly.storage.tigris.dev

# 2. Import to LocalStack
docker-compose up -d
aws s3 sync ./migration-data/ s3://whisper-uploads \
  --endpoint-url=http://localhost:4566
```

## Security Considerations

### DevServices
- ✅ No external access
- ✅ No credentials needed
- ⚠️ Data lost on restart
- ✅ Safe for development

### Docker Compose
- ✅ No external access
- ✅ Test credentials only
- ⚠️ Data on local disk
- ✅ Safe for development

### Tigris
- ⚠️ Internet accessible
- ⚠️ Real credentials required
- ✅ Data encrypted at rest
- ✅ HTTPS only
- ✅ IAM access control
- ✅ Production-ready security

## Monitoring & Debugging

### DevServices
- **Logs**: Quarkus console
- **Monitoring**: Limited
- **Dashboard**: None
- **Debugging**: Docker logs

### Docker Compose
- **Logs**: `docker-compose logs -f localstack`
- **Monitoring**: Basic
- **Dashboard**: LocalStack health endpoint
- **Debugging**: File system inspection

### Tigris
- **Logs**: Tigris Console
- **Monitoring**: Full metrics dashboard
- **Dashboard**: https://console.tigris.dev
- **Debugging**: S3 access logs, CloudWatch-style metrics

## Switching Modes

### Interactive Method
```bash
./dev-mode.sh
# Select desired mode from menu
```

### Manual Method
```bash
# DevServices
./mvnw quarkus:dev

# Docker Compose
docker-compose up -d
USE_DEVSERVICES=false ./mvnw quarkus:dev

# Tigris
source .env.tigris
./mvnw quarkus:dev
```

## Troubleshooting

### DevServices Issues
- **Problem**: Container fails to start
- **Solution**: `docker system prune`

### Docker Compose Issues
- **Problem**: Port 4566 in use
- **Solution**: `docker-compose down`

### Tigris Issues
- **Problem**: Connection timeout
- **Solution**: Check internet, verify endpoint
- **Problem**: Invalid credentials
- **Solution**: Verify keys in Tigris Console
- **Problem**: Bucket not found
- **Solution**: Create bucket in Tigris Console

## Documentation Links

- **DevServices**: Default Quarkus behavior
- **Docker Compose**: [DOCKER_COMPOSE.md](./DOCKER_COMPOSE.md)
- **Tigris**: [TIGRIS_SETUP.md](./TIGRIS_SETUP.md)
- **API Configuration**: [API_CONFIGURATION.md](./API_CONFIGURATION.md)

## Recommendation by Environment

### Development (Local)
**Recommended**: DevServices or Docker Compose
- Fast iteration
- No internet required
- Free

### Staging/Testing
**Recommended**: Tigris
- Real cloud behavior
- Shared access for team
- Free tier sufficient

### Production
**Recommended**: Tigris or AWS S3
- Production-grade reliability
- Global CDN
- Automatic backups
- Monitoring included

## Summary

| Mode | Best For | Setup Time | Cost |
|------|----------|------------|------|
| **DevServices** | Quick dev | 0 min | Free |
| **Docker Compose** | Local testing | 5 min | Free |
| **Tigris** | Production/staging | 10 min | Free tier + usage |

Choose based on your needs:
- **Speed**: DevServices
- **Persistence**: Docker Compose
- **Production**: Tigris
