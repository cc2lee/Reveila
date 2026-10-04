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
            resolvedApiKey = context?.getProxy("SecretManager")
                ?.invoke("getSecret", arrayOf(key.substring(4))) as? String
        } else {
            resolvedApiKey = key
        }
        return resolvedApiKey
    }

    protected open fun getHttpClientService(): HttpClientService? {
        return try {
            val sp = context?.getProxy("HttpClientService")
            sp?.getInstance() as? HttpClientService
        } catch (e: Exception) {
            null
        }
    }

    override fun getName(): String = providerName ?: ""

    open fun setName(name: String?) {
        require(!name.isNullOrBlank()) { "Argument 'name' cannot be null or empty." }
        this.providerName = name
    }

    override fun isEnabled(): Boolean = enabled
}
