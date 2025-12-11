package com.vectordb.main.repository;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.main.cluster.model.ClusterConfig;
import com.vectordb.main.cluster.model.ShardConfig;
import com.vectordb.main.cluster.ownership.ShardOwnership;
import com.vectordb.main.cluster.ownership.ShardReplicationService;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Интеграционный тест репликации
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
public class ReplicationIntegrationTest {

    private static final TestingServer zookeeperServer;

    static {
        try {
            zookeeperServer = new TestingServer(2181, true);
            Thread.sleep(3000);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start ZooKeeper", e);
        }
    }

    @MockBean
    private VectorRepository vectorRepository;

    @AfterAll
    static void stopZooKeeper() throws Exception {
        if (zookeeperServer != null) {
            zookeeperServer.close();
        }
    }

    @DynamicPropertySource
    static void zookeeperProperties(DynamicPropertyRegistry registry) {
        registry.add("zookeeper.connect-string", () -> "localhost:2181");
    }

    @Autowired
    private ShardReplicationService shardReplicationService;


    @Test
    void shouldReplicateDataToReplicaShard() throws Exception {
        // Проверяет репликацию данных в реплику шарда

        String dbId = "test_replication_db";
        float[] vector = new float[128];
        for (int i = 0; i < 128; i++) {
            vector[i] = (float) Math.random();
        }

        VectorEntry entry = new VectorEntry(
            1L,
            vector,
            "test data",
            dbId,
            Instant.now()
        );

        Long expectedId = 123L;
        VectorEntry retrievedEntry = new VectorEntry(expectedId, vector, "test data", dbId, Instant.now());

        when(vectorRepository.add(entry, dbId)).thenReturn(expectedId);
        when(vectorRepository.findById(expectedId, dbId)).thenReturn(java.util.Optional.of(retrievedEntry));

        Long id = vectorRepository.add(entry, dbId);

        assertThat(id).isNotNull();
        assertThat(id).isPositive();

        var retrieved = vectorRepository.findById(id, dbId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().id()).isEqualTo(id);

        verify(vectorRepository).add(entry, dbId);
        verify(vectorRepository).findById(expectedId, dbId);
    }

    @Test
    void shouldSearchWithReplication() throws Exception {
        // Проверяет поиск с учётом репликации

        String dbId = "test_replication_db";
        float[] queryVector = new float[128];
        for (int i = 0; i < 128; i++) {
            queryVector[i] = (float) Math.random();
        }

        List<VectorEntry> mockResults = List.of(
            new VectorEntry(1L, queryVector, "result1", dbId, Instant.now()),
            new VectorEntry(2L, queryVector, "result2", dbId, Instant.now())
        );

        when(vectorRepository.getTopKSimilar(queryVector, 10, dbId)).thenReturn(mockResults);

        var results = vectorRepository.getTopKSimilar(queryVector, 10, dbId);

        assertThat(results).isNotNull();
        assertThat(results.size()).isLessThanOrEqualTo(10);
        assertThat(results.size()).isEqualTo(2);

        verify(vectorRepository).getTopKSimilar(queryVector, 10, dbId);
    }

    @Test
    void shouldDetermineShardOwnership() {
        // Проверяет логику определения владельца шарда и реплик

        ClusterConfig config = new ClusterConfig(List.of(
            new ShardConfig("shard1", "http://localhost:8081", 0L, null),
            new ShardConfig("shard2", "http://localhost:8082", Long.MAX_VALUE / 2, null),
            new ShardConfig("shard3", "http://localhost:8083", Long.MAX_VALUE, null)
        ));

        ShardOwnership ownership = new ShardOwnership(config);

        assertThat(ownership.getReplicaLocation("shard1")).isEqualTo("shard2");
        assertThat(ownership.getReplicaLocation("shard2")).isEqualTo("shard3");
        assertThat(ownership.getReplicaLocation("shard3")).isEqualTo("shard1");

        assertThat(ownership.getReplicaSources("shard1")).contains("shard3");
        assertThat(ownership.getReplicaSources("shard2")).contains("shard1");
        assertThat(ownership.getReplicaSources("shard3")).contains("shard2");
    }
}
