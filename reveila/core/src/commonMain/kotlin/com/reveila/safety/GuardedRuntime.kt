package com.reveila.safety

import com.reveila.system.Plugin
import kotlin.jvm.JvmSuppressWildcards

/**
 * The Execution Layer (Guarded Runtime).
 * Handles the secure execution of plugins in isolated environments.
 */
interface GuardedRuntime {
    fun execute(
        plugin: Plugin,
        perimeter: SecurityPerimeter,
        arguments: Map<String, @JvmSuppressWildcards Any>,
        jitCredentials: Map<String, @JvmSuppressWildcards String>?
    ): InvocationResult

    fun suspend(jitCredentials: Map<String, @JvmSuppressWildcards String>?): Boolean
    fun resume(jitCredentials: Map<String, @JvmSuppressWildcards String>?): Boolean

    fun suspendSandbox(sandboxId: String): Boolean = false
    fun resumeSandbox(sandboxId: String): Boolean = false
    fun killSandbox(sandboxId: String): Boolean = false
    fun isSandboxSuspended(sandboxId: String): Boolean = false
    fun isSandboxKilled(sandboxId: String): Boolean = false
}
