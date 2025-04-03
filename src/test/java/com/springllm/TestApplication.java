package com.springllm;

import com.springllm.config.LlmProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Minimal Spring Boot application configuration for testing the starter.
 * It includes the starter's auto-configuration implicitly.
 * We can also define test-specific beans here if needed, like a specific
 * WebClient.Builder for tests.
 */
@SpringBootApplication // Includes @Configuration, @EnableAutoConfiguration, @ComponentScan
public class TestApplication {

    // Define a WebClient.Builder bean if you want more control during tests
    // (e.g., setting base URLs, default headers).
    // If not defined, the LlmFactoryBean will create a default one.
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    // Define an ObjectMapper bean if needed for consistent JSON handling in tests
    // @Bean
    // public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
    //     return new com.fasterxml.jackson.databind.ObjectMapper();
    // }

}