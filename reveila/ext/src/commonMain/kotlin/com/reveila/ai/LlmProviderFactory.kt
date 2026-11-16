package com.reveila.ai

import com.reveila.error.ConfigurationException
import com.reveila.system.Properties
import com.reveila.system.SystemComponent
import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil

open class LlmProviderFactory : SystemComponent() {

    companion object {
        val DEFAULT_LOCAL_INTENTS: List<String> = listOf(
            TaskRoutingCategory.LOCAL_TEXT_TRANSFORM.name,
            TaskRoutingCategory.LOCAL_STRUCTURED_EXTRACT.name,
            TaskRoutingCategory.LOCAL_DEVICE_ACTION.name
        )

        val DEFAULT_CLOUD_INTENTS: List<String> = listOf(
            TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL.name,
            TaskRoutingCategory.CLOUD_DEEP_REASONING.name,
            TaskRoutingCategory.CLOUD_MULTI_STEP_AGENT.name
        )

        val ALL_PREDEFINED_INTENTS: List<String> = TaskRoutingCategory.entries.map { it.name }
    }

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
            val jsonConfig = getSettings("llm.json")

            if (jsonConfig.isNotBlank()) {
                val configMap = JsonUtil.parseJsonStringToMap(jsonConfig)

                @Suppress("UNCHECKED_CAST")
                val onboarded = configMap["onboarded.providers"] as? List<Map<String, Any>>

                if (onboarded != null) {
                    for (p in onboarded) {
                        val rawType = (p["type"] as? String) ?: (p["Type"] as? String) ?: "Cloud"
                        val providerField = (p["provider"] as? String) ?: (p["Provider"] as? String)
                        val name = (p["name"] as? String) ?: (p["Name"] as? String)
                        if (!name.isNullOrBlank()) {
                            val provider = createProviderInstance(rawType, providerField, name)
                            configureProvider(provider, p)
                            startAndAddProvider(name, provider)
                            logger.info("Registered LLM Provider: '$name' (type=$rawType, provider=$providerField)")
                        }
                    }
                }
            }

