package com.reveila.safety

import com.reveila.crypto.PlatformCryptographer
import com.reveila.data.Entity
import com.reveila.service.DataService
import com.reveila.util.json.JsonUtil

/**
 * Single immutable causal audit event within an enterprise command-and-approval trace.
 */
data class CommandChainEvent(
    val recordId: String,
    val traceId: String,
    val spanId: String,
    val parentSpanId: String?,
    val actorId: String,
    val actorType: String,
    val pluginName: String,
    val sessionId: String,
    val tenantId: String,
    val action: String,
    val details: String,
    val timestamp: Long,
    val prevEventHash: String = "",
    val eventHash: String = ""
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "recordId" to recordId,
        "traceId" to traceId,
        "spanId" to spanId,
        "parentSpanId" to (parentSpanId ?: ""),
        "actorId" to actorId,
        "actorType" to actorType,
        "pluginName" to pluginName,
        "sessionId" to sessionId,
        "tenantId" to tenantId,
        "action" to action,
        "details" to details,
        "timestamp" to timestamp,
        "prevEventHash" to prevEventHash,
        "eventHash" to eventHash
    )
}

/**
 * Hierarchical node in a reconstructed causal command tree (`parentSpanId -> spanId`).
 */
data class CommandChainNode(
    val event: CommandChainEvent,
    val children: List<CommandChainNode> = emptyList()
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "event" to event.toMap(),
        "children" to children.map { it.toMap() }
    )
}

/**
 * Reconstructed enterprise command-and-approval chain for a given [traceId].
 */
data class CommandChainReport(
    val traceId: String,
    val initiatedBy: String,
    val approvedBy: List<String>,
    val rejectedBy: List<String>,
    val roots: List<CommandChainNode>,
    val timeline: List<CommandChainEvent>,
    val chainIntegrityVerified: Boolean
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "traceId" to traceId,
        "initiatedBy" to initiatedBy,
        "approvedBy" to approvedBy,
        "rejectedBy" to rejectedBy,
        "chainIntegrityVerified" to chainIntegrityVerified,
        "eventCount" to timeline.size,
        "roots" to roots.map { it.toMap() },
        "timeline" to timeline.map { it.toMap() }
    )

    fun toJsonString(): String = JsonUtil.toJsonString(toMap())
}

/**
 * Computes tamper-evident SHA-256 hash links and reconstructs causal command trees
 * from persisted `AuditLog` entities on demand.
 */
object CommandChainAudit {

    private val crypto by lazy { PlatformCryptographer() }
    private val INITIATION_ACTIONS = setOf(
        "PROMPT_ISSUED",
        "TASK_CREATED",
        "TASK_CREATED_FROM_CHAT"
    )
    private val APPROVAL_ACTIONS = setOf("TASK_APPROVED", "HITL_APPROVED")
    private val REJECTION_ACTIONS = setOf("TASK_REJECTED", "HITL_REJECTED")

    fun computeEventHash(
        prevEventHash: String,
        traceId: String,
        spanId: String,
        parentSpanId: String?,
        actorId: String,
        actorType: String,
        action: String,
        details: String,
        timestamp: Long
    ): String {
        val canonical = listOf(
            prevEventHash,
            traceId,
            spanId,
            parentSpanId.orEmpty(),
            actorId,
            actorType,
            action,
            details,
            timestamp.toString()
        ).joinToString("|")
        val digest = crypto.sha256(canonical.encodeToByteArray())
        return digest.joinToString("") { byte ->
            val v = byte.toInt() and 0xFF
            val hi = "0123456789abcdef"[v ushr 4]
            val lo = "0123456789abcdef"[v and 0x0F]
            "$hi$lo"
        }
    }

    fun reconstructTrace(traceId: String, dataService: DataService): CommandChainReport {
        val allEntities = dataService.getRepository("AuditLog")?.fetchAll().orEmpty()
        return reconstructFromEntities(traceId, allEntities)
    }

