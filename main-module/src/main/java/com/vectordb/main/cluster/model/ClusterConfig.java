package com.vectordb.main.cluster.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Cluster configuration payload stored in ZooKeeper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClusterConfig(
        @JsonProperty("shards")
        List<ShardConfig> shards
) {

    public ClusterConfig {
        shards = shards == null ? List.of() : List.copyOf(shards);
    }

    public List<ShardConfig> shards() {
        return Collections.unmodifiableList(shards);
    }

}


