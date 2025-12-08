package com.vectordb.storage.service;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.DatabaseInfo;

import java.util.List;
import java.util.Optional;

/** Интерфейс для операций с векторным хранилищем */
public interface VectorStorageService {
    
    /** Добавить вектор в хранилище */
    Long add(VectorEntry entry, String databaseId);
    
    /** Получить вектор по ID */
    Optional<VectorEntry> get(Long id, String databaseId);
    
    /** Удалить вектор по ID */
    boolean delete(Long id, String databaseId);
    
    /** Поиск похожих векторов (KNN) */
    List<SearchResult> search(SearchQuery query);

    /** Создать новую базу данных */
    DatabaseInfo createDatabase(String databaseId, String name, int dimension);
    
    /** Удалить базу данных */
    boolean dropDatabase(String databaseId);
    
    /** Получить информацию о базе данных */
    Optional<DatabaseInfo> getDatabaseInfo(String databaseId);
    
    /** Список всех баз данных */
    List<DatabaseInfo> listDatabases();
    
    /** Перестроить индекс для базы данных */
    boolean rebuildIndex(String databaseId);
    
    /** Проверить здоровье хранилища */
    boolean isHealthy();

    /** Сканировать диапазон векторов по ID (используется для миграции) */
    List<VectorEntry> scanByRange(String databaseId, long fromExclusive, long toInclusive, int limit);

    /** Добавить батч векторов (используется для миграции) */
    void putBatch(String databaseId, List<VectorEntry> entries);

    /** Удалить батч векторов */
    int deleteBatch(String databaseId, List<Long> ids);

    /** Добавить вектор-реплику */
    Long addReplica(VectorEntry entry, String databaseId, String sourceShardId);

    /** Получить вектор-реплику */
    Optional<VectorEntry> getReplica(Long id, String databaseId, String sourceShardId);

    /** Удалить вектор-реплику */
    boolean deleteReplica(Long id, String databaseId, String sourceShardId);

    /** Поиск среди реплик конкретного шарда */
    List<SearchResult> searchReplicas(SearchQuery query, String sourceShardId);
}
