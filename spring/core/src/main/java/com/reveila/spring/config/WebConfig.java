package com.reveila.spring.config;

import java.util.Objects;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.reveila.spring.security.TenantInterceptor;
import com.reveila.spring.security.OversightInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final OversightInterceptor oversightInterceptor;
    private final org.springframework.core.env.Environment env;

    public WebConfig(@NonNull TenantInterceptor tenantInterceptor,
                     @NonNull OversightInterceptor oversightInterceptor,
                     @NonNull org.springframework.core.env.Environment env) {
        this.tenantInterceptor = Objects.requireNonNull(tenantInterceptor, "tenantInterceptor must not be null");
        this.oversightInterceptor = Objects.requireNonNull(oversightInterceptor, "oversightInterceptor must not be null");
        this.env = Objects.requireNonNull(env, "env must not be null");
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // ADR: Resolve system home without relying on Reveila runtime state (avoid NPE during startup)
        String systemHome = env.getProperty(com.reveila.system.Constants.SYSTEM_HOME);
        if (systemHome == null) {
            systemHome = System.getProperty(com.reveila.system.Constants.SYSTEM_HOME);
        }
        if (systemHome == null) {
            systemHome = System.getenv("REVEILA_HOME");
        }
        
        java.nio.file.Path webDistPath;
        if (systemHome != null) {
            // Priority 1: Check for 'web' folder in Reveila Home
            webDistPath = java.nio.file.Path.of(systemHome).resolve("web").toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(webDistPath)) {
                // Priority 2: Fallback to development source tree
                webDistPath = java.nio.file.Path.of(systemHome).resolve("../../web/vue-project/dist/").toAbsolutePath().normalize();
            }
        } else {
            // Ultimate Fallback
            webDistPath = java.nio.file.Path.of("./web/vue-project/dist/").toAbsolutePath().normalize();
        }

        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + webDistPath.toString() + "/")
                .setCachePeriod(0)
                .resourceChain(true);
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        if (this.tenantInterceptor != null) {
            registry.addInterceptor(this.tenantInterceptor)
                    // Targets specifically the Reveila component invocation path
                    .addPathPatterns("/api/components/**/invoke")
                    // Exclude public endpoints like login or status if they are under /api
                    .excludePathPatterns("/api/auth/**", "/api/public/**");
        }

        if (this.oversightInterceptor != null) {
            registry.addInterceptor(this.oversightInterceptor)
                    .addPathPatterns("/api/v1/overwatch/**");
        }
    }

    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        // Redirect any path that doesn't contain a dot (to avoid matching files like
        // .js)
        // and isn't an /api call back to index.html
        // The regex {spring:.*} is used to avoid issues with dots in some path segments
        registry.addViewController("/")
                .setViewName("forward:/index.html");
        registry.addViewController("/{spring:[^\\.]*}")
                .setViewName("forward:/index.html");
        registry.addViewController("/**/{spring:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}