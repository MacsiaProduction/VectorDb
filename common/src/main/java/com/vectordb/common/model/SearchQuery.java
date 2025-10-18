package com.vectordb.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record SearchQuery(
    @NotNull
    @Size(min = 1)
    @JsonProperty("embedding")
    float[] embedding,
    
    @Min(1)
    @JsonProperty("k")
    int k,
    
    @JsonProperty("databaseId")
    String databaseId,
    
    @JsonProperty("threshold")
    Double threshold
) {
    @JsonCreator
    public SearchQuery {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding cannot be null or empty");
        }
        if (threshold != null && (threshold < 0 || threshold > 1)) {
            throw new IllegalArgumentException("Threshold must be between 0 and 1");
        }
    }

    /**
     * Creates a simple search query
     */
    public static SearchQuery simple(float[] embedding, int k, String databaseId) {
        return new SearchQuery(embedding, k, databaseId, null);
    }
    
    /**
     * Creates a search query with similarity threshold
     */
    public static SearchQuery withThreshold(float[] embedding, int k, String databaseId, double threshold) {
        return new SearchQuery(embedding, k, databaseId, threshold);
    }
}
