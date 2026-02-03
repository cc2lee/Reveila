package com.reveila.spring.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import com.reveila.spring.repository.jpa.BaseRepository;

@SpringBootApplication(scanBasePackages = "com.reveila.spring")
@EnableMongoRepositories(basePackages = "com.reveila.spring.repository.mongo")
@EntityScan(basePackages = "com.reveila.spring.model.jpa")
@EnableJpaRepositories(basePackages = "com.reveila.spring.repository.jpa", repositoryBaseClass = BaseRepository.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}