package com.reveila.system

import java.util.UUID

actual class Plugin actual constructor(
    actual val sessionId: String,
    actual val name: String,
    actual val tenantId: String,
    actual val traceId: String
) {
    constructor(sessionId: UUID, name: String, tenantId: String, traceId: String) :
        this(sessionId.toString(), name, tenantId, traceId)

    actual fun deriveChild(childName: String): Plugin {
        return Plugin(UUID.randomUUID().toString(), childName, this.tenantId, this.traceId)
    }

    actual fun createChild(childName: String): Plugin {
        return deriveChild(childName)
    }

    actual companion object {
        actual fun create(name: String, tenantId: String): Plugin {
            return Plugin(UUID.randomUUID().toString(), name, tenantId, UUID.randomUUID().toString())
        }
    }
}
