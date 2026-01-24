# Configuration Methods Clarification

## Why Environment Variables (Not Quarkus Profiles)

The application uses **environment variables** to configure S3 storage backends, not Quarkus profiles. Here's why:

### Environment Variables (What We Use) ✅

```bash
# Set environment variables
export USE_DEVSERVICES=false
export AWS_ACCESS_KEY_ID=tid_your_key
export AWS_SECRET_ACCESS_KEY=tsec_your_secret
export S3_ENDPOINT_URL=https://fly.storage.tigris.dev
export S3_BUCKET_NAME=whisper-uploads

# Run application
./mvnw quarkus:dev
```

**Benefits**:
- ✅ No hardcoded credentials in code
- ✅ Easy to switch between environments
- ✅ Works with CI/CD and containers
- ✅ Secure - credentials stay out of version control
- ✅ 12-factor app compliant

### Quarkus Profiles (What We Don't Use) ❌

```bash
# This is NOT how the app works
./mvnw quarkus:dev -Dquarkus.profile=tigris
```

**Why not**:
- ❌ Would require separate property files
- ❌ Would need credentials in files
- ❌ Less flexible for deployment
- ❌ Not container-friendly

## How application.properties Works

The `application.properties` uses environment variable substitution:

```properties
# These read from environment variables
quarkus.s3.devservices.enabled=${USE_DEVSERVICES:true}
quarkus.s3.aws.region=${AWS_REGION:us-east-1}
quarkus.s3.aws.credentials.static-provider.access-key-id=${AWS_ACCESS_KEY_ID:test}
quarkus.s3.aws.credentials.static-provider.secret-access-key=${AWS_SECRET_ACCESS_KEY:test}
%dev.quarkus.s3.endpoint-override=${S3_ENDPOINT_URL:http://localhost:4566}
bucket.name=${S3_BUCKET_NAME:whisper-uploads}
```

**How it works**:
- `${VARIABLE_NAME:default}` - Uses environment variable if set, otherwise uses default
- `%dev.` prefix - Only applies in dev mode
- No profile needed - just set environment variables

## AWS_PROFILE vs Quarkus Profile

### AWS_PROFILE (AWS CLI Configuration)

```bash
export AWS_PROFILE=tigris
```

This is an **AWS CLI** setting, not a Quarkus profile. It tells the AWS CLI which credentials to use:

```bash
# AWS CLI uses this profile
aws s3 ls --profile tigris
# OR use the environment variable
aws s3 ls  # Uses AWS_PROFILE=tigris automatically
```

This is **optional** and only used for AWS CLI commands, not by the Quarkus application.

### Quarkus Profile (Not Used)

```bash
# This is a Quarkus profile (NOT what we're doing)
./mvnw quarkus:dev -Dquarkus.profile=custom
```

Quarkus profiles would require:
- Separate `application-custom.properties` file
- Profile-specific configuration
- Not ideal for credentials

## Configuration Comparison

### Our Approach (Environment Variables)

```bash
# .env.tigris file
export AWS_ACCESS_KEY_ID=tid_xxx
export AWS_SECRET_ACCESS_KEY=tsec_xxx
export AWS_REGION=auto
export S3_ENDPOINT_URL=https://fly.storage.tigris.dev
export S3_BUCKET_NAME=whisper-uploads
export USE_DEVSERVICES=false

# Load and run
source .env.tigris
./mvnw quarkus:dev
```

### If We Used Profiles (We Don't)

```properties
# application-tigris.properties (NOT USED)
quarkus.s3.devservices.enabled=false
quarkus.s3.endpoint-override=https://fly.storage.tigris.dev
quarkus.s3.aws.credentials.static-provider.access-key-id=tid_xxx  # BAD!
quarkus.s3.aws.credentials.static-provider.secret-access-key=tsec_xxx  # BAD!
```

```bash
# Would run like this (NOT RECOMMENDED)
./mvnw quarkus:dev -Dquarkus.profile=tigris
```

## Real-World Usage

### Development

```bash
# LocalStack (DevServices)
./mvnw quarkus:dev

# LocalStack (Docker Compose)
docker-compose up -d
USE_DEVSERVICES=false ./mvnw quarkus:dev

# Tigris
source .env.tigris
./mvnw quarkus:dev
```

### Production (Docker)

```dockerfile
ENV USE_DEVSERVICES=false
ENV AWS_ACCESS_KEY_ID=${TIGRIS_ACCESS_KEY_ID}
ENV AWS_SECRET_ACCESS_KEY=${TIGRIS_SECRET_ACCESS_KEY}
ENV S3_ENDPOINT_URL=https://fly.storage.tigris.dev
ENV S3_BUCKET_NAME=whisper-uploads
```

### Production (Kubernetes)

```yaml
env:
- name: USE_DEVSERVICES
  value: "false"
- name: AWS_ACCESS_KEY_ID
  valueFrom:
    secretKeyRef:
      name: tigris-creds
      key: access-key-id
- name: AWS_SECRET_ACCESS_KEY
  valueFrom:
    secretKeyRef:
      name: tigris-creds
      key: secret-access-key
- name: S3_ENDPOINT_URL
  value: "https://fly.storage.tigris.dev"
```

## Summary

| What | Used? | Purpose |
|------|-------|---------|
| **Environment Variables** | ✅ Yes | Configure S3 backend |
| **`.env.tigris` file** | ✅ Yes | Store Tigris env vars |
| **`source .env.tigris`** | ✅ Yes | Load env vars |
| **Quarkus Profiles** | ❌ No | Not used in this app |
| **`-Dquarkus.profile=`** | ❌ No | Not needed |
| **`AWS_PROFILE`** | ⚠️ Optional | Only for AWS CLI |

## Correct Commands

### ✅ Correct - Using Environment Variables

```bash
# Setup
./setup-tigris.sh

# Load configuration
source .env.tigris

# Run
./mvnw quarkus:dev
```

### ❌ Incorrect - Using Profiles (Won't Work)

```bash
# This won't work - we don't have profile files
./mvnw quarkus:dev -Dquarkus.profile=tigris
```

## Why This Design?

1. **Security**: Credentials never in code or config files
2. **Flexibility**: Easy to switch environments
3. **Standard**: Follows 12-factor app principles
4. **Container-friendly**: Works perfectly with Docker/Kubernetes
5. **CI/CD ready**: Easy to inject secrets
6. **Simple**: One method for all environments

The application is designed to work with environment variables only - no profiles needed!
