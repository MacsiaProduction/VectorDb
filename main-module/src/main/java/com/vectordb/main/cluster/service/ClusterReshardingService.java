package com.vectordb.main.cluster.service;

import com.vectordb.main.client.ShardedStorageClient;
import com.vectordb.main.cluster.model.ClusterConfig;
import com.vectordb.main.cluster.model.ShardConfig;
import com.vectordb.main.cluster.model.ShardInfo;
import com.vectordb.main.cluster.rebalance.ShardRebalancer;
import com.vectordb.main.cluster.repository.ClusterConfigRepository;
import com.vectordb.main.cluster.ownership.ShardReplicationService;
import com.vectordb.main.repository.VectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for managing cluster resharding operations.
 * Compares old and new configurations, triggers data migration for new shards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterReshardingService {

    private final ClusterConfigRepository clusterConfigRepository;
    private final ShardRebalancer shardRebalancer;
    private final VectorRepository vectorRepository;
    private final ShardedStorageClient shardedStorageClient;
    private final ShardReplicationService shardReplicationService;

    /**
     * Applies a new cluster configuration and triggers resharding if necessary.
     * @param newConfig new cluster configuration
     * @throws Exception if resharding fails
     */
    public void applyNewConfiguration(ClusterConfig newConfig) throws Exception {
        ClusterConfig oldConfig = clusterConfigRepository.getClusterConfig();
        
        log.info("Applying new cluster configuration: {} shards (previously {})", 
                newConfig.shards().size(), oldConfig.shards().size());

        Map<String, ShardConfig> oldShards = toMap(oldConfig.shards());
        Map<String, ShardConfig> newShards = toMap(newConfig.shards());

        clusterConfigRepository.updateClusterConfig(newConfig);

        // Обновляем информацию о репликах при изменении конфигурации
        shardReplicationService.updateOwnership(newConfig);

        List<String> addedShardIds = new ArrayList<>();
        for (String shardId : newShards.keySet()) {
            if (!oldShards.containsKey(shardId)) {
                addedShardIds.add(shardId);
            }
        }

        if (!addedShardIds.isEmpty()) {
            log.info("Detected {} new shard(s): {}", addedShardIds.size(), addedShardIds);
            triggerReshardingForNewShards(oldConfig, newConfig, addedShardIds);
        } else {
            log.info("No new shards detected, configuration update complete");
        }
    }

    private void triggerReshardingForNewShards(
            ClusterConfig oldConfig,
            ClusterConfig newConfig,
            List<String> newShardIds
    ) {
        log.info("Starting resharding process for new shards: {}", newShardIds);

        List<com.vectordb.common.model.DatabaseInfo> databaseInfos;
        try {
            databaseInfos = vectorRepository.getAllDatabases();
        } catch (Exception e) {
            log.error("Failed to retrieve database list for resharding", e);
            return;
        }

        if (databaseInfos.isEmpty()) {
            log.info("No databases found, skipping resharding");
            return;
        }

        log.info("Resharding {} database(s): {}", databaseInfos.size(),
                databaseInfos.stream().map(com.vectordb.common.model.DatabaseInfo::id).toList());

        List<String> databases = databaseInfos.stream().map(com.vectordb.common.model.DatabaseInfo::id).toList();

        Map<String, ShardInfo> oldShardMap = buildShardInfoMap(oldConfig);
        Map<String, ShardInfo> newShardMap = buildShardInfoMap(newConfig);
        
        // Build old hash ring to determine source shards
        com.vectordb.main.cluster.ring.HashRing oldRing =
                com.vectordb.main.cluster.ring.ConsistentHashRing.fromShards(new ArrayList<>(oldShardMap.values()));
        
        for (String newShardId : newShardIds) {
            ShardInfo targetShard = newShardMap.get(newShardId);
            if (targetShard == null) {
                log.warn("New shard {} not found in new config, skipping", newShardId);
                continue;
            }
            
            for (com.vectordb.common.model.DatabaseInfo dbInfo : databaseInfos) {
                try {
                    log.info("Creating database {} on new shard {}", dbInfo.id(), newShardId);
                    shardedStorageClient.getClient(targetShard)
                            .createDatabase(dbInfo.id(), dbInfo.id(), dbInfo.dimension())
                            .toFuture()
                            .get();
                    log.info("Successfully created database {} on shard {}", dbInfo.id(), newShardId);
                } catch (Exception e) {
                    log.error("Failed to create database {} on new shard {}: {}",
                            dbInfo.id(), newShardId, e.getMessage(), e);
                }
            }
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String newShardId : newShardIds) {
            ShardInfo targetShard = newShardMap.get(newShardId);
            if (targetShard == null) {
                log.warn("New shard {} not found in new config, skipping", newShardId);
                continue;
            }

            ShardInfo previousShard = findPreviousShard(targetShard, newConfig);
            
            if (previousShard == null) {
                log.warn("No previous shard found for new shard {}, skipping rebalancing", newShardId);
                continue;
            }

            // Determine which shard in OLD config holds vectors in range (previousShard.hashKey, targetShard.hashKey]
            // Use a test hash just after previousShard.hashKey to find the source shard
            long testHash = previousShard.hashKey() == Long.MAX_VALUE ? Long.MIN_VALUE : previousShard.hashKey() + 1;
            ShardInfo sourceShard;
            try {
                sourceShard = oldRing.locate(testHash);
                log.info("For new shard {}, will rebalance from source shard {} (range: {} to {})",
                        newShardId, sourceShard.shardId(), previousShard.hashKey(), targetShard.hashKey());
            } catch (Exception e) {
                log.error("Failed to locate source shard for new shard {}: {}", newShardId, e.getMessage(), e);
                continue;
            }

            // Rebalance from the source shard that currently holds vectors in this range
            for (String dbId : databases) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        log.info("Rebalancing database {} from shard {} to shard {}",
                                dbId, sourceShard.shardId(), targetShard.shardId());
                        shardRebalancer.rebalance(dbId, previousShard, sourceShard, targetShard);
                        log.info("Completed rebalancing database {} from shard {} to shard {}",
                                dbId, sourceShard.shardId(), targetShard.shardId());
                    } catch (Exception e) {
                        log.error("Failed to rebalance database {} from shard {} to shard {}: {}",
                                dbId, sourceShard.shardId(), targetShard.shardId(), e.getMessage(), e);
                    }
                });
                futures.add(future);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Resharding completed for all databases");
    }

    private Map<String, ShardConfig> toMap(List<ShardConfig> shards) {
        Map<String, ShardConfig> map = new HashMap<>();
        for (ShardConfig shard : shards) {
            map.put(shard.shardId(), shard);
        }
        return map;
    }

    private Map<String, ShardInfo> buildShardInfoMap(ClusterConfig config) {
        Map<String, ShardInfo> map = new HashMap<>();
        for (ShardConfig shardConfig : config.shards()) {
            try {
                ShardInfo info = ShardInfo.fromConfig(shardConfig);
                map.put(info.shardId(), info);
            } catch (Exception e) {
                log.error("Failed to build ShardInfo from config {}", shardConfig, e);
            }
        }
        return map;
    }

    /**
     * Finds the previous shard in the ring (sorted by hashKey).
     * For consistent hashing, the previous shard is the one with the largest hashKey
     * that is still less than the target shard's hashKey.
     */
    private ShardInfo findPreviousShard(ShardInfo targetShard, ClusterConfig config) {
        List<ShardInfo> sortedShards = config.shards().stream()
                .map(ShardInfo::fromConfig)
                .sorted(Comparator.comparingLong(ShardInfo::hashKey))
                .toList();

        ShardInfo previous = null;
        for (ShardInfo shard : sortedShards) {
            if (shard.hashKey() >= targetShard.hashKey()) {
                break;
            }
            previous = shard;
        }

        // If no previous found, wrap around to the last shard
        if (previous == null && !sortedShards.isEmpty()) {
            previous = sortedShards.getLast();
        }

        return previous;
    }
}

