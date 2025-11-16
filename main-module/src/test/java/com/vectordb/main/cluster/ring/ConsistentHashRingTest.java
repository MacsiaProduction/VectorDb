package com.vectordb.main.cluster.ring;

import com.vectordb.main.cluster.model.ShardInfo;
import com.vectordb.main.cluster.model.ShardStatus;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsistentHashRingTest {

    @Test
    void locatesShardByHashOrder() {
        List<ShardInfo> shards = List.of(
                new ShardInfo("s1", URI.create("http://localhost:9001"), 100, ShardStatus.ACTIVE),
                new ShardInfo("s2", URI.create("http://localhost:9002"), 200, ShardStatus.ACTIVE),
                new ShardInfo("s3", URI.create("http://localhost:9003"), 400, ShardStatus.ACTIVE)
        );
        HashRing ring = ConsistentHashRing.fromShards(shards);

        assertEquals("s1", ring.locate(50).shardId());
        assertEquals("s2", ring.locate(150).shardId());
        assertEquals("s3", ring.locate(350).shardId());
        // wrap around when hash > max key
        assertEquals("s1", ring.locate(450).shardId());
    }

    @Test
    void emptyRingThrowsOnLocate() {
        HashRing emptyRing = ConsistentHashRing.fromShards(List.of());
        assertThrows(IllegalStateException.class, () -> emptyRing.locate(10));
    }
}

