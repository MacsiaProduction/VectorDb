package com.vectordb.common.serialization;

import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.VectorEntry;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Бинарный сериализатор списка SearchResult.
 * Формат:
 * [размер массива (varint)]
 * Для каждого SearchResult:
 *   [distance (8 байт double)] [similarity (8 байт double)]
 *   [id (varint)] [createdAt (8 байт long millis)]
 *   [размерность embedding (varint)] [floats (размерность * 4 байт)]
 *   [длина databaseId (varint)] [databaseId UTF-8]
 *   [длина originalData (varint)] [originalData UTF-8]
 */
public class SearchResultSerializer {

    public byte[] serialize(List<SearchResult> results) {
        if (results == null) {
            throw new IllegalArgumentException("Results list cannot be null");
        }

        int totalSize = calculateSize(results);
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);

        writeVarint(buffer, results.size());

        for (SearchResult result : results) {
            serializeSearchResult(buffer, result);
        }

        return buffer.array();
    }

    private void serializeSearchResult(ByteBuffer buffer, SearchResult result) {
        buffer.putDouble(result.distance());
        buffer.putDouble(result.similarity());

        VectorEntry entry = result.entry();
        
        writeVarint(buffer, entry.id());
        buffer.putLong(entry.createdAt().toEpochMilli());

        float[] embedding = entry.embedding();
        writeVarint(buffer, embedding.length);
        for (float value : embedding) {
            buffer.putFloat(value);
        }

        writeString(buffer, entry.databaseId());
        writeString(buffer, entry.originalData());
    }

    private void writeString(ByteBuffer buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarint(buffer, bytes.length);
        buffer.put(bytes);
    }

    void writeVarint(ByteBuffer buffer, long value) {
        while ((value & ~0x7FL) != 0) {
            buffer.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        buffer.put((byte) (value & 0x7F));
    }

    int calculateSize(List<SearchResult> results) {
        int size = varintSize(results.size());

        for (SearchResult result : results) {
            size += 16; // distance + similarity (2 doubles)

            VectorEntry entry = result.entry();
            
            size += varintSize(entry.id());
            size += 8; // createdAt (long)

            int dimension = entry.embedding().length;
            size += varintSize(dimension);
            size += dimension * 4; // floats

            byte[] databaseIdBytes = entry.databaseId().getBytes(StandardCharsets.UTF_8);
            size += varintSize(databaseIdBytes.length);
            size += databaseIdBytes.length;

            byte[] originalDataBytes = entry.originalData().getBytes(StandardCharsets.UTF_8);
            size += varintSize(originalDataBytes.length);
            size += originalDataBytes.length;
        }

        return size;
    }

    private int varintSize(long value) {
        int size = 0;
        while ((value & ~0x7FL) != 0) {
            size++;
            value >>>= 7;
        }
        return size + 1;
    }
}

