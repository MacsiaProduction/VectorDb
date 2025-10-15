package com.vectordb.storage.index;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchResult;
import com.vectordb.storage.similarity.VectorSimilarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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
    
    @Test
    void testIndexCreation() {
        assertNotNull(hnswIndex);
        assertFalse(hnswIndex.isBuilt(TEST_DB_ID));
        assertEquals(0, hnswIndex.size(TEST_DB_ID));
    }
    
    @Test
    void testAddVectorToUnbuiltIndex() {
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
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
        VectorEntry vector1 = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        VectorEntry vector2 = VectorEntry.builder()
            .id("test2")
            .embedding(new double[]{0.0, 1.0, 0.0})
            .build();
        
        VectorEntry vector3 = VectorEntry.builder()
            .id("test3")
            .embedding(new double[]{0.5, 0.5, 0.0})
            .build();
        
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
        VectorEntry vector1 = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        VectorEntry vector2 = VectorEntry.builder()
            .id("test2")
            .embedding(new double[]{0.0, 1.0, 0.0})
            .build();
        
        VectorEntry vector3 = VectorEntry.builder()
            .id("test3")
            .embedding(new double[]{0.5, 0.5, 0.0})
            .build();
        
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        hnswIndex.add(vector3, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);
        
        // Search for similar vector
        double[] queryVector = {0.6, 0.4, 0.0};
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
        VectorEntry vector1 = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        hnswIndex.add(vector1, TEST_DB_ID);
        
        // Should still work with linear search fallback
        double[] queryVector = {1.0, 0.0, 0.0};
        List<SearchResult> results = hnswIndex.search(queryVector, 1, TEST_DB_ID);
        
        assertEquals(1, results.size());
        assertEquals("test1", results.getFirst().entry().id());
    }
    
    @Test
    void testAddToBuiltIndex() {
        // Build empty index first
        hnswIndex.build(TEST_DB_ID);
        assertTrue(hnswIndex.isBuilt(TEST_DB_ID));
        
        // Add vector to built index
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        hnswIndex.add(vector, TEST_DB_ID);
        assertEquals(1, hnswIndex.size(TEST_DB_ID));
        assertTrue(hnswIndex.isBuilt(TEST_DB_ID));
    }
    
    @Test
    void testDimensionConsistency() {
        VectorEntry vector1 = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        VectorEntry vector2 = VectorEntry.builder()
            .id("test2")
            .embedding(new double[]{1.0, 0.0}) // Different dimension
            .build();
        
        hnswIndex.add(vector1, TEST_DB_ID);
        
        // Should throw exception for dimension mismatch
        assertThrows(IllegalArgumentException.class, () -> hnswIndex.add(vector2, TEST_DB_ID));
    }
    
    @Test
    void testSearchDimensionMismatch() {
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        hnswIndex.add(vector, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);
        
        // Query with different dimension should throw exception
        double[] wrongDimensionQuery = {1.0, 0.0};
        assertThrows(IllegalArgumentException.class, () -> hnswIndex.search(wrongDimensionQuery, 1, TEST_DB_ID));
    }
    
    @Test
    void testSearchDimensionMismatchOnUnbuiltIndex() {
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        hnswIndex.add(vector, TEST_DB_ID);
        double[] wrongDimensionQuery = {1.0, 0.0};
        assertThrows(IllegalArgumentException.class, () -> hnswIndex.search(wrongDimensionQuery, 1, TEST_DB_ID));
    }
    
    @Test
    void testSaveAndLoad() {
        // Build index with test data
        VectorEntry vector1 = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        VectorEntry vector2 = VectorEntry.builder()
            .id("test2")
            .embedding(new double[]{0.0, 1.0, 0.0})
            .build();
        
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
        double[] queryVector = {1.0, 0.0, 0.0};
        List<SearchResult> results = loadedIndex.search(queryVector, 1, TEST_DB_ID);
        
        assertEquals(1, results.size());
        assertEquals("test1", results.getFirst().entry().id());
    }
    
    @Test
    void testSaveUnbuiltIndex() {
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
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
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
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
        
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        euclideanIndex.add(vector, TEST_DB_ID);
        euclideanIndex.build(TEST_DB_ID);
        
        double[] queryVector = {1.0, 0.0, 0.0};
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
        
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 1.0, 0.0})
            .build();
        
        manhattanIndex.add(vector, TEST_DB_ID);
        manhattanIndex.build(TEST_DB_ID);
        
        double[] queryVector = {0.0, 0.0, 0.0};
        List<SearchResult> results = manhattanIndex.search(queryVector, 1, TEST_DB_ID);
        
        assertEquals(1, results.size());
        assertEquals(2.0, results.getFirst().distance(), 0.001); // Manhattan distance should be 2.0
    }
    
    @Test
    void testRemoveVectorFromUnbuiltIndex() {
        VectorEntry vector1 = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        VectorEntry vector2 = VectorEntry.builder()
            .id("test2")
            .embedding(new double[]{0.0, 1.0, 0.0})
            .build();
        
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        assertEquals(2, hnswIndex.size(TEST_DB_ID));
        
        // Remove from unbuilt index (should remove from buffer)
        boolean removed = hnswIndex.remove("test1", TEST_DB_ID);
        assertTrue(removed);
        assertEquals(1, hnswIndex.size(TEST_DB_ID));
        
        // Try to remove non-existent vector
        boolean notRemoved = hnswIndex.remove("nonexistent", TEST_DB_ID);
        assertFalse(notRemoved);
        assertEquals(1, hnswIndex.size(TEST_DB_ID));
    }
    
    @Test
    void testRemoveVectorFromBuiltIndex() {
        // Add and build index
        VectorEntry vector1 = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        VectorEntry vector2 = VectorEntry.builder()
            .id("test2")
            .embedding(new double[]{0.0, 1.0, 0.0})
            .build();
        
        VectorEntry vector3 = VectorEntry.builder()
            .id("test3")
            .embedding(new double[]{0.5, 0.5, 0.0})
            .build();
        
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        hnswIndex.add(vector3, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);
        
        assertEquals(3, hnswIndex.size(TEST_DB_ID));
        assertTrue(hnswIndex.isBuilt(TEST_DB_ID));
        
        // Remove from built index
        boolean removed = hnswIndex.remove("test2", TEST_DB_ID);
        assertTrue(removed);
        assertEquals(2, hnswIndex.size(TEST_DB_ID));
        
        // Verify vector is actually removed by searching
        double[] queryVector = {0.0, 1.0, 0.0}; // Should be closest to removed vector
        List<SearchResult> results = hnswIndex.search(queryVector, 3, TEST_DB_ID);
        
        // Should not find the removed vector in results
        assertFalse(results.stream().anyMatch(r -> r.entry().id().equals("test2")));
        
        // Try to remove non-existent vector
        boolean notRemoved = hnswIndex.remove("nonexistent", TEST_DB_ID);
        assertFalse(notRemoved);
    }
    
    @Test
    void testRemoveAndAddToBuiltIndex() {
        // Add and build index
        VectorEntry vector1 = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        VectorEntry vector2 = VectorEntry.builder()
            .id("test2")
            .embedding(new double[]{0.0, 1.0, 0.0})
            .build();
        
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);
        
        // Remove one vector
        boolean removed = hnswIndex.remove("test1", TEST_DB_ID);
        assertTrue(removed);
        assertEquals(1, hnswIndex.size(TEST_DB_ID));
        
        // Add a new vector
        VectorEntry vector3 = VectorEntry.builder()
            .id("test3")
            .embedding(new double[]{0.5, 0.5, 0.0})
            .build();
        
        hnswIndex.add(vector3, TEST_DB_ID);
        assertEquals(2, hnswIndex.size(TEST_DB_ID));
        
        // Search should work with both remaining vectors
        double[] queryVector = {0.0, 0.0, 1.0};
        List<SearchResult> results = hnswIndex.search(queryVector, 2, TEST_DB_ID);
        
        assertEquals(2, results.size());
        assertFalse(results.stream().anyMatch(r -> r.entry().id().equals("test1")));
        assertTrue(results.stream().anyMatch(r -> r.entry().id().equals("test2")));
        assertTrue(results.stream().anyMatch(r -> r.entry().id().equals("test3")));
    }
    
    @Test
    void testAddVectorWithoutSettingDimension() {
        // Create new index without setting dimension
        HnswVectorIndex newIndex = new HnswVectorIndex(
            vectorSimilarity, 1000, 16, 200, 100, "cosine"
        );
        
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
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
        // Add vectors to first database
        VectorEntry vector1 = VectorEntry.builder()
            .id("db1-vec1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        VectorEntry vector2 = VectorEntry.builder()
            .id("db1-vec2")
            .embedding(new double[]{0.0, 1.0, 0.0})
            .build();
        
        hnswIndex.add(vector1, TEST_DB_ID);
        hnswIndex.add(vector2, TEST_DB_ID);
        hnswIndex.build(TEST_DB_ID);
        
        // Add vectors to second database
        VectorEntry vector3 = VectorEntry.builder()
            .id("db2-vec1")
            .embedding(new double[]{0.5, 0.5, 0.0})
            .build();
        
        VectorEntry vector4 = VectorEntry.builder()
            .id("db2-vec2")
            .embedding(new double[]{0.3, 0.7, 0.0})
            .build();
        
        hnswIndex.add(vector3, TEST_DB_ID_2);
        hnswIndex.add(vector4, TEST_DB_ID_2);
        hnswIndex.build(TEST_DB_ID_2);
        
        // Verify sizes
        assertEquals(2, hnswIndex.size(TEST_DB_ID));
        assertEquals(2, hnswIndex.size(TEST_DB_ID_2));
        
        // Search in first database should only return vectors from first database
        double[] queryVector = {1.0, 0.0, 0.0};
        List<SearchResult> resultsDb1 = hnswIndex.search(queryVector, 10, TEST_DB_ID);
        
        assertEquals(2, resultsDb1.size());
        assertTrue(resultsDb1.stream().allMatch(r -> r.entry().id().startsWith("db1-")));
        assertFalse(resultsDb1.stream().anyMatch(r -> r.entry().id().startsWith("db2-")));
        
        // Search in second database should only return vectors from second database
        List<SearchResult> resultsDb2 = hnswIndex.search(queryVector, 10, TEST_DB_ID_2);
        
        assertEquals(2, resultsDb2.size());
        assertTrue(resultsDb2.stream().allMatch(r -> r.entry().id().startsWith("db2-")));
        assertFalse(resultsDb2.stream().anyMatch(r -> r.entry().id().startsWith("db1-")));
    }
    
    @Test
    void testClearAllDatabases() {
        // Add vectors to multiple databases
        VectorEntry vector1 = VectorEntry.builder()
            .id("vec1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
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