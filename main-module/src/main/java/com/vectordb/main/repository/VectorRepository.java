package com.vectordb.main.repository;

import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.common.model.VectorEntry;
import com.vectordb.main.exception.VectorRepositoryException;

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
     * @throws VectorRepositoryException if search fails
     */
    List<VectorEntry> getTopKSimilar(float[] vector, int k, String dbId) throws VectorRepositoryException;
    
    /**
     * Save a vector entry to the specified database
     * @param vectorEntry vector entry to save
     * @param dbId database identifier
     * @return generated ID for the saved vector
     * @throws VectorRepositoryException if save fails
     */
    Long add(VectorEntry vectorEntry, String dbId) throws VectorRepositoryException;
    
    /**
     * Delete a vector by ID from the specified database
     * @param id vector ID to delete
     * @param dbId database identifier
     * @return true if vector was deleted, false otherwise
     * @throws VectorRepositoryException if delete fails
     */
    boolean deleteById(Long id, String dbId) throws VectorRepositoryException;
    
    /**
     * Create a new database
     * @param dbId database identifier
     * @param dimension vector dimension
     * @return true if database was created successfully
     * @throws VectorRepositoryException if creation fails
     */
    boolean createDatabase(String dbId, int dimension) throws VectorRepositoryException;
    
    /**
     * Drop a database
     * @param dbId database identifier
     * @return true if database was dropped successfully
     * @throws VectorRepositoryException if drop fails
     */
    boolean dropDatabase(String dbId) throws VectorRepositoryException;
    
    /**
     * Get list of all available databases
     * @return list of database information
     * @throws VectorRepositoryException if listing fails
     */
    List<DatabaseInfo> getAllDatabases() throws VectorRepositoryException;
    
}
