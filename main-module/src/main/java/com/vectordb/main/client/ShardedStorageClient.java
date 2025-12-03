package com.vectordb.main.client;

import com.vectordb.main.cluster.model.ShardInfo;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShardedStorageClient {

    private final StorageClientFactory storageClientFactory;
    private final Map<String, StorageClient> clients = new ConcurrentHashMap<>();

    public ShardedStorageClient(StorageClientFactory storageClientFactory) {
        this.storageClientFactory = storageClientFactory;
    }

    public StorageClient getClient(ShardInfo shardInfo) {
        return clients.computeIfAbsent(shardInfo.shardId(),
                _ -> storageClientFactory.create(shardInfo.baseUri()));
    }
}


