package com.vectordb.main.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;


@Configuration
@EnableConfigurationProperties(StorageClientConfig.StorageClientProperties.class)
public class StorageClientConfig {
    
    @Bean
    public WebClient storageWebClient(StorageClientProperties properties) {
        return WebClient.builder()
                .baseUrl("http://" + properties.getHost() + ":" + properties.getPort())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }
    
    @Setter
    @Getter
    @ConfigurationProperties("storage-module")
    public static class StorageClientProperties {
        private String host;
        private int port;
        private int connectionTimeout;
        private int readTimeout;
    }
}
