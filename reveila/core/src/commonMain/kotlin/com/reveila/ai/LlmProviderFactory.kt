package com.reveila.ai

import com.reveila.system.SystemComponent
import com.reveila.util.json.JsonUtil

open class LlmProviderFactory : SystemComponent() {

    private val providers: MutableMap<String, LlmProvider> = mutableMapOf()
    private var activeProvider: LlmProvider? = null

    open fun setActiveProvider(name: String?) {
        require(!name.isNullOrBlank()) { "LLMProvider name cannot be null or blank." }
        require(providers.containsKey(name.lowercase())) { "No such LLMProvider: $name" }
        this.activeProvider = providers[name.lowercase()]
    }

    @Throws(Exception::class)
    override fun onStart() {
        loadProviders()
    }

    open fun loadProviders() {
        providers.clear()
        activeProvider = null

        try {
            val jsonConfig = context?.getProxy("UiController")
                ?.invoke("getSettings", arrayOf("llm.json")) as? String

            if (!jsonConfig.isNullOrBlank()) {
                val configMap = JsonUtil.parseJsonStringToMap(jsonConfig)

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
            logger.severe("Critical error loading llm.json: ${e.message}")
        }
    }

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
            logger.warning("Failed to start provider [$name]: ${e.message}")
        }
    }

    private fun createProviderInstance(type: String): BaseLlmProvider {
        val provider: BaseLlmProvider = when (type.lowercase()) {
            "local" -> LocalLlamaProvider()
            "gemini" -> GeminiLlmProvider()
            "openai" -> OpenAiLlmProvider()
            else -> OpenAiLlmProvider()
        }
        provider.context = context
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
        provider.endpoint = endpoint
        provider.model = params["model"] as? String
        provider.apiKey = params["api.key"] as? String
        val tempObj = params["temperature"] ?: 0.7
        provider.temperature = tempObj.toString().toDouble()
    }

    private fun wrapWithTracker(provider: LlmProvider): LlmProvider {
        return try {
            val sp = context?.getProxy("UsageTracker")
            val tracker = sp?.getInstance() as? UsageTracker
            if (tracker != null) TrackedLlmProvider(provider, tracker) else provider
        } catch (e: Exception) {
            logger.warning("Failed to create UsageTracker: ${e.message}")
            provider
        }
    }

    private fun isAndroid(): Boolean {
        val p = context?.properties?.getProperty("platform")
        return "android".equals(p, ignoreCase = true) || "mobile".equals(p, ignoreCase = true)
    }
}
