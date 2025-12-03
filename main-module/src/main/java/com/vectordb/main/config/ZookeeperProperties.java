package com.vectordb.main.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for ZooKeeper connectivity and VectorDB layout.
 */
@Data
@ConfigurationProperties(prefix = "zookeeper")
public class ZookeeperProperties {

    /**
     * ZooKeeper connection string, e.g. host1:2181,host2:2181.
     */
    @NotBlank
    private String connectString = "localhost:2181";

    /**
     * Base path for all VectorDB related znodes.
     */
    @NotBlank
    private String basePath = "/vectordb";

    /**
     * Path to cluster configuration znode. Defaults to ${basePath}/cluster/config.
     */
    private String clusterConfigPath;

    /**
     * Path to store rebalance progress znodes. Defaults to ${basePath}/rebalance.
     */
    private String rebalancePath;

    /**
     * Path used for leader election znodes. Defaults to ${basePath}/coordinators/main.
     */
    private String coordinatorsPath;

    @NotNull
    private Duration sessionTimeout = Duration.ofSeconds(15);

    @NotNull
    private Duration connectionTimeout = Duration.ofSeconds(5);

    @NotNull
    private Retry retry = new Retry();

    public String clusterConfigPath() {
        if (clusterConfigPath == null || clusterConfigPath.isBlank()) {
            clusterConfigPath = basePath + "/cluster/config";
        }
        return clusterConfigPath;
    }

    public String rebalancePath() {
        if (rebalancePath == null || rebalancePath.isBlank()) {
            rebalancePath = basePath + "/rebalance";
        }
        return rebalancePath;
    }

    public String coordinatorsPath() {
        if (coordinatorsPath == null || coordinatorsPath.isBlank()) {
            coordinatorsPath = basePath + "/coordinators/main";
        }
        return coordinatorsPath;
    }

    @Data
    public static class Retry {
        @NotNull
        private Duration baseSleep = Duration.ofMillis(200);
        private int maxRetries = 10;
    }
}


