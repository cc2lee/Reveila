package com.reveila.ai

import com.reveila.data.Entity
import com.reveila.data.Repository
import com.reveila.system.SystemComponent
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

open class UsageTracker : SystemComponent() {

    private var usageRepository: Repository<Entity, Map<String, Map<String, Any>>>? = null

    @Throws(Exception::class)
    @Suppress("UNCHECKED_CAST")
    override fun onStart() {
        val repo = context?.getProxy("DataService")?.invoke("getRepository", arrayOf<Any?>("LlmUsageLog"))
        if (repo is Repository<*, *>) {
            this.usageRepository = repo as Repository<Entity, Map<String, Map<String, Any>>>
        } else {
            logger?.warning("LlmUsageLog repository not found via DataService. UsageTracker will not persist data.")
        }
    }

    @Throws(Exception::class)
    override fun onStop() {
    }

    open fun logUsage(
        tenantId: String?,
        requestId: String?,
        modelId: String?,
        usage: Usage?,
        latencyMs: Long,
        securityResult: JSONObject
    ) {
        val repo = this.usageRepository
        if (repo == null) {
            logger?.warning("Usage data not persisted because usageRepository is null.")
            return
        }

        val u = usage ?: Usage()
        val isSafe = securityResult.optBoolean("safe", true)
        val risk = securityResult.optString("risk_category", "UNKNOWN")
        val mismatch = (!isSafe && risk == "NONE")
        val attributes = mutableMapOf<String, Any>()
        attributes["tenant_id"] = tenantId ?: ""
        attributes["request_id"] = requestId ?: ""
        attributes["model_id"] = modelId ?: ""
        attributes["prompt_tokens"] = u.promptTokens
        attributes["completion_tokens"] = u.completionTokens
        attributes["cached_tokens"] = u.cachedPromptTokens
        attributes["reasoning_tokens"] = u.reasoningTokens ?: 0
        attributes["estimated_cost"] = u.estimatedCost
        attributes["validation_latency_ms"] = latencyMs
        attributes["logic_mismatch"] = mismatch
        attributes["risk_category"] = risk
        attributes["timestamp"] = Instant.now().toString()

        val key = mutableMapOf<String, Map<String, Any>>()
        val idValue = mutableMapOf<String, Any>()
        idValue["value"] = UUID.randomUUID().toString()
        key["id"] = idValue

        val entity = Entity("LlmUsageLog", key, attributes)
        try {
            repo.store(entity)
        } catch (e: Exception) {
            logger?.severe("Failed to store usage log: ${e.message}")
        }
    }
}
