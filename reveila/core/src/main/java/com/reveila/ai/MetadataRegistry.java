package com.reveila.ai;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.data.Entity;
import com.reveila.data.Repository;
import com.reveila.system.Constants;
import com.reveila.system.SystemComponent;

/**
 * A metadata registry where plugins register their capabilities. The registry
 * is built to support the Model Context Protocol (MCP), which is
 * essential for scaling agentic capabilities.
 * 
 * Plugin Manifests: The PluginManifest record includes toolDefinitions,
 * providing the JSON schema required for LLMs to understand how to invoke
 * specific plugins.
 * 
 * Default Guardrails: By including a defaultPerimeter within the manifest, the
 * registry ensures that security constraints are part of the plugin's core
 * registration contract.
 * 
 * @author CL
 */
public class MetadataRegistry extends SystemComponent {
    
    private final Map<String, PluginManifest> plugins = new ConcurrentHashMap<>();
    private Repository<Entity, Map<String, Map<String, Object>>> agentRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Registers a plugin manifest.
     *
     * @param manifest The plugin manifest to register.
     */
    public void register(PluginManifest manifest) {
        plugins.put(manifest.id(), manifest);
    }

    /**
     * Retrieves a plugin manifest by ID.
     * Checks L1 (Memory Cache), then L3 (Database Repository).
     * L2 (File Configs) are loaded into Memory Cache during startup.
     *
     * @param pluginId The plugin ID.
     * @return The manifest, or null if not found.
     */
    public PluginManifest getManifest(String pluginId) {
        // 1. Check L1 Cache
        PluginManifest cached = plugins.get(pluginId);
        if (cached != null) {
            return cached;
        }

        // 2. Check L3 Database Repository
        if (agentRepository != null) {
            try {
                Map<String, Map<String, Object>> key = Map.of("plugin_id", Map.of("value", pluginId));
                Optional<Entity> opt = agentRepository.fetchById(key);
                if (opt.isPresent()) {
                    Entity entity = opt.get();
                    Map<String, Object> attrs = entity.getAttributes();
                    PluginManifest dbManifest = mapAttributesToManifest(pluginId, attrs);
                    // Cache it for future
                    if (dbManifest != null) {
                        plugins.put(pluginId, dbManifest);
                        return dbManifest;
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to fetch agent manifest from repository: " + e.getMessage());
            }
        }

        return null;
    }

    private PluginManifest mapAttributesToManifest(String id, Map<String, Object> attrs) {
        try {
            String name = (String) attrs.getOrDefault("name", id);
            String version = (String) attrs.getOrDefault("version", "1.0");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> tools = attrs.containsKey("tool_definitions") ? 
                (Map<String, Object>) attrs.get("tool_definitions") : new HashMap<>();
            
            String tier = (String) attrs.getOrDefault("tier", "Tier 3");
            
            AgencyPerimeter perimeter = parseAgencyPerimeter(attrs.get("agency_perimeter"));
            
            Set<String> secrets = parseSet(attrs.get("secret_parameters"));
            Set<String> masked = parseSet(attrs.get("masked_parameters"));
            
            return new PluginManifest(id, name, version, tools, tier, perimeter, secrets, masked);
        } catch (Exception e) {
            logger.warning("Invalid manifest attributes for " + id + ": " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseSet(Object obj) {
        if (obj instanceof List) {
            return new HashSet<>((List<String>) obj);
        }
        return new HashSet<>();
    }

    @SuppressWarnings("unchecked")
    private AgencyPerimeter parseAgencyPerimeter(Object obj) {
        if (obj instanceof Map) {
            Map<String, Object> pMap = (Map<String, Object>) obj;
            Set<String> accessScopes = parseSet(pMap.get("accessScopes"));
            Set<String> allowedDomains = parseSet(pMap.get("allowedDomains"));
            boolean internetBlocked = Boolean.TRUE.equals(pMap.get("internetAccessBlocked"));
            long mem = pMap.containsKey("maxMemoryMb") ? ((Number) pMap.get("maxMemoryMb")).longValue() : 512L;
            int cpu = pMap.containsKey("maxCpuCores") ? ((Number) pMap.get("maxCpuCores")).intValue() : 1;
            int exec = pMap.containsKey("maxExecutionSec") ? ((Number) pMap.get("maxExecutionSec")).intValue() : 30;
            boolean delegation = Boolean.TRUE.equals(pMap.get("delegationAllowed"));
            return new AgencyPerimeter(accessScopes, allowedDomains, internetBlocked, mem, cpu, exec, delegation);
        }
        return new AgencyPerimeter(Collections.emptySet(), Collections.emptySet(), true, 128L, 1, 5, false);
    }

    /**
     * Data record for a plugin's metadata.
     */
    public record PluginManifest(
            String plugin_id,
            String name,
            String version,
            Map<String, Object> tool_definitions,
            String tier,
            AgencyPerimeter agency_perimeter,
            java.util.Set<String> secret_parameters,
            java.util.Set<String> masked_parameters) {
        
        // Helper to match old usage if needed or bridge to new snake_case
        public String id() { return plugin_id; }
        public Map<String, Object> toolDefinitions() { return tool_definitions; }
        public AgencyPerimeter defaultPerimeter() { return agency_perimeter; }
        public java.util.Set<String> secretParameters() { return secret_parameters; }
        public java.util.Set<String> maskedParameters() { return masked_parameters; }
        public java.util.Set<String> hitlRequiredIntents() {
            // Standardizing HITL intents to be part of tool_definitions or perimeter
            return java.util.Set.of();
        }
    }

    /**
     * Exports the registry content to a Model Context Protocol (MCP) compliant
     * format.
     *
     * @return A map representing the MCP server capabilities and tools.
     */
    public Map<String, Object> exportToMCP() {
        java.util.List<Map<String, Object>> mcpTools = plugins.values().stream()
                .filter(p -> p.toolDefinitions() != null && !p.toolDefinitions().isEmpty())
                .map(p -> Map.<String, Object>of(
                        "name", p.name(),
                        "description", "Plugin " + p.id() + " version " + p.version(),
                        "inputSchema", p.toolDefinitions()))
                .collect(java.util.stream.Collectors.toList());

        return Map.of(
                "capabilities", Map.of("tools", Map.of()),
                "tools", mcpTools);
    }

    @Override
    protected void onStop() throws Exception {
        plugins.clear();
    }

    @Override
    protected void onStart() throws Exception {
        // 1. Connect to Database Repository
        this.agentRepository = context.getPlatformAdapter().getRepository("agent_manifest");

        // 2. Bootstrap Core Agents
        registerCoreAgents();

        // 3. Discover Plugin Manifests
        discoverPlugins();
        
        logger.info("MetadataRegistry initialized. Loaded " + plugins.size() + " capability manifests.");
    }

    private void registerCoreAgents() {
        // Bootstrap 'ui-client' which is the default caller for UI interactions
        AgencyPerimeter uiClientPerimeter = new AgencyPerimeter(
            Collections.emptySet(), // accessScopes
            Collections.emptySet(), // allowedDomains
            false, // internetAccessBlocked
            1024L, // maxMemoryMb
            2, // maxCpuCores
            60, // maxExecutionSec
            true // delegationAllowed
        );
        
        PluginManifest uiClient = new PluginManifest(
            "ui-client",
            "Reveila UI Client",
            "1.0",
            new HashMap<>(), // No specific tools exposed by the UI itself
            "Tier 3", // UI client is a standard user tier
            uiClientPerimeter,
            Collections.emptySet(),
            Collections.emptySet()
        );
        register(uiClient);
    }

    private void discoverPlugins() {
        try {
            String pluginsDir = Constants.CONFIGS_DIR_NAME + File.separator + "plugins";
            String[] files = context.getPlatformAdapter().listRelativePaths(pluginsDir, ".json");
            
            if (files != null && files.length > 0) {
                for (String file : files) {
                    try (InputStream is = context.getPlatformAdapter().getFileInputStream(file)) {
                        Map<String, Object> attrs = mapper.readValue(is, new TypeReference<Map<String, Object>>() {});
                        String id = (String) attrs.getOrDefault("plugin_id", new File(file).getName().replace(".json", ""));
                        PluginManifest manifest = mapAttributesToManifest(id, attrs);
                        if (manifest != null) {
                            register(manifest);
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to load plugin profile from " + file + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // It's okay if the directory doesn't exist
            logger.info("No file-based plugin profiles discovered.");
        }
    }
}
