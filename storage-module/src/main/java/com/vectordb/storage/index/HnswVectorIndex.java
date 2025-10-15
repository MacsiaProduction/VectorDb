package com.vectordb.storage.index;

import com.github.jelmerk.hnswlib.core.DistanceFunction;
import com.github.jelmerk.hnswlib.core.DistanceFunctions;
import com.github.jelmerk.hnswlib.core.SearchResult;
import com.github.jelmerk.hnswlib.core.hnsw.HnswIndex;
import com.vectordb.common.model.VectorEntry;
import com.vectordb.storage.similarity.VectorSimilarity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Реализация HNSW индекса с использованием чистой Java библиотеки hnswlib.
 * Обеспечивает эффективный приближенный поиск ближайших соседей для векторов высокой размерности.
 */
@Component
@Slf4j
public class HnswVectorIndex implements VectorIndex {
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final VectorSimilarity vectorSimilarity;
    
    // Параметры HNSW
    private final int maxElements;
    private final int m;
    private final int efConstruction;
    private final int efSearch;
    private final String spaceType;
    
    /** Состояние индекса для одной базы данных */
    private static class DatabaseIndex {
        /** Индекс построен */
        boolean isBuilt = false;
        
        /** Размерность векторов (-1 если не определена) */
        int dimension = -1;
        
        /** Буфер векторов до построения индекса */
        List<VectorEntry> vectorBuffer = new ArrayList<>();
        
        /** HNSW индекс для быстрого поиска */
        HnswIndex<Long, float[], VectorEntry, Float> hnswIndex;
        
        /** Маппинг ID → VectorEntry */
        Map<Long, VectorEntry> idToEntry = new HashMap<>();
        
        /** Удалённые ID (мягкое удаление) */
        Set<Long> deletedIds = new HashSet<>();
    }
    
    private final Map<String, DatabaseIndex> databaseIndices = new ConcurrentHashMap<>();
    private int defaultDimension = -1;

    public HnswVectorIndex(
            VectorSimilarity vectorSimilarity,
            @Value("${vector.index.maxElements:10000}") int maxElements,
            @Value("${vector.index.m:16}") int m,
            @Value("${vector.index.efConstruction:200}") int efConstruction,
            @Value("${vector.index.efSearch:100}") int efSearch,
            @Value("${vector.index.spaceType:cosine}") String spaceType) {
        this.vectorSimilarity = vectorSimilarity;
        this.maxElements = maxElements;
        this.m = m;
        this.efConstruction = efConstruction;
        this.efSearch = efSearch;
        this.spaceType = spaceType;
        log.info("Initialized HNSW index using pure Java hnswlib with maxElements={}, m={}, efConstruction={}, efSearch={}, spaceType={}", 
                maxElements, m, efConstruction, efSearch, spaceType);
    }
    
    /** Получить или создать индекс для БД */
    private DatabaseIndex getOrCreateDatabaseIndex(String databaseId) {
        return databaseIndices.computeIfAbsent(databaseId, _ -> {
            DatabaseIndex dbIndex = new DatabaseIndex();
            if (defaultDimension > 0) {
                dbIndex.dimension = defaultDimension;
            }
            return dbIndex;
        });
    }
    
