# Deploying NATS on Fly.io

## Overview

This guide covers deploying a production-ready NATS JetStream server on Fly.io, including how to update the ancient official `nats-cluster` repository and best practices for connecting multiple applications.

## Quick Deploy (Recommended - No Fork Needed)

The simplest approach is to create a new Fly.io app using the latest NATS Docker image directly.

### Step 1: Create fly.toml

```toml
app = "whisper-nats"
primary_region = "mia"  # Choose your region

[build]
  image = "nats:2.10-alpine"

[env]
  # Enable JetStream with file storage and monitoring
  NATS_ARGS = "-js -sd /data -m 8222 --name whisper-nats"

[[mounts]]
  source = "nats_jetstream"
  destination = "/data"

# NATS Client Port (4222)
[[services]]
  internal_port = 4222
  protocol = "tcp"

  [[services.ports]]
    port = 4222

  [services.concurrency]
    type = "connections"
    hard_limit = 1000
    soft_limit = 800

# NATS Monitoring Port (8222)
[[services]]
  internal_port = 8222
  protocol = "tcp"

  [[services.ports]]
    port = 8222
```

### Step 2: Deploy

```bash
# Create the app (without deploying yet)
fly launch --no-deploy --copy-config

# Create persistent volume for JetStream
fly volumes create nats_jetstream --size 10 --region mia

# Deploy
fly deploy

# Verify deployment
fly status
fly logs

# Check NATS is running
curl http://whisper-nats.fly.dev:8222/varz
```

## Production Configuration with Authentication

For production, add authentication and use Fly.io secrets:

### fly.toml (Production)

```toml
app = "whisper-nats-prod"
primary_region = "mia"

[build]
  image = "nats:2.10-alpine"

[env]
  NATS_ARGS = "-js -sd /data -m 8222 --name whisper-nats-prod --user $NATS_USER --pass $NATS_PASS"

[[mounts]]
  source = "nats_jetstream"
  destination = "/data"

# NATS Client Port
[[services]]
  internal_port = 4222
  protocol = "tcp"

  [[services.ports]]
    port = 4222

  [services.concurrency]
    type = "connections"
    hard_limit = 1000
    soft_limit = 800

  [[services.tcp_checks]]
    interval = "10s"
    timeout = "2s"
    grace_period = "5s"

# Monitoring Port (internal only for security)
[[services]]
  internal_port = 8222
  protocol = "tcp"

  [[services.ports]]
    port = 8222
```

### Deploy with Secrets

```bash
# Set authentication credentials as secrets
fly secrets set NATS_USER=admin
fly secrets set NATS_PASS=$(openssl rand -base64 32)

# Create volume
fly volumes create nats_jetstream --size 20 --region mia

# Deploy
fly deploy

# Save credentials for your apps
echo "NATS_USER=admin" >> .env.nats
echo "NATS_PASS=<password-from-above>" >> .env.nats
```

## Updating the Official nats-cluster Repository

If you prefer to fork and update the official Fly.io NATS repository:

### Step 1: Fork the Repository

```bash
# Clone the official repo
git clone https://github.com/fly-apps/nats-cluster.git
cd nats-cluster

# Or fork on GitHub first, then clone your fork
git clone https://github.com/YOUR_USERNAME/nats-cluster.git
cd nats-cluster
```

### Step 2: Update the Dockerfile

Find and update the NATS version:

**Old (likely outdated):**
```dockerfile
FROM nats:2.1.9-alpine
```

**New:**
```dockerfile
FROM nats:2.10-alpine

# Add JetStream data directory
VOLUME /data

# Expose ports
EXPOSE 4222 6222 8222

# Enable JetStream by default
CMD ["-js", "-sd", "/data", "-m", "8222"]
```

### Step 3: Update fly.toml

Replace or update the `fly.toml`:

```toml
app = "your-nats-cluster"
primary_region = "mia"

[build]
  dockerfile = "Dockerfile"

[env]
  NATS_CLUSTER_NAME = "whisper-cluster"

[[mounts]]
  source = "nats_data"
  destination = "/data"

[[services]]
  internal_port = 4222
  protocol = "tcp"

  [[services.ports]]
    port = 4222

[[services]]
  internal_port = 8222
  protocol = "tcp"

  [[services.ports]]
    port = 8222
```

### Step 4: Commit and Deploy

```bash
git add Dockerfile fly.toml
git commit -m "Update NATS to latest version with JetStream"
git push

# Deploy to Fly.io
fly deploy
```

