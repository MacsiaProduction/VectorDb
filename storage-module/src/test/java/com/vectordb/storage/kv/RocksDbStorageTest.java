package com.vectordb.storage.kv;

import com.vectordb.common.model.VectorEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RocksDbStorageTest {

    private RocksDbStorage storage;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("rocks-test");
        storage = new RocksDbStorage();
        ReflectionTestUtils.setField(storage, "dataPath", tempDir.toString());
        storage.initialize();
    }

    @AfterEach
    void tearDown() throws Exception {
        storage.cleanup();
        FileSystemUtils.deleteRecursively(tempDir);
    }

    @Test
    void getAllVectorsReturnsSortedByIdWhenManuallySorted() throws Exception {
        VectorEntry v1 = newEntry(1L, new float[]{0.1f}, "low");
        VectorEntry v2 = newEntry(2L, new float[]{0.2f}, "high");
        storage.putVector("db", v2);
        storage.putVector("db", v1);

        List<VectorEntry> result = storage.getAllVectors("db")
                .stream()
                .sorted(Comparator.comparingLong(VectorEntry::id))
                .toList();

        assertEquals(List.of(1L, 2L), result.stream().map(VectorEntry::id).toList());
    }

    @Test
    void deleteBatchRemovesEntries() throws Exception {
        VectorEntry v1 = newEntry(10L, new float[]{0.1f}, "a");
        VectorEntry v2 = newEntry(11L, new float[]{0.1f}, "b");
        storage.putVector("db", v1);
        storage.putVector("db", v2);

        assertTrue(storage.deleteVector("db", 10L));
        assertTrue(storage.deleteVector("db", 11L));
        assertTrue(storage.getVector("db", 10L).isEmpty());
    }

    private VectorEntry newEntry(Long id, float[] embedding, String data) {
        return new VectorEntry(
                id,
                embedding,
                data,
                "db",
                Instant.now()
        );
    }
}

