package com.vectordb.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@EnableWebFlux
@ConfigurationPropertiesScan
public class MainApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }
}
