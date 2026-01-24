# Docker Compose Setup - Getting Started Checklist

## ✅ Setup Checklist

### Prerequisites
- [ ] Docker installed and running
- [ ] Docker Compose installed
- [ ] AWS CLI installed (optional, for S3 operations)

### Verify Installation
```bash
docker --version
docker-compose --version
aws --version  # optional
```

## 🚀 Quick Start (3 Steps)

### Step 1: Make Script Executable
```bash
chmod +x dev-mode.sh
```

### Step 2: Run Interactive Script
```bash
./dev-mode.sh
```

### Step 3: Select Option 2 (Docker Compose Mode)

The script will:
- Start LocalStack and NATS
- Configure environment variables
- Start Quarkus in Docker Compose mode

## 📁 Files Created

```
quarks-tigris/
├── docker-compose.yml          ✅ Service definitions
├── .env                        ✅ Environment variables
├── dev-mode.sh                 ✅ Interactive switcher
├── DOCKER_COMPOSE.md           ✅ Full documentation
├── DOCKER_SETUP_SUMMARY.md     ✅ Quick reference
├── localstack-data/            (created on first run)
└── nats-data/                  (created on first run)
```

## 🎯 Usage Modes

### Mode 1: Interactive (Recommended)
```bash
./dev-mode.sh
# Select option 2 for Docker Compose
```

### Mode 2: Manual Commands
```bash
# Start services
docker-compose up -d

# Run Quarkus
USE_DEVSERVICES=false ./mvnw quarkus:dev
```

### Mode 3: DevServices (Default)
```bash
# No Docker Compose needed
./mvnw quarkus:dev
```

## 🔍 Verification Commands

### Check Services Are Running
```bash
docker-compose ps
```

### Check LocalStack Health
```bash
curl http://localhost:4566/_localstack/health
```

### List S3 Buckets
```bash
aws --endpoint-url=http://localhost:4566 s3 ls
```

### Test Application
```bash
curl http://localhost:8080/whisper
```

## 📊 Service Endpoints

| Service | URL | Purpose |
|---------|-----|---------|
| Application | http://localhost:8080/whisper | Main app |
| Swagger UI | http://localhost:8080/whisper/swagger-ui | API docs |
| Dev UI | http://localhost:8080/q/dev | Quarkus dev tools |
| LocalStack | http://localhost:4566 | S3 API |
| LocalStack Health | http://localhost:4566/_localstack/health | Health check |
| NATS | nats://localhost:4222 | NATS client |
| NATS Management | http://localhost:8222 | NATS admin UI |

## 🛠️ Common Tasks

### Upload a Test File
```bash
# Via Web UI
open http://localhost:8080/whisper

# Via AWS CLI
echo "test" > test.mp3
aws --endpoint-url=http://localhost:4566 s3 cp test.mp3 s3://whisper-uploads/test/
```

### List Uploaded Files
```bash
aws --endpoint-url=http://localhost:4566 s3 ls s3://whisper-uploads --recursive
```

### Download a File
```bash
aws --endpoint-url=http://localhost:4566 s3 cp s3://whisper-uploads/uploads/email@test.com/file.mp3 ./
```

### View Service Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f localstack
docker-compose logs -f nats
```

### Stop Services
```bash
docker-compose down
```

### Clean All Data
```bash
docker-compose down -v
rm -rf localstack-data nats-data
```

## 🐛 Troubleshooting

### Port 4566 Already in Use
```bash
# Find what's using it
lsof -i :4566

# Stop conflicting service
docker-compose down
```

### Services Not Starting
```bash
# Check Docker is running
docker ps

# View logs
docker-compose logs

# Restart services
docker-compose restart
```

### Bucket Not Found
```bash
# Recreate bucket
aws --endpoint-url=http://localhost:4566 s3 mb s3://whisper-uploads

# Or restart init container
docker-compose up localstack-init
```

### Quarkus Can't Connect
```bash
# Verify LocalStack is accessible
curl http://localhost:4566/_localstack/health

# Check environment variables
echo $USE_DEVSERVICES
echo $S3_ENDPOINT_URL

# Restart Quarkus
# Stop with Ctrl+C, then:
USE_DEVSERVICES=false ./mvnw quarkus:dev
```

## 📖 Documentation

- **DOCKER_COMPOSE.md** - Complete guide with examples
- **DOCKER_SETUP_SUMMARY.md** - Quick reference
- **README.md** - Main project documentation

## 🎓 Next Steps

### After Setup
1. **Upload test file** via web UI
2. **Verify file persists** after restart
3. **Explore LocalStack data** in `localstack-data/`
4. **Try AWS CLI commands**

### Learn More
- Read DOCKER_COMPOSE.md for advanced usage
- Explore NATS management UI
- Configure production deployment

## 💡 Tips

### Development Workflow
```bash
# Morning: Start services
docker-compose up -d

# Work: Use Quarkus with persistent data
USE_DEVSERVICES=false ./mvnw quarkus:dev

# Evening: Keep services running or stop
docker-compose down  # optional
```

### Data Management
```bash
# Backup data
tar -czf backup-$(date +%Y%m%d).tar.gz localstack-data nats-data

# Restore data
tar -xzf backup-20260124.tar.gz
```

### Switching Modes
```bash
# From DevServices to Docker Compose
docker-compose up -d
USE_DEVSERVICES=false ./mvnw quarkus:dev

# From Docker Compose to DevServices
docker-compose down
./mvnw quarkus:dev
```

## ✨ Summary

You now have:
- ✅ Docker Compose with LocalStack and NATS
- ✅ Persistent data storage
- ✅ Interactive mode switcher
- ✅ Complete documentation
- ✅ Health checks and monitoring

Start with: `./dev-mode.sh` and select option 2! 🚀
