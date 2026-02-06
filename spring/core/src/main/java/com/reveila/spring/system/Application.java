package com.reveila.spring.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = "com.reveila.spring",
    exclude = {
        org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class
    }
)
@EnableJpaRepositories(
    basePackages = "com.reveila.spring.repository.jpa",
    repositoryBaseClass = com.reveila.spring.repository.jpa.BaseRepository.class
)
@EntityScan(basePackages = "com.reveila.spring.model.jpa")

// @EnableMongoRepositories(basePackages = "com.reveila.spring.repository.mongo")

public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}