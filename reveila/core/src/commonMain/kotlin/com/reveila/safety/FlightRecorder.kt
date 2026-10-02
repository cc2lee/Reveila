package com.reveila.safety

import com.reveila.system.Plugin
import kotlin.jvm.JvmSuppressWildcards

/**
 * Asynchronous logging of the agent's reasoning chain and tool outputs.
 * Provides forensic auditability for autonomous decisions across all platforms.
 */
interface FlightRecorder {
    /**
     * Records a step in the agent's reasoning process.
     */
    fun recordStep(
        plugin: Plugin,
        stepName: String,
        data: Map<String, @JvmSuppressWildcards Any>
    )

    /**
     * Records the reasoning trace (the 'thought' process) of the agent.
     */
    fun recordReasoning(plugin: Plugin, reasoning: String)

    /**
     * Records the output of a tool/plugin execution.
     */
    fun recordToolOutput(plugin: Plugin, toolName: String, output: Any?)

    /**
     * Captures forensic metadata for execution audit.
     */
    fun recordForensicMetadata(
        plugin: Plugin,
        metadata: Map<String, @JvmSuppressWildcards Any>
    )
}
