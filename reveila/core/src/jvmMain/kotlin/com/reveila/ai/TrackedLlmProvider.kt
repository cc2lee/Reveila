package com.reveila.ai

import com.reveila.util.json.JsonUtil
import org.json.JSONObject
import kotlin.concurrent.thread

open class TrackedLlmProvider(
    private val delegate: LlmProvider,
    private val tracker: UsageTracker
) : LlmProvider {

    @Throws(LlmException::class)
    override fun invoke(request: LlmRequest): LlmResponse {
        val startTime = System.currentTimeMillis()
        val response = delegate.invoke(request)
        val latency = System.currentTimeMillis() - startTime

        val tenantId = request.metadata.getOrDefault("tenantId", "default")?.toString() ?: "default"
        val requestId = response.requestId
        val modelId = request.modelId

        thread {
            val content = response.content
            var securityData = JSONObject()
            try {
                val cleanJson = JsonUtil.clean(content)
                if (cleanJson != null && cleanJson.startsWith("{") && cleanJson.endsWith("}")) {
                    securityData = JSONObject(cleanJson)
                } else {
                    securityData.put("raw_response", content)
                }
            } catch (e: Exception) {
                securityData.put("raw_response", content)
            }
            tracker.logUsage(tenantId, requestId, modelId, response.usage, latency, securityData)
        }

        return response
    }

    override fun isEnabled(): Boolean = delegate.isEnabled()
    override fun isConfigured(): Boolean = delegate.isConfigured()
    override fun getName(): String = delegate.getName()
}
