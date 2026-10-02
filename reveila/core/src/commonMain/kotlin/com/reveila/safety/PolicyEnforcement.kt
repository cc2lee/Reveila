package com.reveila.safety

import com.reveila.system.Plugin
import kotlin.jvm.JvmSuppressWildcards

/**
 * Phase 3: Governance & Security.
 * Responsible for intercepting tool calls, validating against Agency Perimeters,
 * and triggering Human-in-the-Loop (HITL) workflows.
 */
interface PolicyEnforcement {

    /**
     * Determines if a tool call is authorized and if it requires human approval.
     */
    fun authorize(
        plugin: Plugin,
        perimeter: SecurityPerimeter,
        toolName: String,
        arguments: Map<String, @JvmSuppressWildcards Any>
    ): AuthorizationStatus

    /**
     * Injects short-lived credentials into the execution context (JIT).
     */
    fun getJitCredentials(plugin: Plugin, scope: String): Map<String, @JvmSuppressWildcards String>?

    enum class AuthorizationStatus {
        AUTHORIZED,
        DENIED,
        HUMAN_APPROVAL_REQUIRED
    }
}
