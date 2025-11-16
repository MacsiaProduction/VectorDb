package com.vectordb.main.id;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Random-based vector ID generator backed by ThreadLocalRandom.
 */
@Component
public class RandomVectorIdGenerator implements VectorIdGenerator {

    private static final long MIN_VALUE = 1L << 32;

    @Override
    public VectorId nextId() {
        return new VectorId(ThreadLocalRandom.current().nextLong(MIN_VALUE, Long.MAX_VALUE));
    }
}
