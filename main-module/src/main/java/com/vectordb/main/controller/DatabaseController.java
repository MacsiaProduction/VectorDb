package com.vectordb.main.controller;

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

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/databases")
@RequiredArgsConstructor
@Tag(name = "Database Operations", description = "Operations for managing databases in the vector database")
public class DatabaseController {
    
    private final VectorService vectorService;
    
    @PostMapping("/{dbId}")
    @Operation(summary = "Create a database", 
               description = "Create a new database with the specified ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Database successfully created"),
        @ApiResponse(responseCode = "400", description = "Invalid database ID"),
        @ApiResponse(responseCode = "409", description = "Database already exists")
    })
    public ResponseEntity<Boolean> createDb(
            @Parameter(description = "Database ID to create", required = true)
            @PathVariable String dbId) {
        try {
            log.info("Received createDb request: dbId={}", dbId);
            boolean result = vectorService.createDb(dbId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in createDb: ", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @DeleteMapping("/{dbId}")
    @Operation(summary = "Drop a database", 
               description = "Delete the database with the specified ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Database successfully dropped"),
        @ApiResponse(responseCode = "400", description = "Invalid database ID"),
        @ApiResponse(responseCode = "404", description = "Database not found")
    })
    public ResponseEntity<Boolean> dropDb(
            @Parameter(description = "Database ID to drop", required = true)
            @PathVariable String dbId) {
        try {
            log.info("Received dropDb request: dbId={}", dbId);
            boolean result = vectorService.dropDb(dbId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in dropDb: ", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping
    @Operation(summary = "List all databases", 
               description = "Get a list of all available database IDs")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved database list")
    })
    public ResponseEntity<List<String>> showDBs() {
        try {
            log.info("Received showDBs request");
            List<String> result = vectorService.showDBs();
            log.info("Returning {} databases", result.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in showDBs: ", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
