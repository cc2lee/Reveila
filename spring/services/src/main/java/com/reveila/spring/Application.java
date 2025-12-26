package com.reveila.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = { "com.reveila.spring", "com.reveila.spring.common" })
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}