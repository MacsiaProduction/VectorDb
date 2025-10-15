package com.vectordb.storage.config;

import com.vectordb.common.serialization.SearchResultSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SerializationConfig {

    @Bean
    public SearchResultSerializer searchResultSerializer() {
        return new SearchResultSerializer();
    }
}

