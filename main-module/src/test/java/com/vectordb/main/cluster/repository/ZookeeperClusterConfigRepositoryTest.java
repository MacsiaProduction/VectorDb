package com.vectordb.main.cluster.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectordb.main.cluster.model.ClusterConfig;
import com.vectordb.main.cluster.model.ShardConfig;
import com.vectordb.main.cluster.model.ShardStatus;
import com.vectordb.main.config.ZookeeperProperties;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZookeeperClusterConfigRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TestingServer testingServer;
    private CuratorFramework curatorFramework;

    @BeforeEach
    void setUp() throws Exception {
        testingServer = new TestingServer();
        testingServer.start();
        curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(testingServer.getConnectString())
                .retryPolicy(new ExponentialBackoffRetry(100, 3))
                .build();
        curatorFramework.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (curatorFramework != null) {
            curatorFramework.close();
        }
        if (testingServer != null) {
            testingServer.close();
        }
    }

    @Test
    void loadsAndWatchesClusterConfig() throws Exception {
        ZookeeperProperties properties = createProperties();
        writeConfig(properties, new ClusterConfig(
                List.of(
                        new ShardConfig("s1", "http://localhost:9001", 100L, ShardStatus.ACTIVE),
                        new ShardConfig("s2", "http://localhost:9002", 200L, ShardStatus.NEW)
                )
        ));

        ZookeeperClusterConfigRepository repository = new ZookeeperClusterConfigRepository(
                curatorFramework,
                objectMapper,
                properties
        );
        repository.init();

        assertEquals(1, repository.getReadRing().shards().size());
        assertEquals(2, repository.getWriteRing().shards().size());

        // update shard status to ACTIVE and add third shard
        writeConfig(properties, new ClusterConfig(
                List.of(
                        new ShardConfig("s1", "http://localhost:9001", 100L, ShardStatus.ACTIVE),
                        new ShardConfig("s2", "http://localhost:9002", 200L, ShardStatus.ACTIVE),
                        new ShardConfig("s3", "http://localhost:9003", 300L, ShardStatus.NEW)
                )
        ));

        awaitTrue(() -> repository.getReadRing().shards().size() == 2);
        awaitTrue(() -> repository.getWriteRing().shards().size() == 3);

        repository.shutdown();
    }

    private ZookeeperProperties createProperties() {
        ZookeeperProperties properties = new ZookeeperProperties();
        properties.setConnectString(testingServer.getConnectString());
        properties.setBasePath("/vectordb-test");
        properties.setClusterConfigPath(null);
        properties.setRebalancePath(null);
        properties.setCoordinatorsPath(null);
        return properties;
    }

    private void writeConfig(ZookeeperProperties properties, ClusterConfig config) throws Exception {
        byte[] payload = objectMapper.writeValueAsBytes(config);
        if (curatorFramework.checkExists().forPath(properties.clusterConfigPath()) == null) {
            curatorFramework.create().creatingParentsIfNeeded()
                    .forPath(properties.clusterConfigPath(), payload);
        } else {
            curatorFramework.setData().forPath(properties.clusterConfigPath(), payload);
        }
    }

    private void awaitTrue(Check condition) throws InterruptedException {
        long timeoutMs = 2000;
        long wait = 50;
        long elapsed = 0;
        while (elapsed < timeoutMs && !condition.getAsBoolean()) {
            TimeUnit.MILLISECONDS.sleep(wait);
            elapsed += wait;
        }
        assertTrue(condition.getAsBoolean(), "Condition not met within timeout");
    }

    @FunctionalInterface
    private interface Check {
        boolean getAsBoolean();
    }
}

