package com.reveila.safety

import com.reveila.service.DataService
import com.reveila.system.Plugin
import com.reveila.system.SystemComponent
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.Uuid
import kotlin.jvm.JvmSuppressWildcards

/**
 * Standard multiplatform FlightRecorder implementation.
 * Asynchronously captures the agent's full reasoning traces, tool inputs/outputs,
 * and immutable parent-to-child lineage. Persists records through the neutral DataService
 * layer into whatever storage provider the platform uses (Postgres, Room, or Sovereign JSON files).
 */
open class DefaultFlightRecorder : SystemComponent(), FlightRecorder {

    private var dataService: DataService? = null

    @Throws(Exception::class)
    override fun onStart() {
        resolveDataService()
        logger.info("DefaultFlightRecorder initialized with storage neutrality via DataService.")
    }

    @Throws(Exception::class)
    override fun onStop() {
        dataService = null
    }

    private fun resolveDataService(): DataService? {
        if (dataService == null) {
            try {
                val p = context?.getProxy("DataService")
                dataService = (p?.getInstance() as? DataService) ?: p as? DataService
            } catch (e: Exception) {
                // Optional
            }
        }
        return dataService
    }

    override fun recordStep(
        plugin: Plugin,
        stepName: String,
        data: Map<String, @JvmSuppressWildcards Any>
    ) {
        logger.info("[FlightRecorder] [${plugin.name}] [Trace: ${plugin.traceId}] Step: $stepName - Data: $data")
        persistLog(plugin, stepName, data.toString())
    }

    override fun recordReasoning(plugin: Plugin, reasoning: String) {
        logger.info("[FlightRecorder] [${plugin.name}] [Trace: ${plugin.traceId}] Reasoning: $reasoning")
        persistLog(plugin, "REASONING_TRACE", reasoning)
    }

    override fun recordToolOutput(plugin: Plugin, toolName: String, output: Any?) {
        logger.info("[FlightRecorder] [${plugin.name}] [Trace: ${plugin.traceId}] Tool output for $toolName: $output")
        persistLog(plugin, "TOOL_OUTPUT: $toolName", output?.toString() ?: "null")
    }

    override fun recordForensicMetadata(
        plugin: Plugin,
        metadata: Map<String, @JvmSuppressWildcards Any>
    ) {
        logger.info("[FlightRecorder] [${plugin.name}] [Trace: ${plugin.traceId}] Forensic metadata: $metadata")
        persistLog(plugin, "FORENSIC_METRICS", metadata.toString())
    }

    private fun persistLog(plugin: Plugin, action: String, details: String) {
        try {
            val ds = resolveDataService() ?: return
            val recordId = Uuid.randomUuid()
            val timestamp = PlatformSystem.currentTimeMillis()

            val key = mapOf(
                "traceId" to mapOf("value" to plugin.traceId),
                "recordId" to mapOf("value" to recordId)
            )

            val attrs = mutableMapOf<String, Any>()
            attrs["traceId"] = plugin.traceId
            attrs["sessionId"] = plugin.sessionId
            attrs["pluginName"] = plugin.name
            attrs["tenantId"] = plugin.tenantId
            attrs["action"] = action
            attrs["details"] = details
            attrs["timestamp"] = timestamp

            val entityMap = mapOf(
                "key" to key,
                "attributes" to attrs
            )

            ds.save("AuditLog", entityMap)
        } catch (e: Exception) {
            logger.warning("Could not persist audit record to DataService: ${e.message}")
        }
    }
}
