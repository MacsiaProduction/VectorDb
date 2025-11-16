package com.vectordb.main.cluster.ring;

import com.vectordb.main.cluster.model.ShardInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Simple consistent hash ring over physical shards (no virtual nodes).
 */
public final class ConsistentHashRing implements HashRing {

    private final List<RingEntry> entries;
    private final List<ShardInfo> shardView;

    private ConsistentHashRing(List<RingEntry> entries) {
        this.entries = Collections.unmodifiableList(entries);
        this.shardView = entries.stream()
                .map(RingEntry::shard)
                .toList();
    }

    public static HashRing fromShards(List<ShardInfo> shards) {
        if (shards == null || shards.isEmpty()) {
            return HashRing.empty();
        }
        var ringEntries = new ArrayList<RingEntry>(shards.size());
        shards.stream()
                .sorted(Comparator.comparingLong(ShardInfo::hashKey).thenComparing(ShardInfo::shardId))
                .forEach(shard -> ringEntries.add(new RingEntry(shard.hashKey(), shard)));
        return new ConsistentHashRing(ringEntries);
    }

    @Override
    public ShardInfo locate(long hash) {
        if (entries.isEmpty()) {
            throw new IllegalStateException("Hash ring is empty");
        }
        int idx = binarySearch(hash);
        return entries.get(idx).shard();
    }

    @Override
    public List<ShardInfo> shards() {
        return shardView;
    }

    private int binarySearch(long hash) {
        int low = 0;
        int high = entries.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = entries.get(mid).hashKey();
            if (midVal < hash) {
                low = mid + 1;
            } else if (midVal > hash) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return low >= entries.size() ? 0 : low;
    }

    private record RingEntry(long hashKey, ShardInfo shard) {}
}

