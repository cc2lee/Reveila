package com.reveila.system

/**
 * Assigns non-person entity (NPE) identities to each agent session.
 * Multiplatform contract for agent sessions and trace identity.
 */
expect class Plugin(
    sessionId: String,
    name: String,
    tenantId: String,
    traceId: String
) {
    val sessionId: String
    val name: String
    val tenantId: String
    val traceId: String

    fun deriveChild(childName: String): Plugin
    fun createChild(childName: String): Plugin

    companion object {
        fun create(name: String, tenantId: String): Plugin
    }
}
