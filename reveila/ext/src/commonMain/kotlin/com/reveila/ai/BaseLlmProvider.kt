package com.reveila.ai

import com.reveila.service.HttpClientService
import com.reveila.system.SystemComponent
import kotlin.concurrent.Volatile

abstract class BaseLlmProvider : SystemComponent(), LlmProvider {
    var apiKey: String? = null
        set(value) {
            field = value
            resolvedApiKey = null
        }
    protected var resolvedApiKey: String? = null
    var endpoint: String? = null
    var model: String? = null
    var temperature: Double = 0.7
    @Volatile
    var enabled: Boolean = true
    private var providerName: String? = null

    @Throws(LlmException::class)
    protected abstract fun buildRequestBody(request: LlmRequest): String

    @Throws(LlmException::class)
    protected abstract fun parseResponse(json: String): LlmResponse

    @Throws(LlmException::class)
    protected abstract fun getHeaders(): Map<String, String>

    @Throws(Exception::class)
    override fun onStart() {}

    @Throws(Exception::class)
    override fun onStop() {}

    @Throws(LlmException::class)
    override fun invoke(request: LlmRequest): LlmResponse {
        try {
            val httpService = getHttpClientService()
                ?: throw LlmException("HttpClientService unavailable")

            val url = endpoint ?: ""
            val body = buildRequestBody(request)
            val headers = getHeaders()

            val responseJson = httpService.invokeRest(url, "POST", body, HttpClientService.JSON, headers)
                ?: throw LlmException("Empty response received from LLM endpoint: $url")
            return parseResponse(responseJson)
        } catch (e: Exception) {
            logger.severe("Invoke failed for ${getName()}: ${e.message}")
            throw LlmException(e.message, e)
        }
    }

    @Throws(Exception::class)
    protected open fun resolveApiKey(): String? {
        if (resolvedApiKey != null) return resolvedApiKey
        val key = apiKey
        if (key != null && key.startsWith("REF:")) {
            val secretKey = key.substring(4)
            var sec = context?.getProxy("SecretManager")
                ?.invoke("getSecret", arrayOf(secretKey)) as? String
            if (sec.isNullOrBlank()) {
                val altKey = when {
                    secretKey.contains("GEMINI") -> "GOOGLE_GEMINI_API_KEY"
                    secretKey.contains("DEEPSEEK") -> "DEEPSEEK_API_KEY"
                    secretKey.contains("QWEN") -> "QWEN_API_KEY"
                    else -> "OPENAI_API_KEY"
                }
                sec = context?.getProxy("SecretManager")
                    ?.invoke("getSecret", arrayOf(altKey)) as? String
            }
            resolvedApiKey = sec
        } else {
            resolvedApiKey = key
        }
        return resolvedApiKey
    }

    private val fallbackHttpClientService = HttpClientService()

    protected open fun getHttpClientService(): HttpClientService? {
        return try {
            val sp = context?.getProxy("HttpClientService")
            (sp?.getInstance() as? HttpClientService) ?: fallbackHttpClientService
        } catch (e: Exception) {
            fallbackHttpClientService
        }
    }

    override fun getName(): String = providerName ?: ""

    open fun setName(name: String?) {
        require(!name.isNullOrBlank()) { "Argument 'name' cannot be null or empty." }
        this.providerName = name
    }

    override fun isEnabled(): Boolean = enabled
}
