# User Guide

## Quick Start

```bash
# First time - build everything
./scripts/rebuild_all.sh

# After reboot - just restart
./scripts/restart-cluster.sh
```

Wait 30 seconds, then:
- API: http://localhost:8080/swagger-ui.html
- ZooKeeper: http://localhost:9000 (connect to `zookeeper:2181`)

## Basic Commands

### Create Database
```bash
curl -X POST http://localhost:8080/api/v1/databases \
  -H "Content-Type: application/json" \
  -d '{"id": "my-db", "dimension": 384}'
```

### Add Vector
```bash
curl -X POST http://localhost:8080/api/vectors/add \
  -H "Content-Type: application/json" \
  -d '{"vector": [1.0, 2.0, 3.0], "data": "test", "dbId": "my-db"}'
```
Returns ID as **string**: `"8450104734876530697"`

### Search Vectors
```bash
curl -X POST http://localhost:8080/api/v1/vectors/search \
  -H "Content-Type: application/json" \
  -d '{"vector": [1.0, 2.0, 3.0], "k": 5, "dbId": "my-db"}'
```

### Delete Vector
```bash
curl -X DELETE "http://localhost:8080/api/vectors/8450104734876530697?dbId=my-db"
```

## Health Checks

```bash
curl http://localhost:8080/api/health           # Main
curl http://localhost:8081/api/v1/storage/health # Shard 1
curl http://localhost:8082/api/v1/storage/health # Shard 2
```

## Common Issues

| Problem | Fix |
|---------|-----|
| "No shards available" | `./scripts/restart-cluster.sh` |
| "Dimension must be set" | Restart storage: `docker-compose -f docker-compose.sharded.yml restart vector-db-storage-1 vector-db-storage-2` |
| Delete fails | Use ID as **string**, not number |
| ZK won't connect | `docker ps | grep zookeeper` then `echo ruok | nc localhost 2181` |

## Tips

**See shard distribution:**
```bash
curl http://localhost:8081/api/v1/storage/databases/my-db | jq '.vectorCount'
curl http://localhost:8082/api/v1/storage/databases/my-db | jq '.vectorCount'
```

**Watch logs:**
```bash
docker logs -f vector-db-main
```

**Stop safely (keeps data):**
```bash
docker-compose -f docker-compose.sharded.yml down
```

**Wipe everything:**
```bash
docker-compose -f docker-compose.sharded.yml down -v
./scripts/rebuild_all.sh
```

## Understanding

**Ports:**
- 8080 = Main API
- 8081, 8082 = Storage shards
- 9000 = ZooKeeper UI

**How it works:**
1. Add vector → Main generates ID
2. ID hashed → Determines shard
3. Vector saved to that shard
4. Search queries ALL shards

**Data persistence:**
- Vectors: RocksDB (persistent)
- Index: HNSW in RAM (rebuilt on restart)
- Config: ZooKeeper (persistent)