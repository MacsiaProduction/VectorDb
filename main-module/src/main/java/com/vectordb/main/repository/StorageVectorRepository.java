package com.vectordb.main.repository;

import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.VectorEntry;
import com.vectordb.main.client.ShardedStorageClient;
import com.vectordb.main.cluster.model.ShardInfo;
import com.vectordb.main.cluster.router.ShardRouter;
import com.vectordb.main.exception.VectorRepositoryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Storage-based implementation of VectorRepository.
 * This implementation communicates with the storage module via HTTP.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class StorageVectorRepository implements VectorRepository {
    
    private final ShardedStorageClient shardedStorageClient;
    private final ShardRouter shardRouter;
    
    @Override
    public List<VectorEntry> getTopKSimilar(float[] vector, int k, String dbId) throws VectorRepositoryException {
        log.debug("Getting top {} similar vectors in database {} via storage", k, dbId);
        
        SearchQuery query = SearchQuery.simple(vector, k, dbId);
        
        try {
            List<SearchResult> aggregated = new ArrayList<>();
            List<ShardInfo> shards = shardRouter.readableShards();
            List<CompletableFuture<List<SearchResult>>> futures = shards.stream()
                    .map(shard -> shardedStorageClient.getClient(shard).searchVectors(query).toFuture())
                    .toList();
            for (CompletableFuture<List<SearchResult>> future : futures) {
                aggregated.addAll(future.get());
            }
            return aggregated.stream()
                    .sorted(Comparator.comparingDouble(SearchResult::distance))
                    .limit(k)
                    .map(SearchResult::entry)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to get similar vectors for database {}: {}", dbId, e.getMessage());
            throw new VectorRepositoryException("Failed to search vectors in database " + dbId, e);
        }
    }
    
    @Override
    public Optional<VectorEntry> findById(Long id, String dbId) throws VectorRepositoryException {
        log.debug("Finding vector {} in database {} via storage", id, dbId);
        
        List<ShardInfo> allShards = shardRouter.writableShards();
        if (allShards.isEmpty()) {
            throw new VectorRepositoryException("No writable shards configured for database " + dbId);
        }

        ShardInfo primary = shardRouter.routeForWrite(id);

        List<ShardInfo> candidates = new ArrayList<>(allShards.size());
        candidates.add(primary);
        for (ShardInfo shard : allShards) {
            if (!shard.shardId().equals(primary.shardId())) {
                candidates.add(shard);
            }
        }

        for (ShardInfo shard : candidates) {
            try {
                VectorEntry result = shardedStorageClient.getClient(shard)
                        .getVector(id, dbId)
                        .toFuture()
                        .get();

                if (result != null) {
                    log.debug("Vector {} found in database {} on shard {}", id, dbId, shard.shardId());
                    return Optional.of(result);
                } else {
                    log.debug("Vector {} not found on shard {} for database {}", id, shard.shardId(), dbId);
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof WebClientResponseException.NotFound) {
                    log.debug("Vector {} not found on shard {} for database {} (404)", id, shard.shardId(), dbId);
                    continue;
                }
                log.error("Failed to get vector {} from database {} on shard {}: {}",
                        id, dbId, shard.shardId(), cause.getMessage());
                throw new VectorRepositoryException(
                        "Failed to get vector " + id + " from database " + dbId + " on shard " + shard.shardId(),
                        cause
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Get operation interrupted for vector {} in database {} on shard {}", id, dbId, shard.shardId(), e);
                throw new VectorRepositoryException(
                        "Get operation interrupted for vector " + id + " in database " + dbId,
                        e
                );
            } catch (Exception e) {
                log.error("Unexpected error while getting vector {} from database {} on shard {}: {}",
                        id, dbId, shard.shardId(), e.getMessage());
                throw new VectorRepositoryException(
                        "Failed to get vector " + id + " from database " + dbId + " on shard " + shard.shardId(),
                        e
                );
            }
        }

        log.debug("Vector {} not found on any shard for database {}", id, dbId);
        return Optional.empty();
    }
    
    @Override
    public Long add(VectorEntry vectorEntry, String dbId) throws VectorRepositoryException {
        log.debug("Adding vector to database {} via storage", dbId);
        
        try {
            ShardInfo shard = shardRouter.routeForWrite(vectorEntry.id());
            return shardedStorageClient.getClient(shard)
                    .addVector(vectorEntry, dbId)
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
        
        // В норме ID должен лежать только на одном шарде, но после смены кольца / без ребаланса
        // вектор может физически находиться на «старом» шарде. Поэтому:
        // 1) сначала бьём в шард, который выбирает кольцо;
        // 2) если там 404 — пробуем все остальные шард(ы);
        // 3) если нигде не нашли / не удалили — возвращаем false без 500.
        List<ShardInfo> allShards = shardRouter.writableShards();
        if (allShards.isEmpty()) {
            throw new VectorRepositoryException("No writable shards configured for database " + dbId);
        }

        ShardInfo primary = shardRouter.routeForWrite(id);

        // Сформировать список: primary сначала, потом остальные
        List<ShardInfo> candidates = new ArrayList<>(allShards.size());
        candidates.add(primary);
        for (ShardInfo shard : allShards) {
            if (!shard.shardId().equals(primary.shardId())) {
                candidates.add(shard);
            }
        }

        boolean deleted = false;

        for (ShardInfo shard : candidates) {
            try {
                boolean shardDeleted = shardedStorageClient.getClient(shard)
                        .deleteVector(id, dbId)
                        .toFuture()
                        .get();

                if (shardDeleted) {
                    log.debug("Vector {} deleted from database {} on shard {}", id, dbId, shard.shardId());
                    deleted = true;
                    break;
                } else {
                    log.debug("Vector {} not found on shard {} for database {}", id, shard.shardId(), dbId);
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof WebClientResponseException.NotFound) {
                    // На этом шарде вектора нет – пробуем следующий
                    log.debug("Vector {} not found on shard {} for database {} (404)", id, shard.shardId(), dbId);
                    continue;
                }
                log.error("Failed to delete vector {} from database {} on shard {}: {}",
                        id, dbId, shard.shardId(), cause.getMessage());
                throw new VectorRepositoryException(
                        "Failed to delete vector " + id + " from database " + dbId + " on shard " + shard.shardId(),
                        cause
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Delete operation interrupted for vector {} in database {} on shard {}", id, dbId, shard.shardId(), e);
                throw new VectorRepositoryException(
                        "Delete operation interrupted for vector " + id + " in database " + dbId,
                        e
                );
            } catch (Exception e) {
                log.error("Unexpected error while deleting vector {} from database {} on shard {}: {}",
                        id, dbId, shard.shardId(), e.getMessage());
                throw new VectorRepositoryException(
                        "Failed to delete vector " + id + " from database " + dbId + " on shard " + shard.shardId(),
                        e
                );
            }
        }

        if (!deleted) {
            log.debug("Vector {} not found on any shard for database {}", id, dbId);
        }

        return deleted;
    }
    
    @Override
    public boolean createDatabase(String dbId, int dimension) throws VectorRepositoryException {
        log.debug("Creating database {} with dimension {} via storage", dbId, dimension);
        
        try {
            boolean created = false;
            for (ShardInfo shard : shardRouter.writableShards()) {
                created |= shardedStorageClient.getClient(shard)
                        .createDatabase(dbId, "Database " + dbId, dimension)
                        .toFuture()
                        .get() != null;
            }
            return created;
        } catch (Exception e) {
            log.error("Failed to create database {}: {}", dbId, e.getMessage());
            throw new VectorRepositoryException("Failed to create database " + dbId, e);
        }
    }
    
    @Override
    public boolean dropDatabase(String dbId) throws VectorRepositoryException {
        log.debug("Dropping database {} via storage", dbId);
        
        try {
            boolean dropped = false;
            for (ShardInfo shard : shardRouter.writableShards()) {
                dropped |= shardedStorageClient.getClient(shard)
                        .dropDatabase(dbId)
                        .toFuture()
                        .get();
            }
            return dropped;
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
            List<ShardInfo> shards = shardRouter.readableShards();
            if (shards.isEmpty()) {
                return List.of();
            }
            return shardedStorageClient.getClient(shards.getFirst())
                    .listDatabases()
                    .toFuture()
                    .get();
        } catch (Exception e) {
            log.error("Failed to get databases: {}", e.getMessage());
            throw new VectorRepositoryException("Failed to get databases", e);
        }
    }
    
}
