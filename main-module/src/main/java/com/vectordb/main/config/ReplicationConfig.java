package com.vectordb.main.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Конфигурация для репликации данных между шардами
 */
@Configuration
public class ReplicationConfig {

    /**
     * Executor для асинхронной репликации данных
     */
    @Bean(name = "replicationExecutor")
    public Executor replicationExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            10, // corePoolSize
            50, // maximumPoolSize
            60L, TimeUnit.SECONDS, // keepAliveTime
            new LinkedBlockingQueue<>(1000), // workQueue
            new ThreadFactoryBuilder().setNameFormat("replication-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy() // rejection handler
        );

        // Разрешаем уменьшение пула потоков ниже corePoolSize
        executor.allowCoreThreadTimeOut(true);

        return executor;
    }
}


