package com.reveila.ai

import com.reveila.system.platform.PlatformSystem
import com.reveila.util.json.JsonUtil

open class TrackedLlmProvider(
    private val delegate: LlmProvider,
    private val tracker: UsageTracker,
    private val onInvocationResult: ((providerName: String, latencyMs: Long, success: Boolean) -> Unit)? = null
) : LlmProvider {

    @Throws(LlmException::class)
    override fun invoke(request: LlmRequest): LlmResponse {
        val startTime = PlatformSystem.currentTimeMillis()
        val response = try {
            delegate.invoke(request)
        } catch (e: Exception) {
            val failureLatency = PlatformSystem.currentTimeMillis() - startTime
            onInvocationResult?.invoke(delegate.getName(), failureLatency, false)
            throw e
        }
        val latency = PlatformSystem.currentTimeMillis() - startTime
        onInvocationResult?.invoke(delegate.getName(), latency, true)

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
    override fun getSupportedIntents(): List<String> = delegate.getSupportedIntents()
    override fun supportsIntent(intentCategory: String): Boolean = delegate.supportsIntent(intentCategory)

    fun unwrapDelegate(): LlmProvider = delegate
}
