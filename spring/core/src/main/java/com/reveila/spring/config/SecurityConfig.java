package com.reveila.spring.config;

import java.io.IOException;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import com.reveila.safety.SecretManager;
import com.reveila.spring.model.jpa.UserService;
import com.reveila.system.Reveila;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserService userService;
    private final Reveila reveila;

    public SecurityConfig(UserService userService, Reveila reveila) {
        this.userService = userService;
        this.reveila = reveila;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(jitTokenAuthenticationFilter(), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**", "/api/sandbox/**", "/api/governance/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> {
                });

        return http.build();
    }

    /**
     * Authenticates isolated Docker sandbox callbacks carrying a valid JIT Bearer token
     * so they can invoke `/api/components/{componentName}/invoke`.
     */
    private OncePerRequestFilter jitTokenAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    @NonNull HttpServletRequest request,
                    @NonNull HttpServletResponse response,
                    @NonNull FilterChain filterChain) throws ServletException, IOException {
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    String authHeader = request.getHeader("Authorization");
                    if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                        String token = authHeader.substring(7).trim();
                        if (!token.isEmpty() && isValidJitToken(token)) {
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            "jit-sandbox-worker",
                                            token,
                                            List.of(
                                                    new SimpleGrantedAuthority("ROLE_SYSTEM"),
                                                    new SimpleGrantedAuthority("ROLE_PLUGIN")
                                            ));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    private boolean isValidJitToken(String token) {
        try {
            if (reveila != null && reveila.getSystemContext() != null) {
                Object smObj = reveila.getSystemContext().getProxy("SecretManager").getInstance();
                if (smObj instanceof SecretManager sm) {
                    return sm.validateToken(token);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_USER \n ROLE_USER > ROLE_GUEST");
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
