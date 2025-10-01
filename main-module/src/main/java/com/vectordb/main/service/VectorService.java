package com.vectordb.main.service;

import com.vectordb.main.model.VectorEntry;
import com.vectordb.main.repository.VectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for vector operations.
 * This service delegates storage operations to the repository layer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {
    
    private final VectorRepository vectorRepository;
    
    /**
     * Find top K similar vectors to the given query vector
     * @param vector query vector
     * @param k number of similar vectors to return
     * @param dbId database identifier
     * @return list of similar vectors
     */
    public List<VectorEntry> getTopK(double[] vector, int k, String dbId) {
        log.debug("Searching for top {} similar vectors in database {}", k, dbId);
        
        if (!vectorRepository.databaseExists(dbId)) {
            log.warn("Database {} does not exist", dbId);
            return List.of();
        }
        
        return vectorRepository.getTopKSimilar(vector, k, dbId);
    }
    
    /**
     * Add a new vector to the specified database
     * @param vector vector coordinates
     * @param data associated data
     * @param dbId database identifier
     * @return generated ID for the added vector
     */
    public String add(double[] vector, String data, String dbId) {
        log.debug("Adding vector to database {} with data: {}", dbId, data);
        
        if (!vectorRepository.databaseExists(dbId)) {
            log.warn("Database {} does not exist", dbId);
            throw new IllegalArgumentException("Database " + dbId + " does not exist");
        }
        
        VectorEntry vectorEntry = new VectorEntry(null, vector, data);
        String id = vectorRepository.add(vectorEntry, dbId);
        
        log.debug("Vector added with ID: {}", id);
        return id;
    }
    
    /**
     * Delete a vector by ID from the specified database
     * @param id vector ID to delete
     * @param dbId database identifier
     * @return true if vector was deleted, false otherwise
     */
    public boolean delete(String id, String dbId) {
        log.debug("Deleting vector {} from database {}", id, dbId);
        
        if (!vectorRepository.databaseExists(dbId)) {
            log.warn("Database {} does not exist", dbId);
            return false;
        }
        
        return vectorRepository.deleteById(id, dbId);
    }
    
    /**
     * Create a new database
     * @param dbId database identifier
     * @return true if database was created successfully
     */
    public boolean createDb(String dbId) {
        log.debug("Creating database {}", dbId);
        
        if (vectorRepository.databaseExists(dbId)) {
            log.warn("Database {} already exists", dbId);
            return false;
        }
        
        boolean created = vectorRepository.createDatabase(dbId);
        log.debug("Database {} creation result: {}", dbId, created);
        return created;
    }
    
    /**
     * Drop a database
     * @param dbId database identifier
     * @return true if database was dropped successfully
     */
    public boolean dropDb(String dbId) {
        log.debug("Dropping database {}", dbId);
        
        if (!vectorRepository.databaseExists(dbId)) {
            log.warn("Database {} does not exist", dbId);
            return false;
        }
        
        boolean dropped = vectorRepository.dropDatabase(dbId);
        log.debug("Database {} drop result: {}", dbId, dropped);
        return dropped;
    }
    
    /**
     * Get list of all available databases
     * @return list of database IDs
     */
    public List<String> showDBs() {
        log.debug("Listing all databases");
        
        List<String> dbIds = vectorRepository.getAllDatabaseIds();
        log.debug("Found {} databases: {}", dbIds.size(), dbIds);
        return dbIds;
    }
}
