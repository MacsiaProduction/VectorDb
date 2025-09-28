package com.vectordb.storage.index;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchResult;

import java.util.List;

/**
 * Interface for vector indexing operations.
 * This abstraction allows for different indexing implementations (HNSW, IVF, LSH, etc.)
 * while maintaining a consistent API for the storage service.
 */
public interface VectorIndex {
    
    /**
     * Build the index with existing vectors
     */
    void build();
    
    /**
     * Add a vector to the index
     */
    void add(VectorEntry vector);
    
    /**
     * Remove a vector from the index
     * @param vectorId ID of the vector to remove
     * @return true if vector was found and removed, false otherwise
     */
    boolean remove(String vectorId);
    
    /**
     * Search for k nearest neighbors
     * @param queryVector the query vector
     * @param k number of neighbors to return
     * @return list of search results sorted by distance (ascending)
     */
    List<SearchResult> search(double[] queryVector, int k);
    
    /**
     * Save the index to a file
     * @param filePath path to save the index
     */
    void save(String filePath);
    
    /**
     * Load the index from a file
     * @param filePath path to load the index from
     */
    void load(String filePath);
    
    /**
     * Get the number of vectors in the index
     */
    int size();
    
    /**
     * Clear the index
     */
    void clear();
    
    /**
     * Check if the index is built
     */
    boolean isBuilt();
}