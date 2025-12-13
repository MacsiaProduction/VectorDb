package com.vectordb.main.repository;

import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.VectorEntry;
import com.vectordb.main.client.ShardedStorageClient;
import com.vectordb.main.cluster.model.ShardInfo;
import com.vectordb.main.cluster.router.ShardRouter;
import com.vectordb.main.cluster.ownership.ShardReplicationService;
import com.vectordb.main.cluster.health.ShardHealthMonitor;
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
import java.util.concurrent.Executor;

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
    private final ShardReplicationService shardReplicationService;
    private final ShardHealthMonitor shardHealthMonitor;
    @org.springframework.beans.factory.annotation.Qualifier("replicationExecutor")
    private final Executor replicationExecutor; // Для асинхронной записи реплик
    
    @Override
    public List<VectorEntry> getTopKSimilar(float[] vector, int k, String dbId) throws VectorRepositoryException {
        log.debug("Getting top {} similar vectors in database {} via storage", k, dbId);

        SearchQuery query = SearchQuery.simple(vector, k, dbId);

        try {
            List<SearchResult> aggregated = new ArrayList<>();

            // 1. Определяем доступные и недоступные шарды
            List<ShardInfo> allShards = shardRouter.readableShards();
            List<ShardInfo> availableShards = shardHealthMonitor.getAvailableShards(allShards);
            List<ShardInfo> unavailableShards = shardHealthMonitor.getUnavailableShards(allShards);

            // 2. ПАРАЛЛЕЛЬНО выполняем два типа поиска:

            // 2a. Поиск среди primary данных доступных шардов
            List<CompletableFuture<List<SearchResult>>> primaryFutures = availableShards.stream()
                    .map(shard -> shardedStorageClient.getClient(shard).searchVectors(query).toFuture())
                    .toList();

            // 2b. Поиск среди реплик недоступных шардов (на доступных шардах)
            List<CompletableFuture<List<SearchResult>>> replicaFutures = createReplicaSearchFutures(query, unavailableShards, availableShards);

            // 3. Собираем ВСЕ результаты
            List<CompletableFuture<List<SearchResult>>> allFutures = new ArrayList<>(primaryFutures);
            allFutures.addAll(replicaFutures);

            for (CompletableFuture<List<SearchResult>> future : allFutures) {
                try {
                    List<SearchResult> results = future.get();
                    aggregated.addAll(results);
                } catch (Exception e) {
                    log.warn("Failed to get search results from shard/replica", e);
                }
            }

            // 4. Финальная агрегация с дедупликацией
            return aggregated.stream()
                    .distinct() // По ID вектора
                    .sorted(Comparator.comparingDouble(SearchResult::distance))
                    .limit(k)
                    .map(SearchResult::entry)
                    .toList();

        } catch (Exception e) {
            log.error("Failed to get similar vectors for database {}: {}", dbId, e.getMessage());
            throw new VectorRepositoryException("Failed to search vectors in database " + dbId, e);
        }
    }

    private List<CompletableFuture<List<SearchResult>>> createReplicaSearchFutures(
            SearchQuery query,
            List<ShardInfo> unavailableShards,
            List<ShardInfo> availableShards) {

        List<CompletableFuture<List<SearchResult>>> futures = new ArrayList<>();

        for (ShardInfo unavailableShard : unavailableShards) {
            // Находим, на каких доступных шардах хранятся реплики недоступного шарда
            List<ShardInfo> replicaLocations = findReplicaLocations(unavailableShard, availableShards);

            for (ShardInfo replicaLocation : replicaLocations) {
                CompletableFuture<List<SearchResult>> future =
                    shardedStorageClient.getClient(replicaLocation)
                        .searchReplicasForShard(query, unavailableShard.shardId())
                        .toFuture();
                futures.add(future);
            }
        }

        return futures;
    }

    private List<ShardInfo> findReplicaLocations(ShardInfo sourceShard, List<ShardInfo> availableShards) {
        // Используем ShardOwnership для определения, где хранятся реплики
        String replicaLocationId = shardReplicationService.getShardOwnership()
            .getReplicaLocation(sourceShard.shardId());

        return availableShards.stream()
            .filter(shard -> shard.shardId().equals(replicaLocationId))
            .toList();
    }
    
    @Override
    public Optional<VectorEntry> findById(Long id, String dbId) throws VectorRepositoryException {
        log.debug("Finding vector {} in database {} via storage", id, dbId);

        List<ShardInfo> allShards = shardRouter.writableShards();
        if (allShards.isEmpty()) {
            throw new VectorRepositoryException("No writable shards configured for database " + dbId);
        }

        // 1. Используем маршрутизацию с репликами для получения P и R
        List<ShardInfo> targets = shardRouter.routeForWriteWithReplicas(id);
        ShardInfo primary = targets.get(0);
        ShardInfo replica = targets.get(1);

        // 2. Формируем список кандидатов для поиска: P, R, затем остальные
        List<ShardInfo> candidates = new ArrayList<>(allShards.size());
        candidates.add(primary);
        if (!primary.shardId().equals(replica.shardId())) { // Исключаем добавление дважды, если P == R (только 1 шард)
            candidates.add(replica);
        }

        // Добавляем остальные шарды (на случай, если вектор "застрял" после ребаланса)
        for (ShardInfo shard : allShards) {
            if (!shard.shardId().equals(primary.shardId()) && !shard.shardId().equals(replica.shardId())) {
                candidates.add(shard);
            }
        }

        VectorEntry foundEntry = null;
        ShardInfo foundShard = null;
        boolean hadConnectivityErrors = false;

        for (ShardInfo shard : candidates) {
            try {
                VectorEntry result = shardedStorageClient.getClient(shard)
                        .getVector(id, dbId)
                        .toFuture()
                        .get();

                if (result != null) {
                    log.debug("Vector {} found in database {} on shard {}", id, dbId, shard.shardId());
                    foundEntry = result;
                    foundShard = shard;
                    break; // Найдено
                } else {
                    log.debug("Vector {} not found on shard {} for database {}", id, shard.shardId(), dbId);
                    continue; // Пробуем следующий шард
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof WebClientResponseException.NotFound) {
                    log.debug("Vector {} not found on shard {} for database {} (404)", id, shard.shardId(), dbId);
                    continue;
                }
                log.warn("Failed to get vector {} from database {} on shard {}: {}",
                        id, dbId, shard.shardId(), cause.getMessage());
                hadConnectivityErrors = true;
                continue; // Пробуем следующий шард
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Get operation interrupted for vector {} in database {} on shard {}", id, dbId, shard.shardId(), e);
                throw new VectorRepositoryException(
                        "Get operation interrupted for vector " + id + " in database " + dbId,
                        e
                );
            } catch (Exception e) {
                log.warn("Error while getting vector {} from database {} on shard {}: {}",
                        id, dbId, shard.shardId(), e.getMessage());
                hadConnectivityErrors = true;
                continue; // Пробуем следующий шард
            }
        }

        // 3. Механизм Read Repair
        if (foundEntry != null && !foundShard.shardId().equals(primary.shardId())) {
            final VectorEntry entryToRepair = foundEntry;
            final ShardInfo sourceShard = foundShard;

            // Если вектор найден, но не на первичном шарде (например, на реплике), инициируем repair
            CompletableFuture.runAsync(() -> {
                try {
                    // Реплицируем данные обратно на Primary
                    shardedStorageClient.getClient(primary)
                        .addVectorReplica(entryToRepair, dbId, sourceShard.shardId())
                        .toFuture().get();
                    log.info("Read Repair: Replicated vector {} from R={} to P={}",
                            id, sourceShard.shardId(), primary.shardId());

                } catch (Exception repairE) {
                    log.error("Read Repair failed for vector {} on primary shard {}", id, primary.shardId(), repairE.getCause());
                }
            }, replicationExecutor);
        }

        if (foundEntry == null) {
            log.debug("Vector {} not found on any shard for database {}", id, dbId);
        }

        return Optional.ofNullable(foundEntry);
    }

    @Override
    public Long add(VectorEntry vectorEntry, String dbId) throws VectorRepositoryException {
        log.debug("Adding vector to database {} via storage", dbId);

        try {
            // 1. Маршрутизация на Primary (P) и Replica (R)
            List<ShardInfo> targets = shardRouter.routeForWriteWithReplicas(vectorEntry.id());
            ShardInfo primary = targets.get(0);
            ShardInfo replica = targets.get(1);

            // 2. Синхронная запись на Primary (P)
            Long resultId = shardedStorageClient.getClient(primary)
                    .addVector(vectorEntry, dbId)
                    .toFuture()
                    .get();

            // 3. Асинхронная запись на Replica (R)
            CompletableFuture.runAsync(() -> {
                try {
                    // Используем новый метод для репликации
                    shardedStorageClient.getClient(replica)
                        .addVectorReplica(vectorEntry, dbId, primary.shardId())
                        .toFuture().get();
                    log.debug("Vector {} successfully replicated to shard {}", resultId, replica.shardId());
                } catch (Exception e) {
                    // Логируем ошибку, это означает временную или постоянную несогласованность
                    log.error("Failed to replicate vector {} to shard {}. Data is inconsistent.",
                            resultId, replica.shardId(), e.getCause());
                }
            }, replicationExecutor);

            return resultId;
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

        // 1. Маршрутизация на Primary (P) и Replica (R)
        List<ShardInfo> targets = shardRouter.routeForWriteWithReplicas(id);
        ShardInfo primary = targets.get(0);
        ShardInfo replica = targets.get(1);

        // 2. Формируем список кандидатов для удаления: P, R, затем остальные
        List<ShardInfo> candidates = new ArrayList<>(allShards.size());
        candidates.add(primary);
        if (!primary.shardId().equals(replica.shardId())) {
            candidates.add(replica);
        }

        // Добавляем остальные шарды (для удаления "застрявших" векторов)
        for (ShardInfo shard : allShards) {
            if (!shard.shardId().equals(primary.shardId()) && !shard.shardId().equals(replica.shardId())) {
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

                    // Асинхронное удаление на Реплике, если удаление произошло на Primary
                    if (shard.shardId().equals(primary.shardId())) {
                        CompletableFuture.runAsync(() -> {
                            try {
                                // Используем новый метод для репликации удаления
                                shardedStorageClient.getClient(replica)
                                    .deleteVectorReplica(id, dbId, primary.shardId())
                                    .toFuture().get();
                                log.debug("Delete successfully replicated for vector {} to shard {}", id, replica.shardId());
                            } catch (Exception e) {
                                log.warn("Failed to replicate delete for vector {} to shard {}", id, replica.shardId(), e.getCause());
                            }
                        }, replicationExecutor);
                    }

                    // Если удаление произошло на Primary или Replica, это успех.
                    // Break не ставим, чтобы удалить "застрявший" вектор на старых шардах,
                    // но ставим deleted = true.
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
