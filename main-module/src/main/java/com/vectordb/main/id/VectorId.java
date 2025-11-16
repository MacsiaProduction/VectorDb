package com.vectordb.main.id;

/**
 * Represents a generated vector identifier with numeric and textual (string) forms.
 */
public record VectorId(long numericId) {

    public VectorId {
        if (numericId <= 0) {
            throw new IllegalArgumentException("numericId must be positive");
        }
    }
}

