package com.reveila.safety

import com.reveila.system.Plugin
import com.reveila.system.SystemComponent
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSuppressWildcards

/**
 * Abstract base class for all guarded runtimes.
 * Provides a common foundation for executing plugins in isolated environments.
 *
 * @author CL
 */
abstract class AbstractGuardedRuntime : SystemComponent(), GuardedRuntime {

    @JvmField
    protected var isSuspended: Boolean = false

    private val suspendedSandboxes: MutableSet<String> = mutableSetOf()
    private val killedSandboxes: MutableSet<String> = mutableSetOf()

    fun isSuspended(): Boolean = isSuspended

    protected open fun validateRequest(plugin: Plugin, perimeter: SecurityPerimeter) {
        // Validation passes
    }

    override fun execute(
        plugin: Plugin,
        perimeter: SecurityPerimeter,
        arguments: Map<String, @JvmSuppressWildcards Any>,
        jitCredentials: Map<String, String>?
    ): InvocationResult {
        if (isSuspended) {
            return InvocationResult.error("Runtime is currently suspended.")
        }
        val sandboxId = (arguments["_sandbox_id"] as? String)
            ?: plugin.sessionId.ifBlank { plugin.name }
        if (isSandboxKilled(sandboxId) || isSandboxKilled(plugin.sessionId)) {
            return InvocationResult.error("Task sandbox [$sandboxId] was terminated by user.")
        }
        if (isSandboxSuspended(sandboxId) || isSandboxSuspended(plugin.sessionId)) {
            return InvocationResult.error("Task sandbox [$sandboxId] is currently suspended.")
        }
        validateRequest(plugin, perimeter)
        return onExecute(plugin, perimeter, arguments, jitCredentials)
    }

    override fun suspend(jitCredentials: Map<String, String>?): Boolean {
        isSuspended = true
        return true
    }

    override fun resume(jitCredentials: Map<String, String>?): Boolean {
        isSuspended = false
        return true
    }

    override fun suspendSandbox(sandboxId: String): Boolean {
        if (sandboxId.isBlank()) return false
        suspendedSandboxes.add(sandboxId)
        return true
    }

    override fun resumeSandbox(sandboxId: String): Boolean {
        if (sandboxId.isBlank()) return false
        suspendedSandboxes.remove(sandboxId)
        killedSandboxes.remove(sandboxId)
        return true
    }

    override fun killSandbox(sandboxId: String): Boolean {
        if (sandboxId.isBlank()) return false
        suspendedSandboxes.remove(sandboxId)
        killedSandboxes.add(sandboxId)
        return true
    }

    override fun isSandboxSuspended(sandboxId: String): Boolean {
        return sandboxId.isNotBlank() && suspendedSandboxes.contains(sandboxId)
    }

    override fun isSandboxKilled(sandboxId: String): Boolean {
        return sandboxId.isNotBlank() && killedSandboxes.contains(sandboxId)
    }

    protected abstract fun onExecute(
        plugin: Plugin,
        perimeter: SecurityPerimeter,
        arguments: Map<String, @JvmSuppressWildcards Any>,
        jitCredentials: Map<String, String>?
    ): InvocationResult
}
