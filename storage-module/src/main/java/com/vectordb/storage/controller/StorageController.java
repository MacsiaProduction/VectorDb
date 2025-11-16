package com.vectordb.storage.controller;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.common.serialization.SearchResultSerializer;
import com.vectordb.storage.service.VectorStorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {
    
    private final VectorStorageService storageService;
    private final SearchResultSerializer searchResultSerializer;
    
    @PostMapping("/vectors/{databaseId}")
    public ResponseEntity<Long> addVector(
            @PathVariable String databaseId,
            @Valid @RequestBody VectorEntry entry) {
        try {
            Long id = storageService.add(entry, databaseId);
            return ResponseEntity.status(HttpStatus.CREATED).body(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request for database {}: {}", databaseId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to add vector to database {}", databaseId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/vectors/{databaseId}/{id}")
    public ResponseEntity<VectorEntry> getVector(
            @PathVariable String databaseId,
            @PathVariable Long id) {
        return storageService.get(id, databaseId)
                           .map(ResponseEntity::ok)
                           .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/vectors/{databaseId}/{id}")
    public ResponseEntity<Void> deleteVector(
            @PathVariable String databaseId,
            @PathVariable Long id) {
        boolean deleted = storageService.delete(id, databaseId);
        return deleted ? ResponseEntity.noContent().build()
                       : ResponseEntity.notFound().build();
    }
    
    @PostMapping(value = "/search", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public ResponseEntity<?> searchVectors(
            @Valid @RequestBody SearchQuery query,
            @RequestHeader(value = "Accept", defaultValue = MediaType.APPLICATION_JSON_VALUE) String acceptHeader) {
        try {
            List<SearchResult> results = storageService.search(query);
            
            if (acceptHeader.contains(MediaType.APPLICATION_OCTET_STREAM_VALUE)) {
                byte[] serialized = searchResultSerializer.serialize(results);
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(serialized);
            }
            
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            log.error("Invalid search query for database {}: {}", query.databaseId(), e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to search in database {}", query.databaseId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/databases")
    public ResponseEntity<DatabaseInfo> createDatabase(@Valid @RequestBody CreateDatabaseRequest request) {
        try {
            DatabaseInfo dbInfo = storageService.createDatabase(request.id(), request.name(), request.dimension());
            return ResponseEntity.status(HttpStatus.CREATED).body(dbInfo);
        } catch (IllegalArgumentException e) {
            log.error("Invalid database creation request for {}: {}", request.id(), e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to create database {}", request.id(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @DeleteMapping("/databases/{databaseId}")
    public ResponseEntity<Void> dropDatabase(@PathVariable String databaseId) {
        boolean dropped = storageService.dropDatabase(databaseId);
        return dropped ? ResponseEntity.noContent().build() 
                       : ResponseEntity.notFound().build();
    }
    
    @GetMapping("/databases/{databaseId}")
    public ResponseEntity<DatabaseInfo> getDatabaseInfo(@PathVariable String databaseId) {
        return storageService.getDatabaseInfo(databaseId)
                            .map(ResponseEntity::ok)
                            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/databases")
    public ResponseEntity<List<DatabaseInfo>> listDatabases() {
        return ResponseEntity.ok(storageService.listDatabases());
    }
    
    @PostMapping("/databases/{databaseId}/rebuild")
    public ResponseEntity<Void> rebuildIndex(@PathVariable String databaseId) {
        boolean rebuilt = storageService.rebuildIndex(databaseId);
        return rebuilt ? ResponseEntity.ok().build()
                       : ResponseEntity.badRequest().build();
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> getHealth() {
        boolean healthy = storageService.isHealthy();
        return healthy ? ResponseEntity.ok("UP") 
                       : ResponseEntity.status(503).body("DOWN");
    }

    @GetMapping("/admin/vectors/{databaseId}/range")
    public ResponseEntity<List<VectorEntry>> scanRange(
            @PathVariable String databaseId,
            @RequestParam(name = "fromExclusive", defaultValue = "0") long fromExclusive,
            @RequestParam(name = "toInclusive") long toInclusive,
            @RequestParam(name = "limit", defaultValue = "1000") int limit) {
        return ResponseEntity.ok(storageService.scanByRange(databaseId, fromExclusive, toInclusive, limit));
    }

    @PostMapping("/admin/vectors/{databaseId}/batch")
    public ResponseEntity<Void> putBatch(
            @PathVariable String databaseId,
            @Valid @RequestBody List<VectorEntry> entries) {
        storageService.putBatch(databaseId, entries);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/admin/vectors/{databaseId}/batch")
    public ResponseEntity<Integer> deleteBatch(
            @PathVariable String databaseId,
            @Valid @RequestBody DeleteBatchRequest request) {
        int removed = storageService.deleteBatch(databaseId, request.ids());
        return ResponseEntity.ok(removed);
    }
    
    public record CreateDatabaseRequest(
        @NotBlank String id, 
        @NotBlank String name,
        @Min(1) int dimension
    ) {}

    public record DeleteBatchRequest(
            @NotEmpty List<Long> ids
    ) {}
}
