package com.vectordb.main.service;

import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.common.model.VectorEntry;
import com.vectordb.main.exception.VectorRepositoryException;
import com.vectordb.main.id.VectorId;
import com.vectordb.main.id.VectorIdGenerator;
import com.vectordb.main.repository.VectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for vector operations.
 * This service delegates storage operations to the repository layer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {
    
    private final VectorRepository vectorRepository;
    private final VectorIdGenerator vectorIdGenerator;
    
    /**
     * Find top K similar vectors to the given query vector
     * @param vector query vector
     * @param k number of similar vectors to return
     * @param dbId database identifier
     * @return list of similar vectors
     */
    public List<VectorEntry> getTopK(float[] vector, int k, String dbId) throws VectorRepositoryException {
        log.debug("Searching for top {} similar vectors in database {}", k, dbId);
        return vectorRepository.getTopKSimilar(vector, k, dbId);
    }
    
    /**
     * Get a vector by ID from the specified database
     * @param id vector ID
     * @param dbId database identifier
     * @return Optional containing the vector entry if found, empty otherwise
     */
    public Optional<VectorEntry> get(Long id, String dbId) throws VectorRepositoryException {
        log.debug("Getting vector {} from database {}", id, dbId);
        return vectorRepository.findById(id, dbId);
    }
    
    /**
     * Add a new vector to the specified database
     * @param vector vector coordinates
     * @param data associated data
     * @param dbId database identifier
     * @return generated ID for the added vector
     */
    public Long add(float[] vector, String data, String dbId) throws VectorRepositoryException {
        log.debug("Adding vector to database {} with data: {}", dbId, data);
        
        Instant now = Instant.now();
        VectorId vectorId = vectorIdGenerator.nextId();
        VectorEntry vectorEntry = new VectorEntry(
            vectorId.numericId(),
            vector,
            data,
            dbId,
            now
        );
        Long id = vectorRepository.add(vectorEntry, dbId);
        
        log.debug("Vector added with ID: {}", id);
        return id;
    }
    
    /**
     * Delete a vector by ID from the specified database
     * @param id vector ID to delete
     * @param dbId database identifier
     * @return true if vector was deleted, false otherwise
     */
    public boolean delete(Long id, String dbId) throws VectorRepositoryException {
        log.debug("Deleting vector {} from database {}", id, dbId);
        return vectorRepository.deleteById(id, dbId);
    }
    
    /**
     * Create a new database
     * @param dbId database identifier
     * @param dimension vector dimension
     * @return true if database was created successfully
     */
    public boolean createDb(String dbId, int dimension) throws VectorRepositoryException {
        log.debug("Creating database {} with dimension {}", dbId, dimension);
        return vectorRepository.createDatabase(dbId, dimension);
    }
    
    /**
     * Drop a database
     * @param dbId database identifier
     * @return true if database was dropped successfully
     */
    public boolean dropDb(String dbId) throws VectorRepositoryException {
        log.debug("Dropping database {}", dbId);
        return vectorRepository.dropDatabase(dbId);
    }
    
    /**
     * Get list of all available databases
     * @return list of database information
     */
    public List<DatabaseInfo> showDBs() throws VectorRepositoryException {
        log.debug("Listing all databases");
        
        List<DatabaseInfo> databases = vectorRepository.getAllDatabases();
        log.debug("Found {} databases", databases.size());
        return databases;
    }
}
