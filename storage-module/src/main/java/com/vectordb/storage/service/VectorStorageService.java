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
    String add(VectorEntry entry, String databaseId);
    
    /** Получить вектор по ID */
    Optional<VectorEntry> get(String id, String databaseId);
    
    /** Удалить вектор по ID */
    boolean delete(String id, String databaseId);
    
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
}
