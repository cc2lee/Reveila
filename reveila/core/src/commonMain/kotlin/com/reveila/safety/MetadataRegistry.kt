package com.reveila.safety

import com.reveila.data.Entity
import com.reveila.data.Repository
import com.reveila.system.Constants
import com.reveila.system.SystemComponent
import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil

/**
 * A metadata registry where plugins register their capabilities.
 * Built to support the Model Context Protocol (MCP).
 */
open class MetadataRegistry : SystemComponent() {

    private val plugins: MutableMap<String, PluginManifest> = mutableMapOf()
    private var agentRepository: Repository<Entity, Map<String, Map<String, Any>>>? = null

    open fun register(manifest: PluginManifest) {
        val existing = plugins[manifest.id()]
        if (existing != null && manifest.tool_definitions.isEmpty() && manifest.exposeAsTool == null &&
            (existing.tool_definitions.isNotEmpty() || existing.exposeAsTool != null ||
                existing.routingKeywords.isNotEmpty() || existing.allowedModels.isNotEmpty() ||
                existing.cloudOnly || existing.minParameterSizeB != null || existing.methodsSpec.isNotEmpty())
        ) {
            // Preserve JSON-configured AI tool exposure/routing settings when runtime services register themselves.
            plugins[manifest.id()] = manifest.copy(
                tool_definitions = existing.tool_definitions,
                tier = if (manifest.tier == "Tier 3" && existing.tier != "Tier 3") existing.tier else manifest.tier,
                description = manifest.description.ifBlank { existing.description },
                exposeAsTool = existing.exposeAsTool,
                routingKeywords = existing.routingKeywords,
                allowedModels = existing.allowedModels,
                cloudOnly = existing.cloudOnly,
                minParameterSizeB = existing.minParameterSizeB,
                methodsSpec = if (manifest.methodsSpec.isNotEmpty()) manifest.methodsSpec else existing.methodsSpec
            )
            return
        }
        plugins[manifest.id()] = manifest
    }

