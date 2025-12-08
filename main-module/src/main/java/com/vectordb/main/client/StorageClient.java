package com.vectordb.main.client;

import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.serialization.SearchResultDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
public class StorageClient {
    
    private static final String STORAGE_API_BASE_PATH = "/api/v1/storage";
    
    private final WebClient storageWebClient;
    private final SearchResultDeserializer searchResultDeserializer;

    public StorageClient(WebClient storageWebClient, SearchResultDeserializer searchResultDeserializer) {
        this.storageWebClient = storageWebClient;
        this.searchResultDeserializer = searchResultDeserializer;
    }
    
    public Mono<Long> addVector(VectorEntry entry, String databaseId) {
        log.debug("Adding vector to database {} via storage service", databaseId);
        
        return storageWebClient
                .post()
                .uri(STORAGE_API_BASE_PATH + "/vectors/{databaseId}", databaseId)
                .bodyValue(entry)
                .retrieve()
                .bodyToMono(Long.class)
                .doOnSuccess(id -> log.debug("Vector added with ID: {}", id))
                .doOnError(error -> log.error("Failed to add vector to database {}: {}", databaseId, error.getMessage()));
    }
    
    public Mono<VectorEntry> getVector(Long id, String databaseId) {
        log.debug("Getting vector {} from database {} via storage service", id, databaseId);
        
        return storageWebClient
                .get()
                .uri(STORAGE_API_BASE_PATH + "/vectors/{databaseId}/{id}", databaseId, id)
                .retrieve()
                .bodyToMono(VectorEntry.class)
                .doOnError(WebClientResponseException.NotFound.class,
                        _ -> log.debug("Vector {} not found in database {}", id, databaseId));
    }
    
    public Mono<Boolean> deleteVector(Long id, String databaseId) {
        log.debug("Deleting vector {} from database {} via storage service", id, databaseId);
        
        return storageWebClient
                .delete()
                .uri(STORAGE_API_BASE_PATH + "/vectors/{databaseId}/{id}", databaseId, id)
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnSuccess(deleted -> log.debug("Vector {} deletion result: {}", id, deleted));
    }
    
    public Mono<List<SearchResult>> searchVectors(SearchQuery query) {
        log.debug("Searching vectors via storage service (binary format)");
        
        return storageWebClient
                .post()
                .uri(STORAGE_API_BASE_PATH + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(query)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> {
                    try {
                        return searchResultDeserializer.deserialize(bytes);
                    } catch (Exception e) {
                        log.error("Failed to deserialize search results: {}", e.getMessage());
                        throw new RuntimeException("Failed to deserialize search results", e);
                    }
                })
                .doOnSuccess(results -> log.debug("Deserialized {} search results from binary", results.size()))
                .doOnError(error -> log.error("Failed to search vectors: {}", error.getMessage()));
    }
    
