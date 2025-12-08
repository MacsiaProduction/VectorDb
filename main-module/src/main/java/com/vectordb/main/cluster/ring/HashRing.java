package com.vectordb.main.cluster.ring;

import com.vectordb.main.cluster.model.ShardInfo;

import java.util.List;

/**
 * Interface for consistent hash ring implementations.
 */
public interface HashRing {

    /**
     * Locate shard responsible for provided hash value.
     */
    ShardInfo locate(long hash);

    /**
     * Locate next shard responsible for provided hash value.
     */
    ShardInfo locateNext(long hashKey);

    /**
     * @return list of shards participating in this ring in hash order.
     */
    List<ShardInfo> shards();

    /**
     * @return true if the ring has no shards.
     */
    default boolean isEmpty() {
        return shards().isEmpty();
    }

    static HashRing empty() {
        return EmptyHashRing.INSTANCE;
    }


}


