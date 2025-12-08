package com.vectordb.storage.kv;

import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.common.model.VectorEntry;

import java.util.List;
import java.util.Optional;

/**
 * Интерфейс для хранения векторов и метаданных БД
 */
public interface KeyValueStorage {
    
    /** Сохранить вектор */
    void putVector(String databaseId, VectorEntry entry) throws Exception;
    
    /** Получить вектор по ID */
    Optional<VectorEntry> getVector(String databaseId, Long id) throws Exception;
    
    /** Удалить вектор */
    boolean deleteVector(String databaseId, Long id) throws Exception;
    
    /** Получить все векторы БД */
    List<VectorEntry> getAllVectors(String databaseId) throws Exception;
    
    /** Сохранить метаданные БД */
    void putDatabaseInfo(DatabaseInfo dbInfo) throws Exception;
    
    /** Получить метаданные БД */
    Optional<DatabaseInfo> getDatabaseInfo(String databaseId) throws Exception;
    
    /** Удалить метаданные БД */
    boolean deleteDatabaseInfo(String databaseId) throws Exception;
    
    /** Получить список всех БД */
    List<DatabaseInfo> getAllDatabases() throws Exception;

    /** Реплика: Сохранить вектор-реплику */
    void putVectorReplica(String databaseId, VectorEntry entry, String sourceShardId) throws Exception;

    /** Реплика: Получить вектор-реплику */
    Optional<VectorEntry> getVectorReplica(String databaseId, Long id, String sourceShardId) throws Exception;

    /** Реплика: Удалить вектор-реплику */
    boolean deleteVectorReplica(String databaseId, Long id, String sourceShardId) throws Exception;

    /** Реплика: Получить все векторы-реплики для источника */
    List<VectorEntry> getAllVectorReplicas(String databaseId, String sourceShardId) throws Exception;
}