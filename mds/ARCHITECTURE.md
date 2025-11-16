# Architecture

## System Design

```
Client ─→ Main API (8080) ─→ Storage Shards (8081, 8082, ...)
              ↓
         ZooKeeper (2181)
```

## Components

**Main Module** - API gateway, routing, ID generation  
**Storage Modules** - RocksDB persistence + HNSW index  
**ZooKeeper** - Cluster configuration store

## Key Concepts

### Consistent Hashing
- Vectors distributed by `hash(vectorId) % shardCount`
- Minimal data movement when adding shards
- Implementation: [`ConsistentHashRing.java`](../main-module/src/main/java/com/vectordb/main/cluster/ring/ConsistentHashRing.java)

### ID Generation
- Main generates 64-bit Long IDs
- Serialized as String for JavaScript safety
- Generator: [`RandomVectorIdGenerator.java`](../main-module/src/main/java/com/vectordb/main/id/RandomVectorIdGenerator.java)

### Data Persistence
- **RocksDB**: Persistent storage (survives restarts)
- **HNSW**: In-memory index (rebuilt on startup)
- **ZooKeeper**: Cluster config (persistent volumes)

## Operations

### Write Flow
```
POST /api/vectors/add
 → Generate ID
 → Hash → Select shard
 → Save to RocksDB
 → Add to HNSW
 → Return ID
```

### Search Flow
```
POST /api/v1/vectors/search
 → Query ALL shards
 → Each runs HNSW.search()
 → Merge & sort results
 → Return top-K
```

### Restart Behavior
On storage restart:
1. Load databases from RocksDB
2. Rebuild HNSW index from vectors
3. Ready to serve requests

## Configuration

ZooKeeper stores cluster config at `/vectordb/cluster/config`:
```json
{
  "shards": [{
    "shardId": "shard1",
    "baseUrl": "http://storage-1:8081",
    "hashKey": 0,
    "status": "ACTIVE"
  }]
}
```

**Shard statuses**: NEW → ACTIVE → DRAINING → DECOMMISSIONED

## Key Files

| File | Purpose |
|------|---------|
| [`VectorService.java`](../main-module/src/main/java/com/vectordb/main/service/VectorService.java) | Business logic |
| [`ShardRouter.java`](../main-module/src/main/java/com/vectordb/main/cluster/router/ShardRouter.java) | Routing |
| [`VectorStorageServiceImpl.java`](../storage-module/src/main/java/com/vectordb/storage/service/VectorStorageServiceImpl.java) | Storage ops |
| [`ConsistentHashRing.java`](../main-module/src/main/java/com/vectordb/main/cluster/ring/ConsistentHashRing.java) | Distribution |

## Scaling

Add new shard:
1. Start storage container
2. Update ZooKeeper config (via API or zkCli)
3. System auto-detects and starts rebalancing
4. Data migrates to new shard

## Limitations

- Single datacenter (no cross-DC replication)
- No WAL/replication yet
- Manual resharding trigger
- No automatic leader election