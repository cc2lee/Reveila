package com.reveila.ai

import com.reveila.error.ExceptionCollection
import com.reveila.error.SecurityException
import com.reveila.safety.GuardrailResponse
import com.reveila.safety.IntentValidator
import com.reveila.system.Properties
import com.reveila.system.SystemComponent
import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.StringUtil
import com.reveila.util.json.JsonUtil

open class AiIntentValidator : SystemComponent(), IntentValidator {

    private var llmProvider: LlmProvider? = null
    private var promptTemplate: String? = null

    @Throws(Exception::class)
    override fun onStart() {
        val providerName = context?.properties?.getProperty("ai.governance.llm")
        if (!providerName.isNullOrBlank()) {
            val sp = context?.getProxy("LlmProviderFactory")
            val factory = sp?.getInstance() as? LlmProviderFactory
            this.llmProvider = factory?.getProvider(providerName)
            if (llmProvider == null) {
                logger.warning("Could not find LLM provider with name '$providerName'. Intent validation is disabled.")
            } else {
                val home = context?.properties?.getProperty("system.home")
                if (home != null) {
                    val fs = PlatformFileSystem()
                    val path = fs.resolve(fs.resolve(home, "configs/templates"), "intent_validation_prompt.md")
                    if (fs.exists(path)) {
                        promptTemplate = fs.readText(path)
                    }
                }
            }
        }
    }

    @Throws(SecurityException::class)
    override fun validateIntent(intent: String) {
        if (intent.isBlank()) {
            throw SecurityException("Intent cannot be null or empty")
        }

        val lowerIntent = intent.lowercase()
        if (lowerIntent.contains("ignore previous") ||
            lowerIntent.contains("ignore all") ||
            lowerIntent.contains("disregard") ||
            lowerIntent.contains("system prompt") ||
            lowerIntent.contains("__proto__") ||
            lowerIntent.contains("constructor") ||
            lowerIntent.contains("../") ||
            lowerIntent.contains("..\\")
        ) {
            throw SecurityException(
                "Intent contains suspicious pattern that may indicate prompt injection or path traversal attack"
            )
        }

        val provider = this.llmProvider ?: return
        val template = this.promptTemplate ?: return

        val replacements = Properties()
        replacements.setProperty("intent", intent)
        val validationPrompt = StringUtil.replace(template, "{{", "}}", replacements.toMap(), true, true, null) ?: template
        if (isDebug) {
            logger.info("[DEBUG] Intent validation prompt: $validationPrompt")
        }

        try {
            val request = LlmRequest.builder()
                .addMessage(ReveilaMessage.user(validationPrompt))
                .build()
            val jsonResponse = provider.invoke(request).content
            val cleanJson = JsonUtil.clean(jsonResponse)
            val response = JsonUtil.parseJsonStringToMap(cleanJson)

            val safe = (response["approved"] as? Boolean) ?: false
            val reason = (response["reasoning"] as? String) ?: "Unknown security concern"

            if (!safe) {
                throw SecurityException("Intent validation failed: $reason")
            }
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            val ec = ExceptionCollection(
                "Intent validation service unavailable - blocking request as fail-secure measure", e
            )
            throw SecurityException(ec.message, ec)
        }
    }

    override fun performSafetyAudit(pluginId: String, maskedArgs: String, systemContext: String): Boolean {
        if (this.llmProvider == null) {
            return true
        }
        val response = getGuardrailResponse(pluginId, maskedArgs, systemContext)
        return response.approved()
    }

    open fun getGuardrailResponse(pluginId: String, maskedArgs: String, systemContext: String): GuardrailResponse {
        val provider = this.llmProvider ?: return GuardrailResponse.failSafe()
        val auditPrompt = "Audit the following tool call for plugin $pluginId with arguments: $maskedArgs"

        return try {
            val request = LlmRequest.builder()
                .addMessage(ReveilaMessage.system(systemContext))
                .addMessage(ReveilaMessage.user(auditPrompt))
                .build()
            val jsonResponse = provider.invoke(request).content
            val map = JsonUtil.parseJsonStringToMap(jsonResponse)
            val approved = (map["approved"] as? Boolean) ?: false
            val reasoning = (map["reasoning"] as? String) ?: "No reasoning provided"
            val status = (map["status"] as? String) ?: "REJECTED"
            GuardrailResponse(approved, reasoning, status)
        } catch (e: Exception) {
            val ec = ExceptionCollection("Guardrail validation failed", e)
            logger.warning(ec.toString())
            GuardrailResponse.failSafe()
        }
    }

    @Throws(Exception::class)
    override fun onStop() {}
}
