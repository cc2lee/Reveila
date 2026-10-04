package com.reveila.service

class ApplePlatformHttpEngine : PlatformHttpEngine {
    override fun execute(
        url: String,
        method: String,
        payload: String?,
        contentType: String?,
        headers: Map<String, String>?,
        timeoutSeconds: Long
    ): PlatformHttpResponse {
        return PlatformHttpResponse(
            statusCode = 200,
            body = "{\"status\":\"ok\"}",
            headers = emptyMap()
        )
    }
}

actual fun createPlatformHttpEngine(): PlatformHttpEngine = ApplePlatformHttpEngine()
