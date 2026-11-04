package com.reveila.ai

import com.reveila.system.platform.PlatformSystem
import com.reveila.util.json.JsonUtil

open class TrackedLlmProvider(
    private val delegate: LlmProvider,
    private val tracker: UsageTracker
) : LlmProvider {

    @Throws(LlmException::class)
    override fun invoke(request: LlmRequest): LlmResponse {
        val startTime = PlatformSystem.currentTimeMillis()
        val response = delegate.invoke(request)
        val latency = PlatformSystem.currentTimeMillis() - startTime

        val tenantId = request.metadata["tenantId"]?.toString() ?: "default"
        val requestId = response.requestId
        val modelId = request.modelId

        val content = response.content
        var securityData: Map<String, Any?>
        try {
            val cleanJson = JsonUtil.clean(content)
            if (cleanJson != null && cleanJson.startsWith("{") && cleanJson.endsWith("}")) {
                securityData = JsonUtil.parseJsonStringToMap(cleanJson)
            } else {
                securityData = mapOf("raw_response" to content)
            }
        } catch (e: Exception) {
            securityData = mapOf("raw_response" to content)
        }
        tracker.logUsage(tenantId, requestId, modelId, response.usage, latency, securityData)

        return response
    }

    override fun isEnabled(): Boolean = delegate.isEnabled()
    override fun isConfigured(): Boolean = delegate.isConfigured()
    override fun getName(): String = delegate.getName()
}
