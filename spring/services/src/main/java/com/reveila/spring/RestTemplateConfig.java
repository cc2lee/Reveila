package com.reveila.spring;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configures and provides beans for remote communication clients.
 * <p>
 * By using the builders provided by Spring Boot, these clients will be
 * automatically configured with sensible defaults and any custom configuration
 * you provide elsewhere in your application.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

}