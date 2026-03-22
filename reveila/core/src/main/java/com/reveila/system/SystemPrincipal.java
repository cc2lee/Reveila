package com.reveila.system;

import java.security.Principal;

/**
 * Represents a system-level principal that has elevated privileges,
 * such as accessing system components or overriding standard agent restrictions.
 * 
 * @author CL
 */
public record SystemPrincipal(String name) implements Principal {
    
    @Override
    public String getName() {
        return name != null ? name : "SYSTEM";
    }
}
