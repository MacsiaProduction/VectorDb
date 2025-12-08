package com.vectordb.main.cluster.rebalance;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.main.client.ShardedStorageClient;
import com.vectordb.main.client.StorageClient;
import com.vectordb.main.cluster.hash.HashService;
import com.vectordb.main.cluster.model.ShardInfo;
import com.vectordb.main.cluster.ownership.ShardReplicationService;
import com.vectordb.main.cluster.repository.ClusterConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShardRebalancer {

    private final ShardedStorageClient shardedStorageClient;
    private final HashService hashService;
    private final ShardReplicationService shardReplicationService;
    private final ClusterConfigRepository clusterConfigRepository;

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
                    sourceClient.scanRange(databaseId, lastProcessedId, Long.MAX_VALUE, batchSize));
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
            getUnchecked(targetClient.putBatch(databaseId, toMove));
            List<Long> ids = toMove.stream().map(VectorEntry::id).toList();
            getUnchecked(sourceClient.deleteBatch(databaseId, ids));
            moved += toMove.size();
            log.info("Moved {} vectors in database {} from {} to {}", toMove.size(), databaseId,
                    sourceShard.shardId(), targetShard.shardId());

            // После перемещения primary данных обрабатываем реплики
            handleReplicaRebalancing(databaseId, toMove, sourceShard, targetShard);
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

    /**
     * Обрабатывает перемещение реплик после ребалансировки primary данных
     */
    private void handleReplicaRebalancing(String databaseId, List<VectorEntry> movedVectors,
                                         ShardInfo sourceShard, ShardInfo targetShard) {
        try {
            // Определяем локации реплик для source и target шардов
            String sourceReplicaLocation = shardReplicationService.getShardOwnership()
                    .getReplicaLocation(sourceShard.shardId());
            String targetReplicaLocation = shardReplicationService.getShardOwnership()
                    .getReplicaLocation(targetShard.shardId());

            if (sourceReplicaLocation == null || targetReplicaLocation == null) {
                log.warn("Replica locations not found for source shard {} or target shard {}",
                        sourceShard.shardId(), targetShard.shardId());
                return;
            }

            // Если локации реплик совпадают, реплики уже на правильных местах
            if (sourceReplicaLocation.equals(targetReplicaLocation)) {
                log.debug("Replica locations are the same for source and target shards, skipping replica rebalancing");
                return;
            }

            // Получаем клиенты для локаций реплик
            ShardInfo sourceReplicaShard = findShardInfoById(sourceReplicaLocation);
            ShardInfo targetReplicaShard = findShardInfoById(targetReplicaLocation);

            if (sourceReplicaShard == null || targetReplicaShard == null) {
                log.warn("Could not find ShardInfo for replica locations: source={}, target={}",
                        sourceReplicaLocation, targetReplicaLocation);
                return;
            }

            StorageClient sourceReplicaClient = shardedStorageClient.getClient(sourceReplicaShard);
            StorageClient targetReplicaClient = shardedStorageClient.getClient(targetReplicaShard);

            List<Long> vectorIds = movedVectors.stream().map(VectorEntry::id).toList();

            // Перемещаем реплики из старой локации в новую
            List<VectorEntry> replicasToMove = new ArrayList<>();
            for (Long vectorId : vectorIds) {
                try {
                    VectorEntry replica = getUnchecked(sourceReplicaClient
                            .getVectorReplica(vectorId, databaseId, sourceShard.shardId()));
                    if (replica != null) {
                        replicasToMove.add(replica);
                    }
                } catch (Exception e) {
                    log.debug("Replica {} not found in old location {}, skipping", vectorId, sourceReplicaLocation);
                }
            }

            if (!replicasToMove.isEmpty()) {
                // Добавляем реплики в новую локацию
                for (VectorEntry replica : replicasToMove) {
                    try {
                        getUnchecked(targetReplicaClient.addVectorReplica(replica, databaseId, targetShard.shardId()));
                        log.debug("Moved replica {} from {} to {}", replica.id(), sourceReplicaLocation, targetReplicaLocation);
                    } catch (Exception e) {
                        log.error("Failed to add replica {} to new location {}", replica.id(), targetReplicaLocation, e);
                    }
                }

                // Удаляем реплики из старой локации
                for (VectorEntry replica : replicasToMove) {
                    try {
                        getUnchecked(sourceReplicaClient.deleteVectorReplica(replica.id(), databaseId, sourceShard.shardId()));
                        log.debug("Removed replica {} from old location {}", replica.id(), sourceReplicaLocation);
                    } catch (Exception e) {
                        log.error("Failed to remove replica {} from old location {}", replica.id(), sourceReplicaLocation, e);
                    }
                }

                log.info("Rebalanced {} replicas from {} to {} for database {}",
                        replicasToMove.size(), sourceReplicaLocation, targetReplicaLocation, databaseId);
            } else {
                log.debug("No replicas found to rebalance for database {}", databaseId);
            }

        } catch (Exception e) {
            log.error("Failed to handle replica rebalancing for database {}: {}", databaseId, e.getMessage(), e);
            // Не выбрасываем исключение, чтобы не останавливать основной процесс ребалансировки
        }
    }

    private ShardInfo findShardInfoById(String shardId) {
        return clusterConfigRepository.getShards().stream()
                .filter(shard -> shard.shardId().equals(shardId))
                .findFirst()
                .orElse(null);
    }

    private <T> T getUnchecked(Mono<T> mono) {
        try {
            return mono.block();
        } catch (Exception e) {
            throw new RuntimeException("Shard rebalancer operation failed", e);
        }
    }
}


