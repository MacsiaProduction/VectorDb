package com.vectordb.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record SearchResult(
    @NotNull
    @JsonProperty("entry")
    VectorEntry entry,
    
    @NotNull
    @JsonProperty("distance")
    Double distance,
    
    @NotNull
    @JsonProperty("similarity")
    Double similarity
) {
    @JsonCreator
    public SearchResult {
        if (distance == null) {
            throw new IllegalArgumentException("Distance cannot be null");
        }
        if (similarity == null) {
            throw new IllegalArgumentException("Similarity cannot be null");
        }
        if (distance < 0) {
            throw new IllegalArgumentException("Distance cannot be negative");
        }
        if (similarity < 0 || similarity > 1) {
            throw new IllegalArgumentException("Similarity must be between 0 and 1");
        }
    }
}
