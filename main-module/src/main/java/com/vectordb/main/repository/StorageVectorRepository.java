package com.vectordb.main.repository;

import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.VectorEntry;
import com.vectordb.main.client.StorageClient;
import com.vectordb.main.exception.VectorRepositoryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.List;

/**
 * Storage-based implementation of VectorRepository.
 * This implementation communicates with the storage module via HTTP.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class StorageVectorRepository implements VectorRepository {
    
    private final StorageClient storageClient;
    
    @Override
    public List<VectorEntry> getTopKSimilar(float[] vector, int k, String dbId) throws VectorRepositoryException {
        log.debug("Getting top {} similar vectors in database {} via storage", k, dbId);
        
        SearchQuery query = SearchQuery.simple(vector, k, dbId);
        
        try {
            List<SearchResult> results = storageClient.searchVectors(query)
                    .toFuture()
                    .get();
            return results.stream()
                    .map(SearchResult::entry)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to get similar vectors for database {}: {}", dbId, e.getMessage());
            throw new VectorRepositoryException("Failed to search vectors in database " + dbId, e);
        }
    }
    
    @Override
    public Long add(VectorEntry vectorEntry, String dbId) throws VectorRepositoryException {
        log.debug("Adding vector to database {} via storage", dbId);
        
        try {
            return storageClient.addVector(vectorEntry, dbId)
                    .toFuture()
                    .get();
        } catch (Exception e) {
            log.error("Failed to add vector to database {}: {}", dbId, e.getMessage());
            throw new VectorRepositoryException("Failed to add vector to database " + dbId, e);
        }
    }
    
    @Override
    public boolean deleteById(Long id, String dbId) throws VectorRepositoryException {
        log.debug("Deleting vector {} from database {} via storage", id, dbId);
        
        try {
            return storageClient.deleteVector(id, dbId)
                    .toFuture()
                    .get();
        } catch (WebClientResponseException.NotFound e) {
            log.debug("Vector {} not found in database {}", id, dbId);
            return false;
        } catch (Exception e) {
            log.error("Failed to delete vector {} from database {}: {}", id, dbId, e.getMessage());
            throw new VectorRepositoryException("Failed to delete vector " + id + " from database " + dbId, e);
        }
    }
    
    @Override
    public boolean createDatabase(String dbId, int dimension) throws VectorRepositoryException {
        log.debug("Creating database {} with dimension {} via storage", dbId, dimension);
        
        try {
            return storageClient.createDatabase(dbId, "Database " + dbId, dimension)
                    .toFuture()
                    .get() != null;
        } catch (Exception e) {
            log.error("Failed to create database {}: {}", dbId, e.getMessage());
            throw new VectorRepositoryException("Failed to create database " + dbId, e);
        }
    }
    
    @Override
    public boolean dropDatabase(String dbId) throws VectorRepositoryException {
        log.debug("Dropping database {} via storage", dbId);
        
        try {
            return storageClient.dropDatabase(dbId)
                    .toFuture()
                    .get();
        } catch (WebClientResponseException.NotFound e) {
            log.debug("Database {} not found", dbId);
            return false;
        } catch (Exception e) {
            log.error("Failed to drop database {}: {}", dbId, e.getMessage());
            throw new VectorRepositoryException("Failed to drop database " + dbId, e);
        }
    }
    
    @Override
    public List<DatabaseInfo> getAllDatabases() throws VectorRepositoryException {
        log.debug("Getting all databases via storage");
        
        try {
            return storageClient.listDatabases()
                    .toFuture()
                    .get();
        } catch (Exception e) {
            log.error("Failed to get databases: {}", e.getMessage());
            throw new VectorRepositoryException("Failed to get databases", e);
        }
    }
    
}
