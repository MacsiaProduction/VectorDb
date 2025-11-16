package com.vectordb.main.cluster.hash;

import org.springframework.stereotype.Component;

/**
 * Simple 64-bit hash implementation inspired by SplitMix64.
 */
@Component
public class DefaultHashService implements HashService {

    @Override
    public long hash(long vectorId) {
        long z = vectorId + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return z & Long.MAX_VALUE;
    }
}

