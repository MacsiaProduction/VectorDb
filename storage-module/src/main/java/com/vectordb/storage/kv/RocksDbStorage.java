package com.vectordb.storage.kv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.DatabaseInfo;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.rocksdb.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RocksDbStorage {
    private static final String VECTOR_CF = "vectors";
    private static final String DB_INFO_CF = "db_info";
    
    @Value("${vector-db.storage.data-path:./data}")
    private String dataPath;
    
    private RocksDB rocksDB;
    private final Map<String, ColumnFamilyHandle> columnFamilyHandles = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    
    @PostConstruct
    public void initialize() {
        RocksDB.loadLibrary();
        
        try {
            Path dbPath = Paths.get(dataPath);
            dbPath.toFile().mkdirs();
            
            List<ColumnFamilyDescriptor> columnFamilyDescriptors = Arrays.asList(
                new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY),
                new ColumnFamilyDescriptor(VECTOR_CF.getBytes()),
                new ColumnFamilyDescriptor(DB_INFO_CF.getBytes())
            );
            
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            
            DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true);
            
            rocksDB = RocksDB.open(dbOptions, dbPath.toString(), columnFamilyDescriptors, handles);
            
            columnFamilyHandles.put("default", handles.get(0));
            columnFamilyHandles.put(VECTOR_CF, handles.get(1));
            columnFamilyHandles.put(DB_INFO_CF, handles.get(2));
            
            log.info("RocksDB initialized at path: {}", dbPath);
            
        } catch (RocksDBException e) {
            log.error("Failed to initialize RocksDB", e);
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }
    
    public void putVector(String databaseId, VectorEntry entry) throws Exception {
        String key = databaseId + ":" + entry.id();
        byte[] value = objectMapper.writeValueAsBytes(entry);
        rocksDB.put(columnFamilyHandles.get(VECTOR_CF), key.getBytes(), value);
    }
    
    public Optional<VectorEntry> getVector(String databaseId, String id) throws Exception {
        String key = databaseId + ":" + id;
        byte[] value = rocksDB.get(columnFamilyHandles.get(VECTOR_CF), key.getBytes());
        
        if (value == null) {
            return Optional.empty();
        }
        
        VectorEntry entry = objectMapper.readValue(value, VectorEntry.class);
        return Optional.of(entry);
    }
    
    public boolean deleteVector(String databaseId, String id) throws Exception {
        String key = databaseId + ":" + id;
        byte[] existing = rocksDB.get(columnFamilyHandles.get(VECTOR_CF), key.getBytes());
        
        if (existing == null) {
            return false;
        }
        
        rocksDB.delete(columnFamilyHandles.get(VECTOR_CF), key.getBytes());
        return true;
    }
    
    public List<VectorEntry> getAllVectors(String databaseId) throws Exception {
        List<VectorEntry> vectors = new ArrayList<>();
        String prefix = databaseId + ":";
        
        try (RocksIterator iterator = rocksDB.newIterator(columnFamilyHandles.get(VECTOR_CF))) {
            iterator.seekToFirst();
            
            while (iterator.isValid()) {
                String key = new String(iterator.key());
                if (key.startsWith(prefix)) {
                    VectorEntry entry = objectMapper.readValue(iterator.value(), VectorEntry.class);
                    vectors.add(entry);
                }
                iterator.next();
            }
        }
        
        return vectors;
    }
    
    public void putDatabaseInfo(DatabaseInfo dbInfo) throws Exception {
        byte[] value = objectMapper.writeValueAsBytes(dbInfo);
        rocksDB.put(columnFamilyHandles.get(DB_INFO_CF), dbInfo.id().getBytes(), value);
    }
    
    public Optional<DatabaseInfo> getDatabaseInfo(String databaseId) throws Exception {
        byte[] value = rocksDB.get(columnFamilyHandles.get(DB_INFO_CF), databaseId.getBytes());
        
        if (value == null) {
            return Optional.empty();
        }
        
        DatabaseInfo dbInfo = objectMapper.readValue(value, DatabaseInfo.class);
        return Optional.of(dbInfo);
    }
    
    public boolean deleteDatabaseInfo(String databaseId) throws Exception {
        byte[] existing = rocksDB.get(columnFamilyHandles.get(DB_INFO_CF), databaseId.getBytes());
        
        if (existing == null) {
            return false;
        }
        
        rocksDB.delete(columnFamilyHandles.get(DB_INFO_CF), databaseId.getBytes());
        return true;
    }
    
    public List<DatabaseInfo> getAllDatabases() throws Exception {
        List<DatabaseInfo> databases = new ArrayList<>();
        
        try (RocksIterator iterator = rocksDB.newIterator(columnFamilyHandles.get(DB_INFO_CF))) {
            iterator.seekToFirst();
            
            while (iterator.isValid()) {
                DatabaseInfo dbInfo = objectMapper.readValue(iterator.value(), DatabaseInfo.class);
                databases.add(dbInfo);
                iterator.next();
            }
        }
        
        return databases;
    }
    
    @PreDestroy
    public void cleanup() {
        if (rocksDB != null) {
            columnFamilyHandles.values().forEach(ColumnFamilyHandle::close);
            rocksDB.close();
            log.info("RocksDB closed successfully");
        }
    }
}
