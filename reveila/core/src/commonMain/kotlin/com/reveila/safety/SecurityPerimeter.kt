package com.reveila.safety

import kotlin.math.min

/**
 * Defines the physical and logical boundaries for execution within the Guarded Runtime.
 */
data class SecurityPerimeter(
    val accessScopes: Set<String> = emptySet(),
    val allowedDomains: Set<String> = emptySet(),
    val internetAccessBlocked: Boolean = false,
    val maxMemoryMb: Long = 512L,
    val maxCpuCores: Int = 1,
    val maxExecutionSec: Int = 30,
    val delegationAllowed: Boolean = false
) {
    fun accessScopes(): Set<String> = accessScopes
    fun allowedDomains(): Set<String> = allowedDomains
    fun internetAccessBlocked(): Boolean = internetAccessBlocked
    fun maxMemoryMb(): Long = maxMemoryMb
    fun maxCpuCores(): Int = maxCpuCores
    fun maxExecutionSec(): Int = maxExecutionSec
    fun delegationAllowed(): Boolean = delegationAllowed

    fun isScopeAllowed(scope: String): Boolean {
        return accessScopes.contains(scope)
    }

    fun intersect(other: SecurityPerimeter?): SecurityPerimeter {
        if (other == null) return this

        val intersectedScopes = this.accessScopes.intersect(other.accessScopes)
        val intersectedDomains = this.allowedDomains.intersect(other.allowedDomains)

        return SecurityPerimeter(
            accessScopes = intersectedScopes,
            allowedDomains = intersectedDomains,
            internetAccessBlocked = this.internetAccessBlocked || other.internetAccessBlocked,
            maxMemoryMb = min(this.maxMemoryMb, other.maxMemoryMb),
            maxCpuCores = min(this.maxCpuCores, other.maxCpuCores),
            maxExecutionSec = min(this.maxExecutionSec, other.maxExecutionSec),
            delegationAllowed = this.delegationAllowed && other.delegationAllowed
        )
    }
}
