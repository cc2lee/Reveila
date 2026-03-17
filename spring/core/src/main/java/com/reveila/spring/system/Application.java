package com.reveila.spring.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(
    scanBasePackages = "com.reveila.spring"
)
@EnableAsync
@EnableJpaRepositories(
    basePackages = "com.reveila.spring.repository.jpa",
    repositoryBaseClass = com.reveila.spring.repository.jpa.BaseRepository.class
)
@EntityScan(basePackages = "com.reveila.spring.model.jpa")
public class Application {
    public static void main(String[] args) {
        String ollamaUrl = System.getenv("OLLAMA_API_URL");
        if (ollamaUrl == null) ollamaUrl = "http://ollama-service:11434";
        System.out.println("[INFO] Connected to Ollama at " + ollamaUrl);
        
        // Pre-load critical Tomcat/Spring classes to prevent NoClassDefFoundError during JVM shutdown
        // especially when running from a fat JAR.
        try {
            Class.forName("org.apache.catalina.Lifecycle$SingleUse");
            Class.forName("org.springframework.boot.web.server.GracefulShutdownCallback");
        } catch (ClassNotFoundException e) {
            // Ignore if not on classpath
        }
        
        SpringApplication.run(Application.class, args);
    }
}