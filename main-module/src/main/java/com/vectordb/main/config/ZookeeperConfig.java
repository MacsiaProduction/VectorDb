package com.vectordb.main.config;

import lombok.RequiredArgsConstructor;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures ZooKeeper connectivity via Curator.
 */
@Configuration
@EnableConfigurationProperties(ZookeeperProperties.class)
@RequiredArgsConstructor
public class ZookeeperConfig {

    private final ZookeeperProperties properties;

    @Bean(destroyMethod = "close")
    public CuratorFramework curatorFramework() {
        var retry = properties.getRetry();
        var retryPolicy = new ExponentialBackoffRetry(
                Math.toIntExact(retry.getBaseSleep().toMillis()),
                retry.getMaxRetries()
        );

        CuratorFramework framework = CuratorFrameworkFactory.builder()
                .connectString(properties.getConnectString())
                .retryPolicy(retryPolicy)
                .sessionTimeoutMs(Math.toIntExact(properties.getSessionTimeout().toMillis()))
                .connectionTimeoutMs(Math.toIntExact(properties.getConnectionTimeout().toMillis()))
                .build();
        framework.start();
        return framework;
    }
}


