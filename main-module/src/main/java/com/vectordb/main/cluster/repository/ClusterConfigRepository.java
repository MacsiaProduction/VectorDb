package com.vectordb.main.cluster.repository;

import com.vectordb.main.cluster.model.ClusterConfig;
import com.vectordb.main.cluster.model.ShardInfo;
import com.vectordb.main.cluster.ring.HashRing;

import java.util.List;

/**
 * Provides access to current cluster configuration and derived hash rings.
 */
public interface ClusterConfigRepository {

    ClusterConfig getClusterConfig();

    HashRing getReadRing();

    HashRing getWriteRing();

    List<ShardInfo> getShards();
    
    /**
     * Updates the cluster configuration in ZooKeeper.
     * @param config new cluster configuration
     * @throws Exception if update fails
     */
    void updateClusterConfig(ClusterConfig config) throws Exception;
}

