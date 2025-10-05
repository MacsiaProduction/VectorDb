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
    Optional<VectorEntry> getVector(String databaseId, String id) throws Exception;
    
    /** Удалить вектор */
    boolean deleteVector(String databaseId, String id) throws Exception;
    
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
}