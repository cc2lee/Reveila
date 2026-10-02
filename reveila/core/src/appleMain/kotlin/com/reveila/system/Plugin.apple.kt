package com.reveila.system

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
actual class Plugin actual constructor(
    actual val sessionId: String,
    actual val name: String,
    actual val tenantId: String,
    actual val traceId: String
) {
    actual fun deriveChild(childName: String): Plugin {
        return Plugin(kotlin.uuid.Uuid.random().toString(), childName, this.tenantId, this.traceId)
    }

    actual fun createChild(childName: String): Plugin {
        return deriveChild(childName)
    }

    actual companion object {
        actual fun create(name: String, tenantId: String): Plugin {
            return Plugin(
                kotlin.uuid.Uuid.random().toString(),
                name,
                tenantId,
                kotlin.uuid.Uuid.random().toString()
            )
        }
    }
}
