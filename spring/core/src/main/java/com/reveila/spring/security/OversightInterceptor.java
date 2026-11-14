package com.reveila.spring.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor for the unified `/api/components/{componentName}/invoke` endpoint.
 * Enforces the Dual-Key requirement whenever `X-Reveila-Oversight-Token` is supplied.
 * 
 * @author CL
 */
@Component
public class OversightInterceptor implements HandlerInterceptor {

    public static final String OVERSIGHT_TOKEN_HEADER = "X-Reveila-Oversight-Token";
    public static final String OVERSIGHT_TOKEN_ID = "OVERSIGHT_TOKEN_ID";
    private final OversightAuthority oversightAuthority;

    public OversightInterceptor(OversightAuthority oversightAuthority) {
        this.oversightAuthority = oversightAuthority;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, 
                             @NonNull HttpServletResponse response, 
                             @NonNull Object handler) throws Exception {
        
        String token = request.getHeader(OVERSIGHT_TOKEN_HEADER);
        if (token == null) {
            return true;
        }

        if (!oversightAuthority.isValidToken(token)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Dual-Key Requirement Failed: Missing or invalid Oversight Token.");
            return false;
        }

        // Store token in request for forensic tie-in in controllers and FlightRecorder
        request.setAttribute(OVERSIGHT_TOKEN_ID, token);
        return true;
    }
}
