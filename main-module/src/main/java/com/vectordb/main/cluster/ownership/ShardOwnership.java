package com.vectordb.main.cluster.ownership;

import com.vectordb.main.cluster.model.ClusterConfig;
import com.vectordb.main.cluster.model.ShardInfo;

import java.util.*;

/**
 * Определяет, где хранятся primary данные и реплики каждого шарда
 */
public class ShardOwnership {

    /**
     * Для каждого шарда определяет, где хранятся его реплики
     * shardId -> replicaLocationShardId
     */
    private final Map<String, String> shardToReplicaLocation;

    /**
     * Для каждого шарда определяет, чьи реплики он хранит
     * shardId -> Set<sourceShardIds>
     */
    private final Map<String, Set<String>> shardToReplicaSources;

    public ShardOwnership(ClusterConfig config) {
        this.shardToReplicaLocation = buildReplicaLocationMapping(config);
        this.shardToReplicaSources = buildReplicaSourcesMapping(config);
    }

    /**
     * Где хранятся реплики шарда sourceShardId?
     */
    public String getReplicaLocation(String sourceShardId) {
        return shardToReplicaLocation.get(sourceShardId);
    }

    /**
     * Хранит ли shardId реплики sourceShardId?
     */
    public boolean isReplicaStoredHere(String sourceShardId, String shardId) {
        return shardId.equals(getReplicaLocation(sourceShardId));
    }

    /**
     * Какие шарды хранят реплики на данном shardId?
     */
    public Set<String> getReplicaSources(String shardId) {
        return shardToReplicaSources.getOrDefault(shardId, Set.of());
    }

    /**
     * Получить все шарды, которые должны хранить реплики недоступного шарда
     */
    public List<String> getReplicaLocationsForUnavailableShard(String unavailableShardId, List<String> availableShardIds) {
        String replicaLocation = getReplicaLocation(unavailableShardId);
        if (replicaLocation != null && availableShardIds.contains(replicaLocation)) {
            return List.of(replicaLocation);
        }
        return List.of();
    }

    private Map<String, String> buildReplicaLocationMapping(ClusterConfig config) {
        Map<String, String> mapping = new HashMap<>();
        List<ShardInfo> sortedShards = config.shards().stream()
            .map(ShardInfo::fromConfig)
            .sorted(Comparator.comparingLong(ShardInfo::hashKey))
            .toList();

        for (int i = 0; i < sortedShards.size(); i++) {
            String current = sortedShards.get(i).shardId();
            String next = sortedShards.get((i + 1) % sortedShards.size()).shardId();
            mapping.put(current, next); // Реплики current хранятся на next
        }

        return mapping;
    }

    private Map<String, Set<String>> buildReplicaSourcesMapping(ClusterConfig config) {
        Map<String, Set<String>> mapping = new HashMap<>();
        List<ShardInfo> sortedShards = config.shards().stream()
            .map(ShardInfo::fromConfig)
            .sorted(Comparator.comparingLong(ShardInfo::hashKey))
            .toList();

        for (int i = 0; i < sortedShards.size(); i++) {
            String current = sortedShards.get(i).shardId();
            String previous = sortedShards.get((i - 1 + sortedShards.size()) % sortedShards.size()).shardId();

            mapping.computeIfAbsent(current, k -> new HashSet<>()).add(previous);
            // Реплики previous хранятся на current
        }

        return mapping;
    }
}


