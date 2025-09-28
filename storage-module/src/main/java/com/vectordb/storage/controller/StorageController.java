package com.vectordb.storage.controller;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.common.service.VectorStorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {
    
    private final VectorStorageService storageService;
    
    @PostMapping("/vectors/{databaseId}")
    public ResponseEntity<String> addVector(
            @PathVariable String databaseId,
            @Valid @RequestBody VectorEntry entry) {
        try {
            String id = storageService.add(entry, databaseId);
            return ResponseEntity.status(HttpStatus.CREATED).body(id);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to add vector: " + e.getMessage());
        }
    }
    
    @GetMapping("/vectors/{databaseId}/{id}")
    public ResponseEntity<VectorEntry> getVector(
            @PathVariable String databaseId,
            @PathVariable String id) {
        return storageService.get(id, databaseId)
                           .map(ResponseEntity::ok)
                           .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/vectors/{databaseId}/{id}")
    public ResponseEntity<Void> deleteVector(
            @PathVariable String databaseId,
            @PathVariable String id) {
        boolean deleted = storageService.delete(id, databaseId);
        return deleted ? ResponseEntity.noContent().build() 
                       : ResponseEntity.notFound().build();
    }
    
    @PostMapping("/search")
    public ResponseEntity<List<SearchResult>> searchVectors(@Valid @RequestBody SearchQuery query) {
        try {
            List<SearchResult> results = storageService.search(query);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/databases")
    public ResponseEntity<DatabaseInfo> createDatabase(@Valid @RequestBody CreateDatabaseRequest request) {
        try {
            DatabaseInfo dbInfo = storageService.createDatabase(request.id(), request.name());
            return ResponseEntity.status(HttpStatus.CREATED).body(dbInfo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
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
    
    public record CreateDatabaseRequest(
        @NotBlank String id, 
        @NotBlank String name
    ) {}
}