## Connecting Multiple Applications

### Architecture Overview

```
┌─────────────────────────────────────────────┐
│           Fly.io Infrastructure             │
│                                             │
│  ┌──────────────┐      ┌────────────────┐  │
│  │  Quarkus     │──────│  NATS Server   │  │
│  │  (Java)      │      │  JetStream     │  │
│  └──────────────┘      └────────────────┘  │
│         │                      │            │
│         │                      │            │
│  ┌──────────────┐              │            │
│  │  F# Service  │──────────────┘            │
│  │  (Consumer)  │                           │
│  └──────────────┘                           │
│                                             │
└─────────────────────────────────────────────┘
```

### Connection Methods

#### 1. Public URL (External Access)

**NATS URL:** `nats://whisper-nats-prod.fly.dev:4222`

**Use case:** Connecting from outside Fly.io infrastructure

**Quarkus application.properties:**
```properties
%prod.mp.messaging.connector.smallrye-nats.servers=nats://whisper-nats-prod.fly.dev:4222
%prod.mp.messaging.connector.smallrye-nats.username=${NATS_USER}
%prod.mp.messaging.connector.smallrye-nats.password=${NATS_PASSWORD}
```

**F# application:**
```fsharp
let opts = ConnectionFactory.GetDefaultOptions()
opts.Url <- "nats://whisper-nats-prod.fly.dev:4222"
opts.User <- System.Environment.GetEnvironmentVariable("NATS_USER")
opts.Password <- System.Environment.GetEnvironmentVariable("NATS_PASSWORD")
let conn = new ConnectionFactory().CreateConnection(opts)
```

#### 2. Private Network (Fly.io Internal - Recommended)

**NATS URL:** `nats://whisper-nats-prod.internal:4222`

**Advantages:**
- ✅ No external traffic
- ✅ Lower latency
- ✅ No bandwidth charges between apps
- ✅ More secure

**Setup:**
```bash
# Both apps must be in the same Fly.io organization
# NATS app uses .internal domain automatically

# From your Quarkus app
fly ssh console -C "ping whisper-nats-prod.internal"
```

**Quarkus application.properties:**
```properties
%prod.mp.messaging.connector.smallrye-nats.servers=nats://whisper-nats-prod.internal:4222
%prod.mp.messaging.connector.smallrye-nats.username=${NATS_USER}
%prod.mp.messaging.connector.smallrye-nats.password=${NATS_PASSWORD}
```

**F# fly.toml (environment variables):**
```toml
[env]
  NATS_URL = "nats://whisper-nats-prod.internal:4222"
```

#### 3. IPv6 Private Network (Advanced)

**NATS URL:** `nats://[fdaa:0:xxxx::3]:4222`

**Use case:** Direct IPv6 addressing within Fly.io

```bash
# Get the IPv6 address
fly ips private

# Use in connection string
nats://[fdaa:0:1234::3]:4222
```

## Production Recommendations

### 1. Volume Size

Choose based on message retention:

```bash
# Small workload (7 days, ~1000 msgs/day)
fly volumes create nats_jetstream --size 10 --region mia

# Medium workload (30 days, ~10000 msgs/day)
fly volumes create nats_jetstream --size 50 --region mia

# Large workload (90 days, ~100000 msgs/day)
fly volumes create nats_jetstream --size 100 --region mia
```

### 2. Machine Size

```toml
# Small (1 CPU, 256MB RAM) - ~$3/month
[vm]
  cpu_kind = "shared"
  cpus = 1
  memory_mb = 256

# Medium (2 CPUs, 512MB RAM) - ~$6/month
[vm]
  cpu_kind = "shared"
  cpus = 2
  memory_mb = 512

# Large (dedicated CPU) - ~$30/month
[vm]
  cpu_kind = "performance"
  cpus = 2
  memory_mb = 2048
```

### 3. Backups

Enable automatic volume snapshots:

```bash
# Create daily snapshots
fly volumes create nats_jetstream \
  --size 20 \
  --region mia \
  --snapshot-retention 7

# Manually snapshot before major changes
fly volumes snapshots create nats_jetstream
```

### 4. Monitoring

**Add monitoring configuration to fly.toml:**

```toml
[metrics]
  port = 8222
  path = "/varz"
```

