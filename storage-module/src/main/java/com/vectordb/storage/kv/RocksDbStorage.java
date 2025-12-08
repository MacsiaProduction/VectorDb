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
public class RocksDbStorage implements KeyValueStorage {
    private static final String VECTOR_CF = "vectors";           // Primary данные
    private static final String DB_INFO_CF = "db_info";          // Primary метаданные
    private static final String VECTOR_REPLICAS_CF = "vector_replicas"; // Replica данные
    private static final String DB_REPLICAS_CF = "db_replicas";  // Replica метаданные
    
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
                new ColumnFamilyDescriptor(DB_INFO_CF.getBytes()),
                new ColumnFamilyDescriptor(VECTOR_REPLICAS_CF.getBytes()),
                new ColumnFamilyDescriptor(DB_REPLICAS_CF.getBytes())
            );
            
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            
            DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true);
            
            rocksDB = RocksDB.open(dbOptions, dbPath.toString(), columnFamilyDescriptors, handles);

            columnFamilyHandles.put("default", handles.get(0));
            columnFamilyHandles.put(VECTOR_CF, handles.get(1));
            columnFamilyHandles.put(DB_INFO_CF, handles.get(2));
            columnFamilyHandles.put(VECTOR_REPLICAS_CF, handles.get(3));
            columnFamilyHandles.put(DB_REPLICAS_CF, handles.get(4));
            
            log.info("RocksDB initialized at path: {}", dbPath);
            
        } catch (RocksDBException e) {
            log.error("Failed to initialize RocksDB", e);
            throw new RuntimeException("Failed to initialize storage", e);
        }
    }
    
    @Override
    public void putVector(String databaseId, VectorEntry entry) throws Exception {
        String key = databaseId + ":" + entry.id();
        byte[] value = objectMapper.writeValueAsBytes(entry);
        rocksDB.put(columnFamilyHandles.get(VECTOR_CF), key.getBytes(), value);
    }
    
    @Override
    public Optional<VectorEntry> getVector(String databaseId, Long id) throws Exception {
        String key = databaseId + ":" + id;
        byte[] value = rocksDB.get(columnFamilyHandles.get(VECTOR_CF), key.getBytes());
        
        if (value == null) {
            return Optional.empty();
        }
        
        VectorEntry entry = objectMapper.readValue(value, VectorEntry.class);
        return Optional.of(entry);
    }
    
    @Override
    public boolean deleteVector(String databaseId, Long id) throws Exception {
        String key = databaseId + ":" + id;
        byte[] existing = rocksDB.get(columnFamilyHandles.get(VECTOR_CF), key.getBytes());
        
        if (existing == null) {
            return false;
        }
        
        rocksDB.delete(columnFamilyHandles.get(VECTOR_CF), key.getBytes());
        return true;
    }
    
    @Override
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
    
    @Override
    public void putDatabaseInfo(DatabaseInfo dbInfo) throws Exception {
        byte[] value = objectMapper.writeValueAsBytes(dbInfo);
        rocksDB.put(columnFamilyHandles.get(DB_INFO_CF), dbInfo.id().getBytes(), value);
    }
    
    @Override
    public Optional<DatabaseInfo> getDatabaseInfo(String databaseId) throws Exception {
        byte[] value = rocksDB.get(columnFamilyHandles.get(DB_INFO_CF), databaseId.getBytes());
        
        if (value == null) {
            return Optional.empty();
        }
        
        DatabaseInfo dbInfo = objectMapper.readValue(value, DatabaseInfo.class);
        return Optional.of(dbInfo);
    }
    
    @Override
    public boolean deleteDatabaseInfo(String databaseId) throws Exception {
        byte[] existing = rocksDB.get(columnFamilyHandles.get(DB_INFO_CF), databaseId.getBytes());
        
        if (existing == null) {
            return false;
        }
        
        rocksDB.delete(columnFamilyHandles.get(DB_INFO_CF), databaseId.getBytes());
        return true;
    }
    
    @Override
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

    @Override
    public void putVectorReplica(String databaseId, VectorEntry entry, String sourceShardId) throws Exception {
        String key = databaseId + ":" + entry.id() + ":" + sourceShardId;
        byte[] value = objectMapper.writeValueAsBytes(entry);
        rocksDB.put(columnFamilyHandles.get(VECTOR_REPLICAS_CF), key.getBytes(), value);
    }

    @Override
    public Optional<VectorEntry> getVectorReplica(String databaseId, Long id, String sourceShardId) throws Exception {
        String key = databaseId + ":" + id + ":" + sourceShardId;
        byte[] value = rocksDB.get(columnFamilyHandles.get(VECTOR_REPLICAS_CF), key.getBytes());

        if (value == null) {
            return Optional.empty();
        }

        VectorEntry entry = objectMapper.readValue(value, VectorEntry.class);
        return Optional.of(entry);
    }

    @Override
    public boolean deleteVectorReplica(String databaseId, Long id, String sourceShardId) throws Exception {
        String key = databaseId + ":" + id + ":" + sourceShardId;
        byte[] existing = rocksDB.get(columnFamilyHandles.get(VECTOR_REPLICAS_CF), key.getBytes());

        if (existing == null) {
            return false;
        }

        rocksDB.delete(columnFamilyHandles.get(VECTOR_REPLICAS_CF), key.getBytes());
        return true;
    }

    @Override
    public List<VectorEntry> getAllVectorReplicas(String databaseId, String sourceShardId) throws Exception {
        List<VectorEntry> vectors = new ArrayList<>();
        String prefix = databaseId + ":";

        try (RocksIterator iterator = rocksDB.newIterator(columnFamilyHandles.get(VECTOR_REPLICAS_CF))) {
            iterator.seekToFirst();

            while (iterator.isValid()) {
                String key = new String(iterator.key());
                if (key.startsWith(prefix) && key.endsWith(":" + sourceShardId)) {
                    VectorEntry entry = objectMapper.readValue(iterator.value(), VectorEntry.class);
                    vectors.add(entry);
                }
                iterator.next();
            }
        }

        return vectors;
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
