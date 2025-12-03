package com.vectordb.main.cluster.model;

import java.net.URI;

/**
 * Runtime representation of a shard derived from ZooKeeper configuration.
 */
public record ShardInfo(
        String shardId,
        URI baseUri,
        long hashKey,
        ShardStatus status
) {

    public boolean isActiveForWrite() {
        return status == ShardStatus.ACTIVE || status == ShardStatus.NEW;
    }

    public boolean isActiveForRead() {
        return status == ShardStatus.ACTIVE;
    }

    public static ShardInfo fromConfig(ShardConfig config) {
        return new ShardInfo(
                config.shardId(),
                URI.create(config.baseUrl()),
                config.hashKey(),
                config.status()
        );
    }
}


