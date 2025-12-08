package com.vectordb.main.cluster.health;

import com.vectordb.main.cluster.model.ShardInfo;
import com.vectordb.main.cluster.repository.ClusterConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Мониторинг здоровья шардов для определения доступных нод
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShardHealthMonitor {

    private final ClusterConfigRepository clusterConfigRepository;
    private final Map<String, ShardHealth> shardHealth = new ConcurrentHashMap<>();

    /**
     * Проверяет здоровье шарда каждые 5 секунд
     */
    @Scheduled(fixedRate = 5000)
    public void checkShardHealth() {
        List<ShardInfo> shards = clusterConfigRepository.getShards();

        log.debug("Checking health for {} shards", shards.size());

        for (ShardInfo shard : shards) {
            boolean isHealthy = checkShardConnectivity(shard);
            shardHealth.put(shard.shardId(), new ShardHealth(isHealthy, Instant.now()));

            if (!isHealthy) {
                log.warn("Shard {} is not healthy", shard.shardId());
            } else {
                log.debug("Shard {} is healthy", shard.shardId());
            }
        }
    }

    /**
     * Проверяет доступность шарда
     */
    public boolean isShardAvailable(String shardId) {
        ShardHealth health = shardHealth.get(shardId);
        return health != null && health.healthy() &&
               health.lastCheck().isAfter(Instant.now().minusSeconds(30));
    }

    /**
     * Возвращает список доступных шардов из предоставленного списка
     */
    public List<ShardInfo> getAvailableShards(List<ShardInfo> shards) {
        return shards.stream()
            .filter(shard -> isShardAvailable(shard.shardId()))
            .toList();
    }

    /**
     * Возвращает список недоступных шардов из предоставленного списка
     */
    public List<ShardInfo> getUnavailableShards(List<ShardInfo> shards) {
        return shards.stream()
            .filter(shard -> !isShardAvailable(shard.shardId()))
            .toList();
    }

    private boolean checkShardConnectivity(ShardInfo shard) {
        try {
            WebClient.create(shard.baseUri().toString())
                .get().uri("/api/v1/storage/health")
                .retrieve().toBodilessEntity().block();
            return true;
        } catch (Exception e) {
            log.warn("Shard {} is not healthy: {}", shard.shardId(), e.getMessage());
            return false;
        }
    }

    public record ShardHealth(boolean healthy, Instant lastCheck) {}
}
