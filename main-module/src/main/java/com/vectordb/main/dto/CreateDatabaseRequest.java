package com.vectordb.main.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to create a new database")
public record CreateDatabaseRequest(
    @NotBlank
    @Schema(description = "Unique database identifier", example = "my-database", required = true)
    String id,
    
    @Min(1)
    @Schema(description = "Vector dimension (number of components in each vector)", example = "128", minimum = "1", required = true)
    int dimension
) {}
