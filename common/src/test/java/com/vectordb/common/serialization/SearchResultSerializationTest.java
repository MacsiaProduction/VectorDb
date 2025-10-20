package com.vectordb.common.serialization;

import com.vectordb.common.model.SearchResult;
import com.vectordb.common.model.VectorEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchResultSerializationTest {

    private SearchResultSerializer serializer;
    private SearchResultDeserializer deserializer;

    @BeforeEach
    void setUp() {
        serializer = new SearchResultSerializer();
        deserializer = new SearchResultDeserializer();
    }

    @Test
    void testEmptyListSerialization() {
        List<SearchResult> empty = new ArrayList<>();
        
        byte[] serialized = serializer.serialize(empty);
        List<SearchResult> deserialized = deserializer.deserialize(serialized);

        assertNotNull(deserialized);
        assertEquals(0, deserialized.size());
    }

    @Test
    void testSingleSearchResultRoundTrip() {
        VectorEntry entry = createVectorEntry(1L, new float[]{0.1f, 0.2f, 0.3f}, 
                "database1", "original data", Instant.now());
        SearchResult result = new SearchResult(entry, 0.5, 0.8);
        List<SearchResult> results = List.of(result);

        byte[] serialized = serializer.serialize(results);
        List<SearchResult> deserialized = deserializer.deserialize(serialized);

        assertEquals(1, deserialized.size());
        assertSearchResultEquals(result, deserialized.get(0));
    }

    @Test
    void testMultipleSearchResults() {
        List<SearchResult> results = new ArrayList<>();
        Instant now = Instant.now();

        for (int i = 0; i < 10; i++) {
            VectorEntry entry = createVectorEntry(
                    (long) i,
                    new float[]{i * 0.1f, i * 0.2f, i * 0.3f},
                    "database" + i,
                    "data" + i,
                    now.plusSeconds(i)
            );
            results.add(new SearchResult(entry, i * 0.1, 1.0 - i * 0.05));
        }

        byte[] serialized = serializer.serialize(results);
        List<SearchResult> deserialized = deserializer.deserialize(serialized);

        assertEquals(10, deserialized.size());
        for (int i = 0; i < 10; i++) {
            assertSearchResultEquals(results.get(i), deserialized.get(i));
        }
    }

    @Test
    void testLargeEmbedding() {
        float[] largeEmbedding = new float[1536];
        for (int i = 0; i < largeEmbedding.length; i++) {
            largeEmbedding[i] = (float) Math.sin(i * 0.01);
        }

        VectorEntry entry = createVectorEntry(999L, largeEmbedding, 
                "large-db", "large data", Instant.now());
        SearchResult result = new SearchResult(entry, 0.123, 0.987);
        List<SearchResult> results = List.of(result);

        byte[] serialized = serializer.serialize(results);
        List<SearchResult> deserialized = deserializer.deserialize(serialized);

        assertEquals(1, deserialized.size());
        assertSearchResultEquals(result, deserialized.get(0));
    }

    @Test
    void testEdgeCaseZeroDistanceAndSimilarity() {
        VectorEntry entry = createVectorEntry(1L, new float[]{1.0f, 2.0f}, 
                "db", "data", Instant.now());
        SearchResult result = new SearchResult(entry, 0.0, 0.0);
        List<SearchResult> results = List.of(result);

        byte[] serialized = serializer.serialize(results);
        List<SearchResult> deserialized = deserializer.deserialize(serialized);

        assertEquals(1, deserialized.size());
        assertEquals(0.0, deserialized.get(0).distance(), 0.0001);
        assertEquals(0.0, deserialized.get(0).similarity(), 0.0001);
    }

    @Test
    void testEdgeCaseMaxSimilarity() {
        VectorEntry entry = createVectorEntry(1L, new float[]{1.0f}, 
                "db", "data", Instant.now());
        SearchResult result = new SearchResult(entry, 0.0, 1.0);
        List<SearchResult> results = List.of(result);

        byte[] serialized = serializer.serialize(results);
        List<SearchResult> deserialized = deserializer.deserialize(serialized);

        assertEquals(1, deserialized.size());
        assertEquals(1.0, deserialized.get(0).similarity(), 0.0001);
    }

    @Test
    void testLongStrings() {
        String longDatabaseId = "db".repeat(1000);
        String longOriginalData = "data".repeat(5000);

        VectorEntry entry = createVectorEntry(1L, new float[]{0.5f}, 
                longDatabaseId, longOriginalData, Instant.now());
        SearchResult result = new SearchResult(entry, 0.3, 0.7);
        List<SearchResult> results = List.of(result);

        byte[] serialized = serializer.serialize(results);
        List<SearchResult> deserialized = deserializer.deserialize(serialized);

        assertEquals(1, deserialized.size());
        assertEquals(longDatabaseId, deserialized.get(0).entry().databaseId());
        assertEquals(longOriginalData, deserialized.get(0).entry().originalData());
    }

    @Test
    void testVarintEncodingSmallNumbers() {
        ByteBuffer buffer = ByteBuffer.allocate(100);

        serializer.writeVarint(buffer, 0L);
        serializer.writeVarint(buffer, 1L);
        serializer.writeVarint(buffer, 127L);
        serializer.writeVarint(buffer, 128L);
        serializer.writeVarint(buffer, 255L);

        buffer.flip();

        assertEquals(0L, deserializer.readVarint(buffer));
        assertEquals(1L, deserializer.readVarint(buffer));
        assertEquals(127L, deserializer.readVarint(buffer));
        assertEquals(128L, deserializer.readVarint(buffer));
        assertEquals(255L, deserializer.readVarint(buffer));
    }

    @Test
    void testVarintEncodingLargeNumbers() {
        ByteBuffer buffer = ByteBuffer.allocate(100);

        serializer.writeVarint(buffer, 1000000L);
        serializer.writeVarint(buffer, Long.MAX_VALUE);

        buffer.flip();

        assertEquals(1000000L, deserializer.readVarint(buffer));
        assertEquals(Long.MAX_VALUE, deserializer.readVarint(buffer));
    }

    @Test
    void testInstantEncodingAccuracy() {
        Instant[] instants = {
                Instant.ofEpochMilli(0),
                Instant.ofEpochMilli(1000),
                Instant.now(),
                Instant.ofEpochMilli(System.currentTimeMillis() + 1000000)
        };

        for (Instant instant : instants) {
            VectorEntry entry = createVectorEntry(1L, new float[]{1.0f}, 
                    "db", "data", instant);
            SearchResult result = new SearchResult(entry, 0.5, 0.5);
            List<SearchResult> results = List.of(result);

            byte[] serialized = serializer.serialize(results);
            List<SearchResult> deserialized = deserializer.deserialize(serialized);

            assertEquals(instant.toEpochMilli(), 
                    deserialized.get(0).entry().createdAt().toEpochMilli());
        }
    }

    @Test
    void testDataIntegrity() {
        List<SearchResult> results = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            float[] embedding = new float[128];
            for (int j = 0; j < embedding.length; j++) {
                embedding[j] = (float) (i + j * 0.01);
            }

            VectorEntry entry = createVectorEntry(
                    (long) i,
                    embedding,
                    "database_" + i,
                    "original_data_" + i,
                    Instant.ofEpochMilli(1000000000L + i * 1000)
            );

            results.add(new SearchResult(entry, i * 0.01, 1.0 - i * 0.005));
        }

        byte[] serialized = serializer.serialize(results);
        List<SearchResult> deserialized = deserializer.deserialize(serialized);

        assertEquals(100, deserialized.size());

        for (int i = 0; i < 100; i++) {
            assertSearchResultEquals(results.get(i), deserialized.get(i));
        }
    }

    @Test
    void testSerializationWithUnicodeCharacters() {
        VectorEntry entry = createVectorEntry(
                1L,
                new float[]{1.0f, 2.0f},
                "база_данных_🚀",
                "Привет мир! 你好世界! 🎉",
                Instant.now()
        );

        SearchResult result = new SearchResult(entry, 0.5, 0.9);
        List<SearchResult> results = List.of(result);

        byte[] serialized = serializer.serialize(results);
        List<SearchResult> deserialized = deserializer.deserialize(serialized);

        assertEquals(1, deserialized.size());
        assertEquals("база_данных_🚀", deserialized.get(0).entry().databaseId());
        assertEquals("Привет мир! 你好世界! 🎉", deserialized.get(0).entry().originalData());
    }

    @Test
    void testNullInputSerialization() {
        assertThrows(IllegalArgumentException.class, () -> {
            serializer.serialize(null);
        });
    }

    @Test
    void testNullInputDeserialization() {
        assertThrows(IllegalArgumentException.class, () -> {
            deserializer.deserialize(null);
        });
    }

    @Test
    void testEmptyBuffer() {
        assertThrows(Exception.class, () -> {
            deserializer.deserialize(new byte[0]);
        });
    }

    @Test
    void testCorruptedData() {
        byte[] corrupted = new byte[]{1, 2, 3, 4, 5};
        assertThrows(Exception.class, () -> {
            deserializer.deserialize(corrupted);
        });
    }

    @Test
    void testSerializedSizeCalculation() {
        List<SearchResult> results = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            VectorEntry entry = createVectorEntry(
                    (long) i,
                    new float[]{i * 0.1f, i * 0.2f},
                    "db" + i,
                    "data" + i,
                    Instant.now()
            );
            results.add(new SearchResult(entry, i * 0.1, 0.9 - i * 0.1));
        }

        int calculatedSize = serializer.calculateSize(results);
        byte[] serialized = serializer.serialize(results);

        assertEquals(calculatedSize, serialized.length);
    }

    @Test
    void testVarintEfficiency() {
        ByteBuffer smallBuffer = ByteBuffer.allocate(10);
        serializer.writeVarint(smallBuffer, 100L);
        int smallSize = smallBuffer.position();

        ByteBuffer largeBuffer = ByteBuffer.allocate(10);
        serializer.writeVarint(largeBuffer, 1000000000L);
        int largeSize = largeBuffer.position();

        assertTrue(smallSize < 8);
        assertTrue(largeSize <= 10);
        assertTrue(smallSize < largeSize);
    }

    @Test
    void test100EntitiesWithSizeAnalysis() {
        List<SearchResult> results = new ArrayList<>();
        Instant baseTime = Instant.now();

        for (int i = 0; i < 100; i++) {
            float[] embedding = new float[384];
            for (int j = 0; j < embedding.length; j++) {
                embedding[j] = (float) Math.sin(i * 0.1 + j * 0.01);
            }

            VectorEntry entry = createVectorEntry(
                    (long) i,
                    embedding,
                    "database_" + i,
                    "Пример текста для векторного поиска номер " + i,
                    baseTime.plusSeconds(i)
            );

            results.add(new SearchResult(entry, i * 0.01, 1.0 - i * 0.005));
        }

        long startTime = System.currentTimeMillis();
        byte[] serialized = serializer.serialize(results);
        long endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на сериализацию: " + (endTime - startTime) + " мс");

        startTime = System.currentTimeMillis();
        List<SearchResult> deserialized = deserializer.deserialize(serialized);
        endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на десериализацию: " + (endTime - startTime) + " мс");

        assertEquals(100, deserialized.size());
        
        for (int i = 0; i < 100; i++) {
            assertSearchResultEquals(results.get(i), deserialized.get(i));
        }

        int totalSize = serialized.length;
        int avgSizePerEntry = totalSize / 100;

        System.out.println("\n=== Анализ размера сериализации для 100 сущностей ===");
        System.out.println("Общий размер: " + totalSize + " байт (" + String.format("%.2f", totalSize / 1024.0) + " КБ)");
        System.out.println("Средний размер на сущность: " + avgSizePerEntry + " байт");
        System.out.println("Размерность embedding: 384");
        System.out.println("Размер одного embedding: " + (384 * 4) + " байт");
        System.out.println("Накладные расходы на метаданные: " + (avgSizePerEntry - 384 * 4) + " байт на сущность");
        System.out.println("===================================================\n");

        assertTrue(totalSize > 0);
        assertTrue(avgSizePerEntry > 384 * 4);
    }

    @Test
    void test5000EntitiesWithSizeAnalysis() {
        List<SearchResult> results = new ArrayList<>();
        Instant baseTime = Instant.now();

        for (int i = 0; i < 5000; i++) {
            float[] embedding = new float[384];
            for (int j = 0; j < embedding.length; j++) {
                embedding[j] = (float) Math.sin(i * 0.1 + j * 0.01);
            }

            VectorEntry entry = createVectorEntry(
                    (long) i,
                    embedding,
                    "database_" + i,
                    "Пример текста для векторного поиска номер " + i,
                    baseTime.plusSeconds(i)
            );

            results.add(new SearchResult(entry, i * 0.01, 1.0 - i * 0.00005));
        }

        long startTime = System.currentTimeMillis();
        byte[] serialized = serializer.serialize(results);
        long endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на сериализацию: " + (endTime - startTime) + " мс");

        startTime = System.currentTimeMillis();
        List<SearchResult> deserialized = deserializer.deserialize(serialized);
        endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на десериализацию: " + (endTime - startTime) + " мс");

        assertEquals(5000, deserialized.size());

        for (int i = 0; i < 5000; i++) {
            assertSearchResultEquals(results.get(i), deserialized.get(i));
        }

        int totalSize = serialized.length;
        int avgSizePerEntry = totalSize / 5000;

        System.out.println("\n=== Анализ размера сериализации для 5000 сущностей ===");
        System.out.println("Общий размер: " + totalSize + " байт (" + String.format("%.2f", totalSize / 1024.0) + " КБ)");
        System.out.println("Средний размер на сущность: " + avgSizePerEntry + " байт");
        System.out.println("Размерность embedding: 384");
        System.out.println("Размер одного embedding: " + (384 * 4) + " байт");
        System.out.println("Накладные расходы на метаданные: " + (avgSizePerEntry - 384 * 4) + " байт на сущность");
        System.out.println("===================================================\n");

        assertTrue(totalSize > 0);
        assertTrue(avgSizePerEntry > 384 * 4);
    }

    @Test
    void testNEntitiesWithSizeAnalysis() {
        int n = 1000;
        List<SearchResult> results = new ArrayList<>();
        Instant baseTime = Instant.now();

        for (int i = 0; i < n; i++) {
            float[] embedding = new float[384];
            for (int j = 0; j < embedding.length; j++) {
                embedding[j] = (float) Math.sin(i * 0.1 + j * 0.01);
            }

            VectorEntry entry = createVectorEntry(
                    (long) i,
                    embedding,
                    "database_" + i,
                    "Пример текста для векторного поиска номер " + i,
                    baseTime.plusSeconds(i)
            );

            results.add(new SearchResult(entry, i * 0.01, 1.0 - i / (double)n));
        }

        long startTime = System.currentTimeMillis();
        byte[] serialized = serializer.serialize(results);
        long endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на сериализацию: " + (endTime - startTime) + " мс");

        startTime = System.currentTimeMillis();
        List<SearchResult> deserialized = deserializer.deserialize(serialized);
        endTime = System.currentTimeMillis();
        System.out.println("Время потраченное на десериализацию: " + (endTime - startTime) + " мс");

        assertEquals(n, deserialized.size());

        for (int i = 0; i < n; i++) {
            assertSearchResultEquals(results.get(i), deserialized.get(i));
        }

        int totalSize = serialized.length;
        int avgSizePerEntry = totalSize / n;

        System.out.println("\n=== Анализ размера сериализации для " + n + " сущностей ===");
        System.out.println("Общий размер: " + totalSize + " байт (" + String.format("%.2f", totalSize / 1024.0) + " КБ)");
        System.out.println("Средний размер на сущность: " + avgSizePerEntry + " байт");
        System.out.println("Размерность embedding: 384");
        System.out.println("Размер одного embedding: " + (384 * 4) + " байт");
        System.out.println("Накладные расходы на метаданные: " + (avgSizePerEntry - 384 * 4) + " байт на сущность");
        System.out.println("===================================================\n");

        assertTrue(totalSize > 0);
        assertTrue(avgSizePerEntry > 384 * 4);
    }

    private VectorEntry createVectorEntry(Long id, float[] embedding, 
                                          String databaseId, String originalData, 
                                          Instant createdAt) {
        return VectorEntry.builder()
                .id(id)
                .embedding(embedding)
                .databaseId(databaseId)
                .originalData(originalData)
                .createdAt(createdAt)
                .build();
    }

    private void assertSearchResultEquals(SearchResult expected, SearchResult actual) {
        assertEquals(expected.distance(), actual.distance(), 0.0001);
        assertEquals(expected.similarity(), actual.similarity(), 0.0001);

        VectorEntry expectedEntry = expected.entry();
        VectorEntry actualEntry = actual.entry();

        assertEquals(expectedEntry.id(), actualEntry.id());
        assertEquals(expectedEntry.databaseId(), actualEntry.databaseId());
        assertEquals(expectedEntry.originalData(), actualEntry.originalData());
        assertEquals(expectedEntry.createdAt().toEpochMilli(), 
                actualEntry.createdAt().toEpochMilli());

        assertArrayEquals(expectedEntry.embedding(), actualEntry.embedding(), 0.0001f);
    }
}

