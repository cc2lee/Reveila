package com.reveila.system

import com.reveila.util.Uuid
import kotlin.jvm.JvmOverloads

/**
 * Assigns non-person entity (NPE) identities and causal trace/actor lineage to each agent session.
 * Multiplatform contract for agent sessions, trace identity, and command-chain auditing.
 */
class Plugin @JvmOverloads constructor(
    val sessionId: String,
    val name: String,
    val tenantId: String,
    val traceId: String,
    val spanId: String = Uuid.randomUuid(),
    val parentSpanId: String? = null,
    val actorId: String = "",
    val actorType: String = "SYSTEM"
) {
    @JvmOverloads
    fun deriveChild(
        childName: String,
        childActorId: String = this.name,
        childActorType: String = "AGENT"
    ): Plugin {
        return Plugin(
            sessionId = Uuid.randomUuid(),
            name = childName,
            tenantId = this.tenantId,
            traceId = this.traceId,
            spanId = Uuid.randomUuid(),
            parentSpanId = this.spanId,
            actorId = childActorId,
            actorType = childActorType
        )
    }

    fun createChild(childName: String): Plugin {
        return deriveChild(childName)
    }

    override fun toString(): String =
        "Plugin(sessionId='$sessionId', name='$name', tenantId='$tenantId', traceId='$traceId', spanId='$spanId', parentSpanId=$parentSpanId, actorId='$actorId')"

    companion object {
        @JvmOverloads
        fun create(
            name: String,
            tenantId: String,
            actorId: String = "",
            actorType: String = "SYSTEM"
        ): Plugin {
            return Plugin(
                sessionId = Uuid.randomUuid(),
                name = name,
                tenantId = tenantId,
                traceId = Uuid.randomUuid(),
                spanId = Uuid.randomUuid(),
                parentSpanId = null,
                actorId = actorId,
                actorType = actorType
            )
        }
    }
}
