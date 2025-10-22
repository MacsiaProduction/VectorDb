package com.vectordb.common.avro;


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SearchResultAvroTest {

    @Test
    void testLargeEmbedding() throws IOException {
        List<Float> largeEmbedding = new ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) {
            largeEmbedding.add((float) Math.sin(i * 0.01));
        }

        Instant timestamp = Instant.now();

        VectorEntry entry = createVectorEntry(999L, largeEmbedding,
                "large-db", "large data", timestamp);

        SearchResult result = SearchResult.newBuilder().setEntry(entry)
                .setDistance(0.123)
                .setSimilarity(0.987).build();
        List<SearchResult> resultlst = new ArrayList<>();
        resultlst.add(result);
        SearchResultList results = SearchResultList.newBuilder().setResults(resultlst).build();


        byte[] serialized = results.toByteBuffer().array();

        SearchResultList deserialized = SearchResultList.fromByteBuffer(ByteBuffer.wrap(serialized));

        assertEquals(1, deserialized.getResults().size());
        //assertSearchResultEquals(result, deserialized.getResults(0));
    }

    @Test
    void test100EntitiesWithSizeAnalysis() throws IOException {
        int n = 100;

        SearchResultList.Builder resultsBuilder = SearchResultList.newBuilder();
        Instant baseTime = Instant.now();
        List<SearchResult> resultlst = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            List<Float> embedding = new ArrayList<>(384);
            for (int j = 0; j < 384; j++) {
                embedding.add((float) Math.sin(i * 0.1 + j * 0.01));
            }
            baseTime.plusSeconds(i);
            VectorEntry entry = createVectorEntry(
                    (long) i,
                    embedding,
                    "database_" + i,
                    "Пример текста для векторного поиска номер " + i,
                    baseTime
            );
            SearchResult result = SearchResult.newBuilder().setEntry(entry)
                    .setDistance(i * 0.01)
                    .setSimilarity(1.0 - i / (double)n ).build();
            resultlst.add(result);
        }
        SearchResultList results = SearchResultList.newBuilder().setResults(resultlst).build();

        long startTime = System.currentTimeMillis();
        byte[] serialized = results.toByteBuffer().array();
        long endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на сериализацию: " + (endTime - startTime) + " мс");

        startTime = System.currentTimeMillis();
        SearchResultList deserialized = SearchResultList.fromByteBuffer(ByteBuffer.wrap(serialized));
        endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на десериализацию: " + (endTime - startTime) + " мс");

        assertEquals(n, deserialized.getResults().size());

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

    @Test
    void test5000EntitiesWithSizeAnalysis() throws IOException {
        int n = 5000;

        SearchResultList.Builder resultsBuilder = SearchResultList.newBuilder();
        Instant baseTime = Instant.now();
        List<SearchResult> resultlst = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            List<Float> embedding = new ArrayList<>(384);
            for (int j = 0; j < 384; j++) {
                embedding.add((float) Math.sin(i * 0.1 + j * 0.01));
            }
            baseTime.plusSeconds(i);
            VectorEntry entry = createVectorEntry(
                    (long) i,
                    embedding,
                    "database_" + i,
                    "Пример текста для векторного поиска номер " + i,
                    baseTime
            );
            SearchResult result = SearchResult.newBuilder().setEntry(entry)
                    .setDistance(i * 0.01)
                    .setSimilarity(1.0 - i / (double)n ).build();
            resultlst.add(result);
        }
        SearchResultList results = SearchResultList.newBuilder().setResults(resultlst).build();

        long startTime = System.currentTimeMillis();
        byte[] serialized = results.toByteBuffer().array();
        long endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на сериализацию: " + (endTime - startTime) + " мс");

        startTime = System.currentTimeMillis();
        SearchResultList deserialized = SearchResultList.fromByteBuffer(ByteBuffer.wrap(serialized));
        endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на десериализацию: " + (endTime - startTime) + " мс");

        assertEquals(n, deserialized.getResults().size());

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

    @Test
    void testNEntitiesWithSizeAnalysis() throws IOException {
        int n = 1000;

        SearchResultList.Builder resultsBuilder = SearchResultList.newBuilder();
        Instant baseTime = Instant.now();
        List<SearchResult> resultlst = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            List<Float> embedding = new ArrayList<>(384);
            for (int j = 0; j < 384; j++) {
                embedding.add((float) Math.sin(i * 0.1 + j * 0.01));
            }
            baseTime.plusSeconds(i);
            VectorEntry entry = createVectorEntry(
                    (long) i,
                    embedding,
                    "database_" + i,
                    "Пример текста для векторного поиска номер " + i,
                    baseTime
            );
            SearchResult result = SearchResult.newBuilder().setEntry(entry)
                    .setDistance(i * 0.01)
                    .setSimilarity(1.0 - i / (double)n ).build();
            resultlst.add(result);
        }
        SearchResultList results = SearchResultList.newBuilder().setResults(resultlst).build();

        long startTime = System.currentTimeMillis();
        byte[] serialized = results.toByteBuffer().array();
        long endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на сериализацию: " + (endTime - startTime) + " мс");

        startTime = System.currentTimeMillis();
        SearchResultList deserialized = SearchResultList.fromByteBuffer(ByteBuffer.wrap(serialized));
        endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на десериализацию: " + (endTime - startTime) + " мс");

        assertEquals(n, deserialized.getResults().size());

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

    private VectorEntry createVectorEntry(Long id, List<Float> embedding,
                                          String databaseId, String originalData,
                                          Instant createdAt) {
        VectorEntry.Builder newVectorEntry = VectorEntry.newBuilder()
                .setId(id)
                .setDatabaseId(databaseId)
                .setEmbedding(embedding )
                .setOriginalData(originalData)
                .setCreatedAt(createdAt);

        return newVectorEntry.build();
    }
}

