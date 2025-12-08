package com.vectordb.storage.service;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.storage.kv.RocksDbStorage;
import com.vectordb.storage.index.VectorIndex;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorStorageServiceImpl implements VectorStorageService {
    
    private final RocksDbStorage storage;
    private final VectorIndex vectorIndex;
    
    @PostConstruct
    public void init() {
        try {
            log.info("Initializing vector storage service...");
            
            List<DatabaseInfo> databases = storage.getAllDatabases();
            log.info("Found {} existing databases in storage", databases.size());
            
            for (DatabaseInfo dbInfo : databases) {
                try {
                    log.info("Restoring database {}: dimension={}, vectorCount={}",
                            dbInfo.id(), dbInfo.dimension(), dbInfo.vectorCount());
                    
                    vectorIndex.setDimension(dbInfo.dimension());
                    log.info("Set dimension {} for database {}", dbInfo.dimension(), dbInfo.id());
                    
                    List<VectorEntry> vectors = storage.getAllVectors(dbInfo.id());
                    
                    if (!vectors.isEmpty()) {
                        log.info("Rebuilding index for database {} with {} vectors",
                                dbInfo.id(), vectors.size());
                        for (VectorEntry vector : vectors) {
                            vectorIndex.add(vector, dbInfo.id());
                        }
                        
                        vectorIndex.build(dbInfo.id());
                        log.info("Successfully rebuilt index for database {}, index size: {}",
                                dbInfo.id(), vectorIndex.size(dbInfo.id()));
                    } else {
                        log.info("Database {} has no vectors, skipping index rebuild", dbInfo.id());
                    }
                } catch (Exception e) {
                    log.error("Failed to restore database {}", dbInfo.id(), e);
                }
            }
            
            log.info("Vector storage service initialization complete - restored {} databases", databases.size());
        } catch (Exception e) {
            log.error("Failed to initialize vector storage service", e);
        }
    }
    
    @Override
    public Long add(VectorEntry entry, String databaseId) {
        try {
            Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
            if (dbInfo.isEmpty()) {
                throw new IllegalArgumentException("Database not found: " + databaseId);
            }
            
            if (entry.id() == null) {
                throw new IllegalArgumentException("Vector ID must be provided");
            }
            
            storage.putVector(databaseId, entry);
            
            try {
                vectorIndex.add(entry, databaseId);
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("Dimension must be set")) {
                    log.info("Setting dimension for database {} index: {}", databaseId, dbInfo.get().dimension());
                    vectorIndex.setDimension(dbInfo.get().dimension());
                    vectorIndex.add(entry, databaseId);
                } else {
                    throw e;
                }
            }
            
            DatabaseInfo updatedInfo = dbInfo.get().withVectorCount(dbInfo.get().vectorCount() + 1);
            storage.putDatabaseInfo(updatedInfo);
            
            log.debug("Added vector {} to database {}", entry.id(), databaseId);
            return entry.id();
            
        } catch (Exception e) {
            log.error("Failed to add vector to database {}", databaseId, e);
            throw new RuntimeException("Failed to add vector", e);
        }
    }
    
    
    @Override
    public Optional<VectorEntry> get(Long id, String databaseId) {
        try {
            return storage.getVector(databaseId, id);
        } catch (Exception e) {
            log.error("Failed to get vector {} from database {}", id, databaseId, e);
            return Optional.empty();
        }
    }
    
    @Override
    public boolean delete(Long id, String databaseId) {
        try {
            log.info("Attempting to delete vector {} from database {}", id, databaseId);
            
            Optional<VectorEntry> existing = storage.getVector(databaseId, id);
            log.info("Vector {} exists in storage: {}", id, existing.isPresent());
            
            boolean deleted = storage.deleteVector(databaseId, id);
            log.info("Storage delete result for vector {}: {}", id, deleted);
            
            if (deleted) {
                boolean removedFromIndex = vectorIndex.remove(id, databaseId);
                log.info("Removed vector {} from index for database {}: {}", id, databaseId, removedFromIndex);
                
                Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
                if (dbInfo.isPresent()) {
                    DatabaseInfo updatedInfo = dbInfo.get().withVectorCount(
                        Math.max(0, dbInfo.get().vectorCount() - 1)
                    );
                    storage.putDatabaseInfo(updatedInfo);
                    log.info("Updated vector count for database {} to {}", databaseId, updatedInfo.vectorCount());
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
            
            Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
            if (dbInfo.isEmpty()) {
                throw new IllegalArgumentException("Database not found: " + databaseId);
            }
            
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
                List<VectorEntry> vectors = storage.getAllVectors(databaseId);
                for (VectorEntry vector : vectors) {
                    storage.deleteVector(databaseId, vector.id());
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
            Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
            if (dbInfo.isEmpty()) {
                throw new IllegalArgumentException("Database not found: " + databaseId);
            }
            
            vectorIndex.clear(databaseId);
            
            List<VectorEntry> vectors = storage.getAllVectors(databaseId);
            for (VectorEntry vector : vectors) {
                vectorIndex.add(vector, databaseId);
            }
            
            vectorIndex.build(databaseId);
            
            log.info("Rebuilt index for database: {}", databaseId);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to rebuild index for database {}", databaseId, e);
            return false;
        }
    }

    @Override
    public Long addReplica(VectorEntry entry, String databaseId, String sourceShardId) {
        try {
            Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
            if (dbInfo.isEmpty()) {
                throw new IllegalArgumentException("Database not found: " + databaseId);
            }

            if (entry.id() == null) {
                throw new IllegalArgumentException("Vector ID must be provided");
            }

            storage.putVectorReplica(databaseId, entry, sourceShardId);

            try {
                vectorIndex.addReplica(entry, databaseId, sourceShardId);
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("Dimension must be set")) {
                    log.info("Setting dimension for database {} index: {}", databaseId, dbInfo.get().dimension());
                    vectorIndex.setDimension(dbInfo.get().dimension());
                    vectorIndex.addReplica(entry, databaseId, sourceShardId);
                } else {
                    throw e;
                }
            }

            log.debug("Added replica vector {} for database {} from shard {}", entry.id(), databaseId, sourceShardId);
            return entry.id();

        } catch (Exception e) {
            log.error("Failed to add replica vector to database {}", databaseId, e);
            throw new RuntimeException("Failed to add replica vector", e);
        }
    }

    @Override
    public Optional<VectorEntry> getReplica(Long id, String databaseId, String sourceShardId) {
        try {
            return storage.getVectorReplica(databaseId, id, sourceShardId);
        } catch (Exception e) {
            log.error("Failed to get replica vector {} from database {} for shard {}", id, databaseId, sourceShardId, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean deleteReplica(Long id, String databaseId, String sourceShardId) {
        try {
            log.info("Attempting to delete replica vector {} from database {} for shard {}", id, databaseId, sourceShardId);

            boolean deleted = storage.deleteVectorReplica(databaseId, id, sourceShardId);
            log.info("Storage replica delete result for vector {}: {}", id, deleted);

            if (deleted) {
                boolean removedFromIndex = vectorIndex.removeReplica(id, databaseId, sourceShardId);
                log.info("Removed replica vector {} from index for database {} shard {}: {}",
                    id, databaseId, sourceShardId, removedFromIndex);
            }

            return deleted;
        } catch (Exception e) {
            log.error("Failed to delete replica vector {} from database {} for shard {}", id, databaseId, sourceShardId, e);
            return false;
        }
    }

    @Override
    public List<SearchResult> searchReplicas(SearchQuery query, String sourceShardId) {
        try {
            String databaseId = query.databaseId();
            if (databaseId == null) {
                throw new IllegalArgumentException("Database ID is required for replica search");
            }

            Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
            if (dbInfo.isEmpty()) {
                throw new IllegalArgumentException("Database not found: " + databaseId);
            }

            return vectorIndex.searchReplicas(query.embedding(), query.k(), databaseId, sourceShardId);

        } catch (Exception e) {
            log.error("Failed to search replicas in database {} for shard {}", query.databaseId(), sourceShardId, e);
            throw new RuntimeException("Replica search failed", e);
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            storage.getAllDatabases();
            return true;
        } catch (Exception e) {
            log.warn("Health check failed", e);
            return false;
        }
    }

    @Override
    public List<VectorEntry> scanByRange(String databaseId, long fromExclusive, long toInclusive, int limit) {
        try {
            List<VectorEntry> all = new ArrayList<>(storage.getAllVectors(databaseId));
            return all.stream()
                    .sorted(Comparator.comparingLong(VectorEntry::id))
                    .filter(entry -> entry.id() > fromExclusive && entry.id() <= toInclusive)
                    .limit(Math.max(limit, 0))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to scan vectors in database {}", databaseId, e);
            throw new RuntimeException("Failed to scan range for database " + databaseId, e);
        }
    }

    @Override
    public void putBatch(String databaseId, List<VectorEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        try {
            Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
            if (dbInfo.isEmpty()) {
                throw new IllegalArgumentException("Database not found: " + databaseId);
            }
            
            for (VectorEntry entry : entries) {
                if (entry.id() == null) {
                    throw new IllegalArgumentException("Vector ID must be provided for all entries in batch");
                }
                storage.putVector(databaseId, entry);
                
                try {
                    vectorIndex.add(entry, databaseId);
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("Dimension must be set")) {
                        log.info("Setting dimension for database {} index: {}", databaseId, dbInfo.get().dimension());
                        vectorIndex.setDimension(dbInfo.get().dimension());
                        vectorIndex.add(entry, databaseId);
                    } else {
                        throw e;
                    }
                }
            }
            adjustVectorCount(databaseId, entries.size());
        } catch (Exception e) {
            log.error("Failed to insert batch into database {}", databaseId, e);
            throw new RuntimeException("Batch insert failed for database " + databaseId, e);
        }
    }

    @Override
    public int deleteBatch(String databaseId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int removed = 0;
        try {
            for (Long id : ids) {
                if (storage.deleteVector(databaseId, id)) {
                    removed++;
                    vectorIndex.remove(id, databaseId);
                }
            }
            adjustVectorCount(databaseId, -removed);
            return removed;
        } catch (Exception e) {
            log.error("Failed to delete batch from database {}", databaseId, e);
            throw new RuntimeException("Batch delete failed for database " + databaseId, e);
        }
    }

    private void adjustVectorCount(String databaseId, int delta) throws Exception {
        if (delta == 0) {
            return;
        }
        Optional<DatabaseInfo> dbInfo = storage.getDatabaseInfo(databaseId);
        if (dbInfo.isPresent()) {
            DatabaseInfo info = dbInfo.get();
            long updatedCount = Math.max(0L, info.vectorCount() + delta);
            storage.putDatabaseInfo(info.withVectorCount(updatedCount));
        }
    }
}
