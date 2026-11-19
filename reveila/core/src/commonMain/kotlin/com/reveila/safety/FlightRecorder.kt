package com.reveila.safety

import com.reveila.system.Plugin
import kotlin.jvm.JvmSuppressWildcards

/**
 * Asynchronous logging of the agent's reasoning chain, tool outputs, and
 * causal command-and-approval lineage across all platforms.
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

    /**
     * Records an auditable command-chain event (prompt issuance, task creation,
     * HITL escalation/approval/rejection, or execution outcome) with causal linkage
     * (`traceId`, `spanId`, `parentSpanId`) and actor identity (`actorId`, `actorType`).
     */
    fun recordCommandEvent(
        plugin: Plugin,
        action: String,
        details: String,
        actorId: String = plugin.actorId,
        actorType: String = plugin.actorType,
        metadata: Map<String, @JvmSuppressWildcards Any> = emptyMap()
    ) {
        recordStep(
            plugin,
            action,
            metadata + mapOf(
                "details" to details,
                "actorId" to actorId,
                "actorType" to actorType
            )
        )
    }
}
