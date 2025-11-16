package com.vectordb.storage.index;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchResult;
import com.vectordb.storage.similarity.VectorSimilarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit tests for HNSW index functionality
 */
class HnswVectorIndexTest {
    
    private HnswVectorIndex hnswIndex;
    private VectorSimilarity vectorSimilarity;
    private static final String TEST_DB_ID = "test-db";
    private static final String TEST_DB_ID_2 = "test-db-2";

    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        vectorSimilarity = new VectorSimilarity();
        hnswIndex = new HnswVectorIndex(
            vectorSimilarity,
            1000,    // maxElements
            16,      // m
            200,     // efConstruction
            100,     // efSearch
            "cosine" // spaceType
        );
        // Set dimension for all tests
        hnswIndex.setDimension(3);
    }
    
    /**
     * Helper method to create test VectorEntry with minimal required fields
     */
    private VectorEntry createTestVector(Long id, float[] embedding) {
        Instant now = Instant.now();
        return VectorEntry.builder()
            .id(id)
            .embedding(embedding)
            .originalData("test-" + id)
            .databaseId(TEST_DB_ID)
            .createdAt(now)
            .build();
    }

    @Test
    void testIndexCreation() {
        assertNotNull(hnswIndex);
        assertFalse(hnswIndex.isBuilt(TEST_DB_ID));
        assertEquals(0, hnswIndex.size(TEST_DB_ID));
    }
    
    @Test
    void testAddVectorToUnbuiltIndex() {
        VectorEntry vector = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        
        hnswIndex.add(vector, TEST_DB_ID);
        
        assertEquals(1, hnswIndex.size(TEST_DB_ID));
        assertFalse(hnswIndex.isBuilt(TEST_DB_ID));
    }
    
    @Test
    void testBuildEmptyIndex() {
        hnswIndex.build(TEST_DB_ID);
        
        assertTrue(hnswIndex.isBuilt(TEST_DB_ID));
        assertEquals(0, hnswIndex.size(TEST_DB_ID));
    }
    
    @Test
    void testBuildIndexWithVectors() {
        // Add test vectors
        VectorEntry vector1 = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        VectorEntry vector2 = createTestVector(2L, new float[]{0.0f, 1.0f, 0.0f});
        VectorEntry vector3 = createTestVector(3L, new float[]{0.5f, 0.5f, 0.0f});
        
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        hnswIndex.add(vector3, TEST_DB_ID);
        
        assertEquals(3, hnswIndex.size(TEST_DB_ID));
        assertFalse(hnswIndex.isBuilt(TEST_DB_ID));
        
        hnswIndex.build(TEST_DB_ID);
        
        assertTrue(hnswIndex.isBuilt(TEST_DB_ID));
        assertEquals(3, hnswIndex.size(TEST_DB_ID));
    }
    
    @Test
    void testSearchOnBuiltIndex() {
        // Add and build index
        VectorEntry vector1 = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        VectorEntry vector2 = createTestVector(2L, new float[]{0.0f, 1.0f, 0.0f});
        VectorEntry vector3 = createTestVector(3L, new float[]{0.5f, 0.5f, 0.0f});
        
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        hnswIndex.add(vector3, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);
        
        // Search for similar vector
        float[] queryVector = {0.6f, 0.4f, 0.0f};
        List<SearchResult> results = hnswIndex.search(queryVector, 2, TEST_DB_ID);
        
        assertNotNull(results);
        assertEquals(2, results.size());
        
        // Verify results are ordered by distance (ascending = better)
        assertTrue(results.get(0).distance() <= results.get(1).distance());
        
        // Verify similarity scores are reasonable (between 0 and 1)
        for (SearchResult result : results) {
            assertTrue(result.similarity() >= 0.0);
            assertTrue(result.similarity() <= 1.0);
        }
    }
    
    @Test
    void testSearchOnUnbuiltIndex() {
        // Add vectors but don't build
        VectorEntry vector1 = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        
        hnswIndex.add(vector1, TEST_DB_ID);
        
        // Should still work with linear search fallback
        float[] queryVector = {1.0f, 0.0f, 0.0f};
        List<SearchResult> results = hnswIndex.search(queryVector, 1, TEST_DB_ID);
        
        assertEquals(1, results.size());
        assertEquals(1L, results.getFirst().entry().id());
    }
    
    @Test
    void testAddToBuiltIndex() {
        // Build empty index first
        hnswIndex.build(TEST_DB_ID);
        assertTrue(hnswIndex.isBuilt(TEST_DB_ID));
        
        // Add vector to built index
        VectorEntry vector = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        
        hnswIndex.add(vector, TEST_DB_ID);
        assertEquals(1, hnswIndex.size(TEST_DB_ID));
        assertTrue(hnswIndex.isBuilt(TEST_DB_ID));
    }
    
    @Test
    void testDimensionConsistency() {
        VectorEntry vector1 = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        VectorEntry vector2 = createTestVector(2L, new float[]{1.0f, 0.0f}); // Different dimension
        
        hnswIndex.add(vector1, TEST_DB_ID);
        
        // Should throw exception for dimension mismatch
        assertThrows(IllegalArgumentException.class, () -> hnswIndex.add(vector2, TEST_DB_ID));
    }
    
    @Test
    void testSearchDimensionMismatch() {
        VectorEntry vector = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        
        hnswIndex.add(vector, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);
        
        // Query with different dimension should throw exception
        float[] wrongDimensionQuery = {1.0f, 0.0f};
        assertThrows(IllegalArgumentException.class, () -> hnswIndex.search(wrongDimensionQuery, 1, TEST_DB_ID));
    }

    @Test
    void testSearchDimensionMismatchOnUnbuiltIndex() {
        VectorEntry vector = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});

        hnswIndex.add(vector, TEST_DB_ID);
        float[] wrongDimensionQuery = {1.0f, 0.0f};
        assertThrows(IllegalArgumentException.class, () -> hnswIndex.search(wrongDimensionQuery, 1, TEST_DB_ID));
    }

    @Test
    void testSaveAndLoad() {
        // Build index with test data
        VectorEntry vector1 = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        VectorEntry vector2 = createTestVector(2L, new float[]{0.0f, 1.0f, 0.0f});
        
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);
        
        // Save index
        String indexPath = tempDir.resolve("test-index.hnsw").toString();
        hnswIndex.save(indexPath, TEST_DB_ID);
        
        // Create new index and load
        HnswVectorIndex loadedIndex = new HnswVectorIndex(
            vectorSimilarity, 1000, 16, 200, 100, "cosine"
        );
        loadedIndex.load(indexPath, TEST_DB_ID);
        
        // Verify loaded index
        assertTrue(loadedIndex.isBuilt(TEST_DB_ID));
        assertEquals(2, loadedIndex.size(TEST_DB_ID));
        
        // Test search on loaded index
        float[] queryVector = {1.0f, 0.0f, 0.0f};
        List<SearchResult> results = loadedIndex.search(queryVector, 1, TEST_DB_ID);
        
        assertEquals(1, results.size());
        assertEquals(1L, results.getFirst().entry().id());
    }
    
    @Test
    void testSaveUnbuiltIndex() {
        VectorEntry vector = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        
        hnswIndex.add(vector, TEST_DB_ID);
        // Don't build index
        
        String indexPath = tempDir.resolve("test-index.hnsw").toString();
        
        // Should throw exception when trying to save unbuilt index
        assertThrows(IllegalStateException.class, () -> hnswIndex.save(indexPath, TEST_DB_ID));
    }
    
    @Test
    void testLoadNonexistentFile() {
        String nonexistentPath = tempDir.resolve("nonexistent.hnsw").toString();
        
        assertThrows(RuntimeException.class, () -> hnswIndex.load(nonexistentPath, TEST_DB_ID));
    }
    
    @Test
    void testClearIndex() {
        // Add vectors and build
        VectorEntry vector = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        
        hnswIndex.add(vector, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);
        
        assertTrue(hnswIndex.isBuilt(TEST_DB_ID));
        assertEquals(1, hnswIndex.size(TEST_DB_ID));
        
        // Clear index
        hnswIndex.clear(TEST_DB_ID);
        
        assertFalse(hnswIndex.isBuilt(TEST_DB_ID));
        assertEquals(0, hnswIndex.size(TEST_DB_ID));
    }
    
    @Test
    void testDifferentSpaceTypes() {
        // Test Euclidean space
        HnswVectorIndex euclideanIndex = new HnswVectorIndex(
            vectorSimilarity, 1000, 16, 200, 100, "euclidean"
        );
        euclideanIndex.setDimension(3);

        VectorEntry vector = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        
        euclideanIndex.add(vector, TEST_DB_ID);
        euclideanIndex.build(TEST_DB_ID);
        
        float[] queryVector = {1.0f, 0.0f, 0.0f};
        List<SearchResult> results = euclideanIndex.search(queryVector, 1, TEST_DB_ID);
        
        assertEquals(1, results.size());
        assertTrue(results.getFirst().distance() >= 0.0);
    }
    
    @Test
    void testManhattanDistance() {
        HnswVectorIndex manhattanIndex = new HnswVectorIndex(
            vectorSimilarity, 1000, 16, 200, 100, "manhattan"
        );
        manhattanIndex.setDimension(3);

        VectorEntry vector = createTestVector(1L, new float[]{1.0f, 1.0f, 0.0f});
        
        manhattanIndex.add(vector, TEST_DB_ID);
        manhattanIndex.build(TEST_DB_ID);
        
        float[] queryVector = {0.0f, 0.0f, 0.0f};
        List<SearchResult> results = manhattanIndex.search(queryVector, 1, TEST_DB_ID);
        
        assertEquals(1, results.size());
        assertEquals(2.0, results.getFirst().distance(), 0.001); // Manhattan distance should be 2.0
    }
    
    @Test
    void testRemoveVectorFromUnbuiltIndex() {
        VectorEntry vector1 = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        VectorEntry vector2 = createTestVector(2L, new float[]{0.0f, 1.0f, 0.0f});
        
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        assertEquals(2, hnswIndex.size(TEST_DB_ID));
        
        // Remove from unbuilt index (should remove from buffer)
        boolean removed = hnswIndex.remove(1L, TEST_DB_ID);
        assertTrue(removed);
        assertEquals(1, hnswIndex.size(TEST_DB_ID));
        
        // Try to remove non-existent vector
        boolean notRemoved = hnswIndex.remove(999L, TEST_DB_ID);
        assertFalse(notRemoved);
        assertEquals(1, hnswIndex.size(TEST_DB_ID));
    }
    
    @Test
    void testRemoveVectorFromBuiltIndex() {
        // Add and build index
        VectorEntry vector1 = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        VectorEntry vector2 = createTestVector(2L, new float[]{0.0f, 1.0f, 0.0f});
        VectorEntry vector3 = createTestVector(3L, new float[]{0.5f, 0.5f, 0.0f});
        
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        hnswIndex.add(vector3, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);
        
        assertEquals(3, hnswIndex.size(TEST_DB_ID));
        assertTrue(hnswIndex.isBuilt(TEST_DB_ID));
        
        // Remove from built index
        boolean removed = hnswIndex.remove(2L, TEST_DB_ID);
        assertTrue(removed);
        assertEquals(2, hnswIndex.size(TEST_DB_ID));
        
        // Verify vector is actually removed by searching
        float[] queryVector = {0.0f, 1.0f, 0.0f}; // Should be closest to removed vector
        List<SearchResult> results = hnswIndex.search(queryVector, 3, TEST_DB_ID);
        
        // Should not find the removed vector in results
        assertFalse(results.stream().anyMatch(r -> r.entry().id().equals(2L)));
        
        // Try to remove non-existent vector
        boolean notRemoved = hnswIndex.remove(999L, TEST_DB_ID);
        assertFalse(notRemoved);
    }
    
    @Test
    void testRemoveAndAddToBuiltIndex() {
        // Add and build index
        VectorEntry vector1 = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        VectorEntry vector2 = createTestVector(2L, new float[]{0.0f, 1.0f, 0.0f});
        
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);
        
        // Remove one vector
        boolean removed = hnswIndex.remove(1L, TEST_DB_ID);
        assertTrue(removed);
        assertEquals(1, hnswIndex.size(TEST_DB_ID));
        
        // Add a new vector
        VectorEntry vector3 = createTestVector(3L, new float[]{0.5f, 0.5f, 0.0f});
        
        hnswIndex.add(vector3, TEST_DB_ID);
        assertEquals(2, hnswIndex.size(TEST_DB_ID));
        
        // Search should work with both remaining vectors
        float[] queryVector = {0.0f, 0.0f, 1.0f};
        List<SearchResult> results = hnswIndex.search(queryVector, 2, TEST_DB_ID);
        
        assertEquals(2, results.size());
        assertFalse(results.stream().anyMatch(r -> r.entry().id().equals(1L)));
        assertTrue(results.stream().anyMatch(r -> r.entry().id().equals(2L)));
        assertTrue(results.stream().anyMatch(r -> r.entry().id().equals(3L)));
    }

    @Test
    void testAddVectorWithoutSettingDimension() {
        // Create new index without setting dimension
        HnswVectorIndex newIndex = new HnswVectorIndex(
            vectorSimilarity, 1000, 16, 200, 100, "cosine"
        );

        VectorEntry vector = VectorEntry.builder()
            .id(1L)
            .embedding(new float[]{1.0f, 0.0f, 0.0f})
            .originalData("test")
            .databaseId(TEST_DB_ID)
            .createdAt(Instant.now())
            .build();

        // Should throw exception when trying to add vector without setting dimension
        assertThrows(IllegalStateException.class, () -> newIndex.add(vector, TEST_DB_ID));
    }

    @Test
    void testBuildIndexWithoutSettingDimension() {
        // Create new index without setting dimension
        HnswVectorIndex newIndex = new HnswVectorIndex(
            vectorSimilarity, 1000, 16, 200, 100, "cosine"
        );

        // Should throw exception when trying to build index without setting dimension
        assertThrows(RuntimeException.class, () -> newIndex.build(TEST_DB_ID));
    }

    @Test
    void testSetDimensionAfterBuilding() {
        hnswIndex.build(TEST_DB_ID);

        // Should throw exception when trying to set dimension after building
        assertThrows(IllegalStateException.class, () -> hnswIndex.setDimension(5));
    }

    @Test
    void testSetInvalidDimension() {
        // Should throw exception for invalid dimension
        assertThrows(IllegalArgumentException.class, () -> hnswIndex.setDimension(0));
        assertThrows(IllegalArgumentException.class, () -> hnswIndex.setDimension(-1));
    }

    @Test
    void testMultipleDatabasesIsolation() {
        // Add vectors to first database - use IDs 1000-1999 for db1
        VectorEntry vector1 = createTestVector(1001L, new float[]{1.0f, 0.0f, 0.0f});
        VectorEntry vector2 = createTestVector(1002L, new float[]{0.0f, 1.0f, 0.0f});
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);

        // Add vectors to second database - use IDs 2000-2999 for db2
        VectorEntry vector3 = createTestVector(2001L, new float[]{0.5f, 0.5f, 0.0f});
        VectorEntry vector4 = createTestVector(2002L, new float[]{0.3f, 0.7f, 0.0f});
        hnswIndex.add(vector3, TEST_DB_ID_2);
        hnswIndex.add(vector4, TEST_DB_ID_2);
        hnswIndex.build(TEST_DB_ID_2);

        // Verify sizes
        assertEquals(2, hnswIndex.size(TEST_DB_ID));
        assertEquals(2, hnswIndex.size(TEST_DB_ID_2));

        // Search in first database should only return vectors from first database
        float[] queryVector = {1.0f, 0.0f, 0.0f};
        List<SearchResult> resultsDb1 = hnswIndex.search(queryVector, 10, TEST_DB_ID);

        assertEquals(2, resultsDb1.size());
        assertTrue(resultsDb1.stream().allMatch(r -> r.entry().id() >= 1000L && r.entry().id() < 2000L));
        assertFalse(resultsDb1.stream().anyMatch(r -> r.entry().id() >= 2000L));
        // Search in second database should only return vectors from second database
        List<SearchResult> resultsDb2 = hnswIndex.search(queryVector, 10, TEST_DB_ID_2);

        assertEquals(2, resultsDb2.size());
        assertTrue(resultsDb2.stream().allMatch(r -> r.entry().id() >= 2000L && r.entry().id() < 3000L));
        assertFalse(resultsDb2.stream().anyMatch(r -> r.entry().id() >= 1000L && r.entry().id() < 2000L));
    }

    @Test
    void testClearAllDatabases() {
        // Add vectors to multiple databases
        VectorEntry vector1 = createTestVector(1L, new float[]{1.0f, 0.0f, 0.0f});
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);

        hnswIndex.add(vector1, TEST_DB_ID_2);
        hnswIndex.build(TEST_DB_ID_2);

        assertEquals(1, hnswIndex.size(TEST_DB_ID));
        assertEquals(1, hnswIndex.size(TEST_DB_ID_2));

        // Clear all
        hnswIndex.clearAll();

        assertEquals(0, hnswIndex.size(TEST_DB_ID));
        assertEquals(0, hnswIndex.size(TEST_DB_ID_2));
        assertFalse(hnswIndex.isBuilt(TEST_DB_ID));
        assertFalse(hnswIndex.isBuilt(TEST_DB_ID_2));
    }
}