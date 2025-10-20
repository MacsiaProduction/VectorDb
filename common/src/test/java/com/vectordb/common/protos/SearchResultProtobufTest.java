package com.vectordb.common.protos;


import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.protobuf.util.Timestamps;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SearchResultProtobufTest {
    @Test
    void testLargeEmbedding() {
        float[] largeEmbedding = new float[1536];
        for (int i = 0; i < largeEmbedding.length; i++) {
            largeEmbedding[i] = (float) Math.sin(i * 0.01);
        }

        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();

        SearchResult.VectorEntry entry = createVectorEntry(999L, largeEmbedding,
                "large-db", "large data", timestamp);

        SearchResult result = SearchResult.newBuilder().setEntry(entry)
                .setDistance(0.123)
                .setSimilarity(0.987).build();

        SearchResultList results = SearchResultList.newBuilder().addResults(result).build();

        byte[] serialized = results.toByteArray();

        SearchResultList deserialized = SearchResultList.newBuilder().build();
        try {
            deserialized = SearchResultList.parseFrom(serialized);
        }
        catch (com.google.protobuf.InvalidProtocolBufferException ebuf) {
            System.out.println("testLargeEmbedding - Не удалось десериализовать SearchResultList");
        }

        assertEquals(1, deserialized.getResultsCount());
        //assertSearchResultEquals(result, deserialized.getResults(0));
    }


    @Test
    void test100EntitiesWithSizeAnalysis() {
        SearchResultList.Builder resultsBuilder = SearchResultList.newBuilder();
        Instant baseTime = Instant.now();

        for (int i = 0; i < 100; i++) {
            float[] embedding = new float[384];
            for (int j = 0; j < embedding.length; j++) {
                embedding[j] = (float) Math.sin(i * 0.1 + j * 0.01);
            }
            baseTime.plusSeconds(i);
            SearchResult.VectorEntry entry = createVectorEntry(
                    (long) i,
                    embedding,
                    "database_" + i,
                    "Пример текста для векторного поиска номер " + i,
                    Timestamp.newBuilder()
                            .setSeconds(baseTime.getEpochSecond())
                            .setNanos(baseTime.getNano()).build()
            );
            resultsBuilder.addResults(SearchResult.newBuilder().setEntry(entry)
                    .setDistance(i * 0.01)
                    .setSimilarity(1.0 - i * 0.005).build());
        }
        SearchResultList results = resultsBuilder.build();

        long startTime = System.currentTimeMillis();
        byte[] serialized = results.toByteArray();
        long endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на сериализацию: " + (endTime - startTime) + " мс");

        startTime = System.currentTimeMillis();
        SearchResultList deserialized = SearchResultList.newBuilder().build();
        try {
            deserialized = SearchResultList.parseFrom(serialized);
        }
        catch (com.google.protobuf.InvalidProtocolBufferException ebuf) {
            System.out.println("testLargeEmbedding - Не удалось десериализовать SearchResultList");
        }
        endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на десериализацию: " + (endTime - startTime) + " мс");

        assertEquals(100, deserialized.getResultsCount());

        /*for (int i = 0; i < 100; i++) {
            assertSearchResultEquals(results.get(i), deserialized.get(i));
        }*/


        int totalSize = serialized.length;
        int avgSizePerEntry = totalSize / 100;

        System.out.println("\n=== Анализ размера сериализации для 100 сущностей ===");
        System.out.println("Общий размер: " + totalSize + " байт (" + String.format("%.2f", totalSize / 1024.0) + " КБ)");
        System.out.println("Средний размер на сущность: " + avgSizePerEntry + " байт");
        System.out.println("Размерность embedding: 384");
        //System.out.println("Размер одного embedding: " + (384 * 4) + " байт");
        //System.out.println("Накладные расходы на метаданные: " + (avgSizePerEntry - 384 * 4) + " байт на сущность"); // а так ли это будет в данном случае?
        System.out.println("===================================================\n");

        assertTrue(totalSize > 0);
        //assertTrue(avgSizePerEntry > 384 * 4);
    }

    @Test
    void test5000EntitiesWithSizeAnalysis() {
        SearchResultList.Builder resultsBuilder = SearchResultList.newBuilder();
        Instant baseTime = Instant.now();

        for (int i = 0; i < 5000; i++) {
            float[] embedding = new float[384];
            for (int j = 0; j < embedding.length; j++) {
                embedding[j] = (float) Math.sin(i * 0.1 + j * 0.01);
            }
            baseTime.plusSeconds(i);
            SearchResult.VectorEntry entry = createVectorEntry(
                    (long) i,
                    embedding,
                    "database_" + i,
                    "Пример текста для векторного поиска номер " + i,
                    Timestamp.newBuilder()
                            .setSeconds(baseTime.getEpochSecond())
                            .setNanos(baseTime.getNano()).build()
            );
            resultsBuilder.addResults(SearchResult.newBuilder().setEntry(entry)
                    .setDistance(i * 0.01)
                    .setSimilarity(1.0 - i * 0.00005).build());
        }
        SearchResultList results = resultsBuilder.build();

        long startTime = System.currentTimeMillis();
        byte[] serialized = results.toByteArray();
        long endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на сериализацию: " + (endTime - startTime) + " мс");

        startTime = System.currentTimeMillis();
        SearchResultList deserialized = SearchResultList.newBuilder().build();
        try {
            deserialized = SearchResultList.parseFrom(serialized);
        }
        catch (com.google.protobuf.InvalidProtocolBufferException ebuf) {
            System.out.println("testLargeEmbedding - Не удалось десериализовать SearchResultList");
        }
        endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на десериализацию: " + (endTime - startTime) + " мс");

        assertEquals(5000, deserialized.getResultsCount());

        /*for (int i = 0; i < 100; i++) {
            assertSearchResultEquals(results.get(i), deserialized.get(i));
        }*/


        int totalSize = serialized.length;
        int avgSizePerEntry = totalSize / 5000;

        System.out.println("\n=== Анализ размера сериализации для 5000 сущностей ===");
        System.out.println("Общий размер: " + totalSize + " байт (" + String.format("%.2f", totalSize / 1024.0) + " КБ)");
        System.out.println("Средний размер на сущность: " + avgSizePerEntry + " байт");
        System.out.println("Размерность embedding: 384");
        //System.out.println("Размер одного embedding: " + (384 * 4) + " байт");
        //System.out.println("Накладные расходы на метаданные: " + (avgSizePerEntry - 384 * 4) + " байт на сущность"); // а так ли это будет в данном случае?
        System.out.println("===================================================\n");

        assertTrue(totalSize > 0);
        //assertTrue(avgSizePerEntry > 384 * 4);
    }

    @Test
    void testNEntitiesWithSizeAnalysis() {
        int n = 2500;

        SearchResultList.Builder resultsBuilder = SearchResultList.newBuilder();
        Instant baseTime = Instant.now();

        for (int i = 0; i < n; i++) {
            float[] embedding = new float[384];
            for (int j = 0; j < embedding.length; j++) {
                embedding[j] = (float) Math.sin(i * 0.1 + j * 0.01);
            }
            baseTime.plusSeconds(i);
            SearchResult.VectorEntry entry = createVectorEntry(
                    (long) i,
                    embedding,
                    "database_" + i,
                    "Пример текста для векторного поиска номер " + i,
                    Timestamp.newBuilder()
                            .setSeconds(baseTime.getEpochSecond())
                            .setNanos(baseTime.getNano()).build()
            );
            resultsBuilder.addResults(SearchResult.newBuilder().setEntry(entry)
                    .setDistance(i * 0.01)
                    .setSimilarity(1.0 - i / (double)n ).build());
        }
        SearchResultList results = resultsBuilder.build();

        long startTime = System.currentTimeMillis();
        byte[] serialized = results.toByteArray();
        long endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на сериализацию: " + (endTime - startTime) + " мс");

        startTime = System.currentTimeMillis();
        SearchResultList deserialized = SearchResultList.newBuilder().build();
        try {
            deserialized = SearchResultList.parseFrom(serialized);
        }
        catch (com.google.protobuf.InvalidProtocolBufferException ebuf) {
            System.out.println("testLargeEmbedding - Не удалось десериализовать SearchResultList");
        }
        endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на десериализацию: " + (endTime - startTime) + " мс");

        assertEquals(n, deserialized.getResultsCount());

        /*for (int i = 0; i < 100; i++) {
            assertSearchResultEquals(results.get(i), deserialized.get(i));
        }*/


        int totalSize = serialized.length;
        int avgSizePerEntry = totalSize / n;

        System.out.println("\n=== Анализ размера сериализации для " + n + " сущностей ===");
        System.out.println("Общий размер: " + totalSize + " байт (" + String.format("%.2f", totalSize / 1024.0) + " КБ)");
        System.out.println("Средний размер на сущность: " + avgSizePerEntry + " байт");
        System.out.println("Размерность embedding: 384");
        //System.out.println("Размер одного embedding: " + (384 * 4) + " байт");
        //System.out.println("Накладные расходы на метаданные: " + (avgSizePerEntry - 384 * 4) + " байт на сущность"); // а так ли это будет в данном случае?
        System.out.println("===================================================\n");

        assertTrue(totalSize > 0);
        //assertTrue(avgSizePerEntry > 384 * 4);
    }

    private SearchResult.VectorEntry createVectorEntry(Long id, float[] embedding,
                                          String databaseId, String originalData,
                                                       Timestamp createdAt) {
        SearchResult.VectorEntry.Builder newVectorEntry = SearchResult.VectorEntry.newBuilder()
                .setId(id)
                .setDatabaseId(databaseId)
                .setOriginalData(originalData)
                .setCreatedAt(createdAt);
        for (int i = 1; i < embedding.length; i++) {
            newVectorEntry.addEmbedding(embedding[i]);
        }

        return newVectorEntry.build();
    }
}
