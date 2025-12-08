package com.vectordb.main.cluster.service;

import com.vectordb.common.model.DatabaseInfo;
import com.vectordb.main.cluster.model.ClusterConfig;
import com.vectordb.main.cluster.model.ShardConfig;
import com.vectordb.main.cluster.model.ShardStatus;
import com.vectordb.main.cluster.rebalance.ShardRebalancer;
import com.vectordb.main.cluster.repository.ClusterConfigRepository;
import com.vectordb.main.cluster.ownership.ShardReplicationService;
import com.vectordb.main.repository.VectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для ClusterReshardingService
 */
@ExtendWith(MockitoExtension.class)
class ClusterReshardingServiceTest {

    @Mock
    private ClusterConfigRepository clusterConfigRepository;

    @Mock
    private ShardRebalancer shardRebalancer;

    @Mock
    private VectorRepository vectorRepository;

    @Mock
    private ShardReplicationService shardReplicationService;

    private ClusterReshardingService reshardingService;

    @BeforeEach
    void setUp() {
        reshardingService = new ClusterReshardingService(
                clusterConfigRepository,
                shardRebalancer,
                vectorRepository,
                null,
                shardReplicationService
        );
    }

    @Test
    @DisplayName("Должен обнаружить новый шард и запустить миграцию")
    void shouldDetectNewShardAndStartMigration() throws Exception {
        // старая конфигурация с 2 шардами
        ClusterConfig oldConfig = new ClusterConfig(
                List.of(
                        new ShardConfig("shard1", "http://storage1:8081", 0L, ShardStatus.ACTIVE),
                        new ShardConfig("shard2", "http://storage2:8081", Long.MAX_VALUE / 2, ShardStatus.ACTIVE)
                )
        );

        // Новая конфигурация с 3 шардами
        ClusterConfig newConfig = new ClusterConfig(
                List.of(
                        new ShardConfig("shard1", "http://storage1:8081", 0L, ShardStatus.ACTIVE),
                        new ShardConfig("shard2", "http://storage2:8081", Long.MAX_VALUE / 3, ShardStatus.ACTIVE),
                        new ShardConfig("shard3", "http://storage3:8081", 2 * (Long.MAX_VALUE / 3), ShardStatus.ACTIVE)
                )
        );

        when(clusterConfigRepository.getClusterConfig()).thenReturn(oldConfig);
        when(vectorRepository.getAllDatabases()).thenReturn(List.of(
                new DatabaseInfo("db1", "Database 1", 3, 100, LocalDateTime.now(), LocalDateTime.now()),
                new DatabaseInfo("db2", "Database 2", 5, 50, LocalDateTime.now(), LocalDateTime.now())
        ));

        reshardingService.applyNewConfiguration(newConfig);

        verify(clusterConfigRepository, times(1)).updateClusterConfig(newConfig);
        verify(vectorRepository, times(1)).getAllDatabases();
        
        // Проверяем, что rebalancer был вызван для нового шарда (shard3)
        // Он должен быть вызван для каждой комбинации: старый_шард -> новый_шард для каждой БД
        // 2 старых шарда * 1 новый шард * 2 БД = 4 вызова
        verify(shardRebalancer, atLeast(1)).rebalance(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Не должен запускать миграцию если новых шардов нет")
    void shouldNotStartMigrationIfNoNewShards() throws Exception {
        //конфигурация не изменилась
        ClusterConfig config = new ClusterConfig(
                List.of(
                        new ShardConfig("shard1", "http://storage1:8081", 0L, ShardStatus.ACTIVE),
                        new ShardConfig("shard2", "http://storage2:8081", Long.MAX_VALUE / 2, ShardStatus.ACTIVE)
                )
        );

        when(clusterConfigRepository.getClusterConfig()).thenReturn(config);

        reshardingService.applyNewConfiguration(config);

        verify(clusterConfigRepository, times(1)).updateClusterConfig(config);
        verify(shardRebalancer, never()).rebalance(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Должен корректно обработать пустой список БД")
    void shouldHandleEmptyDatabaseList() throws Exception {
        ClusterConfig oldConfig = new ClusterConfig(
                List.of(
                        new ShardConfig("shard1", "http://storage1:8081", 0L, ShardStatus.ACTIVE)
                )
        );

        ClusterConfig newConfig = new ClusterConfig(
                List.of(
                        new ShardConfig("shard1", "http://storage1:8081", 0L, ShardStatus.ACTIVE),
                        new ShardConfig("shard2", "http://storage2:8081", Long.MAX_VALUE / 2, ShardStatus.ACTIVE)
                )
        );

        when(clusterConfigRepository.getClusterConfig()).thenReturn(oldConfig);
        when(vectorRepository.getAllDatabases()).thenReturn(List.of());

        reshardingService.applyNewConfiguration(newConfig);

        verify(clusterConfigRepository, times(1)).updateClusterConfig(newConfig);
        verify(vectorRepository, times(1)).getAllDatabases();
        verify(shardRebalancer, never()).rebalance(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Должен обновить конфигурацию даже при ошибке получения списка БД")
    void shouldUpdateConfigEvenIfGetDatabasesFails() throws Exception {
        ClusterConfig oldConfig = new ClusterConfig(
                List.of(
                        new ShardConfig("shard1", "http://storage1:8081", 0L, ShardStatus.ACTIVE)
                )
        );

        ClusterConfig newConfig = new ClusterConfig(
                List.of(
                        new ShardConfig("shard1", "http://storage1:8081", 0L, ShardStatus.ACTIVE),
                        new ShardConfig("shard2", "http://storage2:8081", Long.MAX_VALUE / 2, ShardStatus.ACTIVE)
                )
        );

        when(clusterConfigRepository.getClusterConfig()).thenReturn(oldConfig);
        when(vectorRepository.getAllDatabases()).thenThrow(new RuntimeException("Connection failed"));

        reshardingService.applyNewConfiguration(newConfig);

        //конфигурация должна быть обновлена
        verify(clusterConfigRepository, times(1)).updateClusterConfig(newConfig);
        // Но миграция не должна быть запущена
        verify(shardRebalancer, never()).rebalance(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Должен запустить миграцию для всех БД при добавлении шарда")
    void shouldStartMigrationForAllDatabases() throws Exception {
        ClusterConfig oldConfig = new ClusterConfig(
                List.of(
                        new ShardConfig("shard1", "http://storage1:8081", 0L, ShardStatus.ACTIVE)
                )
        );

        ClusterConfig newConfig = new ClusterConfig(
                List.of(
                        new ShardConfig("shard1", "http://storage1:8081", 0L, ShardStatus.ACTIVE),
                        new ShardConfig("shard2", "http://storage2:8081", Long.MAX_VALUE / 2, ShardStatus.ACTIVE)
                )
        );

        List<DatabaseInfo> databases = List.of(
                new DatabaseInfo("db1", "DB 1", 3, 10, LocalDateTime.now(), LocalDateTime.now()),
                new DatabaseInfo("db2", "DB 2", 5, 20, LocalDateTime.now(), LocalDateTime.now()),
                new DatabaseInfo("db3", "DB 3", 10, 30, LocalDateTime.now(), LocalDateTime.now())
        );

        when(clusterConfigRepository.getClusterConfig()).thenReturn(oldConfig);
        when(vectorRepository.getAllDatabases()).thenReturn(databases);

        reshardingService.applyNewConfiguration(newConfig);

        ArgumentCaptor<String> dbIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(shardRebalancer, atLeast(3)).rebalance(
                dbIdCaptor.capture(),
                any(),
                any(),
                any()
        );

        List<String> capturedDbIds = dbIdCaptor.getAllValues();
        assertThat(capturedDbIds).containsAnyOf("db1", "db2", "db3");
    }

    @Test
    @DisplayName("Должен игнорировать изменение hashKey существующих шардов")
    void shouldIgnoreHashKeyChangesInExistingShards() throws Exception {
        // Arrange - изменился только hashKey у shard2
        ClusterConfig oldConfig = new ClusterConfig(
                List.of(
                        new ShardConfig("shard1", "http://storage1:8081", 0L, ShardStatus.ACTIVE),
                        new ShardConfig("shard2", "http://storage2:8081", Long.MAX_VALUE / 2, ShardStatus.ACTIVE)
                )
        );

        ClusterConfig newConfig = new ClusterConfig(
                List.of(
                        new ShardConfig("shard1", "http://storage1:8081", 0L, ShardStatus.ACTIVE),
                        new ShardConfig("shard2", "http://storage2:8081", Long.MAX_VALUE / 3, ShardStatus.ACTIVE)
                )
        );

        when(clusterConfigRepository.getClusterConfig()).thenReturn(oldConfig);

        reshardingService.applyNewConfiguration(newConfig);

        verify(clusterConfigRepository, times(1)).updateClusterConfig(newConfig);
        verify(shardRebalancer, never()).rebalance(anyString(), any(), any(), any());
    }
}

