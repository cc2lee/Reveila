package com.reveila.ai;

import java.util.Set;

/**
 * Metadata-defined boundaries for what a plugin is authorized to do.
 * 
 * @author CL
 */
public record AgencyPerimeter(
    Set<String> allowedScopes,
    Set<String> allowedDomains,
    boolean networkAccessEnabled,
    long maxMemoryBytes,
    int maxCpuCores
) {
    /**
     * Checks if a specific scope is allowed within this perimeter.
     *
     * @param scope The scope to check.
     * @return True if the scope is allowed, false otherwise.
     */
    public boolean isScopeAllowed(String scope) {
        return allowedScopes.contains(scope);
    }
}
