package com.vectordb.main.config;

import com.vectordb.main.cluster.hash.DefaultHashService;
import com.vectordb.main.cluster.hash.HashService;
import com.vectordb.main.id.RandomVectorIdGenerator;
import com.vectordb.main.id.VectorIdGenerator;
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
    public HashService hashService() {
        return new DefaultHashService();
    }
    
    @Bean
    public VectorIdGenerator vectorIdGenerator() {
        return new RandomVectorIdGenerator();
    }
    
    @Bean
    @Primary
    public VectorRepository vectorRepository(StorageVectorRepository storageVectorRepository) {
        return storageVectorRepository;
    }
}
