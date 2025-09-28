package com.vectordb.main.controller;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.common.model.SearchQuery;
import com.vectordb.common.model.SearchResult;
import com.vectordb.common.service.VectorStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/vectors")
@RequiredArgsConstructor
public class VectorController {
    
    private final VectorStorageService vectorService;
    
    @PostMapping("/{databaseId}")
    public Mono<ResponseEntity<String>> addVector(
            @PathVariable String databaseId,
            @Valid @RequestBody VectorEntry entry) {
        return Mono.fromSupplier(() -> vectorService.add(entry, databaseId))
                   .map(id -> ResponseEntity.status(HttpStatus.CREATED).body(id))
                   .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    @GetMapping("/{databaseId}/{id}")
    public Mono<ResponseEntity<VectorEntry>> getVector(
            @PathVariable String databaseId,
            @PathVariable String id) {
        return Mono.fromSupplier(() -> vectorService.get(id, databaseId))
                   .map(entry -> entry.map(ResponseEntity::ok)
                                     .orElse(ResponseEntity.notFound().build()));
    }
    
    @DeleteMapping("/{databaseId}/{id}")
    public Mono<ResponseEntity<Void>> deleteVector(
            @PathVariable String databaseId,
            @PathVariable String id) {
        return Mono.fromSupplier(() -> vectorService.delete(id, databaseId))
                   .map(deleted -> deleted ? ResponseEntity.noContent().build()
                                           : ResponseEntity.notFound().build());
    }
    
    @PostMapping("/{databaseId}/search")
    public Flux<SearchResult> searchVectors(
            @PathVariable String databaseId,
            @Valid @RequestBody SearchQuery query) {
        SearchQuery queryWithDb = new SearchQuery(query.embedding(), query.k(), databaseId, query.threshold());
        return Flux.fromIterable(vectorService.search(queryWithDb));
    }
}
