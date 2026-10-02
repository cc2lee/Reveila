package com.reveila.system

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.logging.Logger
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.reveila.error.ConfigurationException

open class UiController : SystemComponent() {

    private val mapper: ObjectMapper = ObjectMapper()

    @Throws(ConfigurationException::class, IOException::class)
    open fun getSettings(tab: String): String {
        val home = context?.properties?.getProperty("system.home") ?: "."

        // Prevent path traversal
        if (tab.contains("..") || tab.contains("/") || tab.contains("\\")) {
            throw IllegalArgumentException("Invalid tab name")
        }

        val tabFile = File(home, "configs/settings/$tab")
        var jsonContent = "{}"
        if (tabFile.exists()) {
            jsonContent = tabFile.readText(StandardCharsets.UTF_8)
        }

        if ("llm.json" == tab) {
            val configMap: MutableMap<String, Any?> = mapper.readValue(
                jsonContent,
                object : TypeReference<MutableMap<String, Any?>>() {}
            )
            val workerLlm = context?.properties?.getProperty("ai.worker.llm")
            val govLlm = context?.properties?.getProperty("ai.governance.llm")
            val maxMsgs = context?.properties?.getProperty("ai.session.maxMessages")

            if (!workerLlm.isNullOrBlank()) {
                configMap["ai.worker.llm"] = workerLlm
            }
            if (govLlm != null) {
                configMap["ai.governance.llm"] = govLlm // Empty string is valid for 'Disable'
            }
            if (maxMsgs != null) {
                configMap["ai.session.maxMessages"] = maxMsgs
            }

            return mapper.writeValueAsString(configMap)
        }

        return jsonContent
    }

    @Throws(Exception::class)
    open fun saveSettings(tab: String, jsonConfig: String) {
        var mutableJsonConfig = jsonConfig
        val home = context?.properties?.getProperty("system.home") ?: "."

        // Prevent path traversal
        if (tab.contains("..") || tab.contains("/") || tab.contains("\\")) {
            throw IllegalArgumentException("Invalid tab name")
        }

        val settingsDir = File(home, "configs/settings")
        if (!settingsDir.exists()) {
            settingsDir.mkdirs()
        }

        val configMap: MutableMap<String, Any?> = mapper.readValue(
            mutableJsonConfig,
            object : TypeReference<MutableMap<String, Any?>>() {}
        )
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

                    // Expose specific legacy properties to DI container
                    if (pName != null && pEndpoint != null) {
                        if (pName.startsWith("Gemma") || pName.equals("Ollama", ignoreCase = true)) {
                            configMap["plugin.OnDeviceProvider.apiUrl"] = pEndpoint
                        }
                    }
                }
            }
        }

        if (modifiedConfig) {
            mutableJsonConfig = mapper.writeValueAsString(configMap)
        }

        val tabFile = File(settingsDir, tab)
        tabFile.writeText(mutableJsonConfig, StandardCharsets.UTF_8)

        // Merge into main properties
        val mainFile = File(home, "configs/reveila.properties")
        val mainProps = Properties()
        if (mainFile.exists()) {
            mainFile.inputStream().use { `is` ->
                mainProps.load(`is`)
            }
        }

        for ((key, value) in configMap) {
            mainProps.setProperty(key, value.toString())
        }

        mainFile.outputStream().use { os ->
            mainProps.store(os, "Merged from Settings tab: $tab")
        }

        // Reload properties
        context?.platformAdapter?.loadProperties(null)

        // If llm.json changed, reload the providers factory
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

    @Throws(IOException::class)
    open fun getUiSchema(): String {
        val home = context?.properties?.getProperty("system.home") ?: "."
        val uiSchemaFile = File(home, "configs/ui-schema.json")
        if (!uiSchemaFile.exists()) {
            return "{}" // Return empty JSON if file doesn't exist
        }

        return uiSchemaFile.readText(StandardCharsets.UTF_8)
    }

    @Throws(Exception::class)
    override fun onStart() {
        logger.info("UiController started.")
    }

    @Throws(Exception::class)
    override fun onStop() {
    }
}
