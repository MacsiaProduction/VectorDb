package com.vectordb.main.cluster.hash;

/**
 * Provides hashing for vector identifiers to place them on the consistent hash ring.
 */
public interface HashService {

    /**
     * Computes a deterministic 64-bit hash for the provided vector identifier.
     */
    long hash(long vectorId);
}

