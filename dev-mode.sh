#!/bin/bash

# Quarks-Tigris Development Mode Switcher
# This script helps you switch between DevServices and Docker Compose modes

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║       Quarks-Tigris Development Mode Switcher             ║${NC}"
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

show_menu() {
    echo -e "${YELLOW}Select Development Mode:${NC}"
    echo ""
    echo "  1) DevServices Mode (Default - Testcontainers)"
    echo "     • Automatic container management"
    echo "     • Clean state on each run"
    echo "     • Zero configuration"
    echo ""
    echo "  2) Docker Compose Mode (Persistent)"
    echo "     • Manual container management"
    echo "     • Persistent data between runs"
    echo "     • Full service control"
    echo ""
    echo "  3) Status - Check current mode and services"
    echo ""
    echo "  4) Stop All Services"
    echo ""
    echo "  5) Clean Data (Remove volumes and data)"
    echo ""
    echo "  6) Exit"
    echo ""
}

start_devservices() {
    print_header
    print_info "Starting in DevServices Mode (Testcontainers)"
    echo ""

    # Stop Docker Compose if running
    if docker-compose ps | grep -q "Up"; then
        print_warning "Stopping Docker Compose services..."
        docker-compose down
    fi

    # Unset environment variables
    unset USE_DEVSERVICES
    unset S3_ENDPOINT_URL

    print_success "DevServices mode configured"
    print_info "Containers will be managed automatically by Quarkus"
    echo ""
    print_info "Starting Quarkus..."
    echo ""

    ./mvnw quarkus:dev
}

start_docker_compose() {
    print_header
    print_info "Starting in Docker Compose Mode"
    echo ""

    # Check if docker-compose is available
    if ! command -v docker-compose &> /dev/null; then
        print_error "docker-compose is not installed"
        exit 1
    fi

    # Start Docker Compose services
    print_info "Starting Docker Compose services..."
    docker-compose up -d

    # Wait for services to be healthy
    print_info "Waiting for services to be ready..."
    sleep 5

    # Check service status
    if docker-compose ps | grep -q "healthy"; then
        print_success "Docker Compose services are running"
    else
        print_warning "Services may not be fully ready yet"
    fi

    echo ""
    docker-compose ps
    echo ""

    # Export environment variables
    export USE_DEVSERVICES=false
    export S3_ENDPOINT_URL=http://localhost:4566
    export S3_BUCKET_NAME=whisper-uploads

    print_success "Environment variables configured:"
    echo "  • USE_DEVSERVICES=false"
    echo "  • S3_ENDPOINT_URL=http://localhost:4566"
    echo "  • S3_BUCKET_NAME=whisper-uploads"
    echo ""

    print_info "Starting Quarkus with Docker Compose backend..."
    echo ""

    USE_DEVSERVICES=false S3_ENDPOINT_URL=http://localhost:4566 S3_BUCKET_NAME=whisper-uploads ./mvnw quarkus:dev
}

show_status() {
    print_header
    print_info "System Status"
    echo ""

    # Check if DevServices would be used
    if [ -z "$USE_DEVSERVICES" ] || [ "$USE_DEVSERVICES" = "true" ]; then
        print_info "Current Mode: ${GREEN}DevServices (Testcontainers)${NC}"
    else
        print_info "Current Mode: ${GREEN}Docker Compose${NC}"
    fi
    echo ""

    # Check Docker Compose services
    print_info "Docker Compose Services:"
    echo ""
    if docker-compose ps | grep -q "Up"; then
        docker-compose ps
        echo ""
        print_success "Docker Compose services are running"

        # Check LocalStack health
        if curl -s http://localhost:4566/_localstack/health > /dev/null 2>&1; then
            print_success "LocalStack is healthy (http://localhost:4566)"
        else
            print_warning "LocalStack may not be ready yet"
        fi

        # Check NATS
        if curl -s http://localhost:8222/healthz > /dev/null 2>&1; then
            print_success "NATS is healthy (http://localhost:8222)"
        else
            print_warning "NATS may not be ready yet"
        fi
    else
        print_warning "No Docker Compose services running"
        echo "  Run: docker-compose up -d"
    fi
    echo ""

    # Check Quarkus
    if curl -s http://localhost:8080/whisper > /dev/null 2>&1; then
        print_success "Quarkus application is running (http://localhost:8080/whisper)"
    else
        print_warning "Quarkus application is not running"
        echo "  Run: ./mvnw quarkus:dev"
    fi
    echo ""

    # Show useful URLs
    print_info "Useful URLs:"
    echo "  • Application:     http://localhost:8080/whisper"
    echo "  • Swagger UI:      http://localhost:8080/whisper/swagger-ui"
    echo "  • Dev UI:          http://localhost:8080/q/dev"
    echo "  • LocalStack:      http://localhost:4566"
    echo "  • NATS Management: http://localhost:8222"
    echo ""
}

stop_services() {
    print_header
    print_info "Stopping all services..."
    echo ""

    # Stop Docker Compose
    if docker-compose ps | grep -q "Up"; then
        print_info "Stopping Docker Compose services..."
        docker-compose down
        print_success "Docker Compose services stopped"
    else
        print_info "No Docker Compose services to stop"
    fi
    echo ""

    print_warning "Note: Quarkus (if running) must be stopped manually (Ctrl+C)"
    echo ""
}

clean_data() {
    print_header
    print_warning "This will remove ALL persistent data!"
    echo ""
    read -p "Are you sure? (yes/no): " -r
    echo ""

    if [[ $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
        print_info "Stopping services..."
        docker-compose down -v

        print_info "Removing data directories..."
        rm -rf localstack-data nats-data

        print_success "All data cleaned"
        echo ""
        print_info "Next start will be with a fresh state"
    else
        print_info "Cancelled"
    fi
    echo ""
}

# Main menu loop
while true; do
    print_header
    show_menu

    read -p "Choose option (1-6): " choice
    echo ""

    case $choice in
        1)
            start_devservices
            break
            ;;
        2)
            start_docker_compose
            break
            ;;
        3)
            show_status
            echo ""
            read -p "Press Enter to continue..."
            clear
            ;;
        4)
            stop_services
            read -p "Press Enter to continue..."
            clear
            ;;
        5)
            clean_data
            read -p "Press Enter to continue..."
            clear
            ;;
        6)
            print_info "Goodbye!"
            exit 0
            ;;
        *)
            print_error "Invalid option. Please choose 1-6"
            sleep 2
            clear
            ;;
    esac
done
