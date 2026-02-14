package com.reveila.spring.repository.mongo;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication(scanBasePackages = "com.reveila.spring.repository.mongo")
@EnableMongoRepositories(basePackages = "com.reveila.spring.repository.mongo")
public class MongoTestConfiguration {
}
