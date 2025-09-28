package com.vectordb.storage.index;

import com.github.jelmerk.hnswlib.core.DistanceFunction;
import com.github.jelmerk.hnswlib.core.DistanceFunctions;
import com.github.jelmerk.hnswlib.core.SearchResult;
import com.github.jelmerk.hnswlib.core.hnsw.HnswIndex;
import com.vectordb.common.model.VectorEntry;
import com.vectordb.storage.similarity.VectorSimilarity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * HNSW index implementation using the pure Java hnswlib library.
 * This provides efficient approximate nearest neighbor search for high-dimensional vector data.
 */
@Component
@Slf4j
public class HnswVectorIndex implements VectorIndex {
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final VectorSimilarity vectorSimilarity;
    
    // HNSW parameters
    private final int maxElements;
    private final int m;
    private final int efConstruction;
    private final int efSearch;
    private final String spaceType;
    
    // Internal state
    private boolean isBuilt = false;
    private int dimension = -1;
    private final List<VectorEntry> vectorBuffer = new ArrayList<>();
    private HnswIndex<String, float[], VectorItem, Float> hnswIndex;
    private final Map<String, VectorEntry> idToEntry = new HashMap<>();
    private final Set<String> deletedIds = new HashSet<>();
    
    public HnswVectorIndex(
            VectorSimilarity vectorSimilarity,
            @Value("${vector.index.maxElements:10000}") int maxElements,
            @Value("${vector.index.m:16}") int m,
            @Value("${vector.index.efConstruction:200}") int efConstruction,
            @Value("${vector.index.efSearch:100}") int efSearch,
            @Value("${vector.index.spaceType:cosine}") String spaceType) {
        this.vectorSimilarity = vectorSimilarity;
        this.maxElements = maxElements;
        this.m = m;
        this.efConstruction = efConstruction;
        this.efSearch = efSearch;
        this.spaceType = spaceType;
        log.info("Initialized HNSW index using pure Java hnswlib with maxElements={}, m={}, efConstruction={}, efSearch={}, spaceType={}", 
                maxElements, m, efConstruction, efSearch, spaceType);
    }
    