    open fun getManifest(pluginId: String): PluginManifest? {
        val cached = plugins[pluginId]
        if (cached != null) return cached

        val repo = agentRepository
        if (repo != null) {
            try {
                val key = mapOf("plugin_id" to mapOf<String, Any>("value" to pluginId))
                val opt = repo.fetchById(key)
                if (opt.isPresent) {
                    val entity = opt.get()
                    val attrs = entity.attributes
                    val dbManifest = mapAttributesToManifest(pluginId, attrs)
                    if (dbManifest != null) {
                        plugins[pluginId] = dbManifest
                        return dbManifest
                    }
                }
            } catch (e: Exception) {
                logger.warning("Failed to fetch agent manifest from repository: ${e.message}")
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapAttributesToManifest(id: String, attrs: Map<String, Any>): PluginManifest? {
        return try {
            val name = (attrs["name"] as? String) ?: id
            val version = (attrs["version"] as? String) ?: "1.0"

            val tools = if (attrs.containsKey("tool_definitions")) {
                (attrs["tool_definitions"] as? Map<String, Any>) ?: emptyMap()
            } else {
                emptyMap()
            }

            val tier = (attrs["tier"] as? String) ?: "Tier 3"
            val perimeter = parseAgencyPerimeter(attrs["agency_perimeter"])
            val secrets = parseSet(attrs["secret_parameters"])
            val masked = parseSet(attrs["masked_parameters"])
            val description = ((tools["description"] as? String)?.takeIf { it.isNotBlank() }
                ?: (attrs["description"] as? String))
                .orEmpty()
            val exposeFlag = when (val rawExpose = attrs["expose_as_tool"] ?: attrs["ai_tool"] ?: attrs["tool_enabled"]) {
                is Boolean -> rawExpose
                is String -> rawExpose.trim().lowercase().let {
                    when (it) {
                        "true", "yes", "1" -> true
                        "false", "no", "0" -> false
                        else -> null
                    }
                }
                else -> null
            }
            val routingKeywords = parseSet(attrs["routing_keywords"] ?: tools["routing_keywords"]).map { it.lowercase() }.toSet()
            val allowedModels = parseSet(attrs["allowed_models"] ?: tools["allowed_models"]).map { it.lowercase() }.toSet()
            val cloudOnly = (attrs["cloud_only"] as? Boolean) ?: (tools["cloud_only"] as? Boolean) ?: false
            val minParamB = ((attrs["min_parameter_size_b"] ?: tools["min_parameter_size_b"]) as? Number)?.toDouble()
            val methodsSpec = (attrs["methods"] as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()

            PluginManifest(
                plugin_id = id,
                manifestName = name,
                manifestVersion = version,
                tool_definitions = tools,
                tier = tier,
                agency_perimeter = perimeter,
                secret_parameters = secrets,
                masked_parameters = masked,
                description = description,
                exposeAsTool = exposeFlag,
                routingKeywords = routingKeywords,
                allowedModels = allowedModels,
                cloudOnly = cloudOnly,
                minParameterSizeB = minParamB,
                methodsSpec = methodsSpec
            )
        } catch (e: Exception) {
            logger.warning("Invalid manifest attributes for $id: ${e.message}")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSet(obj: Any?): Set<String> {
        return if (obj is List<*>) {
            (obj as List<String>).toSet()
        } else {
            emptySet()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAgencyPerimeter(obj: Any?): SecurityPerimeter {
        if (obj is Map<*, *>) {
            val pMap = obj as Map<String, Any?>
            val accessScopes = parseSet(pMap["accessScopes"])
            val allowedDomains = parseSet(pMap["allowedDomains"])
            val internetBlocked = pMap["internetAccessBlocked"] == true
            val mem = (pMap["maxMemoryMb"] as? Number)?.toLong() ?: 512L
            val cpu = (pMap["maxCpuCores"] as? Number)?.toInt() ?: 1
            val exec = (pMap["maxExecutionSec"] as? Number)?.toInt() ?: 30
            val delegation = pMap["delegationAllowed"] == true
            return SecurityPerimeter(accessScopes, allowedDomains, internetBlocked, mem, cpu, exec, delegation)
        }
        return SecurityPerimeter(emptySet(), emptySet(), true, 128L, 1, 5, false)
    }

    data class PluginManifest(
        val plugin_id: String,
        val manifestName: String,
        val manifestVersion: String,
        val tool_definitions: Map<String, Any>,
        val tier: String,
        val agency_perimeter: SecurityPerimeter,
        val secret_parameters: Set<String>,
        val masked_parameters: Set<String>,
        val description: String = "",
        val exposeAsTool: Boolean? = null,
        val routingKeywords: Set<String> = emptySet(),
        val allowedModels: Set<String> = emptySet(),
        val cloudOnly: Boolean = false,
        val minParameterSizeB: Double? = null,
        val methodsSpec: List<Map<String, Any?>> = emptyList()
    ) {
        fun id(): String = plugin_id
        fun plugin_id(): String = plugin_id
        fun name(): String = manifestName
        fun version(): String = manifestVersion
        fun tier(): String = tier
        fun toolDefinitions(): Map<String, Any> = tool_definitions
        fun tool_definitions(): Map<String, Any> = tool_definitions
        fun defaultPerimeter(): SecurityPerimeter = agency_perimeter
        fun agency_perimeter(): SecurityPerimeter = agency_perimeter
        fun secretParameters(): Set<String> = secret_parameters
        fun secret_parameters(): Set<String> = secret_parameters
        fun maskedParameters(): Set<String> = masked_parameters
        fun masked_parameters(): Set<String> = masked_parameters
        fun hitlRequiredIntents(): Set<String> = emptySet()

        /**
         * Determines whether this plugin should be exposed to LLMs as a callable tool.
         * Explicit `expose_as_tool` overrides implicit presence of `tool_definitions`.
         */
        fun isToolExposed(): Boolean = when (exposeAsTool) {
            false -> false
            true -> tool_definitions.isNotEmpty() || methodsSpec.isNotEmpty() || description.isNotBlank()
            null -> tool_definitions.isNotEmpty()
        }

        @Suppress("UNCHECKED_CAST")
        fun resolvedInputSchema(): Map<String, Any?> {
            val nested = (tool_definitions["inputSchema"] as? Map<*, *>)
                ?: (tool_definitions["parameters"] as? Map<*, *>)
            if (nested != null) {
                return nested.entries.associate { (k, v) -> k.toString() to v }
            }
            if (tool_definitions.containsKey("properties") || tool_definitions.containsKey("type")) {
                return tool_definitions
            }
            if (methodsSpec.isEmpty()) {
                return tool_definitions
            }
            val props = linkedMapOf<String, Any?>()
            val methodNames = mutableListOf<String>()
            for (m in methodsSpec) {
                val mName = m["name"]?.toString()?.trim().orEmpty()
                if (mName.isNotEmpty()) methodNames.add(mName)
                val params = m["parameters"] as? List<*> ?: continue
                for (p in params) {
                    val pMap = p as? Map<*, *> ?: continue
                    val pName = pMap["name"]?.toString()?.trim().orEmpty()
                    if (pName.isEmpty() || props.containsKey(pName)) continue
                    val pType = mapJavaTypeToJsonSchemaType(pMap["type"]?.toString())
                    val pDesc = pMap["description"]?.toString()?.trim().orEmpty()
                    props[pName] = buildMap {
                        put("type", pType)
                        if (pDesc.isNotEmpty()) put("description", pDesc)
                    }
                }
            }
            if (methodNames.isNotEmpty()) {
                props["method"] = mapOf(
                    "type" to "string",
                    "enum" to methodNames,
                    "description" to "Target method name on $plugin_id"
                )
            }
            return mapOf(
                "type" to "object",
                "properties" to props
            )
        }

        fun resolvedToolDescription(): String {
            val explicit = (tool_definitions["description"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: description.trim().takeIf { it.isNotEmpty() }
                ?: "Plugin $plugin_id version $manifestVersion"
            if (methodsSpec.isEmpty() || explicit.contains("Methods:", ignoreCase = true)) {
                return explicit
            }
            val methodSummaries = methodsSpec.mapNotNull { m ->
                val mName = m["name"]?.toString()?.trim().orEmpty()
                if (mName.isEmpty()) return@mapNotNull null
                val mDesc = m["description"]?.toString()?.trim().orEmpty()
                val paramNames = (m["parameters"] as? List<*>)?.mapNotNull { p ->
                    (p as? Map<*, *>)?.get("name")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                }.orEmpty()
                val sig = if (paramNames.isEmpty()) "$mName()" else "$mName(${paramNames.joinToString(", ")})"
                if (mDesc.isNotEmpty()) "$sig: $mDesc" else sig
            }
            return if (methodSummaries.isEmpty()) {
                explicit
            } else {
                "$explicit\nMethods: ${methodSummaries.joinToString("; ")}"
            }
        }

        private fun mapJavaTypeToJsonSchemaType(rawType: String?): String = when (rawType?.lowercase()) {
            "int", "integer", "long", "java.lang.integer", "java.lang.long" -> "integer"
            "double", "float", "number", "java.lang.double", "java.lang.float" -> "number"
            "boolean", "bool", "java.lang.boolean" -> "boolean"
            "list", "array", "java.util.list" -> "array"
            "map", "object", "java.util.map" -> "object"
            else -> "string"
        }
    }

    open fun getExposedToolManifests(): List<PluginManifest> = plugins.values.filter { it.isToolExposed() }

    open fun getLocalRoutingKeywords(): Set<String> =
        plugins.values.asSequence()
            .filter { it.isToolExposed() }
            .flatMap { it.routingKeywords.asSequence() }
            .toSet()

    open fun exportToMCP(): Map<String, Any> {
        val mcpTools = getExposedToolManifests()
            .map { p ->
                mapOf(
                    "name" to p.id(),
                    "description" to p.resolvedToolDescription(),
                    "inputSchema" to p.resolvedInputSchema(),
                    "tier" to p.tier(),
                    "cloudOnly" to p.cloudOnly,
                    "allowedModels" to p.allowedModels.toList(),
                    "minParameterSizeB" to p.minParameterSizeB
                )
            }

        return mapOf(
            "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
            "tools" to mcpTools
        )
    }

    @Throws(Exception::class)
    override fun onStop() {
        plugins.clear()
    }

    @Throws(Exception::class)
    @Suppress("UNCHECKED_CAST")
    override fun onStart() {
        val adapter = context?.platformAdapter
        this.agentRepository = adapter?.getRepository("agent_manifest") as? Repository<Entity, Map<String, Map<String, Any>>>
        registerCoreAgents()
        discoverPlugins()
        logger.info("MetadataRegistry initialized. Loaded ${plugins.size} capability manifests.")
    }

    private fun registerCoreAgents() {
        val uiClientPerimeter = SecurityPerimeter(
            emptySet(),
            emptySet(),
            false,
            1024L,
            2,
            60,
            true
        )

        val uiClient = PluginManifest(
            "ui-client",
            "Reveila UI Client",
            "1.0",
            emptyMap(),
            "Tier 3",
            uiClientPerimeter,
            emptySet(),
            emptySet()
        )
        register(uiClient)
    }

    private fun discoverPlugins() {
        try {
            val pluginsDir = Constants.CONFIGS_DIR_NAME + "/plugins"
            val adapter = context?.platformAdapter
            val files = adapter?.listRelativePaths(pluginsDir, ".json")

            if (files != null && files.isNotEmpty()) {
                for (file in files) {
                    try {
                        val content = adapter.readFileString(file)
                        val rawMap = JsonUtil.parseJsonStringToMap(content)
                        @Suppress("UNCHECKED_CAST")
                        val attrs = (rawMap["plugin"] as? Map<String, Any>) ?: rawMap
                        val defaultId = file.substringAfterLast('/').substringAfterLast('\\').replace(".json", "")
                        val id = (attrs["plugin_id"] as? String) ?: (attrs["name"] as? String) ?: defaultId
                        val manifest = mapAttributesToManifest(id, attrs)
                        if (manifest != null) {
                            register(manifest)
                        }
                    } catch (e: Exception) {
                        logger.warning("Failed to load plugin profile from $file: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.info("No file-based plugin profiles discovered.")
        }
    }
}
