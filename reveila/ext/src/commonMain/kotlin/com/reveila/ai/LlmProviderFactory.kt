package com.reveila.ai

import com.reveila.crypto.PlatformCryptographer
import com.reveila.error.ConfigurationException
import com.reveila.system.Properties
import com.reveila.system.SystemComponent
import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil

open class LlmProviderFactory : SystemComponent() {

    private data class ProviderMeta(
        val name: String,
        val type: String,
        val providerField: String,
        val endpoint: String,
        val model: String,
        val apiKey: String,
        val routingProfile: ProviderRoutingProfile
    )

    data class ProviderSelectionResult(
        val provider: LlmProvider?,
        val providerName: String,
        val isAutoMode: Boolean,
        val isCapable: Boolean,
        val intentCategory: String,
        val errorMessage: String? = null,
        val advisoryMessage: String? = null
    )

    companion object {
        const val AUTO_PROVIDER: String = "Auto"

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

        /**
         * Returns true if a local GGUF model is powerful enough (7B+ parameters) or a dedicated
         * safety classifier (Llama Guard / ShieldGemma) to perform reliable AI Safety Review.
         */
        fun isSafetyReviewCapableLocalModel(modelName: String?): Boolean {
            val lower = modelName?.trim()?.lowercase().orEmpty()
            if (lower.isEmpty()) return false
            if (lower.contains("llama-guard") || lower.contains("llamaguard") || lower.contains("shieldgemma")) {
                return true
            }
            val match = Regex("(?<![0-9.])(\\d+(?:\\.\\d+)?)b(?![a-z])").find(lower)
            val billions = match?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return false
            return billions >= 7.0
        }
    }

    private val providers: MutableMap<String, LlmProvider> = mutableMapOf()
    private val providerMeta: MutableMap<String, ProviderMeta> = mutableMapOf()
    private val testedFlags: MutableMap<String, Boolean> = mutableMapOf()
    private val testedFingerprints: MutableMap<String, String> = mutableMapOf()
    private val runtimeStats: MutableMap<String, ProviderRuntimeStats> = mutableMapOf()
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

    private var cachedAssistantLlm: String = ""

