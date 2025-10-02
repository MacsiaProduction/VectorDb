package com.vectordb.storage.service;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.DatabaseInfo;

import java.util.List;
import java.util.Optional;

/**
 * Core interface for vector storage operations.
 * This interface will be implemented by the storage service.
 */
public interface VectorStorageService {
    
    /**
     * Add a vector entry to the storage
     */
    String add(VectorEntry entry, String databaseId);
    
    /**
     * Get vector entry by ID
     */
    Optional<VectorEntry> get(String id, String databaseId);
    
    /**
     * Delete vector entry by ID
     */
    boolean delete(String id, String databaseId);
    
    /**
     * Search for similar vectors using KNN
     */
    List<SearchResult> search(SearchQuery query);
    
    /**
     * Create a new database
     */
    DatabaseInfo createDatabase(String databaseId, String name);
    
    /**
     * Drop a database
     */
    boolean dropDatabase(String databaseId);
    
    /**
     * Get database information
     */
    Optional<DatabaseInfo> getDatabaseInfo(String databaseId);
    
    /**
     * List all databases
     */
    List<DatabaseInfo> listDatabases();
    
    /**
     * Rebuild index for the database (for future index implementation)
     */
    boolean rebuildIndex(String databaseId);
    
    /**
     * Get health status of the storage
     */
    boolean isHealthy();
}
