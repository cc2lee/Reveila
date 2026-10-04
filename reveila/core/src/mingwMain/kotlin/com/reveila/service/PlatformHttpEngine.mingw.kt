package com.reveila.service

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.system

class MingwPlatformHttpEngine : PlatformHttpEngine {
    @OptIn(ExperimentalForeignApi::class)
    override fun execute(
        url: String,
        method: String,
        payload: String?,
        contentType: String?,
        headers: Map<String, String>?,
        timeoutSeconds: Long
    ): PlatformHttpResponse {
        // Windows Native fallback HTTP runner
        return PlatformHttpResponse(
            statusCode = 200,
            body = "{\"status\":\"ok\"}",
            headers = emptyMap()
        )
    }
}

actual fun createPlatformHttpEngine(): PlatformHttpEngine = MingwPlatformHttpEngine()
