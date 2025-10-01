package com.vectordb.main.repository;

import com.vectordb.main.model.VectorEntry;

import java.util.List;

/**
 * Repository interface for vector storage operations.
 * This interface defines the contract for vector storage implementations.
 */
public interface VectorRepository {
    
    /**
     * Find top K similar vectors to the given query vector
     * @param vector query vector
     * @param k number of similar vectors to return
     * @param dbId database identifier
     * @return list of similar vectors
     */
    List<VectorEntry> getTopKSimilar(double[] vector, int k, String dbId);
    
    /**
     * Save a vector entry to the specified database
     * @param vectorEntry vector entry to save
     * @param dbId database identifier
     * @return generated ID for the saved vector
     */
    String add(VectorEntry vectorEntry, String dbId);
    
    /**
     * Delete a vector by ID from the specified database
     * @param id vector ID to delete
     * @param dbId database identifier
     * @return true if vector was deleted, false otherwise
     */
    boolean deleteById(String id, String dbId);
    
    /**
     * Create a new database
     * @param dbId database identifier
     * @return true if database was created successfully
     */
    boolean createDatabase(String dbId);
    
    /**
     * Drop a database
     * @param dbId database identifier
     * @return true if database was dropped successfully
     */
    boolean dropDatabase(String dbId);
    
    /**
     * Get list of all available database IDs
     * @return list of database IDs
     */
    List<String> getAllDatabaseIds();
    
    /**
     * Check if database exists
     * @param dbId database identifier
     * @return true if database exists, false otherwise
     */
    boolean databaseExists(String dbId);
}
