package com.reveila.service

data class PlatformHttpResponse(
    val statusCode: Int,
    val body: String?,
    val headers: Map<String, List<String>> = emptyMap()
)

/**
 * Multiplatform HTTP engine abstraction for sovereign outbound network requests.
 */
interface PlatformHttpEngine {
    fun execute(
        url: String,
        method: String,
        payload: String?,
        contentType: String?,
        headers: Map<String, String>?,
        timeoutSeconds: Long
    ): PlatformHttpResponse

    companion object {
        operator fun invoke(): PlatformHttpEngine = createPlatformHttpEngine()
    }
}

expect fun createPlatformHttpEngine(): PlatformHttpEngine
