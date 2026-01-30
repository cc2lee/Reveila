package com.reveila.spring.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull; // Use Spring's version
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.reveila.spring.data.User;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request, 
            @NonNull HttpServletResponse response, 
            @NonNull Object handler) {
            
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            if (user.getOrg() != null) {
                TenantContext.setTenantId(user.getOrg().getId());
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request, 
            @NonNull HttpServletResponse response, 
            @NonNull Object handler, 
            @Nullable Exception ex) {
        
        TenantContext.clear(); 
    }
}