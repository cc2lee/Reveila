package com.reveila.spring.config;

import java.util.Objects;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.reveila.spring.security.TenantInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;

    public WebConfig(@NonNull TenantInterceptor tenantInterceptor) {
        // Objects.requireNonNull provides a runtime check that satisfies
        // most strict static analysis tools (like Eclipse/JDT or FindBugs)
        this.tenantInterceptor = Objects.requireNonNull(tenantInterceptor, "tenantInterceptor must not be null");
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
    }

    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        // Redirect any path that doesn't contain a dot (to avoid matching files like .js)
        // and isn't an /api call back to index.html
        registry.addViewController("/{spring:[^\\.]*}")
                .setViewName("forward:/index.html");
        registry.addViewController("/**/{spring:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}