package com.vectordb.main.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Vector entry containing vector data and metadata")
public class VectorEntry {
    
    @Schema(description = "Unique identifier for the vector entry", example = "12345")
    private String id;
    
    @Schema(description = "Vector coordinates as array of doubles", example = "[1.0, 2.0, 3.0]")
    private double[] vector;
    
    @Schema(description = "String data associated with the vector", example = "Hello World")
    private String data;
}
