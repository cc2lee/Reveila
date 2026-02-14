package com.reveila.ai;

import java.util.Set;

/**
 * The AgencyPerimeter record defines the physical and logical boundaries for
 * the Guarded Runtime, that is, what a plugin is authorized to do.
 * 
 * Reconciled to a standardized snake_case naming convention for JSON parity.
 * 
 * @author CL
 */
public record AgencyPerimeter(
        Set<String> accessScopes,
        Set<String> allowedDomains,
        boolean internetAccessBlocked,
        long maxMemoryMb,
        int maxCpuCores,
        int maxExecutionSec,
        boolean delegationAllowed) {

    /**
     * Checks if a specific scope is allowed within this perimeter.
     *
     * @param scope The scope to check.
     * @return True if the scope is allowed, false otherwise.
     */
    public boolean isScopeAllowed(String scope) {
        return accessScopes.contains(scope);
    }

    /**
     * Performs a 'Perimeter Intersection', returning a new perimeter that contains
     * only the permissions allowed by BOTH this and the other perimeter.
     * It always selects the most restrictive resource limits.
     *
     * @param other The other perimeter to intersect with.
     * @return A new, intersected AgencyPerimeter.
     */
    public AgencyPerimeter intersect(AgencyPerimeter other) {
        if (other == null)
            return this;

        java.util.Set<String> intersectedScopes = new java.util.HashSet<>(this.accessScopes);
        intersectedScopes.retainAll(other.accessScopes);

        java.util.Set<String> intersectedDomains = new java.util.HashSet<>(this.allowedDomains);
        intersectedDomains.retainAll(other.allowedDomains);

        return new AgencyPerimeter(
                java.util.Collections.unmodifiableSet(intersectedScopes),
                java.util.Collections.unmodifiableSet(intersectedDomains),
                this.internetAccessBlocked || other.internetAccessBlocked, // Most restrictive
                Math.min(this.maxMemoryMb, other.maxMemoryMb),
                Math.min(this.maxCpuCores, other.maxCpuCores),
                Math.min(this.maxExecutionSec, other.maxExecutionSec),
                this.delegationAllowed && other.delegationAllowed);
    }
}
