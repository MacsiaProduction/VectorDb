package com.vectordb.main.controller;

import com.vectordb.main.cluster.model.ClusterConfig;
import com.vectordb.main.cluster.repository.ClusterConfigRepository;
import com.vectordb.main.cluster.service.ClusterReshardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin controller for managing cluster configuration.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/cluster")
@RequiredArgsConstructor
@Tag(name = "Cluster Administration", description = "Admin operations for managing cluster configuration")
public class ClusterAdminController {

    private final ClusterConfigRepository clusterConfigRepository;
    private final ClusterReshardingService clusterReshardingService;

    @GetMapping("/config")
    @Operation(summary = "Get current cluster configuration",
               description = "Retrieve the current cluster configuration from ZooKeeper")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved cluster configuration"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ClusterConfig> getClusterConfig() {
        log.info("Received request to get cluster configuration");
        ClusterConfig config = clusterConfigRepository.getClusterConfig();
        return ResponseEntity.ok(config);
    }

    @PostMapping("/config")
    @Operation(summary = "Update cluster configuration",
               description = "Update the cluster configuration in ZooKeeper and trigger resharding if necessary")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cluster configuration updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid configuration"),
        @ApiResponse(responseCode = "500", description = "Internal server error during configuration update")
    })
    public ResponseEntity<String> updateClusterConfig(@RequestBody ClusterConfig newConfig) {
        log.info("Received request to update cluster configuration: {} shards", newConfig.shards().size());
        
        try {
            clusterReshardingService.applyNewConfiguration(newConfig);
            return ResponseEntity.ok("Cluster configuration updated successfully. Resharding initiated if necessary.");
        } catch (Exception e) {
            log.error("Failed to update cluster configuration", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to update cluster configuration: " + e.getMessage());
        }
    }
}

