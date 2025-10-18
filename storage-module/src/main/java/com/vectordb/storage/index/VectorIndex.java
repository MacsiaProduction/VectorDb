package com.vectordb.storage.index;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchResult;

import java.util.List;

/**
 * Интерфейс для операций с векторным индексом.
 * Позволяет использовать разные реализации (HNSW, IVF, LSH и т.д.)
 * с единым API для сервиса хранения.
 */
public interface VectorIndex {
    
    /**
     * Построить индекс для БД
     * @param databaseId ID базы данных
     */
    void build(String databaseId);
    
    /**
     * Добавить вектор в индекс
     * @param vector вектор для добавления
     * @param databaseId ID базы данных
     */
    void add(VectorEntry vector, String databaseId);
    
    /**
     * Удалить вектор из индекса
     * @param vectorId ID вектора
     * @param databaseId ID базы данных
     * @return true если вектор найден и удалён
     */
    boolean remove(Long vectorId, String databaseId);
    
    /**
     * Поиск k ближайших соседей
     * @param queryVector вектор запроса
     * @param k количество соседей
     * @param databaseId ID базы данных
     * @return список результатов, отсортированных по расстоянию
     */
    List<SearchResult> search(float[] queryVector, int k, String databaseId);
    
    /**
     * Сохранить индекс в файл
     * @param filePath путь к файлу
     * @param databaseId ID базы данных
     */
    void save(String filePath, String databaseId);
    
    /**
     * Загрузить индекс из файла
     * @param filePath путь к файлу
     * @param databaseId ID базы данных
     */
    void load(String filePath, String databaseId);
    
    /**
     * Получить количество векторов в индексе
     * @param databaseId ID базы данных
     */
    int size(String databaseId);
    
    /**
     * Очистить индекс для БД
     * @param databaseId ID базы данных
     */
    void clear(String databaseId);
    
    /**
     * Очистить все индексы всех БД
     */
    void clearAll();
    
    /**
     * Проверить, построен ли индекс
     * @param databaseId ID базы данных
     */
    boolean isBuilt(String databaseId);
    
    /**
     * Set the dimension for the index
     * @param dimension the vector dimension
     */
    void setDimension(int dimension);
}