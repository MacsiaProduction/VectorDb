package com.vectordb.main.controller;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.main.dto.AddVectorRequest;
import com.vectordb.main.dto.DeleteVectorRequest;
import com.vectordb.main.dto.GetTopKRequest;
import com.vectordb.main.service.VectorService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/vectors")
@RequiredArgsConstructor
@Tag(name = "Vector Operations", description = "Operations for managing vectors in the vector database")
public class VectorController {
    
    private final VectorService vectorService;
    
    @PostMapping("/topK")
    @Operation(summary = "Get top K similar vectors", 
               description = "Find the K most similar vectors to the given query vector")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved top K vectors"),
        @ApiResponse(responseCode = "404", description = "Database not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error during vector search")
    })
    public ResponseEntity<List<VectorEntry>> getTopK(@RequestBody GetTopKRequest request) {
        log.info("Received getTopK request: vector length={}, k={}, dbId={}", 
                request.getVector() != null ? request.getVector().length : 0, 
                request.getK(), 
                request.getDbId());
        
        List<VectorEntry> result = vectorService.getTopK(
            request.getVector(), 
            request.getK(), 
            request.getDbId()
        );
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/add")
    @Operation(summary = "Add a vector", 
               description = "Add a new vector entry to the specified database")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vector successfully added"),
        @ApiResponse(responseCode = "404", description = "Database not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error during vector addition")
    })
    public ResponseEntity<Long> add(@RequestBody AddVectorRequest request) {
        log.info("Received add request: vector length={}, data={}, dbId={}",
                request.getVector() != null ? request.getVector().length : 0,
                request.getData(),
                request.getDbId());
        
        Long result = vectorService.add(request.getVector(), request.getData(), request.getDbId());
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/delete")
    @Operation(summary = "Delete a vector", 
               description = "Delete a vector entry from the specified database")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vector successfully deleted"),
        @ApiResponse(responseCode = "404", description = "Database not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error during vector deletion")
    })
    public ResponseEntity<Boolean> delete(@RequestBody DeleteVectorRequest request) {
        log.info("Received delete request: id={}, dbId={}", request.getId(), request.getDbId());
        
        boolean result = vectorService.delete(request.getId(), request.getDbId());
        return ResponseEntity.ok(result);
    }
}
