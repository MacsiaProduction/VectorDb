package com.vectordb.main.config;

import com.vectordb.main.repository.StorageVectorRepository;
import com.vectordb.main.repository.VectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@RequiredArgsConstructor
public class RepositoryConfig {
    
    @Bean
    @Primary
    public VectorRepository vectorRepository(StorageVectorRepository storageVectorRepository) {
        return storageVectorRepository;
    }
}
