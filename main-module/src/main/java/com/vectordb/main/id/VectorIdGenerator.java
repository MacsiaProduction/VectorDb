package com.vectordb.main.id;

/**
 * Provides generation and decoding of vector identifiers.
 */
public interface VectorIdGenerator {

    /**
     * Generate a new unique vector identifier.
     */
    VectorId nextId();
}


