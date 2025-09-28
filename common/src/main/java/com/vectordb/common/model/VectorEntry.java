package com.vectordb.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.io.Serializable;

/**
 * Represents a vector entry in the database.
 * ID is optional for creation requests - storage will generate it.
 */
@Builder
public record VectorEntry(
    @JsonProperty("id")
    String id,
    
    @NotNull
    @Size(min = 1)
    @JsonProperty("embedding")
    double[] embedding,
    
    @JsonProperty("originalData")
    String originalData,
    
    @JsonProperty("metadata")
    byte[] metadata
) implements Serializable {
    @JsonCreator
    public VectorEntry {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding cannot be null or empty");
        }
    }
    
    /**
     * Creates a new VectorEntry with generated ID for storage
     */
    public VectorEntry withId(String newId) {
        return new VectorEntry(newId, embedding, originalData, metadata);
    }
    
    /**
     * Gets the dimension of the embedding vector
     */
    public int dimension() {
        return embedding.length;
    }

}
