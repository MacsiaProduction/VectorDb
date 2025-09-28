package com.vectordb.main.controller;

import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.common.service.VectorStorageService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/databases")
@RequiredArgsConstructor
public class DatabaseController {
    
    private final VectorStorageService vectorService;
    
    @PostMapping
    public Mono<ResponseEntity<DatabaseInfo>> createDatabase(
            @RequestBody CreateDatabaseRequest request) {
        return Mono.fromSupplier(() -> 
                vectorService.createDatabase(request.id(), request.name()))
                   .map(dbInfo -> ResponseEntity.status(HttpStatus.CREATED).body(dbInfo))
                   .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @DeleteMapping("/{databaseId}")
    public Mono<ResponseEntity<Void>> dropDatabase(@PathVariable String databaseId) {
        return Mono.fromSupplier(() -> vectorService.dropDatabase(databaseId))
                   .map(deleted -> deleted ? ResponseEntity.noContent().build()
                                           : ResponseEntity.notFound().build());
    }
    
    @GetMapping("/{databaseId}")
    public Mono<ResponseEntity<DatabaseInfo>> getDatabaseInfo(@PathVariable String databaseId) {
        return Mono.fromSupplier(() -> vectorService.getDatabaseInfo(databaseId))
                   .map(dbInfo -> dbInfo.map(ResponseEntity::ok)
                                       .orElse(ResponseEntity.notFound().build()));
    }
    
    @GetMapping
    public Flux<DatabaseInfo> listDatabases() {
        return Flux.fromIterable(vectorService.listDatabases());
    }
    
    public record CreateDatabaseRequest(
        @NotBlank String id,
        @NotBlank String name
    ) {}
}
