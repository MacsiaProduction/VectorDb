package com.vectordb.storage.index;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchResult;
import com.vectordb.storage.similarity.VectorSimilarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit tests for HNSW index functionality
 */
class HnswVectorIndexTest {
    
    private HnswVectorIndex hnswIndex;
    private VectorSimilarity vectorSimilarity;
    
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
    }
    
    @Test
    void testIndexCreation() {
        assertNotNull(hnswIndex);
        assertFalse(hnswIndex.isBuilt());
        assertEquals(0, hnswIndex.size());
    }
    
    @Test
    void testAddVectorToUnbuiltIndex() {
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        hnswIndex.add(vector);
        
        assertEquals(1, hnswIndex.size());
        assertFalse(hnswIndex.isBuilt());
    }
    
    @Test
    void testBuildEmptyIndex() {
        hnswIndex.build();
        
        assertTrue(hnswIndex.isBuilt());
        assertEquals(0, hnswIndex.size());
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
        
        hnswIndex.add(vector1);
        hnswIndex.add(vector2);
        hnswIndex.add(vector3);
        
        assertEquals(3, hnswIndex.size());
        assertFalse(hnswIndex.isBuilt());
        
        hnswIndex.build();
        
        assertTrue(hnswIndex.isBuilt());
        assertEquals(3, hnswIndex.size());
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
        
        hnswIndex.add(vector1);
        hnswIndex.add(vector2);
        hnswIndex.add(vector3);
        hnswIndex.build();
        
        // Search for similar vector
        double[] queryVector = {0.6, 0.4, 0.0};
        List<SearchResult> results = hnswIndex.search(queryVector, 2);
        
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
        
        hnswIndex.add(vector1);
        
        // Should still work with linear search fallback
        double[] queryVector = {1.0, 0.0, 0.0};
        List<SearchResult> results = hnswIndex.search(queryVector, 1);
        
        assertEquals(1, results.size());
        assertEquals("test1", results.getFirst().entry().id());
    }
    
    @Test
    void testAddToBuiltIndex() {
        // Build empty index first
        hnswIndex.build();
        assertTrue(hnswIndex.isBuilt());
        
        // Add vector to built index
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        hnswIndex.add(vector);
        assertEquals(1, hnswIndex.size());
        assertTrue(hnswIndex.isBuilt());
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
        
        hnswIndex.add(vector1);
        
        // Should throw exception for dimension mismatch
        assertThrows(IllegalArgumentException.class, () -> hnswIndex.add(vector2));
    }
    
    @Test
    void testSearchDimensionMismatch() {
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        hnswIndex.add(vector);
        hnswIndex.build();
        
        // Query with different dimension should throw exception
        double[] wrongDimensionQuery = {1.0, 0.0};
        assertThrows(IllegalArgumentException.class, () -> hnswIndex.search(wrongDimensionQuery, 1));
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
        
        hnswIndex.add(vector1);
        hnswIndex.add(vector2);
        hnswIndex.build();
        
        // Save index
        String indexPath = tempDir.resolve("test-index.hnsw").toString();
        hnswIndex.save(indexPath);
        
        // Create new index and load
        HnswVectorIndex loadedIndex = new HnswVectorIndex(
            vectorSimilarity, 1000, 16, 200, 100, "cosine"
        );
        loadedIndex.load(indexPath);
        
        // Verify loaded index
        assertTrue(loadedIndex.isBuilt());
        assertEquals(2, loadedIndex.size());
        
        // Test search on loaded index
        double[] queryVector = {1.0, 0.0, 0.0};
        List<SearchResult> results = loadedIndex.search(queryVector, 1);
        
        assertEquals(1, results.size());
        assertEquals("test1", results.getFirst().entry().id());
    }
    
    @Test
    void testSaveUnbuiltIndex() {
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        hnswIndex.add(vector);
        // Don't build index
        
        String indexPath = tempDir.resolve("test-index.hnsw").toString();
        
        // Should throw exception when trying to save unbuilt index
        assertThrows(IllegalStateException.class, () -> hnswIndex.save(indexPath));
    }
    
    @Test
    void testLoadNonexistentFile() {
        String nonexistentPath = tempDir.resolve("nonexistent.hnsw").toString();
        
        assertThrows(RuntimeException.class, () -> hnswIndex.load(nonexistentPath));
    }
    
    @Test
    void testClearIndex() {
        // Add vectors and build
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        hnswIndex.add(vector);
        hnswIndex.build();
        
        assertTrue(hnswIndex.isBuilt());
        assertEquals(1, hnswIndex.size());
        
        // Clear index
        hnswIndex.clear();
        
        assertFalse(hnswIndex.isBuilt());
        assertEquals(0, hnswIndex.size());
    }
    
    @Test
    void testDifferentSpaceTypes() {
        // Test Euclidean space
        HnswVectorIndex euclideanIndex = new HnswVectorIndex(
            vectorSimilarity, 1000, 16, 200, 100, "euclidean"
        );
        
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 0.0, 0.0})
            .build();
        
        euclideanIndex.add(vector);
        euclideanIndex.build();
        
        double[] queryVector = {1.0, 0.0, 0.0};
        List<SearchResult> results = euclideanIndex.search(queryVector, 1);
        
        assertEquals(1, results.size());
        assertTrue(results.getFirst().distance() >= 0.0);
    }
    
    @Test
    void testManhattanDistance() {
        HnswVectorIndex manhattanIndex = new HnswVectorIndex(
            vectorSimilarity, 1000, 16, 200, 100, "manhattan"
        );
        
        VectorEntry vector = VectorEntry.builder()
            .id("test1")
            .embedding(new double[]{1.0, 1.0, 0.0})
            .build();
        
        manhattanIndex.add(vector);
        manhattanIndex.build();
        
        double[] queryVector = {0.0, 0.0, 0.0};
        List<SearchResult> results = manhattanIndex.search(queryVector, 1);
        
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
        
        hnswIndex.add(vector1);
        hnswIndex.add(vector2);
        assertEquals(2, hnswIndex.size());
        
        // Remove from unbuilt index (should remove from buffer)
        boolean removed = hnswIndex.remove("test1");
        assertTrue(removed);
        assertEquals(1, hnswIndex.size());
        
        // Try to remove non-existent vector
        boolean notRemoved = hnswIndex.remove("nonexistent");
        assertFalse(notRemoved);
        assertEquals(1, hnswIndex.size());
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
        
        hnswIndex.add(vector1);
        hnswIndex.add(vector2);
        hnswIndex.add(vector3);
        hnswIndex.build();
        
        assertEquals(3, hnswIndex.size());
        assertTrue(hnswIndex.isBuilt());
        
        // Remove from built index
        boolean removed = hnswIndex.remove("test2");
        assertTrue(removed);
        assertEquals(2, hnswIndex.size());
        
        // Verify vector is actually removed by searching
        double[] queryVector = {0.0, 1.0, 0.0}; // Should be closest to removed vector
        List<SearchResult> results = hnswIndex.search(queryVector, 3);
        
        // Should not find the removed vector in results
        assertFalse(results.stream().anyMatch(r -> r.entry().id().equals("test2")));
        
        // Try to remove non-existent vector
        boolean notRemoved = hnswIndex.remove("nonexistent");
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
        
        hnswIndex.add(vector1);
        hnswIndex.add(vector2);
        hnswIndex.build();
        
        // Remove one vector
        boolean removed = hnswIndex.remove("test1");
        assertTrue(removed);
        assertEquals(1, hnswIndex.size());
        
        // Add a new vector
        VectorEntry vector3 = VectorEntry.builder()
            .id("test3")
            .embedding(new double[]{0.5, 0.5, 0.0})
            .build();
        
        hnswIndex.add(vector3);
        assertEquals(2, hnswIndex.size());
        
        // Search should work with both remaining vectors
        double[] queryVector = {0.0, 0.0, 1.0};
        List<SearchResult> results = hnswIndex.search(queryVector, 2);
        
        assertEquals(2, results.size());
        assertFalse(results.stream().anyMatch(r -> r.entry().id().equals("test1")));
        assertTrue(results.stream().anyMatch(r -> r.entry().id().equals("test2")));
        assertTrue(results.stream().anyMatch(r -> r.entry().id().equals("test3")));
    }
}