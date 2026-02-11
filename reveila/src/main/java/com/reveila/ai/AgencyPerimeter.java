package com.reveila.ai;

import java.util.Set;

/**
 * The AgencyPerimeter record defines the physical and logical boundaries for
 * the Guarded Runtime, that is, what a plugin is authorized to do.
 * 
 * Resource Quotas: It explicitly includes cgroups-style parameters such as
 * maxMemoryBytes, cpuQuotaUs, and pidsLimit.
 * 
 * Network Control: It supports domain-specific whitelisting (allowedDomains),
 * which is a primary defense against data exfiltration by rogue plugins.
 * 
 * @author CL
 */
public record AgencyPerimeter(
        Set<String> allowedScopes,
        Set<String> allowedDomains,
        boolean networkAccessEnabled,
        long maxMemoryBytes,
        int maxCpuCores,
        int pidsLimit,
        long cpuPeriodUs,
        long cpuQuotaUs) {
    /**
     * Checks if a specific scope is allowed within this perimeter.
     *
     * @param scope The scope to check.
     * @return True if the scope is allowed, false otherwise.
     */
    public boolean isScopeAllowed(String scope) {
        return allowedScopes.contains(scope);
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

        java.util.Set<String> intersectedScopes = new java.util.HashSet<>(this.allowedScopes);
        intersectedScopes.retainAll(other.allowedScopes);

        java.util.Set<String> intersectedDomains = new java.util.HashSet<>(this.allowedDomains);
        intersectedDomains.retainAll(other.allowedDomains);

        return new AgencyPerimeter(
                java.util.Collections.unmodifiableSet(intersectedScopes),
                java.util.Collections.unmodifiableSet(intersectedDomains),
                this.networkAccessEnabled && other.networkAccessEnabled,
                Math.min(this.maxMemoryBytes, other.maxMemoryBytes),
                Math.min(this.maxCpuCores, other.maxCpuCores),
                Math.min(this.pidsLimit, other.pidsLimit),
                this.cpuPeriodUs, // Period usually stays constant for the system
                Math.min(this.cpuQuotaUs, other.cpuQuotaUs));
    }
}
