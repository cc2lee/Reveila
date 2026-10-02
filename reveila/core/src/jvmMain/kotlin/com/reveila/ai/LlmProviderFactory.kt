package com.reveila.ai

import com.fasterxml.jackson.core.type.TypeReference
import com.reveila.system.SystemComponent
import com.reveila.system.SystemProxy
import com.reveila.util.json.JsonUtil
import java.util.LinkedHashMap

open class LlmProviderFactory : SystemComponent() {

    private val providers: MutableMap<String, LlmProvider> = LinkedHashMap()
    private var activeProvider: LlmProvider? = null

    @Synchronized
    open fun setActiveProvider(name: String?) {
        require(!name.isNullOrBlank()) { "LLMProvider name cannot be null or blank." }
        require(providers.containsKey(name.lowercase())) { "No such LLMProvider: $name" }
        this.activeProvider = providers[name.lowercase()]
    }

    @Throws(Exception::class)
    override fun onStart() {
        loadProviders()
    }

    @Synchronized
    open fun loadProviders() {
        providers.clear()
        activeProvider = null

        try {
            val jsonConfig = context?.getProxy("UiController")
                ?.invoke("getSettings", arrayOf("llm.json")) as? String

            if (!jsonConfig.isNullOrBlank()) {
                val configMap: Map<String, Any> = JsonUtil.MAPPER.readValue(
                    jsonConfig,
                    object : TypeReference<Map<String, Any>>() {}
                )

                @Suppress("UNCHECKED_CAST")
                val onboarded = configMap["onboarded.providers"] as? List<Map<String, Any>>

                if (onboarded != null) {
                    for (p in onboarded) {
                        val type = (p["type"] as? String) ?: "openai"
                        val name = p["name"] as? String
                        if (!name.isNullOrBlank()) {
                            val provider = createProviderInstance(type)
                            configureProvider(provider, p)
                            startAndAddProvider(name, provider)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger?.severe("Critical error loading llm.json: ${e.message}")
        }
    }

    @Synchronized
    open fun getActiveProvider(): LlmProvider? {
        if (activeProvider == null) {
            val configured = context?.properties?.getProperty("ai.worker.llm")
            activeProvider = getProvider(configured)
        }
        return activeProvider
    }

    open fun getProvider(name: String?): LlmProvider? {
        return if (name == null) null else providers[name.lowercase()]
    }

    @Throws(Exception::class)
    override fun onStop() {}

    private fun startAndAddProvider(name: String, provider: BaseLlmProvider) {
        try {
            provider.start()
            providers[name.lowercase()] = wrapWithTracker(provider)
        } catch (e: Exception) {
            logger?.warning("Failed to start provider [$name]: ${e.message}")
        }
    }

    private fun createProviderInstance(type: String): BaseLlmProvider {
        val provider: BaseLlmProvider = when (type.lowercase()) {
            "local" -> LocalLlamaProvider()
            "gemini" -> GeminiLlmProvider()
            "openai" -> OpenAiLlmProvider()
            else -> OpenAiLlmProvider()
        }
        provider.setContext(context)
        return provider
    }

    private fun configureProvider(provider: BaseLlmProvider, params: Map<String, Any>) {
        val name = params["name"] as? String
        var endpoint = params["endpoint"] as? String

        if (endpoint != null && (endpoint.contains("localhost") || endpoint.contains("127.0.0.1"))) {
            if (isAndroid()) {
                endpoint = endpoint.replace("localhost", "10.0.2.2").replace("127.0.0.1", "10.0.2.2")
            }
        }

        provider.setName(name)
        provider.setEndpoint(endpoint)
        provider.setModel(params["model"] as? String)
        provider.setApiKey(params["api.key"] as? String)
        val tempObj = params.getOrDefault("temperature", 0.7)
        provider.setTemperature(tempObj.toString().toDouble())
    }

    private fun wrapWithTracker(provider: LlmProvider): LlmProvider {
        return try {
            val sp = context?.getProxy("UsageTracker")
            val tracker = (sp as? SystemProxy)?.getInstance() as? UsageTracker
            if (tracker != null) TrackedLlmProvider(provider, tracker) else provider
        } catch (e: Exception) {
            logger?.warning("Failed to create UsageTracker: ${e.message}")
            provider
        }
    }

    private fun isAndroid(): Boolean {
        val p = context?.properties?.getProperty("platform")
        return "android".equals(p, ignoreCase = true) || "mobile".equals(p, ignoreCase = true)
    }
}
