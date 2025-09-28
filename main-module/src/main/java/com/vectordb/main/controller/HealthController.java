package com.vectordb.main.controller;

import com.vectordb.common.service.VectorStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {
    
    private final VectorStorageService vectorService;
    
    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> getHealth() {
        return Mono.fromSupplier(() -> {
            boolean healthy = vectorService.isHealthy();
            
            Map<String, Object> healthInfo = Map.of(
                "status", healthy ? "UP" : "DOWN",
                "storage", healthy
            );
            
            return healthy
                ? ResponseEntity.ok(healthInfo)
                : ResponseEntity.status(503).body(healthInfo);
        });
    }
}
