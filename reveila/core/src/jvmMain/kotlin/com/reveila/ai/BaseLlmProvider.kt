package com.reveila.ai

import com.reveila.service.HttpClientService
import com.reveila.system.SystemComponent
import com.reveila.system.SystemProxy

abstract class BaseLlmProvider : SystemComponent(), LlmProvider {
    @JvmField
    protected var name: String? = null
    @JvmField
    protected var apiKey: String? = null
    @JvmField
    protected var resolvedApiKey: String? = null
    @JvmField
    protected var endpoint: String? = null
    @JvmField
    protected var model: String? = null
    @JvmField
    protected var temperature: Double = 0.7
    @JvmField
    protected var enabled: Boolean = true

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

            val url = getEndpoint()
            val body = buildRequestBody(request)
            val headers = getHeaders()

            val responseJson = httpService.invokeRest(url, "POST", body, HttpClientService.JSON, headers)
                ?: throw LlmException("Empty response received from LLM endpoint: $url")
            return parseResponse(responseJson)
        } catch (e: Exception) {
            logger?.severe("Invoke failed for $name: ${e.message}")
            throw LlmException(e.message, e)
        }
    }

    @Throws(Exception::class)
    protected open fun resolveApiKey(): String? {
        if (resolvedApiKey != null) return resolvedApiKey
        if (apiKey != null && apiKey!!.startsWith("REF:")) {
            resolvedApiKey = context?.getProxy("SecretManager")
                ?.invoke("getSecret", arrayOf(apiKey!!.substring(4))) as? String
        } else {
            resolvedApiKey = apiKey
        }
        return resolvedApiKey
    }

    protected open fun getHttpClientService(): HttpClientService? {
        return try {
            val sp = context?.getProxy("HttpClientService")
            (sp as? SystemProxy)?.getInstance() as? HttpClientService
        } catch (e: Exception) {
            null
        }
    }

    override fun getName(): String = name ?: ""

    open fun setName(name: String?) {
        require(!name.isNullOrBlank()) { "Argument 'name' cannot be null or empty." }
        this.name = name
    }

    open fun getEndpoint(): String = endpoint ?: ""

    open fun setEndpoint(endpoint: String?) {
        require(!endpoint.isNullOrBlank()) { "Argument 'endpoint' cannot be null or empty." }
        this.endpoint = endpoint
    }

    open fun setApiKey(apiKey: String?) {
        this.apiKey = apiKey
        this.resolvedApiKey = null
    }

    open fun setModel(model: String?) {
        require(!model.isNullOrBlank()) { "Argument 'model' cannot be null or empty." }
        this.model = model
    }

    open fun setTemperature(temp: Double) {
        require(temp in 0.0..1.0) { "Argument 'temp' must be between 0 and 1." }
        this.temperature = temp
    }

    @Synchronized
    override fun isEnabled(): Boolean = enabled

    @Synchronized
    open fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
