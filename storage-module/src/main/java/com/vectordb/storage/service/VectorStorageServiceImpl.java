package com.vectordb.storage.service;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.storage.kv.RocksDbStorage;
import com.vectordb.storage.index.VectorIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorStorageServiceImpl implements VectorStorageService {
    
    private final RocksDbStorage storage;
    private final VectorIndex vectorIndex;
    
    @Override
    public String add(VectorEntry entry, String databaseId) {
        try {
            // Validate that database exists
            Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
            if (dbInfo.isEmpty()) {
                throw new IllegalArgumentException("Database not found: " + databaseId);
            }
            
            // Generate unique ID for the vector
            String vectorId = generateVectorId();
            VectorEntry entryWithId = entry.withId(vectorId);
            
            storage.putVector(databaseId, entryWithId);
            
            // Add to vector index for this database
            vectorIndex.add(entryWithId, databaseId);
            
            // Update vector count
            DatabaseInfo updatedInfo = dbInfo.get().withVectorCount(dbInfo.get().vectorCount() + 1);
            storage.putDatabaseInfo(updatedInfo);
            
            log.debug("Added vector {} to database {}", vectorId, databaseId);
            return vectorId;
            
        } catch (Exception e) {
            log.error("Failed to add vector to database {}", databaseId, e);
            throw new RuntimeException("Failed to add vector", e);
        }
    }
    
    /**
     * Generate unique vector ID
     */
    private String generateVectorId() {
        return UUID.randomUUID().toString();
    }
    
    @Override
    public Optional<VectorEntry> get(String id, String databaseId) {
        try {
            return storage.getVector(databaseId, id);
        } catch (Exception e) {
            log.error("Failed to get vector {} from database {}", id, databaseId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public boolean delete(String id, String databaseId) {
        try {
            boolean deleted = storage.deleteVector(databaseId, id);
            
            if (deleted) {
                // Remove from vector index for this database
                boolean removedFromIndex = vectorIndex.remove(id, databaseId);
                log.debug("Removed vector {} from index for database {}: {}", id, databaseId, removedFromIndex);
                
                // Update vector count
                Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
                if (dbInfo.isPresent()) {
                    DatabaseInfo updatedInfo = dbInfo.get().withVectorCount(
                        Math.max(0, dbInfo.get().vectorCount() - 1)
                    );
                    storage.putDatabaseInfo(updatedInfo);
                }
            }
            
            return deleted;
        } catch (Exception e) {
            log.error("Failed to delete vector {} from database {}", id, databaseId, e);
            return false;
        }
    }
    
    @Override
    public List<SearchResult> search(SearchQuery query) {
        try {
            String databaseId = query.databaseId();
            if (databaseId == null) {
                throw new IllegalArgumentException("Database ID is required for search");
            }
            
            // Validate database exists
            Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
            if (dbInfo.isEmpty()) {
                throw new IllegalArgumentException("Database not found: " + databaseId);
            }
            
            // Use vector index for search in this database
            return vectorIndex.search(query.embedding(), query.k(), databaseId);
            
        } catch (Exception e) {
            log.error("Failed to search in database {}", query.databaseId(), e);
            throw new RuntimeException("Search failed", e);
        }
    }
    
    @Override
    public DatabaseInfo createDatabase(String databaseId, String name, int dimension) {
        try {
            Optional<DatabaseInfo> existing = storage.getDatabaseInfo(databaseId);
            if (existing.isPresent()) {
                throw new IllegalArgumentException("Database already exists: " + databaseId);
            }
            
            DatabaseInfo dbInfo = DatabaseInfo.forNewDatabase(databaseId, name, dimension);
            storage.putDatabaseInfo(dbInfo);
            
            // Set dimension for vector index
            vectorIndex.setDimension(dimension);
            
            log.info("Created database: {} with dimension: {}", databaseId, dimension);
            return dbInfo;
            
        } catch (Exception e) {
            log.error("Failed to create database {}", databaseId, e);
            throw new RuntimeException("Failed to create database", e);
        }
    }
    
    @Override
    public boolean dropDatabase(String databaseId) {
        try {
            boolean dbDeleted = storage.deleteDatabaseInfo(databaseId);
            
            if (dbDeleted) {
                // Delete all vectors in the database
                List<VectorEntry> vectors = storage.getAllVectors(databaseId);
                for (VectorEntry vector : vectors) {
                    // Remove from storage
                    storage.deleteVector(databaseId, vector.id());
                    // Remove from vector index for this database
                    vectorIndex.remove(vector.id(), databaseId);
                }
                
                log.info("Dropped database: {} with {} vectors", databaseId, vectors.size());
            }
            
            return dbDeleted;
            
        } catch (Exception e) {
            log.error("Failed to drop database {}", databaseId, e);
            return false;
        }
    }
    
    @Override
    public Optional<DatabaseInfo> getDatabaseInfo(String databaseId) {
        try {
            return storage.getDatabaseInfo(databaseId);
        } catch (Exception e) {
            log.error("Failed to get database info for {}", databaseId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public List<DatabaseInfo> listDatabases() {
        try {
            return storage.getAllDatabases();
        } catch (Exception e) {
            log.error("Failed to list databases", e);
            return List.of();
        }
    }
    
    @Override
    public boolean rebuildIndex(String databaseId) {
        try {
            // Validate database exists
            Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
            if (dbInfo.isEmpty()) {
                throw new IllegalArgumentException("Database not found: " + databaseId);
            }
            
            // Rebuild the vector index for this database
            vectorIndex.clear(databaseId);
            
            // Get all vectors and add them to the index
            List<VectorEntry> vectors = storage.getAllVectors(databaseId);
            for (VectorEntry vector : vectors) {
                vectorIndex.add(vector, databaseId);
            }
            
            // Build the index for this database
            vectorIndex.build(databaseId);
            
            log.info("Rebuilt index for database: {}", databaseId);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to rebuild index for database {}", databaseId, e);
            return false;
        }
    }
    
    @Override
    public boolean isHealthy() {
        try {
            // Simple health check - try to list databases
            storage.getAllDatabases();
            return true;
        } catch (Exception e) {
            log.warn("Health check failed", e);
            return false;
        }
    }
}
