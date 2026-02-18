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

    public WebConfig(@NonNull TenantInterceptor tenantInterceptor, @NonNull OversightInterceptor oversightInterceptor) {
        this.tenantInterceptor = Objects.requireNonNull(tenantInterceptor, "tenantInterceptor must not be null");
        this.oversightInterceptor = Objects.requireNonNull(oversightInterceptor, "oversightInterceptor must not be null");
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("file:../../web/vue-project/dist/")
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