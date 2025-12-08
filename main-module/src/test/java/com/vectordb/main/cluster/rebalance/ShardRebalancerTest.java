package com.vectordb.main.cluster.rebalance;

import com.vectordb.common.model.VectorEntry;
import com.vectordb.main.client.ShardedStorageClient;
import com.vectordb.main.client.StorageClient;
import com.vectordb.main.cluster.hash.HashService;
import com.vectordb.main.cluster.model.ShardInfo;
import com.vectordb.main.cluster.model.ShardStatus;
import com.vectordb.main.cluster.ownership.ShardOwnership;
import com.vectordb.main.cluster.ownership.ShardReplicationService;
import com.vectordb.main.cluster.repository.ClusterConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для ShardRebalancer
 */
@ExtendWith(MockitoExtension.class)
class ShardRebalancerTest {

    @Mock
    private ShardedStorageClient shardedStorageClient;

    @Mock
    private HashService hashService;

    @Mock
    private ShardReplicationService shardReplicationService;

    @Mock
    private ClusterConfigRepository clusterConfigRepository;

    @Mock
    private ShardOwnership shardOwnership;

    @Mock
    private StorageClient sourceReplicaClient;

    @Mock
    private StorageClient targetReplicaClient;

    @Mock
    private StorageClient sourceClient;

    @Mock
    private StorageClient targetClient;

    private ShardRebalancer shardRebalancer;

    private ShardInfo sourceShard;
    private ShardInfo targetShard;
    private ShardInfo sourceReplicaShard;
    private ShardInfo targetReplicaShard;

    @BeforeEach
    void setUp() {
        shardRebalancer = new ShardRebalancer(
                shardedStorageClient,
                hashService,
                shardReplicationService,
                clusterConfigRepository
        );

        // Создаем тестовые shard'ы
        sourceShard = new ShardInfo("shard1", URI.create("http://localhost:8080"), 1000L, ShardStatus.ACTIVE);
        targetShard = new ShardInfo("shard2", URI.create("http://localhost:8081"), 2000L, ShardStatus.ACTIVE);
        sourceReplicaShard = new ShardInfo("shard2", URI.create("http://localhost:8081"), 2000L, ShardStatus.ACTIVE);
        targetReplicaShard = new ShardInfo("shard3", URI.create("http://localhost:8082"), 3000L, ShardStatus.ACTIVE);
    }

