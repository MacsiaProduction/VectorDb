package com.vectordb.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record SearchResult(
    @NotNull
    @JsonProperty("entry")
    VectorEntry entry,
    
    @JsonProperty("distance")
    double distance,
    
    @JsonProperty("similarity")
    double similarity
) {
    @JsonCreator
    public SearchResult {
        if (distance < 0) {
            throw new IllegalArgumentException("Distance cannot be negative");
        }
        if (similarity < 0 || similarity > 1) {
            throw new IllegalArgumentException("Similarity must be between 0 and 1");
        }
    }
}
