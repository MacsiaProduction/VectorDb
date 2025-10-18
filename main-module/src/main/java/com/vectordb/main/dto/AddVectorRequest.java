package com.vectordb.main.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for adding a vector to database")
public class AddVectorRequest {
    
    @Schema(description = "Vector coordinates as array of floats", example = "[1.0, 2.0, 3.0]")
    private float[] vector;
    
    @Schema(description = "String data associated with the vector", example = "Hello World")
    private String data;
    
    @Schema(description = "Database ID to add vector to", example = "db1")
    private String dbId;
}
