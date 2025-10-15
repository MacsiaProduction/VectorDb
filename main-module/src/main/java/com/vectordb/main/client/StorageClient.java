package com.vectordb.main.client;

import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.VectorEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageClient {
    
    private static final String STORAGE_API_BASE_PATH = "/api/v1/storage";
    
    private final WebClient storageWebClient;
    
    public Mono<String> addVector(VectorEntry entry, String databaseId) {
        log.debug("Adding vector to database {} via storage service", databaseId);
        
        return storageWebClient
                .post()
                .uri(STORAGE_API_BASE_PATH + "/vectors/{databaseId}", databaseId)
                .bodyValue(entry)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(id -> log.debug("Vector added with ID: {}", id))
                .doOnError(error -> log.error("Failed to add vector to database {}: {}", databaseId, error.getMessage()));
    }
    
    public Mono<VectorEntry> getVector(String id, String databaseId) {
        log.debug("Getting vector {} from database {} via storage service", id, databaseId);
        
        return storageWebClient
                .get()
                .uri(STORAGE_API_BASE_PATH + "/vectors/{databaseId}/{id}", databaseId, id)
                .retrieve()
                .bodyToMono(VectorEntry.class)
                .doOnError(WebClientResponseException.NotFound.class,
                        _ -> log.debug("Vector {} not found in database {}", id, databaseId));
    }
    
    public Mono<Boolean> deleteVector(String id, String databaseId) {
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
        log.debug("Searching vectors via storage service");
        
        return storageWebClient
                .post()
                .uri(STORAGE_API_BASE_PATH + "/search")
                .bodyValue(query)
                .retrieve()
                .bodyToFlux(SearchResult.class)
                .collectList()
                .doOnSuccess(results -> log.debug("Found {} search results", results.size()))
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
    
    public Mono<Boolean> rebuildIndex(String databaseId) {
        log.debug("Rebuilding index for database {} via storage service", databaseId);
        
        return storageWebClient
                .post()
                .uri(STORAGE_API_BASE_PATH + "/databases/{databaseId}/rebuild", databaseId)
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnSuccess(rebuilt -> log.debug("Database {} rebuild result: {}", databaseId, rebuilt));
    }
    
    public Mono<Boolean> isHealthy() {
        log.debug("Checking storage service health");
        
        return storageWebClient
                .get()
                .uri(STORAGE_API_BASE_PATH + "/health")
                .retrieve()
                .bodyToMono(String.class)
                .map("UP"::equals)
                .doOnSuccess(healthy -> log.debug("Storage service health: {}", healthy ? "UP" : "DOWN"));
    }
    
    public record CreateDatabaseRequest(String id, String name, int dimension) {}
}
