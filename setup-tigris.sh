#!/bin/bash

# Tigris Object Storage Setup Helper
# This script helps you configure Tigris credentials

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║        Tigris Object Storage Configuration                ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_header

echo "This script will help you configure Tigris Object Storage."
echo ""

# Check if .env.tigris already exists
if [ -f .env.tigris ]; then
    print_warning ".env.tigris already exists"
    read -p "Do you want to overwrite it? (yes/no): " -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        print_info "Keeping existing .env.tigris file"
        print_info "To load it, run: source .env.tigris"
        exit 0
    fi
fi

echo "Please provide your Tigris credentials:"
echo ""
echo "Get them from: ${BLUE}https://console.tigris.dev${NC}"
echo "  → Access Keys → Create Access Key"
echo ""

# Prompt for credentials
read -p "Tigris Access Key ID (tid_...): " TIGRIS_KEY
echo ""
read -sp "Tigris Secret Access Key (tsec_...): " TIGRIS_SECRET
echo ""
echo ""
read -p "Tigris Bucket Name (e.g., whisper-uploads): " TIGRIS_BUCKET
echo ""

# Validate inputs
if [ -z "$TIGRIS_KEY" ] || [ -z "$TIGRIS_SECRET" ] || [ -z "$TIGRIS_BUCKET" ]; then
    print_error "All fields are required"
    exit 1
fi

if [[ ! $TIGRIS_KEY == tid_* ]]; then
    print_warning "Access Key ID should start with 'tid_'"
fi

if [[ ! $TIGRIS_SECRET == tsec_* ]]; then
    print_warning "Secret Access Key should start with 'tsec_'"
fi

# Ask for region preference
echo "Select Tigris endpoint:"
echo "  1) Auto (Global Edge - Recommended)"
echo "  2) US East"
echo "  3) EU West"
echo "  4) Asia Pacific"
echo ""
read -p "Choose (1-4): " REGION_CHOICE

case $REGION_CHOICE in
    1)
        ENDPOINT="https://fly.storage.tigris.dev"
        REGION="auto"
        ;;
    2)
        ENDPOINT="https://us-east-1.storage.tigris.dev"
        REGION="us-east-1"
        ;;
    3)
        ENDPOINT="https://eu-west-1.storage.tigris.dev"
        REGION="eu-west-1"
        ;;
    4)
        ENDPOINT="https://ap-southeast-1.storage.tigris.dev"
        REGION="ap-southeast-1"
        ;;
    *)
        print_info "Invalid choice, using Auto (Global Edge)"
        ENDPOINT="https://fly.storage.tigris.dev"
        REGION="auto"
        ;;
esac

echo ""
print_info "Creating .env.tigris file..."

# Create .env.tigris
cat > .env.tigris << EOF
# Tigris Object Storage Configuration
# Generated on $(date)

# Tigris Credentials
export AWS_ACCESS_KEY_ID=${TIGRIS_KEY}
export AWS_SECRET_ACCESS_KEY=${TIGRIS_SECRET}

# Tigris Configuration
export AWS_REGION=${REGION}
export S3_ENDPOINT_URL=${ENDPOINT}
export S3_BUCKET_NAME=${TIGRIS_BUCKET}
export S3_PATH_STYLE_ACCESS=true
export USE_DEVSERVICES=false

# Optional: Set these for AWS CLI profile
export AWS_PROFILE=tigris
EOF

print_success ".env.tigris created successfully"
echo ""

# Test connection
print_info "Testing connection to Tigris..."
echo ""

# Source the file
source .env.tigris

# Test with curl
if curl -s -I ${ENDPOINT} > /dev/null 2>&1; then
    print_success "Tigris endpoint is reachable"
else
    print_warning "Could not reach Tigris endpoint"
    print_info "Check your internet connection"
fi

# Test with AWS CLI if available
if command -v aws &> /dev/null; then
    print_info "Testing AWS CLI connection..."

    if aws s3 ls s3://${TIGRIS_BUCKET} --endpoint-url=${ENDPOINT} --region=${REGION} > /dev/null 2>&1; then
        print_success "AWS CLI can access bucket: ${TIGRIS_BUCKET}"
    else
        print_warning "Could not list bucket with AWS CLI"
        print_info "Verify:"
        print_info "  1. Bucket exists in Tigris Console"
        print_info "  2. Credentials are correct"
        print_info "  3. Bucket name matches"
    fi
else
    print_info "AWS CLI not installed (optional)"
    print_info "Install with: brew install awscli  (macOS)"
    print_info "          or: pip install awscli   (Linux)"
fi

echo ""
print_success "Tigris configuration complete!"
echo ""
print_info "Next steps:"
echo ""
echo "  1. Load configuration:"
echo "     ${GREEN}source .env.tigris${NC}"
echo ""
echo "  2. Run the application:"
echo "     ${GREEN}./mvnw quarkus:dev${NC}"
echo ""
echo "  3. Access at:"
echo "     ${GREEN}http://localhost:8080/whisper${NC}"
echo ""

print_info "Configuration details:"
echo "  • Endpoint: ${ENDPOINT}"
echo "  • Region: ${REGION}"
echo "  • Bucket: ${TIGRIS_BUCKET}"
echo ""

print_info "To verify your setup:"
echo "  • Check Tigris Console: ${BLUE}https://console.tigris.dev${NC}"
echo "  • Read guide: ${BLUE}TIGRIS_SETUP.md${NC}"
echo ""
