package com.reveila.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.regions.Region;

@Configuration
public class AwsConfig {

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.US_EAST_1) // Or read from application.properties
                .build();
    }
}
