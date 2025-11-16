#!/usr/bin/env bash

set -euo pipefail

# Determine project root (one level up from this script directory)
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

echo "==> Full rebuild and deployment starting..."
echo "==> This will clean everything and force a complete rebuild"
echo ""

echo "==> Stopping existing containers..."
docker-compose down || true
docker-compose -f docker-compose.sharded.yml down || true
docker-compose -f docker-compose.sharded.yml --profile with-shard3 down || true

echo ""
echo "==> Force-stopping any remaining containers..."
docker stop vector-db-storage-3 2>/dev/null || true
docker rm vector-db-storage-3 2>/dev/null || true

echo ""
echo "==> Removing volumes to ensure clean state..."
docker volume rm vectordb_zookeeper-data vectordb_zookeeper-logs vectordb_storage-data-1 vectordb_storage-data-2 vectordb_storage-data-3 2>/dev/null || true
docker volume rm vectordb_vector-db-zookeeper-data vectordb_vector-db-zookeeper-datalog vectordb_vector-db-data-1 vectordb_vector-db-data-2 vectordb_vector-db-data-3 2>/dev/null || true
docker volume rm vectordb_vector-db-logs-1 vectordb_vector-db-logs-2 vectordb_vector-db-logs-3 2>/dev/null || true

echo ""
echo "==> Removing old Docker images to force rebuild..."
docker rmi vector-db-main:latest 2>/dev/null || echo "  (main image not found, skipping)"
docker rmi vector-db-storage:latest 2>/dev/null || echo "  (storage image not found, skipping)"

echo ""
echo "==> Cleaning Gradle build..."
./gradlew clean

echo ""
echo "==> Building project (jars)..."
./gradlew :main-module:build -x test
./gradlew :storage-module:build -x test

echo ""
echo "==> Building Docker images (no cache)..."
echo "  Building storage module..."
docker build --no-cache -t vector-db-storage:latest storage-module/

echo "  Building main module..."
docker build --no-cache -t vector-db-main:latest main-module/

echo ""
echo "==> Starting sharded cluster with fresh images..."
docker-compose -f docker-compose.sharded.yml up -d

echo ""
echo "==> Waiting for services to be ready (10s)..."
sleep 10

echo ""
echo "==> Initializing cluster configuration in ZooKeeper..."
./scripts/init-cluster-config.sh

echo ""
echo "==> Done! Running containers:"
docker-compose -f docker-compose.sharded.yml ps

echo ""
echo "==> Service URLs:"
echo "  Main API:        http://localhost:8080/swagger-ui.html"
echo "  ZooKeeper UI:    http://localhost:9000"
echo "  Storage Shard 1: http://localhost:8081/api/v1/storage/health"
echo "  Storage Shard 2: http://localhost:8082/api/v1/storage/health"
echo ""
echo "==> To view logs:"
echo "  docker logs -f vector-db-main"
echo "  docker logs -f vector-db-storage-1"
echo "  docker logs -f vector-db-storage-2"
echo ""
echo "==> Full rebuild complete!"
