package com.reveila.spring.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor for /api/v1/overwatch/* endpoints.
 * Enforces the Dual-Key requirement using X-Reveila-Oversight-Token.
 * 
 * @author CL
 */
@Component
public class OversightInterceptor implements HandlerInterceptor {

    public static final String OVERSIGHT_TOKEN_HEADER = "X-Reveila-Oversight-Token";
    private final OversightAuthority oversightAuthority;

    public OversightInterceptor(OversightAuthority oversightAuthority) {
        this.oversightAuthority = oversightAuthority;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, 
                             @NonNull HttpServletResponse response, 
                             @NonNull Object handler) throws Exception {
        
        String token = request.getHeader(OVERSIGHT_TOKEN_HEADER);

        if (!oversightAuthority.isValidToken(token)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Dual-Key Requirement Failed: Missing or invalid Oversight Token.");
            return false;
        }

        // Store token in request for forensic tie-in in controllers
        request.setAttribute("OVERSIGHT_TOKEN_ID", token);
        return true;
    }
}
