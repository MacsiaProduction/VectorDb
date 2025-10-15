package com.vectordb.main.controller;

import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.main.dto.CreateDatabaseRequest;
import com.vectordb.main.service.VectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/databases")
@RequiredArgsConstructor
@Tag(name = "Database Operations", description = "Operations for managing databases in the vector database")
public class DatabaseController {
    
    private final VectorService vectorService;
    
    @PostMapping
    @Operation(summary = "Create a database", 
               description = "Create a new database with the specified ID and vector dimension. The dimension parameter determines the size of vectors that can be stored in this database.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Database successfully created"),
        @ApiResponse(responseCode = "400", description = "Invalid database ID or dimension"),
        @ApiResponse(responseCode = "500", description = "Internal server error during database creation")
    })
    public ResponseEntity<Boolean> createDb(@Valid @RequestBody CreateDatabaseRequest request) {
        log.info("Received createDb request: dbId={}, dimension={}", request.id(), request.dimension());
        boolean result = vectorService.createDb(request.id(), request.dimension());
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{dbId}")
    @Operation(summary = "Drop a database", 
               description = "Delete the database with the specified ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Database successfully dropped"),
        @ApiResponse(responseCode = "400", description = "Invalid database ID or database does not exist"),
        @ApiResponse(responseCode = "500", description = "Internal server error during database deletion")
    })
    public ResponseEntity<Boolean> dropDb(
            @Parameter(description = "Database ID to drop", required = true)
            @PathVariable String dbId) {
        log.info("Received dropDb request: dbId={}", dbId);
        boolean result = vectorService.dropDb(dbId);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping
    @Operation(summary = "List all databases", 
               description = "Get a list of all available databases with their complete information including dimension, vector count, and timestamps")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved database list with full information"),
        @ApiResponse(responseCode = "500", description = "Internal server error during database listing")
    })
    public ResponseEntity<List<DatabaseInfo>> showDBs() {
        log.info("Received showDBs request");
        List<DatabaseInfo> result = vectorService.showDBs();
        log.info("Returning {} databases", result.size());
        return ResponseEntity.ok(result);
    }
    
}
