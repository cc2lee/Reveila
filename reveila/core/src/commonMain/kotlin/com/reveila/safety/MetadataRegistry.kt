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
                attrs["tool_definitions"] as Map<String, Any>
            } else {
                emptyMap()
            }

            val tier = (attrs["tier"] as? String) ?: "Tier 3"
            val perimeter = parseAgencyPerimeter(attrs["agency_perimeter"])
            val secrets = parseSet(attrs["secret_parameters"])
            val masked = parseSet(attrs["masked_parameters"])

            PluginManifest(id, name, version, tools, tier, perimeter, secrets, masked)
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
        val masked_parameters: Set<String>
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
    }

    open fun exportToMCP(): Map<String, Any> {
        val mcpTools = plugins.values
            .filter { it.toolDefinitions().isNotEmpty() }
            .map { p ->
                mapOf(
                    "name" to p.name(),
                    "description" to "Plugin ${p.id()} version ${p.version()}",
                    "inputSchema" to p.toolDefinitions()
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
            val fs = PlatformFileSystem()
            val pluginsDir = Constants.CONFIGS_DIR_NAME + "/plugins"
            val files = context?.platformAdapter?.listRelativePaths(pluginsDir, ".json")

            if (files != null && files.isNotEmpty()) {
                for (file in files) {
                    try {
                        val content = fs.readText(file)
                        val attrs = JsonUtil.parseJsonStringToMap(content)
                        val defaultId = file.substringAfterLast('/').substringAfterLast('\\').replace(".json", "")
                        val id = (attrs["plugin_id"] as? String) ?: defaultId
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
