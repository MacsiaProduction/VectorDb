package com.vectordb.main.cluster.ownership;

import com.vectordb.main.client.ShardedStorageClient;
import com.vectordb.main.cluster.model.ClusterConfig;
import com.vectordb.main.cluster.repository.ClusterConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Сервис для управления репликацией между шардами
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShardReplicationService {

    private final ClusterConfigRepository clusterConfigRepository;
    private final ShardedStorageClient shardedStorageClient;

    private ShardOwnership shardOwnership;

    @PostConstruct
    void initialize() {
        ClusterConfig config = clusterConfigRepository.getClusterConfig();
        this.shardOwnership = new ShardOwnership(config);

    }

    /**
     * Обновляет ShardOwnership при изменении конфигурации кластера
     */
    public void updateOwnership(ClusterConfig newConfig) {
        this.shardOwnership = new ShardOwnership(newConfig);
        log.info("Updated shard ownership mapping for {} shards", newConfig.shards().size());
    }

    /**
     * Получить текущую карту владения репликами
     */
    public ShardOwnership getShardOwnership() {
        return shardOwnership;
    }
}