**Monitor via curl:**
```bash
# Server info
curl http://whisper-nats-prod.fly.dev:8222/varz

# JetStream info
curl http://whisper-nats-prod.fly.dev:8222/jsz

# Connections
curl http://whisper-nats-prod.fly.dev:8222/connz
```

### 5. High Availability (Optional)

For mission-critical workloads, run 3 instances:

```bash
# Scale to 3 instances
fly scale count 3 --region mia

# NATS will form a cluster automatically
```

**Update fly.toml for clustering:**
```toml
[env]
  NATS_ARGS = "-js -sd /data -m 8222 --cluster nats://0.0.0.0:6222 --routes nats://whisper-nats-prod.internal:6222"

[[services]]
  internal_port = 6222  # Cluster port
  protocol = "tcp"
```

## Environment Variables Summary

### For NATS Server (Fly.io Secrets)

```bash
fly secrets set NATS_USER=admin
fly secrets set NATS_PASS=your-secure-password
```

### For Client Applications

**Quarkus (application.properties):**
```properties
# Set via fly secrets or environment
NATS_SERVERS=nats://whisper-nats-prod.internal:4222
NATS_USER=admin
NATS_PASSWORD=your-secure-password
```

**F# (fly.toml):**
```toml
[env]
  NATS_URL = "nats://whisper-nats-prod.internal:4222"

# Secrets (set separately)
fly secrets set NATS_USER=admin
fly secrets set NATS_PASSWORD=your-secure-password
```

## Testing the Connection

### From Your Local Machine

```bash
# Install NATS CLI
brew install nats-io/nats-tools/nats

# Test connection
nats server ping nats://whisper-nats-prod.fly.dev:4222 \
  --user admin \
  --password your-password

# Subscribe to test
nats sub file.uploads \
  --server nats://whisper-nats-prod.fly.dev:4222 \
  --user admin \
  --password your-password
```

### From Quarkus App on Fly.io

```bash
# SSH into your Quarkus app
fly ssh console -a your-quarkus-app

# Test NATS connection (internal network)
curl http://whisper-nats-prod.internal:8222/healthz
```

## Troubleshooting

### Connection Refused

```bash
# Check NATS is running
fly status -a whisper-nats-prod

# Check logs
fly logs -a whisper-nats-prod

# Restart if needed
fly apps restart whisper-nats-prod
```

### Authentication Failed

```bash
# Verify secrets are set
fly secrets list -a whisper-nats-prod

# Update if needed
fly secrets set NATS_USER=admin -a whisper-nats-prod
fly secrets set NATS_PASS=new-password -a whisper-nats-prod

# Restart to apply
fly apps restart whisper-nats-prod
```

### Volume Full

```bash
# Check volume usage
fly volumes list -a whisper-nats-prod

# Extend volume size
fly volumes extend nats_jetstream --size 50 -a whisper-nats-prod
```

## Cost Estimation

**Small Production Setup:**
- VM (shared-1x): ~$3/month
- Volume (10GB): ~$1.50/month
- Bandwidth: ~$1/month
- **Total**: ~$5.50/month

**Medium Production Setup:**
- VM (shared-2x): ~$6/month
- Volume (50GB): ~$7.50/month
- Bandwidth: ~$2/month
- **Total**: ~$15.50/month

**High Availability (3 instances):**
- VMs (3x shared-1x): ~$9/month
- Volumes (3x 10GB): ~$4.50/month
- Bandwidth: ~$2/month
- **Total**: ~$15.50/month

## Migration Checklist

- [ ] Create NATS app on Fly.io with latest image
- [ ] Create persistent volume for JetStream
- [ ] Set authentication secrets
- [ ] Deploy NATS server
- [ ] Update Quarkus application.properties with NATS URL
- [ ] Update F# consumer with NATS URL
- [ ] Test connection from both apps
- [ ] Monitor for 24 hours
- [ ] Set up automated backups
- [ ] Document credentials securely

## Summary

**Recommended Approach:**
1. ✅ Use latest `nats:2.10-alpine` image directly
2. ✅ Deploy via Fly.io with persistent volumes
3. ✅ Use Fly.io internal networking (`.internal` domain)
4. ✅ Enable authentication with secrets
5. ✅ Monitor via HTTP endpoint (port 8222)
6. ✅ Regular volume snapshots for backups

**Connection URLs:**
- External: `nats://whisper-nats-prod.fly.dev:4222`
- Internal: `nats://whisper-nats-prod.internal:4222` ← Recommended

**Your applications are ready to connect - just set the environment variables and deploy!** 🚀