    @Test
    @DisplayName("Должен корректно перебалансировать реплики при перемещении primary данных")
    void shouldRebalanceReplicasWhenPrimaryDataIsMoved() throws Exception {
        String databaseId = "test-db";
        List<VectorEntry> movedVectors = List.of(
                createTestVectorEntry(1L, databaseId),
                createTestVectorEntry(2L, databaseId),
                createTestVectorEntry(3L, databaseId)
        );

        // Настройка репликационных локаций
        when(shardReplicationService.getShardOwnership()).thenReturn(shardOwnership);
        when(shardOwnership.getReplicaLocation("shard1")).thenReturn("shard2"); // Реплики shard1 хранятся на shard2
        when(shardOwnership.getReplicaLocation("shard2")).thenReturn("shard3"); // Реплики shard2 хранятся на shard3

        // Настройка поиска ShardInfo по ID
        when(clusterConfigRepository.getShards()).thenReturn(List.of(sourceReplicaShard, targetReplicaShard));
        when(shardedStorageClient.getClient(sourceReplicaShard)).thenReturn(sourceReplicaClient);
        when(shardedStorageClient.getClient(targetReplicaShard)).thenReturn(targetReplicaClient);

        // Настройка получения реплик из старой локации
        VectorEntry replica1 = createTestVectorEntry(1L, databaseId);
        VectorEntry replica2 = createTestVectorEntry(2L, databaseId);
        VectorEntry replica3 = createTestVectorEntry(3L, databaseId);

        when(sourceReplicaClient.getVectorReplica(1L, databaseId, "shard1"))
                .thenReturn(Mono.just(replica1));
        when(sourceReplicaClient.getVectorReplica(2L, databaseId, "shard1"))
                .thenReturn(Mono.just(replica2));
        when(sourceReplicaClient.getVectorReplica(3L, databaseId, "shard1"))
                .thenReturn(Mono.just(replica3));

        // Настройка успешного добавления реплик в новую локацию
        when(targetReplicaClient.addVectorReplica(any(VectorEntry.class), eq(databaseId), eq("shard2")))
                .thenReturn(Mono.just(1L));

        // Настройка успешного удаления реплик из старой локации
        when(sourceReplicaClient.deleteVectorReplica(anyLong(), eq(databaseId), eq("shard1")))
                .thenReturn(Mono.just(true));

        // Используем reflection для вызова приватного метода
        java.lang.reflect.Method method = ShardRebalancer.class.getDeclaredMethod(
                "handleReplicaRebalancing", String.class, List.class, ShardInfo.class, ShardInfo.class);
        method.setAccessible(true);
        method.invoke(shardRebalancer, databaseId, movedVectors, sourceShard, targetShard);

        // Проверяем, что реплики были получены из старой локации
        verify(sourceReplicaClient).getVectorReplica(1L, databaseId, "shard1");
        verify(sourceReplicaClient).getVectorReplica(2L, databaseId, "shard1");
        verify(sourceReplicaClient).getVectorReplica(3L, databaseId, "shard1");

        // Проверяем, что реплики были добавлены в новую локацию
        ArgumentCaptor<VectorEntry> replicaCaptor = ArgumentCaptor.forClass(VectorEntry.class);
        verify(targetReplicaClient, times(3)).addVectorReplica(replicaCaptor.capture(), eq(databaseId), eq("shard2"));

        List<VectorEntry> addedReplicas = replicaCaptor.getAllValues();
        assertThat(addedReplicas).hasSize(3);
        assertThat(addedReplicas.stream().map(VectorEntry::id)).containsExactlyInAnyOrder(1L, 2L, 3L);

        // Проверяем, что реплики были удалены из старой локации
        verify(sourceReplicaClient).deleteVectorReplica(1L, databaseId, "shard1");
        verify(sourceReplicaClient).deleteVectorReplica(2L, databaseId, "shard1");
        verify(sourceReplicaClient).deleteVectorReplica(3L, databaseId, "shard1");
    }

