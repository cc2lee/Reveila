package com.reveila.spring.security;

import org.springframework.stereotype.Service;
import java.util.Set;

/**
 * Validates the specialized X-Reveila-Oversight-Token for high-stakes actions.
 * Provides the "Who watches the watchers?" architectural guardrail.
 * 
 * @author CL
 */
@Service
public class OversightAuthority {

    // In a production scenario, these would be hardware-bound or environment secrets
    private static final Set<String> AUTHORIZED_OVERSIGHT_TOKENS = Set.of(
        "REV-OVR-9921-X", 
        "REV-OVR-4402-Y"
    );

    /**
     * Validates if the provided token has authority to trigger oversight actions.
     */
    public boolean isValidToken(String token) {
        return token != null && AUTHORIZED_OVERSIGHT_TOKENS.contains(token);
    }
}
