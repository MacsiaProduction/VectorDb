package com.vectordb.main.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for getting top K similar vectors")
public class GetTopKRequest {
    
    @Schema(description = "Query vector coordinates for similarity search", example = "[1.0, 2.0, 3.0]")
    private double[] vector;
    
    @Schema(description = "Number of top similar vectors to return", example = "10")
    private int k;
    
    @Schema(description = "Database ID to search in", example = "db1")
    private String dbId;
}