    public Mono<DatabaseInfo> createDatabase(String id, String name, int dimension) {
        log.debug("Creating database {} with name {} and dimension {} via storage service", id, name, dimension);
        
        CreateDatabaseRequest request = new CreateDatabaseRequest(id, name, dimension);
        
        return storageWebClient
                .post()
                .uri(STORAGE_API_BASE_PATH + "/databases")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DatabaseInfo.class)
                .doOnSuccess(dbInfo -> log.debug("Database created: {}", dbInfo))
                .doOnError(error -> log.error("Failed to create database {}: {}", id, error.getMessage()));
    }
    
    public Mono<Boolean> dropDatabase(String databaseId) {
        log.debug("Dropping database {} via storage service", databaseId);
        
        return storageWebClient
                .delete()
                .uri(STORAGE_API_BASE_PATH + "/databases/{databaseId}", databaseId)
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnSuccess(dropped -> log.debug("Database {} drop result: {}", databaseId, dropped));
    }
    
    public Mono<DatabaseInfo> getDatabaseInfo(String databaseId) {
        log.debug("Getting database info for {} via storage service", databaseId);
        
        return storageWebClient
                .get()
                .uri(STORAGE_API_BASE_PATH + "/databases/{databaseId}", databaseId)
                .retrieve()
                .bodyToMono(DatabaseInfo.class)
                .doOnError(WebClientResponseException.NotFound.class,
                        _ -> log.debug("Database {} not found", databaseId));
    }
    
    public Mono<List<DatabaseInfo>> listDatabases() {
        log.debug("Listing all databases via storage service");
        
        return storageWebClient
                .get()
                .uri(STORAGE_API_BASE_PATH + "/databases")
                .retrieve()
                .bodyToFlux(DatabaseInfo.class)
                .collectList()
                .doOnSuccess(databases -> log.debug("Found {} databases", databases.size()))
                .doOnError(error -> log.error("Failed to list databases: {}", error.getMessage()));
    }

    public record CreateDatabaseRequest(String id, String name, int dimension) {}

    public Mono<List<VectorEntry>> scanRange(String databaseId, long fromExclusive, long toInclusive, int limit) {
        return storageWebClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(STORAGE_API_BASE_PATH + "/admin/vectors/{databaseId}/range")
                        .queryParam("fromExclusive", fromExclusive)
                        .queryParam("toInclusive", toInclusive)
                        .queryParam("limit", limit)
                        .build(databaseId))
                .retrieve()
                .bodyToFlux(VectorEntry.class)
                .collectList();
    }

    public Mono<Void> putBatch(String databaseId, List<VectorEntry> entries) {
        return storageWebClient
                .post()
                .uri(STORAGE_API_BASE_PATH + "/admin/vectors/{databaseId}/batch", databaseId)
                .bodyValue(entries)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public Mono<Integer> deleteBatch(String databaseId, List<Long> ids) {
        return storageWebClient
                .method(org.springframework.http.HttpMethod.DELETE)
                .uri(STORAGE_API_BASE_PATH + "/admin/vectors/{databaseId}/batch", databaseId)
                .bodyValue(new DeleteBatchRequest(ids))
                .retrieve()
                .bodyToMono(Integer.class);
    }


    public Mono<Void> replicateVector(VectorEntry entry, String databaseId) {
        log.debug("Replicating vector {} to database {} via storage service", entry.id(), databaseId);

        return storageWebClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path(STORAGE_API_BASE_PATH + "/vectors/{databaseId}")
                        .queryParam("replica", "true") // Флаг репликации
                        .build(databaseId))
                .bodyValue(entry)
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(error -> log.warn("Failed to replicate vector {} to database {}: {}",
                        entry.id(), databaseId, error.getMessage()));
    }

    public Mono<Void> replicateDelete(Long id, String databaseId) {
        log.debug("Replicating delete for vector {} in database {}", id, databaseId);

        return storageWebClient
                .delete()
                .uri(uriBuilder -> uriBuilder
                        .path(STORAGE_API_BASE_PATH + "/vectors/{databaseId}/{id}")
                        .queryParam("replica", "true") // Флаг репликации
                        .build(databaseId, id))
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(error -> log.warn("Failed to replicate delete for vector {} in database {}: {}",
                        id, databaseId, error.getMessage()));
    }

    // Новые методы для работы с репликами
    public Mono<Long> addVectorReplica(VectorEntry entry, String databaseId, String sourceShardId) {
        log.debug("Adding replica vector {} to database {} from shard {}", entry.id(), databaseId, sourceShardId);

        return storageWebClient
                .post()
                .uri(STORAGE_API_BASE_PATH + "/vectors/{databaseId}/replica?sourceShardId={sourceShardId}",
                        databaseId, sourceShardId)
                .bodyValue(entry)
                .retrieve()
                .bodyToMono(Long.class)
                .doOnSuccess(id -> log.debug("Replica vector added with ID: {}", id))
                .doOnError(error -> log.error("Failed to add replica vector to database {}: {}", databaseId, error.getMessage()));
    }

    public Mono<VectorEntry> getVectorReplica(Long id, String databaseId, String sourceShardId) {
        log.debug("Getting replica vector {} from database {} for shard {}", id, databaseId, sourceShardId);

        return storageWebClient
                .get()
                .uri(STORAGE_API_BASE_PATH + "/vectors/{databaseId}/replica/{id}?sourceShardId={sourceShardId}",
                        databaseId, id, sourceShardId)
                .retrieve()
                .bodyToMono(VectorEntry.class)
                .doOnError(WebClientResponseException.NotFound.class,
                        _ -> log.debug("Replica vector {} not found in database {} for shard {}", id, databaseId, sourceShardId));
    }

    public Mono<Boolean> deleteVectorReplica(Long id, String databaseId, String sourceShardId) {
        log.debug("Deleting replica vector {} from database {} for shard {}", id, databaseId, sourceShardId);

        return storageWebClient
                .delete()
                .uri(STORAGE_API_BASE_PATH + "/vectors/{databaseId}/replica/{id}?sourceShardId={sourceShardId}",
                        databaseId, id, sourceShardId)
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnSuccess(deleted -> log.debug("Replica vector {} deletion result: {}", id, deleted));
    }

    public Mono<List<SearchResult>> searchReplicasForShard(SearchQuery query, String sourceShardId) {
        log.debug("Searching replicas for shard {} via storage service", sourceShardId);

        return storageWebClient
                .post()
                .uri(STORAGE_API_BASE_PATH + "/search/replicas/{sourceShardId}", sourceShardId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(query)
                .retrieve()
                .bodyToMono(byte[].class)
                .map(bytes -> {
                    try {
                        return searchResultDeserializer.deserialize(bytes);
                    } catch (Exception e) {
                        log.error("Failed to deserialize replica search results: {}", e.getMessage());
                        throw new RuntimeException("Failed to deserialize replica search results", e);
                    }
                })
                .doOnSuccess(results -> log.debug("Deserialized {} replica search results from binary", results.size()))
                .doOnError(error -> log.error("Failed to search replicas: {}", error.getMessage()));
    }

    public record DeleteBatchRequest(List<Long> ids) {}
}
