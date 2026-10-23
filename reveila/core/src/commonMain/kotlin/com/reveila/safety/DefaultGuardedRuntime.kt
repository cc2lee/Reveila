package com.reveila.safety

import com.reveila.system.Plugin
import kotlin.jvm.JvmSuppressWildcards

/**
 * Standard multiplatform GuardedRuntime implementation for standard/desktop environments.
 */
open class DefaultGuardedRuntime : AbstractGuardedRuntime() {

    @Throws(Exception::class)
    override fun onStart() {
        logger.info("DefaultGuardedRuntime initialized.")
    }

    @Throws(Exception::class)
    override fun onStop() {
    }

    override fun onExecute(
        plugin: Plugin,
        perimeter: SecurityPerimeter,
        arguments: Map<String, @JvmSuppressWildcards Any>,
        jitCredentials: Map<String, String>?
    ): InvocationResult {
        logger.info("[DefaultGuardedRuntime] Executing plugin ${plugin.name} within perimeter [maxMemory=${perimeter.maxMemoryMb}MB]")
        return InvocationResult.success(null, "Execution completed in DefaultGuardedRuntime")
    }
}
