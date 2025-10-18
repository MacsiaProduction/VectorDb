package com.vectordb.common.serialization;

import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.VectorEntry;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SearchResultDeserializer {

    public List<SearchResult> deserialize(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        int size = (int) readVarint(buffer);
        List<SearchResult> results = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            results.add(deserializeSearchResult(buffer));
        }

        return results;
    }

    private SearchResult deserializeSearchResult(ByteBuffer buffer) {
        double distance = buffer.getDouble();
        double similarity = buffer.getDouble();

        long id = readVarint(buffer);
        Instant createdAt = Instant.ofEpochMilli(buffer.getLong());

        int dimension = (int) readVarint(buffer);
        float[] embedding = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            embedding[i] = buffer.getFloat();
        }

        String databaseId = readString(buffer);
        String originalData = readString(buffer);

        VectorEntry entry = VectorEntry.builder()
                .id(id)
                .embedding(embedding)
                .databaseId(databaseId)
                .originalData(originalData)
                .createdAt(createdAt)
                .build();

        return new SearchResult(entry, distance, similarity);
    }

    private String readString(ByteBuffer buffer) {
        int length = (int) readVarint(buffer);
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    long readVarint(ByteBuffer buffer) {
        long result = 0;
        int shift = 0;
        byte b;

        do {
            if (shift >= 64) {
                throw new IllegalStateException("Varint too long");
            }
            b = buffer.get();
            result |= (long) (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);

        return result;
    }
}

