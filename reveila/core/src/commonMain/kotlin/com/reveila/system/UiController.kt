package com.reveila.system

import com.reveila.error.ConfigurationException
import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil

open class UiController : SystemComponent() {

    @Throws(ConfigurationException::class)
    open fun getSettings(tab: String): String {
        val fs = PlatformFileSystem()
        val home = context?.properties?.getProperty("system.home") ?: "."

        // Prevent path traversal
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
    open fun saveSettings(tab: String, jsonConfig: String) {
        val fs = PlatformFileSystem()
        var mutableJsonConfig = jsonConfig
        val home = context?.properties?.getProperty("system.home") ?: "."

        // Prevent path traversal
        if (tab.contains("..") || tab.contains("/") || tab.contains("\\")) {
            throw IllegalArgumentException("Invalid tab name")
        }

        val settingsDir = fs.resolve(home, "configs/settings")
        if (!fs.exists(settingsDir)) {
            fs.createDirectories(settingsDir)
        }

        val configMap: MutableMap<String, Any?> = JsonUtil.parseJsonStringToMap(mutableJsonConfig).toMutableMap()
        var modifiedConfig = false

        if ("llm.json" == tab) {
            val onboardedObj = configMap["onboarded.providers"]
            if (onboardedObj is List<*>) {
                @Suppress("UNCHECKED_CAST")
                val providers = onboardedObj as List<MutableMap<String, Any?>>
                for (p in providers) {
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

        val tabPath = fs.resolve(settingsDir, tab)
        fs.writeText(tabPath, mutableJsonConfig, append = false)

        // Merge into main properties
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

        // Reload properties
        context?.platformAdapter?.loadProperties(null)

        if ("llm.json" == tab) {
            try {
                val factory = context?.getProxy("LlmProviderFactory")
                factory?.invoke("loadProviders", null)
                logger.info("LLM Providers factory reloaded successfully.")
            } catch (e: Exception) {
                logger.warning("Failed to reload LlmProviderFactory: ${e.message}")
            }
        }

        logger.info("Saved and reloaded settings for tab: $tab")
    }

    open fun getUiSchema(): String {
        val fs = PlatformFileSystem()
        val home = context?.properties?.getProperty("system.home") ?: "."
        val uiSchemaPath = fs.resolve(home, "configs/ui-schema.json")
        if (!fs.exists(uiSchemaPath)) {
            return "{}"
        }
        return fs.readText(uiSchemaPath)
    }

    @Throws(Exception::class)
    override fun onStart() {
        logger.info("UiController started.")
    }

    @Throws(Exception::class)
    override fun onStop() {
    }
}