    fun reconstructFromEntities(traceId: String, entities: List<Entity>): CommandChainReport {
        val cleanTraceId = traceId.trim()
        val matching = entities.mapNotNull { entity ->
            val attrs = entity.attributes
            val rowTraceId = attrs["traceId"]?.toString()?.trim()
                ?: entity.key?.get("traceId")?.get("value")?.toString()?.trim()
                ?: return@mapNotNull null
            if (rowTraceId != cleanTraceId) return@mapNotNull null

            val recordId = attrs["recordId"]?.toString()
                ?: entity.key?.get("recordId")?.get("value")?.toString()
                ?: ""
            val spanId = attrs["spanId"]?.toString()?.takeIf { it.isNotBlank() } ?: recordId
            val parentSpanId = attrs["parentSpanId"]?.toString()?.takeIf { it.isNotBlank() }
            val actorId = attrs["actorId"]?.toString().orEmpty()
            val actorType = attrs["actorType"]?.toString()?.takeIf { it.isNotBlank() } ?: "SYSTEM"
            val pluginName = attrs["pluginName"]?.toString().orEmpty()
            val sessionId = attrs["sessionId"]?.toString().orEmpty()
            val tenantId = attrs["tenantId"]?.toString().orEmpty()
            val action = attrs["action"]?.toString() ?: "EVENT"
            val details = attrs["details"]?.toString().orEmpty()
            val timestamp = (attrs["timestamp"] as? Number)?.toLong() ?: 0L
            val prevEventHash = attrs["prevEventHash"]?.toString().orEmpty()
            val eventHash = attrs["eventHash"]?.toString().orEmpty()

            CommandChainEvent(
                recordId = recordId,
                traceId = rowTraceId,
                spanId = spanId,
                parentSpanId = parentSpanId,
                actorId = actorId,
                actorType = actorType,
                pluginName = pluginName,
                sessionId = sessionId,
                tenantId = tenantId,
                action = action,
                details = details,
                timestamp = timestamp,
                prevEventHash = prevEventHash,
                eventHash = eventHash
            )
        }.sortedWith(compareBy<CommandChainEvent>({ it.timestamp }, { it.recordId }))

        // Verify tamper-evident hash chain across hashed events
        var integrityOk = matching.isNotEmpty()
        var expectedPrevHash = ""
        for (ev in matching) {
            if (ev.eventHash.isBlank()) continue
            if (ev.prevEventHash != expectedPrevHash) {
                integrityOk = false
                break
            }
            val recomputed = computeEventHash(
                prevEventHash = ev.prevEventHash,
                traceId = ev.traceId,
                spanId = ev.spanId,
                parentSpanId = ev.parentSpanId,
                actorId = ev.actorId,
                actorType = ev.actorType,
                action = ev.action,
                details = ev.details,
                timestamp = ev.timestamp
            )
            if (recomputed != ev.eventHash) {
                integrityOk = false
                break
            }
            expectedPrevHash = ev.eventHash
        }

        val childrenByParent = matching
            .filter { !it.parentSpanId.isNullOrBlank() && it.parentSpanId != it.spanId }
            .groupBy { it.parentSpanId!! }
        val knownSpanIds = matching.map { it.spanId }.toSet()
        val rootEvents = matching.filter { it.parentSpanId.isNullOrBlank() || it.parentSpanId !in knownSpanIds }

        val visited = mutableSetOf<String>()
        fun buildNode(ev: CommandChainEvent): CommandChainNode {
            if (!visited.add(ev.spanId)) {
                return CommandChainNode(ev, emptyList())
            }
            val kids = childrenByParent[ev.spanId].orEmpty().map { buildNode(it) }
            return CommandChainNode(ev, kids)
        }

        val roots = rootEvents.map { buildNode(it) }

        val initiatedBy = matching.firstOrNull {
            it.action.uppercase() in INITIATION_ACTIONS && it.actorId.isNotBlank()
        }?.actorId ?: matching.firstOrNull {
            it.actorId.isNotBlank() && it.actorType.equals("USER", ignoreCase = true)
        }?.actorId ?: matching.firstOrNull { it.actorId.isNotBlank() }?.actorId.orEmpty()

        val approvedBy = matching
            .filter { it.action.uppercase() in APPROVAL_ACTIONS && it.actorId.isNotBlank() }
            .map { it.actorId }
            .distinct()

        val rejectedBy = matching
            .filter { it.action.uppercase() in REJECTION_ACTIONS && it.actorId.isNotBlank() }
            .map { it.actorId }
            .distinct()

        return CommandChainReport(
            traceId = cleanTraceId,
            initiatedBy = initiatedBy,
            approvedBy = approvedBy,
            rejectedBy = rejectedBy,
            roots = roots,
            timeline = matching,
            chainIntegrityVerified = integrityOk
        )
    }
}
