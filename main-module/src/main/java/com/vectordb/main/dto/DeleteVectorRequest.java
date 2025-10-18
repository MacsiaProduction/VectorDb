package com.vectordb.main.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for deleting a vector from database")
public class DeleteVectorRequest {
    
    @Schema(description = "ID of vector to delete", example = "12345")
    private Long id;
    
    @Schema(description = "Database ID to delete vector from", example = "db1")
    private String dbId;
}
