# Tigris Object Storage Setup Guide

## Overview

This guide explains how to configure the application to use [Tigris Object Storage](https://www.tigrisdata.com/) instead of LocalStack for S3-compatible storage.

## What is Tigris?

Tigris is a globally distributed S3-compatible object storage platform with:
- ✅ Free tier available
- ✅ Pay-as-you-go pricing
- ✅ Global CDN distribution
- ✅ S3-compatible API
- ✅ No egress fees
- ✅ Fast performance

## Prerequisites

### 1. Create Tigris Account

1. Go to https://console.tigris.dev
2. Sign up for a free account
3. Verify your email

### 2. Create Access Credentials

1. In Tigris Console, go to **Access Keys**
2. Click **Create Access Key**
3. Save your credentials:
   - **Access Key ID**: `tid_xxxxxxxxxxxxx`
   - **Secret Access Key**: `tsec_xxxxxxxxxxxxx`

### 3. Create a Bucket

1. In Tigris Console, go to **Buckets**
2. Click **Create Bucket**
3. Enter bucket name (e.g., `whisper-uploads`)
4. Select region (e.g., `us-east-1` or `auto` for global)
5. Click **Create**

## Configuration Methods

### Method 1: Environment Variables (Recommended)

Create a `.env.tigris` file:

```bash
# Tigris Credentials
export AWS_ACCESS_KEY_ID=tid_your_access_key_id_here
export AWS_SECRET_ACCESS_KEY=tsec_your_secret_access_key_here

# Tigris Configuration
export AWS_REGION=auto
export S3_ENDPOINT_URL=https://fly.storage.tigris.dev
export S3_BUCKET_NAME=whisper-uploads
export S3_PATH_STYLE_ACCESS=true
export USE_DEVSERVICES=false
```

Load and run:

```bash
# Load environment variables
source .env.tigris

# Run Quarkus
./mvnw quarkus:dev
```

### Method 2: Command Line

```bash
USE_DEVSERVICES=false \
AWS_ACCESS_KEY_ID=tid_your_key \
AWS_SECRET_ACCESS_KEY=tsec_your_secret \
AWS_REGION=auto \
S3_ENDPOINT_URL=https://fly.storage.tigris.dev \
S3_BUCKET_NAME=whisper-uploads \
S3_PATH_STYLE_ACCESS=true \
./mvnw quarkus:dev
```

### Method 3: Direct in application.properties (Not Recommended)

**Note**: This method requires hardcoding credentials, which is NOT recommended for security reasons. Use environment variables instead.

If you must use this approach for testing, you would need to modify `application.properties` directly and set:

```properties
# NOT RECOMMENDED - Use environment variables instead
quarkus.s3.devservices.enabled=false
quarkus.s3.endpoint-override=https://fly.storage.tigris.dev
quarkus.s3.aws.region=auto
# etc...
```

**Security Warning**: Never commit credentials to version control!

## Step-by-Step Setup

### Step 1: Get Tigris Credentials

```bash
# From Tigris Console
ACCESS_KEY_ID=tid_xxxxxxxxxxxxx
SECRET_ACCESS_KEY=tsec_xxxxxxxxxxxxx
BUCKET_NAME=whisper-uploads
```

### Step 2: Configure Environment

```bash
# Create .env.tigris file
cat > .env.tigris << 'EOF'
export AWS_ACCESS_KEY_ID=tid_your_actual_key
export AWS_SECRET_ACCESS_KEY=tsec_your_actual_secret
export AWS_REGION=auto
export S3_ENDPOINT_URL=https://fly.storage.tigris.dev
export S3_BUCKET_NAME=whisper-uploads
export S3_PATH_STYLE_ACCESS=true
export USE_DEVSERVICES=false
EOF

# Load variables
source .env.tigris
```

### Step 3: Verify Configuration

```bash
# Test connection with AWS CLI
aws s3 ls s3://${S3_BUCKET_NAME} \
  --endpoint-url=${S3_ENDPOINT_URL} \
  --region=${AWS_REGION}
```

### Step 4: Run Application

```bash
./mvnw quarkus:dev
```

### Step 5: Test Upload

1. Navigate to http://localhost:8080/whisper
2. Enter passphrase: `your-secret-passphrase`
3. Upload an MP3 file
4. Verify in Tigris Console

## Configuration Reference

### Required Environment Variables

| Variable | Value | Description |
|----------|-------|-------------|
| `USE_DEVSERVICES` | `false` | Disable testcontainers |
| `AWS_ACCESS_KEY_ID` | `tid_*` | Tigris access key ID |
| `AWS_SECRET_ACCESS_KEY` | `tsec_*` | Tigris secret key |
| `AWS_REGION` | `auto` | Tigris region (or specific region) |
| `S3_ENDPOINT_URL` | `https://fly.storage.tigris.dev` | Tigris endpoint |
| `S3_BUCKET_NAME` | Your bucket name | Existing Tigris bucket |
| `S3_PATH_STYLE_ACCESS` | `true` | Use path-style URLs |

### Tigris Endpoints

Tigris provides multiple endpoints:

- **Global Edge**: `https://fly.storage.tigris.dev`
- **US East**: `https://us-east-1.storage.tigris.dev`
- **EU West**: `https://eu-west-1.storage.tigris.dev`
- **Asia Pacific**: `https://ap-southeast-1.storage.tigris.dev`

**Recommendation**: Use `https://fly.storage.tigris.dev` with `AWS_REGION=auto` for automatic routing.

## AWS CLI with Tigris

### Configuration

```bash
# Configure AWS CLI profile for Tigris
aws configure set aws_access_key_id tid_your_key --profile tigris
aws configure set aws_secret_access_key tsec_your_secret --profile tigris
aws configure set region auto --profile tigris
```

### Common Commands

```bash
# List buckets
aws s3 ls \
  --endpoint-url=https://fly.storage.tigris.dev \
  --profile tigris

# List objects in bucket
aws s3 ls s3://whisper-uploads \
  --endpoint-url=https://fly.storage.tigris.dev \
  --profile tigris \
  --recursive

# Upload file
aws s3 cp test.mp3 s3://whisper-uploads/test/ \
  --endpoint-url=https://fly.storage.tigris.dev \
  --profile tigris

# Download file
aws s3 cp s3://whisper-uploads/uploads/email@test.com/file.mp3 ./ \
  --endpoint-url=https://fly.storage.tigris.dev \
  --profile tigris

# Delete file
aws s3 rm s3://whisper-uploads/uploads/email@test.com/file.mp3 \
  --endpoint-url=https://fly.storage.tigris.dev \
  --profile tigris
```

### Create Alias for Convenience

```bash
# Add to ~/.bashrc or ~/.zshrc
alias tigris-aws='aws --endpoint-url=https://fly.storage.tigris.dev --profile tigris'

# Usage
tigris-aws s3 ls s3://whisper-uploads
```

## Switching Between LocalStack and Tigris

### Use LocalStack (Default)

```bash
# Stop Quarkus (Ctrl+C)

# Use DevServices (automatic)
./mvnw quarkus:dev

# Or use Docker Compose
docker-compose up -d
USE_DEVSERVICES=false ./mvnw quarkus:dev
```

### Use Tigris

```bash
# Stop Quarkus (Ctrl+C)

# Load Tigris config
source .env.tigris

# Run
./mvnw quarkus:dev
```

## Interactive Script Support

Update the `dev-mode.sh` script to support Tigris:

```bash
# Add this option to dev-mode.sh menu
show_menu() {
    # ...existing options...
    echo "  3) Tigris Mode (Cloud Object Storage)"
    echo "     • S3-compatible cloud storage"
    echo "     • Global distribution"
    echo "     • Production-ready"
    # ...
}

start_tigris() {
    print_header
    print_info "Starting in Tigris Mode"
    
    if [ ! -f .env.tigris ]; then
        print_error ".env.tigris file not found"
        print_info "Create .env.tigris with your Tigris credentials"
        exit 1
    fi
    
    source .env.tigris
    
    print_success "Tigris configuration loaded"
    print_info "Starting Quarkus with Tigris backend..."
    
    ./mvnw quarkus:dev
}
```

## Production Deployment

For production, use system environment variables or secrets management:

### Docker/Container

```dockerfile
# Dockerfile
ENV AWS_ACCESS_KEY_ID=${TIGRIS_ACCESS_KEY_ID}
ENV AWS_SECRET_ACCESS_KEY=${TIGRIS_SECRET_ACCESS_KEY}
ENV AWS_REGION=auto
ENV S3_ENDPOINT_URL=https://fly.storage.tigris.dev
ENV S3_BUCKET_NAME=whisper-uploads
ENV S3_PATH_STYLE_ACCESS=true
ENV USE_DEVSERVICES=false
```

### Kubernetes

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: tigris-credentials
type: Opaque
stringData:
  access-key-id: tid_your_key
  secret-access-key: tsec_your_secret

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: whisper-api
spec:
  template:
    spec:
      containers:
      - name: app
        env:
        - name: AWS_ACCESS_KEY_ID
          valueFrom:
            secretKeyRef:
              name: tigris-credentials
              key: access-key-id
        - name: AWS_SECRET_ACCESS_KEY
          valueFrom:
            secretKeyRef:
              name: tigris-credentials
              key: secret-access-key
        - name: AWS_REGION
          value: "auto"
        - name: S3_ENDPOINT_URL
          value: "https://fly.storage.tigris.dev"
        - name: S3_BUCKET_NAME
          value: "whisper-uploads"
        - name: USE_DEVSERVICES
          value: "false"
```

### Fly.io

```toml
# fly.toml
[env]
  AWS_REGION = "auto"
  S3_ENDPOINT_URL = "https://fly.storage.tigris.dev"
  S3_BUCKET_NAME = "whisper-uploads"
  USE_DEVSERVICES = "false"

[secrets]
  # Set with: fly secrets set AWS_ACCESS_KEY_ID=tid_...
  # AWS_ACCESS_KEY_ID
  # AWS_SECRET_ACCESS_KEY
```

## Troubleshooting

### Issue: Connection Timeout

**Error**: `Unable to execute HTTP request: Connect to fly.storage.tigris.dev:443 timed out`

**Solution**: Check network/firewall settings, verify endpoint URL

```bash
# Test connectivity
curl -I https://fly.storage.tigris.dev
```

### Issue: Invalid Credentials

**Error**: `The AWS Access Key Id you provided does not exist in our records`

**Solution**: Verify credentials are correct

```bash
# Check credentials
echo $AWS_ACCESS_KEY_ID
echo $AWS_SECRET_ACCESS_KEY

# Test with AWS CLI
aws s3 ls --endpoint-url=https://fly.storage.tigris.dev
```

### Issue: Bucket Not Found

**Error**: `The specified bucket does not exist`

**Solution**: Create bucket in Tigris Console or via CLI

```bash
# Create bucket via AWS CLI
aws s3 mb s3://whisper-uploads \
  --endpoint-url=https://fly.storage.tigris.dev \
  --region=auto
```

### Issue: Path Style Access

**Error**: `S3 requests fail with "PermanentRedirect"`

**Solution**: Enable path-style access

```bash
export S3_PATH_STYLE_ACCESS=true
```

### Issue: Wrong Region

**Error**: `The bucket is in this region: auto. Please use this region to retry the request`

**Solution**: Use `auto` region

```bash
export AWS_REGION=auto
```

## Verification Checklist

- [ ] Tigris account created
- [ ] Access credentials generated
- [ ] Bucket created in Tigris Console
- [ ] `.env.tigris` file created with credentials
- [ ] Environment variables loaded (`source .env.tigris`)
- [ ] AWS CLI test successful
- [ ] Quarkus starts without errors
- [ ] File upload works via web UI
- [ ] File visible in Tigris Console

## Cost Considerations

### Tigris Pricing (as of Jan 2024)

- **Free Tier**: 25 GB storage, 250 GB bandwidth/month
- **Storage**: $0.02 per GB/month after free tier
- **Bandwidth**: $0.09 per GB after free tier
- **Operations**: Free (no per-request charges)
- **No egress fees**

### Estimated Costs for MP3 Upload App

Assuming:
- Average MP3 size: 5 MB
- 1000 uploads/month
- Total storage: 5 GB

**Monthly Cost**:
- Storage: Free (under 25 GB)
- Bandwidth: Free (under 250 GB)
- **Total: $0.00/month** (within free tier)

## Best Practices

### Security

1. **Never commit credentials** to Git
   - Use `.env.tigris` (already in .gitignore)
   - Use secrets management in production

2. **Rotate credentials regularly**
   - Generate new keys every 90 days
   - Revoke old keys in Tigris Console

3. **Use bucket policies**
   - Restrict public access
   - Set appropriate IAM policies

### Performance

1. **Use CDN edge endpoint** (`fly.storage.tigris.dev`)
2. **Enable compression** for large files
3. **Set appropriate cache headers**
4. **Use multipart upload** for files > 5MB (already configured)

### Monitoring

1. **Check Tigris Console** for usage metrics
2. **Monitor upload success rates**
3. **Set up alerts** for quota limits
4. **Track storage growth**

## Migration from LocalStack to Tigris

### Step 1: Export Data from LocalStack

```bash
# Create export directory
mkdir -p tigris-migration

# Download all files from LocalStack
aws s3 sync s3://whisper-uploads ./tigris-migration/ \
  --endpoint-url=http://localhost:4566
```

### Step 2: Upload to Tigris

```bash
# Configure Tigris
source .env.tigris

# Upload to Tigris
aws s3 sync ./tigris-migration/ s3://whisper-uploads \
  --endpoint-url=https://fly.storage.tigris.dev
```

### Step 3: Verify Migration

```bash
# List files in Tigris
aws s3 ls s3://whisper-uploads --recursive \
  --endpoint-url=https://fly.storage.tigris.dev
```

### Step 4: Update Application

```bash
# Switch to Tigris
source .env.tigris
./mvnw quarkus:dev
```

## Summary

### Quick Start with Tigris

```bash
# 1. Create .env.tigris with your credentials
cat > .env.tigris << 'EOF'
export AWS_ACCESS_KEY_ID=tid_your_key
export AWS_SECRET_ACCESS_KEY=tsec_your_secret
export AWS_REGION=auto
export S3_ENDPOINT_URL=https://fly.storage.tigris.dev
export S3_BUCKET_NAME=whisper-uploads
export S3_PATH_STYLE_ACCESS=true
export USE_DEVSERVICES=false
EOF

# 2. Load config
source .env.tigris

# 3. Run
./mvnw quarkus:dev

# 4. Access
open http://localhost:8080/whisper
```

### Benefits of Tigris

✅ **Production-ready** - Real cloud storage  
✅ **Global CDN** - Fast worldwide access  
✅ **Free tier** - 25 GB storage included  
✅ **No egress fees** - Unlimited downloads  
✅ **S3-compatible** - Works with existing code  
✅ **Simple pricing** - Pay-as-you-go  

You're now ready to use Tigris Object Storage! 🚀