    @Override
    public void build() {
        lock.writeLock().lock();
        try {
            if (vectorBuffer.isEmpty()) {
                // For empty index, we still need to create a minimal HNSW index structure
                // But we need at least dimension information
                if (dimension <= 0) {
                    // Default dimension for empty index
                    dimension = 768; // Common embedding dimension
                }
                
                // Create distance function based on space type
                DistanceFunction<float[], Float> distanceFunction = createDistanceFunction();
                
                // Create HNSW index builder using pure Java hnswlib
                hnswIndex = HnswIndex.newBuilder(dimension, distanceFunction, maxElements)
                    .withM(m)
                    .withEfConstruction(efConstruction)
                    .withEf(efSearch)
                    .build();
                
                isBuilt = true;
                log.info("Built empty HNSW index with default dimension={}", dimension);
                return;
            }
            
            // Initialize dimension from first vector
            dimension = vectorBuffer.getFirst().dimension();
            log.info("Building HNSW index with {} vectors, dimension={}", vectorBuffer.size(), dimension);
            
            // Create distance function based on space type
            DistanceFunction<float[], Float> distanceFunction = createDistanceFunction();
            
            // Create HNSW index builder using pure Java hnswlib
            hnswIndex = HnswIndex.newBuilder(dimension, distanceFunction, maxElements)
                .withM(m)
                .withEfConstruction(efConstruction)
                .withEf(efSearch)
                .build();
            
            // Convert vectors to VectorItems and add to index
            List<VectorItem> items = new ArrayList<>();
            for (VectorEntry entry : vectorBuffer) {
                VectorItem item = VectorItem.fromVectorEntry(entry);
                items.add(item);
                idToEntry.put(entry.id(), entry);
            }
            
            // Add all items to the built index
            hnswIndex.addAll(items);
            
            isBuilt = true;
            vectorBuffer.clear();
            log.info("Successfully built HNSW index with {} vectors using pure Java hnswlib", items.size());
            
        } catch (Exception e) {
            log.error("Failed to build HNSW index", e);
            throw new RuntimeException("Failed to build HNSW index", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void add(VectorEntry vector) {
        lock.writeLock().lock();
        try {
            if (!isBuilt) {
                // Buffer vectors until index is built
                if (dimension == -1) {
                    dimension = vector.dimension();
                } else if (dimension != vector.dimension()) {
                    throw new IllegalArgumentException(
                        String.format("Vector dimension mismatch. Expected: %d, got: %d",
                            dimension, vector.dimension()));
                }
                vectorBuffer.add(vector);
                log.debug("Buffered vector {} for index building", vector.id());
                return;
            }
            
            // For built index with no vectors yet, we can adapt to the first vector's dimension
            if (hnswIndex.size() == 0 && idToEntry.isEmpty()) {
                // This is an empty built index, rebuild it with the correct dimension
                dimension = vector.dimension();
                
                // Create distance function based on space type
                DistanceFunction<float[], Float> distanceFunction = createDistanceFunction();
                
                // Create HNSW index builder using pure Java hnswlib
                hnswIndex = HnswIndex.newBuilder(dimension, distanceFunction, maxElements)
                    .withM(m)
                    .withEfConstruction(efConstruction)
                    .withEf(efSearch)
                    .build();
                    
                log.debug("Rebuilt empty HNSW index with dimension={}", dimension);
            } else if (dimension != vector.dimension()) {
                throw new IllegalArgumentException(
                    String.format("Vector dimension mismatch. Expected: %d, got: %d",
                        dimension, vector.dimension()));
            }
            
            // Add to built index using pure Java hnswlib
            VectorItem item = VectorItem.fromVectorEntry(vector);
            hnswIndex.add(item);
            idToEntry.put(vector.id(), vector);
            log.debug("Added vector {} to built index using pure Java hnswlib", vector.id());
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean remove(String vectorId) {
        lock.writeLock().lock();
        try {
            if (!isBuilt || hnswIndex == null) {
                // Remove from buffer if index not built yet
                boolean removed = vectorBuffer.removeIf(entry -> entry.id().equals(vectorId));
                log.debug("Removed vector {} from buffer: {}", vectorId, removed);
                return removed;
            }
            
            // For built index, check if vector exists in our mapping and not already deleted
            if (!idToEntry.containsKey(vectorId) || deletedIds.contains(vectorId)) {
                log.debug("Vector {} not found in built index or already deleted", vectorId);
                return false;
            }
            
            // Mark as deleted instead of physically removing from HNSW index
            // This is safer as some HNSW implementations don't support dynamic removal
            deletedIds.add(vectorId);
            log.debug("Marked vector {} as deleted in built HNSW index", vectorId);
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public List<com.vectordb.common.model.SearchResult> search(double[] queryVector, int k) {
        lock.readLock().lock();
        try {
            if (!isBuilt || hnswIndex == null) {
                // Fallback to linear search on buffered vectors if index is not built
                log.debug("Index not built, performing linear search on {} buffered vectors", vectorBuffer.size());
                return linearSearch(queryVector, k, vectorBuffer);
            }
            
            // Validate query vector dimension
            if (queryVector.length != dimension) {
                throw new IllegalArgumentException(
                    String.format("Query vector dimension mismatch. Expected: %d, got: %d",
                        dimension, queryVector.length));
            }
            
            log.debug("Performing HNSW search for k={} on {} indexed vectors using pure Java hnswlib", k, hnswIndex.size());
            
            // Convert query vector to float array
            float[] queryVectorFloat = doubleArrayToFloat(queryVector);
            
            // Create a temporary query item with unique ID
            String tempQueryId = "__query_" + System.nanoTime() + "__";
            VectorItem queryItem = new VectorItem(tempQueryId, queryVectorFloat, null);
            
            // Add query item temporarily to the index
            hnswIndex.add(queryItem);
            
            try {
                // Search for neighbors, requesting more than needed to account for deleted items
                int searchK = Math.min(k * 2 + 10, hnswIndex.size());
                List<SearchResult<VectorItem, Float>> allResults = hnswIndex.findNeighbors(tempQueryId, searchK);
                
                // Process the results, filtering out query item and deleted vectors
                List<com.vectordb.common.model.SearchResult> results = new ArrayList<>();
                for (SearchResult<VectorItem, Float> hnswResult : allResults) {
                    VectorItem item = hnswResult.item();
                    String itemId = item.id();
                    
                    // Skip the query item itself
                    if (itemId.equals(tempQueryId)) {
                        continue;
                    }
                    
                    // Skip deleted vectors
                    if (deletedIds.contains(itemId)) {
                        continue;
                    }
                    
                    // Get entry from our mapping or from the item itself
                    VectorEntry entry = idToEntry.get(itemId);
                    if (entry == null) {
                        entry = item.getEntry();
                    }
                    if (entry == null) {
                        log.warn("No entry found for vector ID: {}", itemId);
                        continue;
                    }
                    
                    float distance = hnswResult.distance();
                    double similarity = distanceToSimilarity(distance);
                    results.add(new com.vectordb.common.model.SearchResult(entry, distance, similarity));
                    
                    // Stop when we have enough results
                    if (results.size() >= k) {
                        break;
                    }
                }
                
                log.debug("HNSW search found {} results using pure Java hnswlib", results.size());
                return results;
                
            } finally {
                // Remove the query item from the index to keep it clean
                try {
                    hnswIndex.remove(tempQueryId, 0);
                } catch (Exception e) {
                    log.warn("Failed to remove temporary query item from index: {}", e.getMessage());
                }
            }
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void save(String filePath) {
        lock.writeLock().lock();
        try {
            if (!isBuilt || hnswIndex == null) {
                throw new IllegalStateException("Index must be built before saving");
            }
            
            // Create parent directories if they don't exist
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            
            // Save the HNSW index using a more compatible approach
            try (FileOutputStream fos = new FileOutputStream(filePath);
                 ObjectOutputStream out = new ObjectOutputStream(fos)) {
                
                // First save our metadata
                out.writeObject(idToEntry);
                out.writeInt(dimension);
                out.writeObject(spaceType);
                out.writeObject(deletedIds);
                
                // Then save the HNSW index
                hnswIndex.save(out);
                out.flush();
                
                log.info("Saved HNSW index to {} using pure Java hnswlib", filePath);
            }
            
        } catch (IOException e) {
            log.error("Failed to save index to {}", filePath, e);
            throw new RuntimeException("Failed to save index to " + filePath, e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void load(String filePath) {
        lock.writeLock().lock();
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IllegalArgumentException("Index file not found: " + filePath);
            }
            
            // Load the HNSW index using pure Java hnswlib's load functionality
            try (FileInputStream fis = new FileInputStream(filePath);
                 ObjectInputStream in = new ObjectInputStream(fis)) {
                
                // First load our metadata
                @SuppressWarnings("unchecked")
                Map<String, VectorEntry> loadedIdToEntry = (Map<String, VectorEntry>) in.readObject();
                idToEntry.clear();
                idToEntry.putAll(loadedIdToEntry);
                
                dimension = in.readInt();
                String loadedSpaceType = (String) in.readObject();
                
                @SuppressWarnings("unchecked")
                Set<String> loadedDeletedIds = (Set<String>) in.readObject();
                deletedIds.clear();
                deletedIds.addAll(loadedDeletedIds);
                
                // Then load the HNSW index
                hnswIndex = HnswIndex.load(in);
                
                isBuilt = true;
                vectorBuffer.clear();
                
                log.info("Loaded HNSW index from {} with {} vectors, dimension={} using pure Java hnswlib",
                        filePath, hnswIndex.size(), dimension);
            }
            
        } catch (IOException | ClassNotFoundException e) {
            log.error("Failed to load index from {}", filePath, e);
            throw new RuntimeException("Failed to load index from " + filePath, e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public int size() {
        lock.readLock().lock();
        try {
            if (!isBuilt || hnswIndex == null) {
                return vectorBuffer.size();
            }
            // Return size minus deleted items
            return hnswIndex.size() - deletedIds.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            hnswIndex = null;
            idToEntry.clear();
            deletedIds.clear();
            isBuilt = false;
            vectorBuffer.clear();
            dimension = -1;
            log.info("Cleared HNSW index");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean isBuilt() {
        lock.readLock().lock();
        try {
            return isBuilt;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Create distance function based on configured space type
     */
    private DistanceFunction<float[], Float> createDistanceFunction() {
        return switch (spaceType.toLowerCase()) {
            case "cosine" -> DistanceFunctions.FLOAT_COSINE_DISTANCE;
            case "euclidean", "l2" -> DistanceFunctions.FLOAT_EUCLIDEAN_DISTANCE;
            case "manhattan", "l1" -> DistanceFunctions.FLOAT_MANHATTAN_DISTANCE;
            default -> {
                log.warn("Unknown space type: {}, defaulting to cosine", spaceType);
                yield DistanceFunctions.FLOAT_COSINE_DISTANCE;
            }
        };
    }
    
    /**
     * Convert double array to float array for hnswlib compatibility
     */
    private float[] doubleArrayToFloat(double[] doubleArray) {
        float[] floatArray = new float[doubleArray.length];
        for (int i = 0; i < doubleArray.length; i++) {
            floatArray[i] = (float) doubleArray[i];
        }
        return floatArray;
    }
    
    /**
     * Convert distance to similarity score (higher is better)
     */
    private double distanceToSimilarity(double distance) {
        return switch (spaceType.toLowerCase()) {
            case "cosine" -> 1.0 - distance; // Cosine distance is 1 - cosine_similarity
            case "euclidean", "l2", "manhattan", "l1" -> 1.0 / (1.0 + distance); // Convert distance to similarity
            default -> 1.0 - distance;
        };
    }
    
    /**
     * Perform linear search on a collection of vectors (fallback method)
     */
    private List<com.vectordb.common.model.SearchResult> linearSearch(double[] queryVector, int k, List<VectorEntry> vectors) {
        List<com.vectordb.common.model.SearchResult> results = new ArrayList<>();
        
        for (VectorEntry vector : vectors) {
            if (vector.dimension() != queryVector.length) {
                continue;
            }
            
            double distance = calculateDistance(queryVector, vector.embedding());
            double similarity = distanceToSimilarity(distance);
            results.add(new com.vectordb.common.model.SearchResult(vector, distance, similarity));
        }
        
        // Sort by distance (ascending = better) and return top k
        results.sort(Comparator.comparingDouble(com.vectordb.common.model.SearchResult::distance));
        int resultSize = Math.min(k, results.size());
        
        log.debug("Linear search found {} results, returning top {}", results.size(), resultSize);
        return results.subList(0, resultSize);
    }
    
    /**
     * Calculate distance between two vectors using VectorSimilarity
     */
    private double calculateDistance(double[] a, double[] b) {
        return switch (spaceType.toLowerCase()) {
            case "cosine" -> 1.0 - vectorSimilarity.cosineSimilarity(a, b);
            case "euclidean", "l2" -> vectorSimilarity.euclideanDistance(a, b);
            case "manhattan", "l1" -> vectorSimilarity.manhattanDistance(a, b);
            default -> {
                log.warn("Unknown space type: {}, defaulting to cosine", spaceType);
                yield 1.0 - vectorSimilarity.cosineSimilarity(a, b);
            }
        };
    }
}