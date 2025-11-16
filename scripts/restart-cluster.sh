#!/usr/bin/env bash

set -euo pipefail

# Determine project root (one level up from this script directory)
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

echo "==> Restarting sharded cluster..."
echo ""

echo "==> Stopping containers..."
docker-compose -f docker-compose.sharded.yml down

echo ""
echo "==> Starting containers (with persistent data)..."
docker-compose -f docker-compose.sharded.yml up -d

echo ""
echo "==> Waiting for services to be ready (10s)..."
sleep 10

echo ""
echo "==> Checking cluster configuration in ZooKeeper..."
if docker exec vector-db-zookeeper zkCli.sh get /vectordb/cluster/config >/dev/null 2>&1; then
  echo "✓ Cluster configuration exists (persistent volumes working)"
  docker exec vector-db-zookeeper zkCli.sh get /vectordb/cluster/config 2>/dev/null | grep -A 100 "^{" | python3 -m json.tool 2>/dev/null || true
else
  echo "⚠ Cluster configuration missing! Reinitializing..."
  ./scripts/init-cluster-config.sh
fi

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
echo "==> Cluster restarted successfully!"
echo "   All data and configuration should be preserved."
echo ""