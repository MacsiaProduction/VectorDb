package com.vectordb.main.cluster.router;

import com.vectordb.main.cluster.hash.HashService;
import com.vectordb.main.cluster.model.ShardInfo;
import com.vectordb.main.cluster.repository.ClusterConfigRepository;
import com.vectordb.main.cluster.ring.HashRing;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves shard placement for vector identifiers based on consistent hash rings.
 */
@Component
public class ShardRouter {

    private final ClusterConfigRepository clusterConfigRepository;
    private final HashService hashService;

    public ShardRouter(ClusterConfigRepository clusterConfigRepository, HashService hashService) {
        this.clusterConfigRepository = clusterConfigRepository;
        this.hashService = hashService;
    }

    public ShardInfo routeForWrite(long vectorId) {
        return locate(clusterConfigRepository.getWriteRing(), vectorId);
    }

    public List<ShardInfo> readableShards() {
        return clusterConfigRepository.getReadRing().shards();
    }

    public List<ShardInfo> writableShards() {
        return clusterConfigRepository.getWriteRing().shards();
    }

    private ShardInfo locate(HashRing ring, long vectorId) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No shards available in the cluster");
        }
        long hash = hashService.hash(vectorId);
        return ring.locate(hash);
    }
}