    open fun loadProviders() {
        providers.clear()
        providerMeta.clear()
        testedFlags.clear()
        testedFingerprints.clear()
        cachedAssistantLlm = ""
        activeProvider = null

        try {
            val jsonConfig = getSettings("llm.json")

            if (jsonConfig.isNotBlank()) {
                val configMap = JsonUtil.parseJsonStringToMap(jsonConfig)
                cachedAssistantLlm = (configMap["ai.assistant.llm"] as? String)?.trim().orEmpty()

                @Suppress("UNCHECKED_CAST")
                val onboarded = configMap["onboarded.providers"] as? List<Map<String, Any>>

                if (onboarded != null) {
                    for (p in onboarded) {
                        val rawType = (p["type"] as? String) ?: (p["Type"] as? String) ?: "Cloud"
                        val providerField = (p["provider"] as? String) ?: (p["Provider"] as? String) ?: ""
                        val name = (p["name"] as? String) ?: (p["Name"] as? String)
                        if (!name.isNullOrBlank()) {
                            val endpoint = ((p["endpoint"] ?: p["Endpoint"]) as? String)?.trim().orEmpty()
                            val model = ((p["model"] ?: p["Model"]) as? String)?.trim().orEmpty()
                            val rawKey = ((p["Api.Key"] ?: p["api.key"] ?: p["apiKey"] ?: p["API.Key"]) as? String)?.trim().orEmpty()
                            val normType = normalizeType(rawType, name)
                            val testedFlag = (p["Tested"] ?: p["tested"])?.let {
                                it == true || it.toString().equals("true", ignoreCase = true)
                            } ?: false
                            val testedFp = ((p["Tested.Fingerprint"] ?: p["tested.fingerprint"]) as? String)?.trim().orEmpty()
                            val userCostTier = ((p["Cost.Tier"] ?: p["cost.tier"] ?: p["costTier"]) as? String)?.trim().orEmpty()
                            val userCostPer1M = (p["Cost.Per1MTokens"] ?: p["cost.per1m.tokens"] ?: p["costPer1MTokens"])
                                ?.toString()?.trim()?.toDoubleOrNull()
                            val rawModelPricing = (p["Model.Pricing"] ?: p["model.pricing"] ?: p["modelPricing"] ?: p["Cost.Per1MTokens.ByModel"]) as? Map<*, *>
                            val modelPricing: Map<String, Double> = rawModelPricing?.entries?.mapNotNull { (k, v) ->
                                val modelKey = k?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                                val price = v?.toString()?.trim()?.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return@mapNotNull null
                                modelKey to price
                            }?.toMap().orEmpty()
                            val userPerfTier = (p["Performance.Tier"] ?: p["performance.tier"] ?: p["performanceTier"])
                                ?.toString()?.trim().orEmpty()
                            val routingProfile = AutoModelRouter.resolveProfile(
                                type = normType,
                                model = model,
                                providerField = providerField,
                                userCostTier = userCostTier.ifBlank { null },
                                userCostPer1MTokens = userCostPer1M,
                                userPerformanceTier = userPerfTier.ifBlank { null },
                                modelPricing = modelPricing
                            )

                            val provider = createProviderInstance(normType, providerField, name)
                            configureProvider(provider, p)
                            startAndAddProvider(name, provider)

                            val keyLower = name.lowercase()
                            providerMeta[keyLower] = ProviderMeta(
                                name = name,
                                type = normType,
                                providerField = providerField,
                                endpoint = endpoint,
                                model = model,
                                apiKey = rawKey,
                                routingProfile = routingProfile
                            )
                            testedFlags[keyLower] = testedFlag
                            testedFingerprints[keyLower] = testedFp
                            logger.info("Registered LLM Provider: '$name' (type=$normType, provider=$providerField, tested=$testedFlag)")
                        }
                    }
                }
            }

            if (!providers.containsKey("on-device model")) {
                val ep = context?.properties?.getProperty("ai.llm.endpoint") ?: "local://on-device"
                val mdl = context?.properties?.getProperty("ai.llm.model.name") ?: "auto"
                val p = LocalLlamaProvider().apply {
                    this.context = this@LlmProviderFactory.context
                    setName("On-Device Model")
                    endpoint = ep
                    model = mdl
                    temperature = 0.7
                    intentOptions = DEFAULT_LOCAL_INTENTS
                }
                startAndAddProvider("On-Device Model", p)
                providerMeta["on-device model"] = ProviderMeta(
                    name = "On-Device Model",
                    type = "Local",
                    providerField = "",
                    endpoint = ep,
                    model = mdl,
                    apiKey = "",
                    routingProfile = AutoModelRouter.resolveProfile(type = "Local", model = mdl)
                )
            }
            val parsedConfig = if (jsonConfig.isNotBlank()) JsonUtil.parseJsonStringToMap(jsonConfig) else emptyMap()
            val hasExplicitOnboarded = parsedConfig.containsKey("onboarded.providers")
            if (!hasExplicitOnboarded) {
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
            val assistantLlm = context?.properties?.getProperty("ai.assistant.llm")
            val maxMsgs = context?.properties?.getProperty("ai.session.maxMessages")

            if (!workerLlm.isNullOrBlank()) {
                configMap["ai.worker.llm"] = workerLlm
            }
            if (govLlm != null) {
                configMap["ai.governance.llm"] = govLlm
            }
            if (!assistantLlm.isNullOrBlank()) {
                configMap["ai.assistant.llm"] = assistantLlm
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
                    val pName = (p["name"] as? String) ?: (p["Name"] as? String)
                    val rawType = (p["type"] as? String) ?: (p["Type"] as? String) ?: "Cloud"
                    val normType = normalizeType(rawType, pName)
                    val pField = (p["provider"] as? String) ?: (p["Provider"] as? String) ?: ""
                    val pKey = (p["Api.Key"] as? String) ?: (p["api.key"] as? String) ?: (p["apiKey"] as? String) ?: (p["API.Key"] as? String)
                    val pEndpoint = ((p["endpoint"] ?: p["Endpoint"]) as? String)?.trim().orEmpty()
                    val pModel = ((p["model"] ?: p["Model"]) as? String)?.trim().orEmpty()

                    if (!pName.isNullOrBlank() && !pKey.isNullOrBlank() && !pKey.startsWith("REF:")) {
                        val sKey = pName.replace("\\s+".toRegex(), "_").uppercase() + "_API_KEY"
                        try {
                            context?.getProxy("SecretManager")?.invoke("storeSecret", arrayOf<Any?>(sKey, pKey))
                            p.remove("apiKey")
                            p.remove("API.Key")
                            p.remove("api.key")
                            p["Api.Key"] = "REF:$sKey"
                            modifiedConfig = true
                        } catch (e: Exception) {
                            logger.warning("Failed to store provider API key in SecretManager: ${e.message}")
                        }
                    }

                    val effectiveKey = (p["Api.Key"] as? String) ?: (p["api.key"] as? String) ?: pKey
                    val isTestedFlag = (p["Tested"] ?: p["tested"])?.let {
                        it == true || it.toString().equals("true", ignoreCase = true)
                    } ?: false
                    val expectedFp = computeConfigSignature(
                        name = pName.orEmpty(),
                        type = normType,
                        providerField = pField,
                        endpoint = pEndpoint,
                        model = pModel,
                        rawOrRefKey = effectiveKey
                    )
                    val incomingFp = ((p["Tested.Fingerprint"] ?: p["tested.fingerprint"]) as? String)?.trim().orEmpty()
                    if (isTestedFlag && incomingFp != expectedFp) {
                        p["Tested"] = false
                        p["Tested.Fingerprint"] = ""
                        modifiedConfig = true
                    }

                    if (pName != null && pEndpoint.isNotEmpty()) {
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

    open fun getConfiguredWorkerName(): String {
        val raw = context?.properties?.getProperty("ai.worker.llm")?.trim()
        return if (raw.isNullOrBlank()) "On-Device Model" else raw
    }

    open fun getConfiguredGovernanceName(): String {
        val raw = context?.properties?.getProperty("ai.governance.llm")?.trim()
        return if (raw.isNullOrBlank()) "Disable" else raw
    }

    open fun getConfiguredAssistantName(): String {
        val raw = context?.properties?.getProperty("ai.assistant.llm")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: cachedAssistantLlm.takeIf { it.isNotEmpty() }
        return if (raw.isNullOrBlank()) getConfiguredWorkerName() else raw
    }

    open fun isProviderTested(name: String?): Boolean {
        if (name.isNullOrBlank() || name.equals("Disable", ignoreCase = true) || name.equals("Custom", ignoreCase = true)) {
            return false
        }
        if (name.equals(AUTO_PROVIDER, ignoreCase = true)) return true
        val key = name.lowercase()
        val p = providers[key] ?: return false
        if (!p.isConfigured()) return false
        if (testedFlags[key] != true) return false
        val meta = providerMeta[key] ?: return false
        val expectedFp = computeConfigSignature(
            name = meta.name,
            type = meta.type,
            providerField = meta.providerField,
            endpoint = meta.endpoint,
            model = meta.model,
            rawOrRefKey = meta.apiKey
        )
        return testedFingerprints[key] == expectedFp
    }

    /**
     * Tests a single provider configuration JSON object live and returns a JSON payload:
     * `{"ok": boolean, "message": string, "fingerprint": string}`.
     */
    open fun testProviderConfigJson(providerJson: String): String {
        return try {
            val params = JsonUtil.parseJsonStringToMap(providerJson)
            val name = ((params["name"] ?: params["Name"]) as? String)?.trim().orEmpty()
            val rawType = ((params["type"] ?: params["Type"]) as? String)?.trim() ?: "Cloud"
            val normType = normalizeType(rawType, name)
            val providerField = ((params["provider"] ?: params["Provider"]) as? String)?.trim().orEmpty()
            val endpoint = ((params["endpoint"] ?: params["Endpoint"]) as? String)?.trim().orEmpty()
            val model = ((params["model"] ?: params["Model"]) as? String)?.trim().orEmpty()
            val rawKey = ((params["Api.Key"] ?: params["api.key"] ?: params["apiKey"] ?: params["API.Key"]) as? String)?.trim().orEmpty()
            val localReadyHint = (params["localModelReady"] ?: params["local_model_ready"])?.let {
                it == true || it.toString().equals("true", ignoreCase = true)
            } ?: false

            val fingerprint = computeConfigSignature(
                name = name,
                type = normType,
                providerField = providerField,
                endpoint = endpoint,
                model = model,
                rawOrRefKey = rawKey
            )

            if (normType == "Local") {
                if (model.isBlank()) {
                    return JsonUtil.toJsonString(
                        mapOf("ok" to false, "message" to "Embedded GGUF model filename is required.", "fingerprint" to "")
                    )
                }
                if (localReadyHint) {
                    return JsonUtil.toJsonString(
                        mapOf("ok" to true, "message" to "On-device GGUF model '$model' is downloaded and ready.", "fingerprint" to fingerprint)
                    )
                }
                val instance = createProviderInstance(normType, providerField, name)
                configureProvider(instance, params)
                val (ok, msg) = instance.testConnection(timeoutSeconds = 10L)
                return JsonUtil.toJsonString(
                    mapOf("ok" to ok, "message" to msg, "fingerprint" to if (ok) fingerprint else "")
                )
            }

            if (endpoint.isBlank()) {
                return JsonUtil.toJsonString(
                    mapOf("ok" to false, "message" to "Endpoint URL is required.", "fingerprint" to "")
                )
            }
            if (model.isBlank()) {
                return JsonUtil.toJsonString(
                    mapOf("ok" to false, "message" to "Model name is required.", "fingerprint" to "")
                )
            }
            val resolvedKey = resolveSecretOrRawKey(name, normType, rawKey)
            if (normType != "Self-Hosted" && resolvedKey.isBlank()) {
                return JsonUtil.toJsonString(
                    mapOf("ok" to false, "message" to "API key is required for $name.", "fingerprint" to "")
                )
            }

            val instance = createProviderInstance(normType, providerField, name)
            configureProvider(instance, params)
            val (ok, msg) = instance.testConnection(timeoutSeconds = 15L)
            JsonUtil.toJsonString(
                mapOf("ok" to ok, "message" to msg, "fingerprint" to if (ok) fingerprint else "")
            )
        } catch (e: Exception) {
            JsonUtil.toJsonString(
                mapOf("ok" to false, "message" to (e.message ?: "Connection test failed"), "fingerprint" to "")
            )
        }
    }

    open fun resolveExplicitProviderForIntent(
        providerName: String,
        intentCategory: String?
    ): ProviderSelectionResult {
        val targetCategory = TaskRoutingCategory.fromRawOrSafeDefault(intentCategory).name
        if (providerName.equals(AUTO_PROVIDER, ignoreCase = true)) {
            return resolveWorkerProviderForIntent(targetCategory, forceAuto = true)
        }
        val explicit = providers[providerName.lowercase()]
        if (explicit == null || !explicit.isConfigured()) {
            return ProviderSelectionResult(
                provider = explicit,
                providerName = providerName,
                isAutoMode = false,
                isCapable = false,
                intentCategory = targetCategory,
                errorMessage = "Selected LLM '$providerName' is not configured yet. Please configure and test it in Studio -> Configuration."
            )
        }
        return ProviderSelectionResult(
            provider = explicit,
            providerName = explicit.getName(),
            isAutoMode = false,
            isCapable = true,
            intentCategory = targetCategory
        )
    }

    private fun normalizeType(rawType: String, name: String?): String {
        val norm = rawType.lowercase().trim()
        val isSelfHosted = norm == "self-hosted" || norm == "selfhosted"
        val isLocal = !isSelfHosted && (
            norm == "local" ||
                norm.contains("llama") ||
                norm.contains("ondevice") ||
                name.equals("On-Device Model", ignoreCase = true)
            )
        return when {
            isLocal -> "Local"
            isSelfHosted -> "Self-Hosted"
            else -> "Cloud"
        }
    }

    private fun resolveSecretOrRawKey(name: String, normType: String, rawApiKey: String?): String {
        if (normType == "Local") return ""
        val key = rawApiKey?.trim().orEmpty()
        if (key.isEmpty()) return ""
        if (!key.startsWith("REF:")) return key
        val secretKey = key.substring(4)
        var sec = try {
            context?.getProxy("SecretManager")?.invoke("getSecret", arrayOf(secretKey)) as? String
        } catch (_: Exception) {
            null
        }
        if (sec.isNullOrBlank()) {
            val altKey = when {
                secretKey.contains("GEMINI") -> "GOOGLE_GEMINI_API_KEY"
                secretKey.contains("DEEPSEEK") -> "DEEPSEEK_API_KEY"
                secretKey.contains("QWEN") -> "QWEN_API_KEY"
                else -> "OPENAI_API_KEY"
            }
            sec = try {
                context?.getProxy("SecretManager")?.invoke("getSecret", arrayOf(altKey)) as? String
            } catch (_: Exception) {
                null
            }
        }
        return sec?.trim().orEmpty()
    }

    private fun computeConfigSignature(
        name: String,
        type: String,
        providerField: String,
        endpoint: String,
        model: String,
        rawOrRefKey: String?
    ): String {
        val normType = normalizeType(type, name)
        val normProvider = when (normType) {
            "Local" -> ""
            "Self-Hosted" -> "OpenAI"
            else -> providerField.trim()
        }
        val resolvedKey = resolveSecretOrRawKey(name, normType, rawOrRefKey)
        val canonical = "${normType}|${normProvider}|${endpoint.trim()}|${model.trim()}|${resolvedKey}"
        val crypto = PlatformCryptographer()
        return crypto.base64Encode(crypto.sha256(canonical.encodeToByteArray()))
    }

    open fun isAutoWorker(): Boolean = getConfiguredWorkerName().equals(AUTO_PROVIDER, ignoreCase = true)

    open fun isAutoGovernance(): Boolean = getConfiguredGovernanceName().equals(AUTO_PROVIDER, ignoreCase = true)

    open fun getActiveProvider(): LlmProvider? {
        val configured = context?.properties?.getProperty("ai.worker.llm")
        if (!configured.isNullOrBlank() && !configured.equals(AUTO_PROVIDER, ignoreCase = true)) {
            val matched = getProvider(configured)
            if (matched != null) {
                activeProvider = matched
                return activeProvider
            }
        }
        if (configured?.equals(AUTO_PROVIDER, ignoreCase = true) == true) {
            val autoDefault = getProviderForIntent(TaskRoutingCategory.LOCAL_TEXT_TRANSFORM.name)
            if (autoDefault != null) {
                activeProvider = autoDefault
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
        if (name.isNullOrBlank()) return null
        if (name.equals(AUTO_PROVIDER, ignoreCase = true)) {
            return getProviderForIntent(TaskRoutingCategory.LOCAL_TEXT_TRANSFORM.name)
        }
        return providers[name.lowercase()]
    }

    open fun isProviderConfigured(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        if (name.equals(AUTO_PROVIDER, ignoreCase = true)) return true
        val p = providers[name.lowercase()] ?: return false
        return p.isConfigured()
    }

    private fun isLocalProvider(provider: LlmProvider): Boolean {
        val unwrapped = (provider as? TrackedLlmProvider)?.unwrapDelegate() ?: provider
        return unwrapped is LocalLlamaProvider ||
            provider.getName().equals("On-Device Model", ignoreCase = true)
    }

    private fun isSelfHostedProvider(provider: LlmProvider): Boolean {
        val meta = providerMeta[provider.getName().lowercase()]
        return meta?.type.equals("Self-Hosted", ignoreCase = true)
    }

    private fun effectiveLocalModelName(provider: LlmProvider): String {
        val unwrapped = (provider as? TrackedLlmProvider)?.unwrapDelegate() ?: provider
        val configured = (unwrapped as? BaseLlmProvider)?.model?.trim().orEmpty()
        return if (configured.isEmpty() || configured.equals("auto", ignoreCase = true)) {
            LocalLlamaProvider.nativeDelegate?.getLoadedModelPath()?.substringAfterLast('/')?.substringAfterLast('\\').orEmpty()
        } else {
            configured
        }
    }

    open fun isSafetyReviewCapableProvider(provider: LlmProvider): Boolean {
        if (!provider.isEnabled() || !provider.isConfigured()) return false
        if (isLocalProvider(provider)) {
            return isSafetyReviewCapableLocalModel(effectiveLocalModelName(provider))
        }
        return true
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

    open fun recordProviderInvocation(providerName: String, latencyMs: Long, success: Boolean) {
        val key = providerName.trim().lowercase()
        if (key.isEmpty()) return
        val current = runtimeStats[key] ?: ProviderRuntimeStats()
        runtimeStats[key] = if (success) {
            current.recordSuccess(latencyMs)
        } else {
            current.recordFailure(latencyMs)
        }
    }

    open fun getProviderRuntimeStats(providerName: String): ProviderRuntimeStats {
        return runtimeStats[providerName.trim().lowercase()] ?: ProviderRuntimeStats()
    }

    open fun getProviderRoutingProfile(providerName: String): ProviderRoutingProfile {
        val key = providerName.trim().lowercase()
        providerMeta[key]?.routingProfile?.let { return it }
        val p = providers[key]
        val unwrapped = (p as? TrackedLlmProvider)?.unwrapDelegate() ?: p
        val type = when {
            p != null && isLocalProvider(p) -> "Local"
            p != null && isSelfHostedProvider(p) -> "Self-Hosted"
            else -> "Cloud"
        }
        val model = (unwrapped as? BaseLlmProvider)?.model?.trim().orEmpty()
        return AutoModelRouter.resolveProfile(type = type, model = model)
    }

    private fun buildRoutingCandidate(provider: LlmProvider): RoutingCandidate {
        val name = provider.getName()
        val key = name.lowercase()
        val meta = providerMeta[key]
        val type = meta?.type ?: when {
            isLocalProvider(provider) -> "Local"
            isSelfHostedProvider(provider) -> "Self-Hosted"
            else -> "Cloud"
        }
        val unwrapped = (provider as? TrackedLlmProvider)?.unwrapDelegate() ?: provider
        val model = meta?.model?.takeIf { it.isNotBlank() }
            ?: (unwrapped as? BaseLlmProvider)?.model?.trim().orEmpty()
        val profile = meta?.routingProfile ?: AutoModelRouter.resolveProfile(
            type = type,
            model = model,
            providerField = meta?.providerField.orEmpty()
        )
        return RoutingCandidate(
            name = name,
            type = type,
            providerField = meta?.providerField.orEmpty(),
            model = model,
            isTested = isProviderTested(name),
            profile = profile,
            stats = runtimeStats[key] ?: ProviderRuntimeStats()
        )
    }

    /**
     * Ranks all enabled, configured providers that support [intentCategory] using
     * [AutoModelRouter] (capability fit + token cost + performance + tested status + live telemetry).
     */
    open fun rankProvidersForIntent(intentCategory: String?): List<ScoredRoutingCandidate> {
        val category = TaskRoutingCategory.fromRawOrSafeDefault(intentCategory)
        val targetCategory = category.name
        val capableCandidates = providers.values
            .filter { it.isEnabled() && it.isConfigured() && it.supportsIntent(targetCategory) }
            .map { buildRoutingCandidate(it) }
        return AutoModelRouter.rankCandidates(capableCandidates, category)
    }

    /**
     * Selects the best configured [LlmProvider] capable of handling [intentCategory] using
     * [AutoModelRouter]'s capability + cost/performance + live-telemetry ranking:
     * - `LOCAL_*` intents stay local-first (Local > Self-Hosted > Economy Cloud).
     * - `CLOUD_LIVE_RETRIEVAL` favors fast, low-cost models (Self-Hosted / Economy Cloud).
     * - `CLOUD_DEEP_REASONING` & `CLOUD_MULTI_STEP_AGENT` favor high-capability models with cost as a tie-breaker.
     * - Verified (`Tested`) providers are preferred over untested ones, and recent failures/high latency penalize a provider.
     * - Fallback if no provider declares [intentCategory]: ranks all configured non-local providers for `CLOUD_*`,
     *   or falls back to On-Device Model / active provider.
     */
    open fun getProviderForIntent(intentCategory: String?): LlmProvider? {
        val category = TaskRoutingCategory.fromRawOrSafeDefault(intentCategory)
        val ranked = rankProvidersForIntent(category.name)
        if (ranked.isNotEmpty()) {
            val winner = ranked.first()
            logger.info("AutoModelRouter selected ${winner.reason} for ${category.name}")
            return providers[winner.candidate.name.lowercase()]
        }

        // Fallback if no provider explicitly declares support for targetCategory in Intent.Options
        if (category.isCloudRoute) {
            val cloudFallbackCandidates = providers.values
                .filter { !isLocalProvider(it) && it.isEnabled() && it.isConfigured() }
                .map { buildRoutingCandidate(it) }
            val fallbackWinner = AutoModelRouter.rankCandidates(cloudFallbackCandidates, category).firstOrNull()
            if (fallbackWinner != null) {
                return providers[fallbackWinner.candidate.name.lowercase()]
            }
        }
        return getProvider("On-Device Model") ?: getActiveProvider()
    }

    open fun getProviderNameForIntent(intentCategory: String?): String {
        return getProviderForIntent(intentCategory)?.getName() ?: "On-Device Model"
    }

    /**
     * Resolves the Worker LLM for [intentCategory]:
     * - When `ai.worker.llm` is `"Auto"` (or [forceAuto] is true), automatically selects the
     *   best configured LLM using the local-first rule ([getProviderForIntent]).
     * - When an explicit Worker LLM is selected, that exact provider MUST be used; if it is not
     *   configured or its `Intent.Options` does not support [intentCategory], returns `isCapable = false`
     *   with an actionable alert message prompting the user to choose a different LLM.
     */
    open fun resolveWorkerProviderForIntent(
        intentCategory: String?,
        forceAuto: Boolean = false
    ): ProviderSelectionResult {
        val targetCategory = TaskRoutingCategory.fromRawOrSafeDefault(intentCategory).name
        val configuredName = getConfiguredWorkerName()

        if (forceAuto || configuredName.equals(AUTO_PROVIDER, ignoreCase = true)) {
            val selected = getProviderForIntent(targetCategory)
            val selectedName = selected?.getName() ?: "On-Device Model"
            return ProviderSelectionResult(
                provider = selected,
                providerName = selectedName,
                isAutoMode = true,
                isCapable = selected != null,
                intentCategory = targetCategory
            )
        }

        val explicit = providers[configuredName.lowercase()]
        if (explicit == null || !explicit.isConfigured()) {
            return ProviderSelectionResult(
                provider = explicit,
                providerName = configuredName,
                isAutoMode = false,
                isCapable = false,
                intentCategory = targetCategory,
                errorMessage = "Selected Worker LLM '$configuredName' is not configured yet. Please configure it or select 'Auto' in Studio -> Configuration."
            )
        }

        val supported = explicit.getSupportedIntents()
        if (supported.isNotEmpty() && !explicit.supportsIntent(targetCategory)) {
            return ProviderSelectionResult(
                provider = explicit,
                providerName = explicit.getName(),
                isAutoMode = false,
                isCapable = false,
                intentCategory = targetCategory,
                errorMessage = "Selected Worker LLM '${explicit.getName()}' cannot handle task type '$targetCategory' (supported: ${supported.joinToString(", ")}). Please choose a different LLM or select 'Auto' in Studio -> Configuration."
            )
        }

        return ProviderSelectionResult(
            provider = explicit,
            providerName = explicit.getName(),
            isAutoMode = false,
            isCapable = true,
            intentCategory = targetCategory
        )
    }

    /**
     * Resolves the Governance (AI Safety Review) LLM for [intentCategory] using a privacy-first policy:
     * - When `ai.governance.llm` is `"Disable"` or blank, AI Safety Review is disabled
     *   (`isCapable = true`, `provider = null`); deterministic `GovernancePolicy` guardrails stay active.
     * - When `ai.governance.llm` is `"Auto"`, selects strictly in privacy-preserving order:
     *   1. Capable On-Device Local Model (7B+ or dedicated guard model),
     *   2. Self-Hosted private-network model (`Type: "Self-Hosted"`),
     *   3. Disabled with an advisory (never silently sends safety reviews to a public Cloud LLM).
     * - When an explicit Governance LLM is selected:
     *   - Rejects underpowered embedded local models (< 7B general models) with a clear alert.
     *   - Allows capable local, self-hosted, or explicitly opted-in Cloud providers.
     */
    open fun resolveGovernanceProviderForIntent(intentCategory: String?): ProviderSelectionResult {
        val targetCategory = TaskRoutingCategory.fromRawOrSafeDefault(intentCategory).name
        val configuredName = getConfiguredGovernanceName()

        if (configuredName.isBlank() || configuredName.equals("Disable", ignoreCase = true)) {
            return ProviderSelectionResult(
                provider = null,
                providerName = "Disable",
                isAutoMode = false,
                isCapable = true,
                intentCategory = targetCategory
            )
        }

        if (configuredName.equals(AUTO_PROVIDER, ignoreCase = true)) {
            // 1. Prefer capable on-device local model (7B+ or dedicated guard model)
            val capableLocal = providers.values.firstOrNull {
                isLocalProvider(it) && isSafetyReviewCapableProvider(it)
            }
            if (capableLocal != null) {
                return ProviderSelectionResult(
                    provider = capableLocal,
                    providerName = capableLocal.getName(),
                    isAutoMode = true,
                    isCapable = true,
                    intentCategory = targetCategory
                )
            }

            // 2. Next prefer self-hosted private-network model
            val selfHosted = providers.values.firstOrNull {
                isSelfHostedProvider(it) && it.isEnabled() && it.isConfigured()
            }
            if (selfHosted != null) {
                return ProviderSelectionResult(
                    provider = selfHosted,
                    providerName = selfHosted.getName(),
                    isAutoMode = true,
                    isCapable = true,
                    intentCategory = targetCategory
                )
            }

            // 3. Never silently escalate safety review to public Cloud; disable AI review & keep deterministic guardrails
            return ProviderSelectionResult(
                provider = null,
                providerName = "Disable",
                isAutoMode = true,
                isCapable = true,
                intentCategory = targetCategory,
                advisoryMessage = "AI Safety Review is paused because the embedded local model is not powerful enough and no self-hosted private-network model is configured. Deterministic policy guardrails remain active."
            )
        }

        val explicit = providers[configuredName.lowercase()]
        if (explicit == null || !explicit.isConfigured()) {
            return ProviderSelectionResult(
                provider = explicit,
                providerName = configuredName,
                isAutoMode = false,
                isCapable = false,
                intentCategory = targetCategory,
                errorMessage = "Selected Safety Review AI '$configuredName' is not configured yet. Please configure it, select 'Auto', or set to 'Disable' in Settings."
            )
        }

        if (isLocalProvider(explicit) && !isSafetyReviewCapableProvider(explicit)) {
            val modelLabel = effectiveLocalModelName(explicit).ifBlank { "current embedded model" }
            return ProviderSelectionResult(
                provider = explicit,
                providerName = explicit.getName(),
                isAutoMode = false,
                isCapable = false,
                intentCategory = targetCategory,
                errorMessage = "Embedded local model '$modelLabel' is not powerful enough for reliable AI Safety Review (requires a 7B+ desktop local model, a dedicated guard model, or a self-hosted private-network model). Deterministic policy guardrails remain active; set Safety Review AI to 'Disable' or connect a self-hosted model."
            )
        }

        val cloudAdvisory = if (!isLocalProvider(explicit) && !isSelfHostedProvider(explicit)) {
            "Note: Using cloud provider '${explicit.getName()}' for AI Safety Review sends prompts off-device for auditing."
        } else {
            null
        }

        return ProviderSelectionResult(
            provider = explicit,
            providerName = explicit.getName(),
            isAutoMode = false,
            isCapable = true,
            intentCategory = targetCategory,
            advisoryMessage = cloudAdvisory
        )
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
            normType == "self-hosted" || normType == "selfhosted" -> {
                OpenAiLlmProvider().apply { allowNoApiKey = true }
            }
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
        provider.apiKey = (params["Api.Key"] as? String) ?: (params["api.key"] as? String) ?: (params["apiKey"] as? String) ?: (params["API.Key"] as? String)
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
            if (tracker != null) {
                TrackedLlmProvider(
                    delegate = provider,
                    tracker = tracker,
                    onInvocationResult = { name, latencyMs, success ->
                        recordProviderInvocation(name, latencyMs, success)
                    }
                )
            } else {
                provider
            }
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