            if (!providers.containsKey("on-device model")) {
                val p = LocalLlamaProvider().apply {
                    this.context = this@LlmProviderFactory.context
                    setName("On-Device Model")
                    endpoint = context?.properties?.getProperty("ai.llm.endpoint") ?: "local://on-device"
                    model = context?.properties?.getProperty("ai.llm.model.name") ?: "auto"
                    temperature = 0.7
                    intentOptions = DEFAULT_LOCAL_INTENTS
                }
                startAndAddProvider("On-Device Model", p)
            }
            if (!providers.containsKey("google gemini")) {
                val p = GeminiLlmProvider().apply {
                    this.context = this@LlmProviderFactory.context
                    setName("Google Gemini")
                    endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
                    model = "gemini-1.5-flash"
                    temperature = 0.1
                    apiKey = "REF:GEMINI_KEY"
                    intentOptions = DEFAULT_CLOUD_INTENTS
                }
                startAndAddProvider("Google Gemini", p)
            }
            if (!providers.containsKey("openai")) {
                val p = OpenAiLlmProvider().apply {
                    this.context = this@LlmProviderFactory.context
                    setName("OpenAI")
                    endpoint = "https://api.openai.com/v1/chat/completions"
                    model = "gpt-4o-mini"
                    temperature = 0.7
                    apiKey = "REF:OPENAI_KEY"
                    intentOptions = DEFAULT_CLOUD_INTENTS
                }
                startAndAddProvider("OpenAI", p)
            }
            if (!providers.containsKey("deepseek")) {
                val p = OpenAiLlmProvider().apply {
                    this.context = this@LlmProviderFactory.context
                    setName("DeepSeek")
                    endpoint = "https://api.deepseek.com/chat/completions"
                    model = "deepseek-chat"
                    temperature = 0.7
                    apiKey = "REF:DEEPSEEK_KEY"
                    intentOptions = DEFAULT_CLOUD_INTENTS
                }
                startAndAddProvider("DeepSeek", p)
            }
            if (!providers.containsKey("qwen")) {
                val p = OpenAiLlmProvider().apply {
                    this.context = this@LlmProviderFactory.context
                    setName("Qwen")
                    endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
                    model = "qwen-plus"
                    temperature = 0.7
                    apiKey = "REF:QWEN_KEY"
                    intentOptions = DEFAULT_CLOUD_INTENTS
                }
                startAndAddProvider("Qwen", p)
            }
        } catch (e: Exception) {
            logger.severe("Critical error loading llm.json: ${e.message}")
        }
    }

    @Throws(ConfigurationException::class)
    open fun getSettings(tab: String): String {
        val fs = PlatformFileSystem()
        val home = context?.properties?.getProperty("system.home") ?: "."

        if (tab.contains("..") || tab.contains("/") || tab.contains("\\")) {
            throw IllegalArgumentException("Invalid tab name")
        }

        val tabPath = fs.resolve(home, "configs/settings/$tab")
        var jsonContent = "{}"
        if (fs.exists(tabPath)) {
            jsonContent = fs.readText(tabPath)
        }

        if ("llm.json" == tab) {
            val configMap: MutableMap<String, Any?> = JsonUtil.parseJsonStringToMap(jsonContent).toMutableMap()
            val workerLlm = context?.properties?.getProperty("ai.worker.llm")
            val govLlm = context?.properties?.getProperty("ai.governance.llm")
            val maxMsgs = context?.properties?.getProperty("ai.session.maxMessages")

            if (!workerLlm.isNullOrBlank()) {
                configMap["ai.worker.llm"] = workerLlm
            }
            if (govLlm != null) {
                configMap["ai.governance.llm"] = govLlm
            }
            if (maxMsgs != null) {
                configMap["ai.session.maxMessages"] = maxMsgs
            }

            return JsonUtil.toJsonString(configMap)
        }

        return jsonContent
    }

    @Throws(Exception::class)
    open fun setSettings(tab: String, jsonConfig: String) {
        saveSettings(tab, jsonConfig)
    }

    @Throws(Exception::class)
    open fun saveSettings(tab: String, jsonConfig: String) {
        val fs = PlatformFileSystem()
        var mutableJsonConfig = jsonConfig
        val home = context?.properties?.getProperty("system.home") ?: "."

        if (tab.contains("..") || tab.contains("/") || tab.contains("\\")) {
            throw IllegalArgumentException("Invalid tab name")
        }

        val settingsDir = fs.resolve(home, "configs/settings")
        if (!fs.exists(settingsDir)) {
            fs.createDirectories(settingsDir)
        }

        val tabPath = fs.resolve(settingsDir, tab)
        val existingMap: MutableMap<String, Any?> = if (fs.exists(tabPath)) {
            try {
                JsonUtil.parseJsonStringToMap(fs.readText(tabPath)).toMutableMap()
            } catch (_: Exception) {
                mutableMapOf()
            }
        } else {
            mutableMapOf()
        }
        val incomingMap: Map<String, Any?> = JsonUtil.parseJsonStringToMap(mutableJsonConfig)
        val configMap: MutableMap<String, Any?> = existingMap.apply { putAll(incomingMap) }
        var modifiedConfig = existingMap.isNotEmpty()

        if ("llm.json" == tab) {
            val onboardedObj = configMap["onboarded.providers"]
            if (onboardedObj is List<*>) {
                @Suppress("UNCHECKED_CAST")
                val providersList = onboardedObj as List<MutableMap<String, Any?>>
                for (p in providersList) {
                    val pName = p["name"] as? String
                    val pKey = p["api.key"] as? String
                    val pEndpoint = p["endpoint"] as? String

                    if (!pName.isNullOrBlank() && !pKey.isNullOrBlank() && !pKey.startsWith("REF:")) {
                        val sKey = pName.replace("\\s+".toRegex(), "_").uppercase() + "_API_KEY"
                        try {
                            context?.getProxy("SecretManager")?.invoke("storeSecret", arrayOf<Any?>(sKey, pKey))
                            p["api.key"] = "REF:$sKey"
                            modifiedConfig = true
                        } catch (e: Exception) {
                            logger.warning("Failed to store provider API key in SecretManager: ${e.message}")
                        }
                    }

                    if (pName != null && pEndpoint != null) {
                        if (pName.startsWith("Gemma") || pName.equals("Ollama", ignoreCase = true)) {
                            configMap["plugin.OnDeviceProvider.apiUrl"] = pEndpoint
                        }
                    }
                }
            }
        }

        if (modifiedConfig) {
            mutableJsonConfig = JsonUtil.toJsonString(configMap)
        }

        fs.writeText(tabPath, mutableJsonConfig, append = false)

        val mainPath = fs.resolve(home, "configs/reveila.properties")
        val mainProps = Properties()
        if (fs.exists(mainPath)) {
            mainProps.load(fs.readText(mainPath))
        }

        for ((key, value) in configMap) {
            if (value != null) {
                mainProps.setProperty(key, value.toString())
            }
        }

        fs.writeText(mainPath, mainProps.store("Merged from Settings tab: $tab"), append = false)

        context?.platformAdapter?.loadProperties(null)

        if ("llm.json" == tab) {
            try {
                loadProviders()
                logger.info("LLM Providers factory reloaded successfully.")
            } catch (e: Exception) {
                logger.warning("Failed to reload LlmProviderFactory: ${e.message}")
            }
        }

        logger.info("Saved and reloaded settings for tab: $tab")
    }

    open fun getActiveProvider(): LlmProvider? {
        val configured = context?.properties?.getProperty("ai.worker.llm")
        if (!configured.isNullOrBlank()) {
            val matched = getProvider(configured)
            if (matched != null) {
                activeProvider = matched
                return activeProvider
            }
        }
        if (activeProvider == null) {
            activeProvider = getProvider("On-Device Model")
                ?: providers.values.firstOrNull()
                ?: LocalLlamaProvider().apply {
                    context = this@LlmProviderFactory.context
                    setName("On-Device Model")
                    start()
                }
        }
        return activeProvider
    }

    open fun getProvider(name: String?): LlmProvider? {
        return if (name == null) null else providers[name.lowercase()]
    }

    open fun isProviderConfigured(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val p = getProvider(name) ?: return false
        return p.isConfigured()
    }

    private fun isLocalProvider(provider: LlmProvider): Boolean {
        val unwrapped = (provider as? TrackedLlmProvider)?.unwrapDelegate() ?: provider
        return unwrapped is LocalLlamaProvider ||
            provider.getName().equals("On-Device Model", ignoreCase = true)
    }

    open fun getConfiguredCloudProvider(): String? {
        val priorityList = listOf("Google Gemini", "OpenAI", "DeepSeek", "Qwen", "Anthropic")
        for (name in priorityList) {
            val p = getProvider(name)
            if (p != null && !isLocalProvider(p) && p.isConfigured()) {
                return p.getName()
            }
        }
        for ((_, p) in providers) {
            if (!isLocalProvider(p) && p.isConfigured()) {
                return p.getName()
            }
        }
        return null
    }

    /**
     * Selects a configured [LlmProvider] capable of handling [intentCategory] based on each
     * provider's configured `Intent.Options` array in `llm.json`.
     *
     * Preference Order:
     * 1. Local On-Device LLM (`LocalLlamaProvider`) if enabled, configured, and its `Intent.Options` includes [intentCategory].
     * 2. Active worker Cloud LLM if configured and its `Intent.Options` includes [intentCategory].
     * 3. Any other configured Cloud LLM whose `Intent.Options` includes [intentCategory].
     * 4. Fallback to any configured Cloud LLM (for `CLOUD_*` intents) or active provider.
     */
    open fun getProviderForIntent(intentCategory: String?): LlmProvider? {
        val targetCategory = TaskRoutingCategory.fromRawOrSafeDefault(intentCategory).name

        // 1. Highest Preference: Local On-Device LLM that declares support for targetCategory
        val onDevice = getProvider("On-Device Model")
        if (onDevice != null && onDevice.isEnabled() && onDevice.isConfigured() && onDevice.supportsIntent(targetCategory)) {
            return onDevice
        }
        for ((_, p) in providers) {
            if (isLocalProvider(p) && p.isEnabled() && p.isConfigured() && p.supportsIntent(targetCategory)) {
                return p
            }
        }

        // 2. Active Worker Provider (if it's a configured Cloud LLM that supports targetCategory)
        val configuredWorkerName = context?.properties?.getProperty("ai.worker.llm")
        if (!configuredWorkerName.isNullOrBlank()) {
            val worker = getProvider(configuredWorkerName)
            if (worker != null && !isLocalProvider(worker) && worker.isEnabled() && worker.isConfigured() && worker.supportsIntent(targetCategory)) {
                return worker
            }
        }

        // 3. Configured Cloud Providers matching targetCategory in priority order
        val priorityList = listOf("Google Gemini", "OpenAI", "DeepSeek", "Qwen", "Anthropic")
        for (name in priorityList) {
            val p = getProvider(name)
            if (p != null && !isLocalProvider(p) && p.isEnabled() && p.isConfigured() && p.supportsIntent(targetCategory)) {
                return p
            }
        }
        for ((_, p) in providers) {
            if (!isLocalProvider(p) && p.isEnabled() && p.isConfigured() && p.supportsIntent(targetCategory)) {
                return p
            }
        }

        // 4. Fallback if no provider with a matching Intent.Option is currently configured with an API key
        if (targetCategory.startsWith("CLOUD_")) {
            val fallbackCloudName = getConfiguredCloudProvider()
            if (!fallbackCloudName.isNullOrBlank()) {
                return getProvider(fallbackCloudName)
            }
        }
        return onDevice ?: getActiveProvider()
    }

    open fun getProviderNameForIntent(intentCategory: String?): String {
        return getProviderForIntent(intentCategory)?.getName() ?: "On-Device Model"
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

    private fun createProviderInstance(type: String, providerField: String? = null, name: String? = null): BaseLlmProvider {
        val normType = type.lowercase().trim()
        val normProvider = (providerField ?: name ?: "").lowercase().trim()
        val provider: BaseLlmProvider = when {
            normType == "local" || normType.contains("llama") || normType.contains("ondevice") -> LocalLlamaProvider()
            normType == "cloud" -> {
                when {
                    normProvider.contains("gemini") -> GeminiLlmProvider()
                    else -> OpenAiLlmProvider()
                }
            }
            normType.contains("gemini") -> GeminiLlmProvider()
            else -> OpenAiLlmProvider()
        }
        provider.context = context
        return provider
    }

    private fun configureProvider(provider: BaseLlmProvider, params: Map<String, Any>) {
        val name = (params["name"] as? String) ?: (params["Name"] as? String)
        var endpoint = (params["endpoint"] as? String) ?: (params["Endpoint"] as? String)

        if (endpoint != null && (endpoint.contains("localhost") || endpoint.contains("127.0.0.1"))) {
            if (isAndroid() && provider !is LocalLlamaProvider) {
                endpoint = endpoint.replace("localhost", "10.0.2.2").replace("127.0.0.1", "10.0.2.2")
            }
        }

        provider.setName(name)
        provider.endpoint = endpoint
        provider.model = (params["model"] as? String) ?: (params["Model"] as? String)
        provider.apiKey = (params["api.key"] as? String) ?: (params["apiKey"] as? String) ?: (params["API.Key"] as? String)
        val tempObj = params["temperature"] ?: params["Temperature"] ?: 0.7
        provider.temperature = tempObj.toString().toDouble()

        val rawIntentsObj = params["Intent.Options"] ?: params["intent.options"] ?: params["intent_options"]
        val parsedIntents = when (rawIntentsObj) {
            is List<*> -> rawIntentsObj.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
            is String -> rawIntentsObj.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
        provider.intentOptions = if (parsedIntents.isNotEmpty()) {
            parsedIntents
        } else if (provider is LocalLlamaProvider) {
            DEFAULT_LOCAL_INTENTS
        } else {
            DEFAULT_CLOUD_INTENTS
        }
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
