package com.reveila.spring.config;

import java.util.Objects;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
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
        // Explicitly check for null here if your IDE is still flagging the field usage
        if (this.tenantInterceptor != null) {
            registry.addInterceptor(this.tenantInterceptor).addPathPatterns("/api/**"); // TODO: Adjust path patterns as needed
        }
    }
}