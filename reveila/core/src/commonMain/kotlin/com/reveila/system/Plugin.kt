package com.reveila.system

import com.reveila.util.Uuid

/**
 * Assigns non-person entity (NPE) identities to each agent session.
 * Multiplatform contract for agent sessions and trace identity.
 */
class Plugin(
    val sessionId: String,
    val name: String,
    val tenantId: String,
    val traceId: String
) {
    fun deriveChild(childName: String): Plugin {
        return Plugin(Uuid.randomUuid(), childName, this.tenantId, this.traceId)
    }

    fun createChild(childName: String): Plugin {
        return deriveChild(childName)
    }

    override fun toString(): String = "Plugin(sessionId='$sessionId', name='$name', tenantId='$tenantId', traceId='$traceId')"

    companion object {
        fun create(name: String, tenantId: String): Plugin {
            return Plugin(
                Uuid.randomUuid(),
                name,
                tenantId,
                Uuid.randomUuid()
            )
        }
    }
}
