#!/bin/bash

# Быстрый тест для проверки работы кластера
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

print_step() {
    echo -e "${BLUE}==== $1 ====${NC}"
}

print_success() {
    echo -e "${GREEN} $1${NC}"
}

print_error() {
    echo -e "${RED} $1${NC}"
}

print_info() {
    echo -e "${YELLOW}  $1${NC}"
}

docker_compose() {
    if docker compose version &> /dev/null 2>&1; then
        docker compose -f "$PROJECT_DIR/docker-compose.sharded.yml" "$@"
    else
        docker-compose -f "$PROJECT_DIR/docker-compose.sharded.yml" "$@"
    fi
}

check_health() {
    local url=$1
    local name=$2
    local health_endpoint=""
    if [[ "$url" == *"8080"* ]]; then
        health_endpoint="$url/api/health"
    else
        health_endpoint="$url/api/v1/storage/health"
    fi

    if curl -s -f "$health_endpoint" > /dev/null 2>&1; then
        print_success "$name доступен"
        return 0
    else
        print_error "$name недоступен"
        return 1
    fi
}

print_step "Быстрый тест кластера"

# Проверяем запущенные контейнеры
if docker ps | grep -q "vector-db-main"; then
    print_success "Кластер уже запущен"
else
    print_info "Запускаю кластер..."
    docker_compose up -d
    sleep 30
fi

# Проверяем доступность
check_health "http://localhost:8080" "Main API"
check_health "http://localhost:8081" "Storage 1"
check_health "http://localhost:8082" "Storage 2"
check_health "http://localhost:8083" "Storage 3"

print_success "Тест завершен!"
