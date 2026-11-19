package com.reveila.safety

import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil
import kotlin.jvm.JvmStatic

/**
 * Immutable snapshot of causal trace and actor context propagated across nested
 * agent/tool/HITL invocations.
 */
data class TraceContextSnapshot(
    val traceId: String,
    val spanId: String = Uuid.randomUuid(),
    val parentSpanId: String? = null,
    val actorId: String = "",
    val actorType: String = "SYSTEM"
)

/**
 * TraceContextHolder provides a mechanism for passing trace_id, causal span linkage
 * (`spanId` / `parentSpanId`), and actor identity (`actorId` / `actorType`) through
 * nested calls, ensuring FlightRecorder maintains an auditable causal tree.
 */
class TraceContextHolder private constructor() {

    companion object {
        private const val ENVELOPE_MARKER = "__tc"

        @JvmStatic
        fun setTraceId(traceId: String?) {
            if (traceId.isNullOrBlank()) {
                PlatformTraceContext.clear()
                return
            }
            val current = getContext()
            val next = if (current != null && current.traceId == traceId) {
                current
            } else {
                TraceContextSnapshot(
                    traceId = traceId,
                    spanId = Uuid.randomUuid(),
                    parentSpanId = null,
                    actorId = current?.actorId.orEmpty(),
                    actorType = current?.actorType ?: "SYSTEM"
                )
            }
            setContext(next)
        }

        @JvmStatic
        fun getTraceId(): String? = getContext()?.traceId

        @JvmStatic
        fun setActor(actorId: String, actorType: String = "USER") {
            val current = getContext()
            val traceId = current?.traceId ?: Uuid.randomUuid()
            setContext(
                TraceContextSnapshot(
                    traceId = traceId,
                    spanId = current?.spanId ?: Uuid.randomUuid(),
                    parentSpanId = current?.parentSpanId,
                    actorId = actorId.trim(),
                    actorType = actorType.trim().ifBlank { "USER" }
                )
            )
        }

        @JvmStatic
        fun setContext(snapshot: TraceContextSnapshot?) {
            if (snapshot == null || snapshot.traceId.isBlank()) {
                PlatformTraceContext.clear()
                return
            }
            val map = linkedMapOf<String, Any?>(
                ENVELOPE_MARKER to true,
                "traceId" to snapshot.traceId,
                "spanId" to snapshot.spanId,
                "parentSpanId" to (snapshot.parentSpanId ?: ""),
                "actorId" to snapshot.actorId,
                "actorType" to snapshot.actorType
            )
            PlatformTraceContext.set(JsonUtil.toJsonString(map))
        }

        @JvmStatic
        fun getContext(): TraceContextSnapshot? {
            val raw = PlatformTraceContext.get()?.trim() ?: return null
            if (raw.isEmpty()) return null
            if (!raw.startsWith("{")) {
                return TraceContextSnapshot(traceId = raw, spanId = raw)
            }
            return try {
                val map = JsonUtil.parseJsonStringToMap(raw)
                if (map[ENVELOPE_MARKER] == true) {
                    val traceId = map["traceId"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
                    val spanId = map["spanId"]?.toString()?.takeIf { it.isNotBlank() } ?: Uuid.randomUuid()
                    val parentSpanId = map["parentSpanId"]?.toString()?.takeIf { it.isNotBlank() }
                    val actorId = map["actorId"]?.toString().orEmpty()
                    val actorType = map["actorType"]?.toString()?.takeIf { it.isNotBlank() } ?: "SYSTEM"
                    TraceContextSnapshot(traceId, spanId, parentSpanId, actorId, actorType)
                } else {
                    TraceContextSnapshot(traceId = raw, spanId = raw)
                }
            } catch (_: Exception) {
                TraceContextSnapshot(traceId = raw, spanId = raw)
            }
        }

        @JvmStatic
        fun deriveChildSpan(
            actorId: String? = null,
            actorType: String? = null
        ): TraceContextSnapshot {
            val current = getContext()
            val traceId = current?.traceId ?: Uuid.randomUuid()
            val child = TraceContextSnapshot(
                traceId = traceId,
                spanId = Uuid.randomUuid(),
                parentSpanId = current?.spanId,
                actorId = actorId?.takeIf { it.isNotBlank() } ?: current?.actorId.orEmpty(),
                actorType = actorType?.takeIf { it.isNotBlank() } ?: (current?.actorType ?: "SYSTEM")
            )
            setContext(child)
            return child
        }

        @JvmStatic
        fun clear() {
            PlatformTraceContext.clear()
        }
    }
}

expect object PlatformTraceContext {
    fun get(): String?
    fun set(value: String?)
    fun clear()
}
