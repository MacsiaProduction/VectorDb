package com.vectordb.main.config;

import com.vectordb.common.serialization.SearchResultDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SerializationConfig {

    @Bean
    public SearchResultDeserializer searchResultDeserializer() {
        return new SearchResultDeserializer();
    }
}

