package com.vectordb.main.cluster.ring;

import com.vectordb.main.cluster.model.ShardInfo;

import java.util.Collections;
import java.util.List;

/**
 * No-op hash ring used when cluster configuration is empty.
 */
enum EmptyHashRing implements HashRing {
    INSTANCE;

    @Override
    public ShardInfo locate(long hash) {
        throw new IllegalStateException("Hash ring is empty");
    }

    @Override
    public ShardInfo locateNext(long hashKey) {
        throw new IllegalStateException("Hash ring is empty");
    }

    @Override
    public List<ShardInfo> shards() {
        return Collections.emptyList();
    }
}


