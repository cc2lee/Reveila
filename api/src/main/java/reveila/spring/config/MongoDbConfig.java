package reveila.spring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "reveila.spring.repository.mongo")
public class MongoDbConfig {
}
