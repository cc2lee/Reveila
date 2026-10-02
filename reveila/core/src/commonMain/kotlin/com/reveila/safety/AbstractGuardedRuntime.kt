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

    protected abstract fun onExecute(
        plugin: Plugin,
        perimeter: SecurityPerimeter,
        arguments: Map<String, @JvmSuppressWildcards Any>,
        jitCredentials: Map<String, String>?
    ): InvocationResult
}
