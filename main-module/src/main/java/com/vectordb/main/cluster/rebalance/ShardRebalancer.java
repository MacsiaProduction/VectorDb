package com.vectordb.main.cluster.rebalance;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.main.client.ShardedStorageClient;
import com.vectordb.main.client.StorageClient;
import com.vectordb.main.cluster.hash.HashService;
import com.vectordb.main.cluster.model.ShardInfo;
import com.vectordb.main.cluster.ownership.ShardReplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShardRebalancer {

    private final ShardedStorageClient shardedStorageClient;
    private final HashService hashService;
    private final ShardReplicationService shardReplicationService;

    @Value("${vectordb.rebalancer.batch-size:500}")
    private int batchSize;

    public void rebalance(String databaseId, ShardInfo previousShard, ShardInfo sourceShard, ShardInfo targetShard) {
        StorageClient sourceClient = shardedStorageClient.getClient(sourceShard);
        StorageClient targetClient = shardedStorageClient.getClient(targetShard);

        long lastProcessedId = Long.MIN_VALUE;
        long moved = 0;
        long startHash = previousShard.hashKey();
        long endHash = targetShard.hashKey();

        while (true) {
            List<VectorEntry> batch = getUnchecked(
                    sourceClient.scanRange(databaseId, lastProcessedId, Long.MAX_VALUE, batchSize).toFuture());
            if (batch.isEmpty()) {
                break;
            }
            lastProcessedId = batch.getLast().id();
            List<VectorEntry> toMove = batch.stream()
                    .filter(entry -> belongsToRange(hashService.hash(entry.id()), startHash, endHash))
                    .toList();
            if (toMove.isEmpty()) {
                continue;
            }
            getUnchecked(targetClient.putBatch(databaseId, toMove).toFuture());
            List<Long> ids = toMove.stream().map(VectorEntry::id).toList();
            getUnchecked(sourceClient.deleteBatch(databaseId, ids).toFuture());
            moved += toMove.size();
            log.info("Moved {} vectors in database {} from {} to {}", toMove.size(), databaseId,
                    sourceShard.shardId(), targetShard.shardId());

            // TODO: После перемещения primary данных нужно:
            // 1. Определить новые локации реплик для перемещенных векторов
            // 2. Создать реплики на новых локациях
            // 3. Удалить старые реплики
            // Это требует значительных изменений в архитектуре
        }
        log.info("Completed rebalance of database {} (moved {} vectors) from {} to {}", databaseId, moved,
                sourceShard.shardId(), targetShard.shardId());
    }

    private boolean belongsToRange(long hash, long startExclusive, long endInclusive) {
        if (startExclusive < endInclusive) {
            return hash > startExclusive && hash <= endInclusive;
        }
        return hash > startExclusive || hash <= endInclusive;
    }

    private <T> T getUnchecked(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException("Shard rebalancer operation failed", e);
        }
    }
}


