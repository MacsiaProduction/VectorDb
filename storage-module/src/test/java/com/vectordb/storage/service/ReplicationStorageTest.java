package com.vectordb.storage.service;

import com.vectordb.common.model.VectorEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тест репликации в storage модуле
 */
@SpringBootTest
@ActiveProfiles("test")
public class ReplicationStorageTest {

    @Autowired
    private VectorStorageService storageService;

    @Test
    void shouldAddAndRetrieveReplica() throws Exception {
        // Given
        String dbId = "test_replica_db";
        String sourceShardId = "shard1";
        float[] vector = new float[128];
        for (int i = 0; i < 128; i++) {
            vector[i] = (float) Math.random();
        }

        VectorEntry entry = new VectorEntry(
            12345L,
            vector,
            "test replica data",
            dbId,
            Instant.now()
        );

        // When
        Long id = storageService.addReplica(entry, dbId, sourceShardId);

        // Then
        assertThat(id).isEqualTo(12345L);

        // Verify replica can be retrieved
        var retrieved = storageService.getReplica(id, dbId, sourceShardId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().id()).isEqualTo(id);
        assertThat(retrieved.get().databaseId()).isEqualTo(dbId);
    }

    @Test
    void shouldSearchReplicas() throws Exception {
        // Given
        String dbId = "test_replica_db";
        String sourceShardId = "shard1";
        float[] queryVector = new float[128];
        for (int i = 0; i < 128; i++) {
            queryVector[i] = (float) Math.random();
        }

        // When
        var results = storageService.searchReplicas(null, sourceShardId); // query object will be created inside

        // Then
        assertThat(results).isNotNull();
        // Note: Results may be empty if no replicas exist
    }
}


