package com.vectordb.main.cluster.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Serializable configuration of a shard as stored in ZooKeeper.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShardConfig(
        @JsonProperty("shardId")
        String shardId,

        @JsonProperty("baseUrl")
        String baseUrl,

        @JsonProperty("hashKey")
        long hashKey,

        @JsonProperty("status")
        ShardStatus status
) {

    public ShardConfig {
        if (shardId == null || shardId.isBlank()) {
            throw new IllegalArgumentException("shardId must be provided");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must be provided");
        }
        status = status == null ? ShardStatus.NEW : status;
    }
}

