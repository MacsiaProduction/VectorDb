package com.vectordb.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jelmerk.hnswlib.core.Item;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a vector entry in the database.
 * ID is optional for creation requests - storage will generate it.
 */
@Builder
public record VectorEntry(
    @NotNull
    @JsonProperty("id")
    Long id,
    
    @NotNull
    @Size(min = 1)
    @JsonProperty("embedding")
    float[] embedding,
    
    @NotNull
    @JsonProperty("originalData")
    String originalData,
    
    @NotNull
    @JsonProperty("databaseId")
    String databaseId,
    
    @NotNull
    @JsonProperty("createdAt")
    Instant createdAt
) implements Serializable, Item<Long, float[]> {
    @JsonCreator
    public VectorEntry {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding cannot be null or empty");
        }
        if (originalData == null) {
            throw new IllegalArgumentException("Original data cannot be null");
        }
        if (databaseId == null) {
            throw new IllegalArgumentException("Database ID cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created at cannot be null");
        }
    }
    
    /**
     * Creates a new VectorEntry with generated ID for storage
     */
    public VectorEntry withId(Long newId) {
        return new VectorEntry(newId, embedding, originalData, databaseId, createdAt);
    }
    
    /**
     * Gets the dimension of the embedding vector
     */
    public int dimension() {
        return embedding.length;
    }
    
    /**
     * Returns the embedding vector for HNSW compatibility
     */
    @JsonIgnore
    public float[] vector() {
        return embedding;
    }
    
    /**
     * Returns dimensions for HNSW compatibility (same as dimension())
     */
    @JsonIgnore
    public int dimensions() {
        return embedding.length;
    }

}