    @Override
    public void build(String databaseId) {
        lock.writeLock().lock();
        try {
            DatabaseIndex dbIndex = getOrCreateDatabaseIndex(databaseId);
            
            // Check if dimension is set
            if (dbIndex.dimension == -1) {
                throw new IllegalStateException("Dimension must be set before building index for database " + databaseId);
            }

            if (dbIndex.vectorBuffer.isEmpty()) {
                // Create empty index
                DistanceFunction<float[], Float> distanceFunction = createDistanceFunction();

                dbIndex.hnswIndex = HnswIndex.newBuilder(dbIndex.dimension, distanceFunction, maxElements)
                    .withM(m)
                    .withEfConstruction(efConstruction)
                    .withEf(efSearch)
                    .build();

                dbIndex.isBuilt = true;
                log.info("Built empty HNSW index for database {} with dimension={}", databaseId, dbIndex.dimension);
                dbIndex.isBuilt = true;
                return;
            }

            log.info("Building HNSW index for database {} with {} vectors, dimension={}", 
                    databaseId, dbIndex.vectorBuffer.size(), dbIndex.dimension);
            
            DistanceFunction<float[], Float> distanceFunction = createDistanceFunction();
            
            dbIndex.hnswIndex = HnswIndex.newBuilder(dbIndex.dimension, distanceFunction, maxElements)
                .withM(m)
                .withEfConstruction(efConstruction)
                .withEf(efSearch)
                .build();
            
            List<VectorEntry> items = new ArrayList<>();
            for (VectorEntry entry : dbIndex.vectorBuffer) {
                items.add(entry);
                dbIndex.idToEntry.put(entry.id(), entry);
            }
            
            dbIndex.hnswIndex.addAll(items);
            
            dbIndex.isBuilt = true;
            dbIndex.vectorBuffer.clear();
            log.info("Successfully built HNSW index for database {} with {} vectors using pure Java hnswlib", 
                    databaseId, items.size());
            
        } catch (Exception e) {
            log.error("Failed to build HNSW index for database {}", databaseId, e);
            throw new RuntimeException("Failed to build HNSW index for database " + databaseId, e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void add(VectorEntry vector, String databaseId) {
        lock.writeLock().lock();
        try {
            DatabaseIndex dbIndex = getOrCreateDatabaseIndex(databaseId);
            
            if (!dbIndex.isBuilt) {
                if (dbIndex.dimension == -1) {
                    throw new IllegalStateException("Dimension must be set before adding vectors to database " + databaseId);
                } else if (dbIndex.dimension != vector.dimension()) {
                    throw new IllegalArgumentException(
                        String.format("Vector dimension mismatch for database %s. Expected: %d, got: %d",
                            databaseId, dbIndex.dimension, vector.dimension()));
                }
                dbIndex.vectorBuffer.add(vector);
                log.debug("Buffered vector {} for database {} index building", vector.id(), databaseId);
                return;
            }
            
            if (dbIndex.hnswIndex == null || (dbIndex.hnswIndex.size() == 0 && dbIndex.idToEntry.isEmpty())) {
                dbIndex.dimension = vector.dimension();
                
                DistanceFunction<float[], Float> distanceFunction = createDistanceFunction();
                
                dbIndex.hnswIndex = HnswIndex.newBuilder(dbIndex.dimension, distanceFunction, maxElements)
                    .withM(m)
                    .withEfConstruction(efConstruction)
                    .withEf(efSearch)
                    .build();
                    
                log.debug("Rebuilt empty HNSW index for database {} with dimension={}", databaseId, dbIndex.dimension);
            } else if (dbIndex.dimension != vector.dimension()) {
                throw new IllegalArgumentException(
                    String.format("Vector dimension mismatch for database %s. Expected: %d, got: %d",
                        databaseId, dbIndex.dimension, vector.dimension()));
            }
            
            dbIndex.hnswIndex.add(vector);
            dbIndex.idToEntry.put(vector.id(), vector);
            log.debug("Added vector {} to built index for database {} using pure Java hnswlib", vector.id(), databaseId);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean remove(Long vectorId, String databaseId) {
        lock.writeLock().lock();
        try {
            DatabaseIndex dbIndex = databaseIndices.get(databaseId);
            if (dbIndex == null) {
                log.debug("Database {} not found", databaseId);
                return false;
            }
            
            if (!dbIndex.isBuilt || dbIndex.hnswIndex == null) {
                boolean removed = dbIndex.vectorBuffer.removeIf(entry -> entry.id().equals(vectorId));
                log.debug("Removed vector {} from buffer for database {}: {}", vectorId, databaseId, removed);
                return removed;
            }
            
            if (!dbIndex.idToEntry.containsKey(vectorId) || dbIndex.deletedIds.contains(vectorId)) {
                log.debug("Vector {} not found in built index for database {} or already deleted", vectorId, databaseId);
                return false;
            }
            
            // Помечаем как удаленный вместо физического удаления из HNSW индекса
            dbIndex.deletedIds.add(vectorId);
            log.debug("Marked vector {} as deleted in built HNSW index for database {}", vectorId, databaseId);
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public List<com.vectordb.common.model.SearchResult> search(float[] queryVector, int k, String databaseId) {
        lock.readLock().lock();
        try {
            DatabaseIndex dbIndex = databaseIndices.get(databaseId);
            if (dbIndex == null) {
                log.debug("Database {} not found, returning empty results", databaseId);
                return List.of();
            }
            
            if (!dbIndex.isBuilt || dbIndex.hnswIndex == null) {
                log.debug("Index not built for database {}, performing linear search on {} buffered vectors", 
                        databaseId, dbIndex.vectorBuffer.size());
                return linearSearch(queryVector, k, dbIndex.vectorBuffer, databaseId);
            }
            
            if (queryVector.length != dbIndex.dimension) {
                throw new IllegalArgumentException(
                    String.format("Query vector dimension mismatch for database %s. Expected: %d, got: %d",
                        databaseId, dbIndex.dimension, queryVector.length));
            }
            
            log.debug("Performing HNSW search for k={} on {} indexed vectors in database {} using pure Java hnswlib", 
                    k, dbIndex.hnswIndex.size(), databaseId);

            Long tempQueryId = Long.MIN_VALUE + System.nanoTime();
            VectorEntry queryItem = new VectorEntry(
                tempQueryId,                                      // id
                queryVector,                                      // embedding
                null,                                             // originalData
                databaseId,                                       // databaseId
                null                                              // createdAt
            );
            
            dbIndex.hnswIndex.add(queryItem);
            
            try {
                int searchK = Math.min(k * 2 + 10, dbIndex.hnswIndex.size());
                List<SearchResult<VectorEntry, Float>> allResults = dbIndex.hnswIndex.findNeighbors(tempQueryId, searchK);
                
                List<com.vectordb.common.model.SearchResult> results = new ArrayList<>();
                for (SearchResult<VectorEntry, Float> hnswResult : allResults) {
                    VectorEntry item = hnswResult.item();
                    Long itemId = item.id();
                    
                    if (itemId.equals(tempQueryId)) {
                        continue;
                    }
                    
                    if (dbIndex.deletedIds.contains(itemId)) {
                        continue;
                    }
                    
                    VectorEntry entry = dbIndex.idToEntry.get(itemId);
                    if (entry == null) {
                        entry = item;
                    }
                    if (entry == null) {
                        log.warn("No entry found for vector ID: {} in database {}", itemId, databaseId);
                        continue;
                    }
                    
                    float distance = hnswResult.distance();
                    double similarity = distanceToSimilarity(distance);
                    results.add(new com.vectordb.common.model.SearchResult(entry, distance, similarity));
                    
                    if (results.size() >= k) {
                        break;
                    }
                }
                
                log.debug("HNSW search found {} results for database {} using pure Java hnswlib", results.size(), databaseId);
                return results;
                
            } finally {
                try {
                    dbIndex.hnswIndex.remove(tempQueryId, 0);
                } catch (Exception e) {
                    log.warn("Failed to remove temporary query item from index for database {}: {}", databaseId, e.getMessage());
                }
            }
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void save(String filePath, String databaseId) {
        lock.writeLock().lock();
        try {
            DatabaseIndex dbIndex = databaseIndices.get(databaseId);
            if (dbIndex == null || !dbIndex.isBuilt || dbIndex.hnswIndex == null) {
                throw new IllegalStateException("Index must be built before saving for database " + databaseId);
            }
            
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            
            try (FileOutputStream fos = new FileOutputStream(filePath);
                 ObjectOutputStream out = new ObjectOutputStream(fos)) {
                
                out.writeObject(dbIndex.idToEntry);
                out.writeInt(dbIndex.dimension);
                out.writeObject(spaceType);
                out.writeObject(dbIndex.deletedIds);
                
                dbIndex.hnswIndex.save(out);
                out.flush();
                
                log.info("Saved HNSW index for database {} to {} using pure Java hnswlib", databaseId, filePath);
            }
            
        } catch (IOException e) {
            log.error("Failed to save index for database {} to {}", databaseId, filePath, e);
            throw new RuntimeException("Failed to save index for database " + databaseId + " to " + filePath, e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void load(String filePath, String databaseId) {
        lock.writeLock().lock();
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IllegalArgumentException("Index file not found: " + filePath);
            }
            
            DatabaseIndex dbIndex = getOrCreateDatabaseIndex(databaseId);
            
            try (FileInputStream fis = new FileInputStream(filePath);
                 ObjectInputStream in = new ObjectInputStream(fis)) {
                
                @SuppressWarnings("unchecked")
                Map<Long, VectorEntry> loadedIdToEntry = (Map<Long, VectorEntry>) in.readObject();
                dbIndex.idToEntry.clear();
                dbIndex.idToEntry.putAll(loadedIdToEntry);
                
                dbIndex.dimension = in.readInt();
                @SuppressWarnings("unused")
                String loadedSpaceType = (String) in.readObject();
                
                @SuppressWarnings("unchecked")
                Set<Long> loadedDeletedIds = (Set<Long>) in.readObject();
                dbIndex.deletedIds.clear();
                dbIndex.deletedIds.addAll(loadedDeletedIds);
                
                dbIndex.hnswIndex = HnswIndex.load(in);
                
                dbIndex.isBuilt = true;
                dbIndex.vectorBuffer.clear();
                
                log.info("Loaded HNSW index for database {} from {} with {} vectors, dimension={} using pure Java hnswlib",
                        databaseId, filePath, dbIndex.hnswIndex.size(), dbIndex.dimension);
            }
            
        } catch (IOException | ClassNotFoundException e) {
            log.error("Failed to load index for database {} from {}", databaseId, filePath, e);
            throw new RuntimeException("Failed to load index for database " + databaseId + " from " + filePath, e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public int size(String databaseId) {
        lock.readLock().lock();
        try {
            DatabaseIndex dbIndex = databaseIndices.get(databaseId);
            if (dbIndex == null) {
                return 0;
            }
            
            if (!dbIndex.isBuilt || dbIndex.hnswIndex == null) {
                return dbIndex.vectorBuffer.size();
            }
            return dbIndex.hnswIndex.size() - dbIndex.deletedIds.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void clear(String databaseId) {
        lock.writeLock().lock();
        try {
            DatabaseIndex dbIndex = databaseIndices.get(databaseId);
            if (dbIndex != null) {
                dbIndex.hnswIndex = null;
                dbIndex.idToEntry.clear();
                dbIndex.deletedIds.clear();
                dbIndex.isBuilt = false;
                dbIndex.vectorBuffer.clear();
                dbIndex.dimension = -1;
                log.info("Cleared HNSW index for database {}", databaseId);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void clearAll() {
        lock.writeLock().lock();
        try {
            databaseIndices.clear();
            log.info("Cleared all HNSW indices");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean isBuilt(String databaseId) {
        lock.readLock().lock();
        try {
            DatabaseIndex dbIndex = databaseIndices.get(databaseId);
            return dbIndex != null && dbIndex.isBuilt;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void setDimension(int dimension) {
        lock.writeLock().lock();
        try {
            if (dimension <= 0) {
                throw new IllegalArgumentException("Dimension must be positive");
            }
            // Set dimension for all databases that don't have it set yet
            for (DatabaseIndex dbIndex : databaseIndices.values()) {
                if (dbIndex.isBuilt) {
                    throw new IllegalStateException("Cannot set dimension after index is built");
                }
                if (dbIndex.dimension == -1) {
                    dbIndex.dimension = dimension;
                }
            }
            // Set default dimension for future databases
            this.defaultDimension = dimension;
            log.info("Set index dimension to {}", dimension);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Создание функции расстояния на основе типа пространства
     */
    private DistanceFunction<float[], Float> createDistanceFunction() {
        return switch (spaceType.toLowerCase()) {
            case "cosine" -> DistanceFunctions.FLOAT_COSINE_DISTANCE;
            case "euclidean", "l2" -> DistanceFunctions.FLOAT_EUCLIDEAN_DISTANCE;
            case "manhattan", "l1" -> DistanceFunctions.FLOAT_MANHATTAN_DISTANCE;
            default -> {
                log.warn("Unknown space type: {}, defaulting to cosine", spaceType);
                yield DistanceFunctions.FLOAT_COSINE_DISTANCE;
            }
        };
    }

    /**
     * Конвертация расстояния в оценку сходства (выше - лучше)
     */
    private double distanceToSimilarity(double distance) {
        return switch (spaceType.toLowerCase()) {
            case "cosine" -> 1.0 - distance; // Cosine distance is 1 - cosine_similarity
            case "euclidean", "l2", "manhattan", "l1" -> 1.0 / (1.0 + distance); // Convert distance to similarity
            default -> 1.0 - distance;
        };
    }
    
    /**
     * Линейный поиск по коллекции векторов (резервный метод)
     */
    private List<com.vectordb.common.model.SearchResult> linearSearch(float[] queryVector, int k, List<VectorEntry> vectors, String databaseId) {
        DatabaseIndex dbIndex = databaseIndices.get(databaseId);
        if (dbIndex == null) {
            return List.of();
        }

        // Validate query vector dimension
        if (dbIndex.dimension > 0 && queryVector.length != dbIndex.dimension) {
            throw new IllegalArgumentException(
                String.format("Query vector dimension mismatch for database %s. Expected: %d, got: %d",
                    databaseId, dbIndex.dimension, queryVector.length));
        }

        List<com.vectordb.common.model.SearchResult> results = new ArrayList<>();
        
        for (VectorEntry vector : vectors) {
            if (vector.dimension() != queryVector.length) {
                continue;
            }
            
            float distance = calculateDistance(queryVector, vector.embedding());
            double similarity = distanceToSimilarity(distance);
            results.add(new com.vectordb.common.model.SearchResult(vector, distance, similarity));
        }
        
        results.sort(Comparator.comparingDouble(com.vectordb.common.model.SearchResult::distance));
        int resultSize = Math.min(k, results.size());
        
        log.debug("Linear search found {} results, returning top {}", results.size(), resultSize);
        return results.subList(0, resultSize);
    }
    
    /**
     * Вычисление расстояния между двумя векторами с использованием VectorSimilarity
     */
    private float calculateDistance(float[] a, float[] b) {
        // Convert to double for calculation, then back to float
        double[] aDouble = new double[a.length];
        double[] bDouble = new double[b.length];
        for (int i = 0; i < a.length; i++) {
            aDouble[i] = a[i];
            bDouble[i] = b[i];
        }

        double distance = switch (spaceType.toLowerCase()) {
            case "cosine" -> 1.0 - vectorSimilarity.cosineSimilarity(aDouble, bDouble);
            case "euclidean", "l2" -> vectorSimilarity.euclideanDistance(aDouble, bDouble);
            case "manhattan", "l1" -> vectorSimilarity.manhattanDistance(aDouble, bDouble);
            default -> {
                log.warn("Unknown space type: {}, defaulting to cosine", spaceType);
                yield 1.0 - vectorSimilarity.cosineSimilarity(aDouble, bDouble);
            }
        };
        return (float) distance;
    }
}