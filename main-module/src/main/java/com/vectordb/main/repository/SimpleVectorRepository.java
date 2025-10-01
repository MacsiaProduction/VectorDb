package com.vectordb.main.repository;

import com.vectordb.main.model.VectorEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory implementation of VectorRepository.
 * This is a minimal implementation for demonstration purposes.
 */
@Slf4j
@Repository
public class SimpleVectorRepository implements VectorRepository {
    
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, VectorEntry>> databases = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);
    
    @Override
    public List<VectorEntry> getTopKSimilar(double[] vector, int k, String dbId) {
        log.debug("Getting top {} similar vectors in database {}", k, dbId);
        // TODO: Implement actual similarity search
        return new ArrayList<>();
    }
    
    @Override
    public String add(VectorEntry vectorEntry, String dbId) {
        log.debug("Adding vector to database {}", dbId);
        
        String id = String.valueOf(idCounter.getAndIncrement());
        VectorEntry entryWithId = new VectorEntry(id, vectorEntry.getVector(), vectorEntry.getData());
        
        databases.computeIfAbsent(dbId, k -> new ConcurrentHashMap<>())
                .put(id, entryWithId);
        
        return id;
    }
    
    @Override
    public boolean deleteById(String id, String dbId) {
        log.debug("Deleting vector {} from database {}", id, dbId);
        
        ConcurrentHashMap<String, VectorEntry> db = databases.get(dbId);
        return db != null && db.remove(id) != null;
    }
    
    @Override
    public boolean createDatabase(String dbId) {
        log.debug("Creating database {}", dbId);
        databases.putIfAbsent(dbId, new ConcurrentHashMap<>());
        return true;
    }
    
    @Override
    public boolean dropDatabase(String dbId) {
        log.debug("Dropping database {}", dbId);
        return databases.remove(dbId) != null;
    }
    
    @Override
    public List<String> getAllDatabaseIds() {
        log.debug("Getting all database IDs");
        return new ArrayList<>(databases.keySet());
    }
    
    @Override
    public boolean databaseExists(String dbId) {
        return databases.containsKey(dbId);
    }
}
