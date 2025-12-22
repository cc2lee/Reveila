package com.reveila.spring.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // This enables the @PreAuthorize annotation
public class SharedSecurityConfig {

    @Value("${app.security.public-only:false}")
    private boolean isPublicOnly;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> {
                if (isPublicOnly) {
                    auth.anyRequest().permitAll();
                } else {
                    // Standard secure default
                    auth.requestMatchers("/api/public/**").permitAll()
                        .anyRequest().authenticated();
                }
            });
        return http.build();
    }
}