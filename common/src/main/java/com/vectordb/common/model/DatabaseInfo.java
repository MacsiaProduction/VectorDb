package com.vectordb.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record DatabaseInfo(
    @NotBlank
    @JsonProperty("id")
    String id,
    
    @NotBlank
    @JsonProperty("name")
    String name,
    
    @Min(0)
    @JsonProperty("vectorCount")
    long vectorCount,
    
    @JsonProperty("createdAt")
    LocalDateTime createdAt,
    
    @JsonProperty("updatedAt")
    LocalDateTime updatedAt
) {
    @JsonCreator
    public DatabaseInfo {
        if (vectorCount < 0) {
            throw new IllegalArgumentException("Vector count cannot be negative");
        }
    }
    
    /**
     * Creates a new database info with updated vector count
     */
    public DatabaseInfo withVectorCount(long newCount) {
        return new DatabaseInfo(id, name, newCount, createdAt, LocalDateTime.now());
    }
    
    /**
     * Creates database info for new database
     */
    public static DatabaseInfo forNewDatabase(String id, String name) {
        LocalDateTime now = LocalDateTime.now();
        return new DatabaseInfo(id, name, 0, now, now);
    }
}
