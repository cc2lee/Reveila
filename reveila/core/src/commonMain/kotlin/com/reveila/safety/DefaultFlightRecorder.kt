package com.reveila.safety

import com.reveila.service.DataService
import com.reveila.system.Plugin
import com.reveila.system.SystemComponent
import com.reveila.system.concurrency.PlatformLock
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.Uuid
import kotlin.jvm.JvmSuppressWildcards

/**
 * Standard multiplatform FlightRecorder implementation.
 * Captures reasoning traces, tool inputs/outputs, and tamper-evident causal
 * command-and-approval events (`traceId`, `spanId`, `parentSpanId`, `actorId`, `eventHash`).
 */
open class DefaultFlightRecorder : SystemComponent(), FlightRecorder {

    private var dataService: DataService? = null
    private val hashLock = PlatformLock()
    private val lastHashByTrace = mutableMapOf<String, String>()
    private var eventSeq: Long = 0L

    @Throws(Exception::class)
    override fun onStart() {
        resolveDataService()
        logger.info("DefaultFlightRecorder initialized with storage neutrality via DataService.")
    }

    @Throws(Exception::class)
    override fun onStop() {
        dataService = null
        hashLock.withLock {
            lastHashByTrace.clear()
        }
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
        persistLog(plugin, stepName, data.toString(), plugin.actorId, plugin.actorType)
    }

    override fun recordReasoning(plugin: Plugin, reasoning: String) {
        logger.info("[FlightRecorder] [${plugin.name}] [Trace: ${plugin.traceId}] Reasoning: $reasoning")
        persistLog(plugin, "REASONING_TRACE", reasoning, plugin.actorId, plugin.actorType)
    }

    override fun recordToolOutput(plugin: Plugin, toolName: String, output: Any?) {
        logger.info("[FlightRecorder] [${plugin.name}] [Trace: ${plugin.traceId}] Tool output for $toolName: $output")
        persistLog(plugin, "TOOL_OUTPUT: $toolName", output?.toString() ?: "null", plugin.actorId, plugin.actorType)
    }

    override fun recordForensicMetadata(
        plugin: Plugin,
        metadata: Map<String, @JvmSuppressWildcards Any>
    ) {
        logger.info("[FlightRecorder] [${plugin.name}] [Trace: ${plugin.traceId}] Forensic metadata: $metadata")
        persistLog(plugin, "FORENSIC_METRICS", metadata.toString(), plugin.actorId, plugin.actorType)
    }

    override fun recordCommandEvent(
        plugin: Plugin,
        action: String,
        details: String,
        actorId: String,
        actorType: String,
        metadata: Map<String, @JvmSuppressWildcards Any>
    ) {
        logger.info("[FlightRecorder] [${plugin.name}] [Trace: ${plugin.traceId}] [Actor: $actorType:$actorId] $action - $details")
        val enrichedDetails = if (metadata.isEmpty()) details else "$details | $metadata"
        persistLog(plugin, action, enrichedDetails, actorId, actorType)
    }

    /**
     * Reconstructs the causal command-and-approval tree for [traceId] from persisted AuditLog records.
     */
    open fun reconstructCommandChain(traceId: String): CommandChainReport? {
        val ds = resolveDataService() ?: return null
        return CommandChainAudit.reconstructTrace(traceId, ds)
    }

    private fun persistLog(
        plugin: Plugin,
        action: String,
        details: String,
        actorId: String,
        actorType: String
    ) {
        try {
            val ds = resolveDataService() ?: return
            val timestamp = PlatformSystem.currentTimeMillis()
            val ctxSnapshot = TraceContextHolder.getContext()
            val resolvedActorId = actorId.ifBlank { ctxSnapshot?.actorId.orEmpty() }
            val resolvedActorType = actorType.ifBlank { ctxSnapshot?.actorType ?: "SYSTEM" }
            val resolvedParentSpan = plugin.parentSpanId ?: ctxSnapshot?.parentSpanId

            val (recordId, prevHash, eventHash) = hashLock.withLock {
                eventSeq += 1L
                val seqStr = eventSeq.toString().padStart(9, '0')
                val id = "$seqStr-${Uuid.randomUuid().take(8)}"
                val prev = lastHashByTrace[plugin.traceId] ?: resolveLatestPersistedHash(ds, plugin.traceId)
                val next = CommandChainAudit.computeEventHash(
                    prevEventHash = prev,
                    traceId = plugin.traceId,
                    spanId = plugin.spanId,
                    parentSpanId = resolvedParentSpan,
                    actorId = resolvedActorId,
                    actorType = resolvedActorType,
                    action = action,
                    details = details,
                    timestamp = timestamp
                )
                lastHashByTrace[plugin.traceId] = next
                Triple(id, prev, next)
            }

            val key = mapOf(
                "recordId" to mapOf("value" to recordId)
            )

            val attrs = mutableMapOf<String, Any>()
            attrs["recordId"] = recordId
            attrs["traceId"] = plugin.traceId
            attrs["spanId"] = plugin.spanId
            if (!resolvedParentSpan.isNullOrBlank()) {
                attrs["parentSpanId"] = resolvedParentSpan
            }
            attrs["actorId"] = resolvedActorId
            attrs["actorType"] = resolvedActorType
            attrs["sessionId"] = plugin.sessionId
            attrs["pluginName"] = plugin.name
            attrs["tenantId"] = plugin.tenantId
            attrs["action"] = action
            attrs["details"] = details
            attrs["timestamp"] = timestamp
            attrs["prevEventHash"] = prevHash
            attrs["eventHash"] = eventHash

            val entityMap = mapOf(
                "key" to key,
                "attributes" to attrs
            )

            ds.save("AuditLog", entityMap)
        } catch (e: Exception) {
            logger.warning("Could not persist audit record to DataService: ${e.message}")
        }
    }

    private fun resolveLatestPersistedHash(ds: DataService, traceId: String): String {
        return try {
            ds.getRepository("AuditLog")?.fetchAll().orEmpty()
                .filter { it.attributes["traceId"]?.toString() == traceId }
                .maxByOrNull { (it.attributes["timestamp"] as? Number)?.toLong() ?: 0L }
                ?.attributes
                ?.get("eventHash")
                ?.toString()
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}
