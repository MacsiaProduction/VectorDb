package com.vectordb.main.cluster.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectordb.main.cluster.model.ClusterConfig;
import com.vectordb.main.cluster.model.ShardConfig;
import com.vectordb.main.cluster.model.ShardInfo;
import com.vectordb.main.cluster.ring.ConsistentHashRing;
import com.vectordb.main.cluster.ring.HashRing;
import com.vectordb.main.config.ZookeeperProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.utils.EnsurePath;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ZooKeeper-backed implementation of {@link ClusterConfigRepository}.
 */
@Slf4j
@Component
public class ZookeeperClusterConfigRepository implements ClusterConfigRepository {

    private final CuratorFramework curatorFramework;
    private final ObjectMapper objectMapper;
    private final ZookeeperProperties properties;
    private final AtomicReference<ClusterState> state = new AtomicReference<>(ClusterState.empty());
    private CuratorCache configCache;

    public ZookeeperClusterConfigRepository(
            CuratorFramework curatorFramework,
            ObjectMapper objectMapper,
            ZookeeperProperties properties
    ) {
        this.curatorFramework = curatorFramework;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        waitForConnection();
        ensurePaths();
        refreshStateFromZookeeper();
        startWatcher();
    }

    @PreDestroy
    void shutdown() {
        if (configCache != null) {
            configCache.close();
        }
    }

    @Override
    public ClusterConfig getClusterConfig() {
        return state.get().config();
    }

    @Override
    public HashRing getReadRing() {
        return state.get().readRing();
    }

    @Override
    public HashRing getWriteRing() {
        return state.get().writeRing();
    }

    @Override
    public List<ShardInfo> getShards() {
        return state.get().shards();
    }

    @Override
    public void updateClusterConfig(ClusterConfig config) throws Exception {
        log.info("Updating cluster configuration with {} shards", config.shards().size());
        byte[] data = objectMapper.writeValueAsBytes(config);
        
        try {
            curatorFramework.setData().forPath(properties.clusterConfigPath(), data);
            log.info("Successfully updated cluster configuration in ZooKeeper");
        } catch (org.apache.zookeeper.KeeperException.NoNodeException e) {
            log.warn("Config node does not exist, creating it");
            curatorFramework.create()
                    .creatingParentsIfNeeded()
                    .forPath(properties.clusterConfigPath(), data);
            log.info("Successfully created cluster configuration in ZooKeeper");
        }
    }

    private void waitForConnection() {
        try {
            boolean connected = curatorFramework.blockUntilConnected(
                    Math.toIntExact(properties.getConnectionTimeout().toMillis()),
                    TimeUnit.MILLISECONDS
            );
            if (!connected) {
                throw new IllegalStateException("Failed to connect to ZooKeeper within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while connecting to ZooKeeper", e);
        }
    }

    private void ensurePaths() {
        try {
            new EnsurePath(properties.clusterConfigPath()).ensure(curatorFramework.getZookeeperClient());
            new EnsurePath(properties.rebalancePath()).ensure(curatorFramework.getZookeeperClient());
            new EnsurePath(properties.coordinatorsPath()).ensure(curatorFramework.getZookeeperClient());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to ensure ZooKeeper paths", e);
        }
    }

    private void startWatcher() {
        configCache = CuratorCache.build(curatorFramework, properties.clusterConfigPath());
        CuratorCacheListener listener = CuratorCacheListener.builder()
                .forNodeCache(this::refreshStateFromZookeeper)
                .build();
        configCache.listenable().addListener(listener);
        configCache.start();
    }

    private void refreshStateFromZookeeper() {
        try {
            byte[] data = curatorFramework.getData().forPath(properties.clusterConfigPath());
            applyNewState(parseConfig(data));
        } catch (org.apache.zookeeper.KeeperException.NoNodeException e) {
            log.warn("Cluster config node {} is missing, assuming empty configuration", properties.clusterConfigPath());
            applyNewState(new ClusterConfig(List.of()));
        } catch (Exception e) {
            log.error("Failed to refresh cluster configuration from ZooKeeper", e);
        }
    }

    private ClusterConfig parseConfig(byte[] data) {
        if (data == null || data.length == 0) {
            return new ClusterConfig(List.of());
        }
        try {
            ClusterConfig config = objectMapper.readValue(data, ClusterConfig.class);
            if (log.isDebugEnabled()) {
                log.debug("Loaded cluster config: {}", new String(data, StandardCharsets.UTF_8));
            }
            return config;
        } catch (Exception e) {
            log.error("Failed to parse cluster configuration JSON, keeping previous state", e);
            return state.get().config();
        }
    }

    private void applyNewState(ClusterConfig config) {
        List<ShardInfo> shards = config.shards().stream()
                .map(this::toShardInfo)
                .flatMap(Optional::stream)
                .toList();

        HashRing readRing = ConsistentHashRing.fromShards(
                shards.stream()
                        .filter(ShardInfo::isActiveForRead)
                        .toList()
        );
        HashRing writeRing = ConsistentHashRing.fromShards(
                shards.stream()
                        .filter(ShardInfo::isActiveForWrite)
                        .toList()
        );

        ClusterState newState = new ClusterState(config, shards, readRing, writeRing);
        state.set(newState);
        log.info("Cluster config refreshed: {} shards (read={}, write={})",
                shards.size(),
                readRing.shards().size(),
                writeRing.shards().size());
    }

    private Optional<ShardInfo> toShardInfo(ShardConfig config) {
        try {
            return Optional.of(ShardInfo.fromConfig(config));
        } catch (Exception e) {
            log.error("Failed to build shard info from config {}", config, e);
            return Optional.empty();
        }
    }

    private record ClusterState(
            ClusterConfig config,
            List<ShardInfo> shards,
            HashRing readRing,
            HashRing writeRing
    ) {
        private static ClusterState empty() {
            var config = new ClusterConfig(List.of());
            return new ClusterState(config, List.of(), HashRing.empty(), HashRing.empty());
        }
    }
}