    @Test
    @DisplayName("Должен пропустить перебалансировку реплик, если локации совпадают")
    void shouldSkipReplicaRebalancingWhenLocationsAreSame() throws Exception {
        String databaseId = "test-db";
        List<VectorEntry> movedVectors = List.of(createTestVectorEntry(1L, databaseId));

        // Настройка одинаковых репликационных локаций
        when(shardReplicationService.getShardOwnership()).thenReturn(shardOwnership);
        when(shardOwnership.getReplicaLocation("shard1")).thenReturn("shard2");
        when(shardOwnership.getReplicaLocation("shard2")).thenReturn("shard2"); // Одинаковые локации

        // Используем reflection для вызова приватного метода
        java.lang.reflect.Method method = ShardRebalancer.class.getDeclaredMethod(
                "handleReplicaRebalancing", String.class, List.class, ShardInfo.class, ShardInfo.class);
        method.setAccessible(true);
        method.invoke(shardRebalancer, databaseId, movedVectors, sourceShard, targetShard);

        // Проверяем, что не было обращений к клиентам реплик
        verify(shardedStorageClient, never()).getClient(any(ShardInfo.class));
        verify(sourceReplicaClient, never()).getVectorReplica(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Должен продолжить работу при ошибке получения реплики")
    void shouldContinueWhenReplicaRetrievalFails() throws Exception {
        String databaseId = "test-db";
        List<VectorEntry> movedVectors = List.of(
                createTestVectorEntry(1L, databaseId),
                createTestVectorEntry(2L, databaseId)
        );

        // Настройка репликационных локаций
        when(shardReplicationService.getShardOwnership()).thenReturn(shardOwnership);
        when(shardOwnership.getReplicaLocation("shard1")).thenReturn("shard2");
        when(shardOwnership.getReplicaLocation("shard2")).thenReturn("shard3");

        // Настройка поиска ShardInfo по ID
        when(clusterConfigRepository.getShards()).thenReturn(List.of(sourceReplicaShard, targetReplicaShard));
        when(shardedStorageClient.getClient(sourceReplicaShard)).thenReturn(sourceReplicaClient);
        when(shardedStorageClient.getClient(targetReplicaShard)).thenReturn(targetReplicaClient);

        // Первая реплика существует, вторая - ошибка
        VectorEntry replica1 = createTestVectorEntry(1L, databaseId);
        when(sourceReplicaClient.getVectorReplica(1L, databaseId, "shard1"))
                .thenReturn(Mono.just(replica1));
        when(sourceReplicaClient.getVectorReplica(2L, databaseId, "shard1"))
                .thenReturn(Mono.error(new RuntimeException("Replica not found")));

        // Настройка успешного добавления реплики в новую локацию
        when(targetReplicaClient.addVectorReplica(any(VectorEntry.class), eq(databaseId), eq("shard2")))
                .thenReturn(Mono.just(1L));

        // Настройка успешного удаления реплики из старой локации
        when(sourceReplicaClient.deleteVectorReplica(anyLong(), eq(databaseId), eq("shard1")))
                .thenReturn(Mono.just(true));

        // Используем reflection для вызова приватного метода
        java.lang.reflect.Method method = ShardRebalancer.class.getDeclaredMethod(
                "handleReplicaRebalancing", String.class, List.class, ShardInfo.class, ShardInfo.class);
        method.setAccessible(true);
        method.invoke(shardRebalancer, databaseId, movedVectors, sourceShard, targetShard);

        // Проверяем, что только существующая реплика была обработана
        verify(targetReplicaClient, times(1)).addVectorReplica(any(VectorEntry.class), eq(databaseId), eq("shard2"));
        verify(sourceReplicaClient, times(1)).deleteVectorReplica(anyLong(), eq(databaseId), eq("shard1"));
    }

    @Test
    @DisplayName("Должен продолжить работу при ошибке добавления реплики")
    void shouldContinueWhenReplicaAdditionFails() throws Exception {
        String databaseId = "test-db";
        List<VectorEntry> movedVectors = List.of(createTestVectorEntry(1L, databaseId));

        // Настройка репликационных локаций
        when(shardReplicationService.getShardOwnership()).thenReturn(shardOwnership);
        when(shardOwnership.getReplicaLocation("shard1")).thenReturn("shard2");
        when(shardOwnership.getReplicaLocation("shard2")).thenReturn("shard3");

        // Настройка поиска ShardInfo по ID
        when(clusterConfigRepository.getShards()).thenReturn(List.of(sourceReplicaShard, targetReplicaShard));
        when(shardedStorageClient.getClient(sourceReplicaShard)).thenReturn(sourceReplicaClient);
        when(shardedStorageClient.getClient(targetReplicaShard)).thenReturn(targetReplicaClient);

        // Настройка получения реплики
        VectorEntry replica = createTestVectorEntry(1L, databaseId);
        when(sourceReplicaClient.getVectorReplica(1L, databaseId, "shard1"))
                .thenReturn(Mono.just(replica));

        // Ошибка при добавлении реплики в новую локацию
        when(targetReplicaClient.addVectorReplica(any(VectorEntry.class), eq(databaseId), eq("shard2")))
                .thenReturn(Mono.error(new RuntimeException("Failed to add replica")));

        // Настройка успешного удаления реплики из старой локации
        when(sourceReplicaClient.deleteVectorReplica(anyLong(), eq(databaseId), eq("shard1")))
                .thenReturn(Mono.just(true));

        // Используем reflection для вызова приватного метода
        java.lang.reflect.Method method = ShardRebalancer.class.getDeclaredMethod(
                "handleReplicaRebalancing", String.class, List.class, ShardInfo.class, ShardInfo.class);
        method.setAccessible(true);
        method.invoke(shardRebalancer, databaseId, movedVectors, sourceShard, targetShard);

        // Проверяем, что несмотря на ошибку добавления, удаление все равно произошло
        verify(sourceReplicaClient).deleteVectorReplica(1L, databaseId, "shard1");
    }

    private VectorEntry createTestVectorEntry(Long id, String databaseId) {
        float[] vector = new float[128];
        for (int i = 0; i < 128; i++) {
            vector[i] = (float) Math.random();
        }
        return new VectorEntry(id, vector, "test data " + id, databaseId, Instant.now());
    }
}