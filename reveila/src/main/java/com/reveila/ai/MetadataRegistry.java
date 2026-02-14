package com.reveila.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
public class MetadataRegistry {
    private final Map<String, PluginManifest> plugins = new ConcurrentHashMap<>();

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
     *
     * @param pluginId The plugin ID.
     * @return The manifest, or null if not found.
     */
    public PluginManifest getManifest(String pluginId) {
        return plugins.get(pluginId);
    }

    /**
     * Data record for a plugin's metadata.
     */
    public record PluginManifest(
            String plugin_id,
            String name,
            String version,
            Map<String, Object> tool_definitions,
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
                .map(p -> Map.<String, Object>of(
                        "name", p.name(),
                        "description", "Plugin " + p.id() + " version " + p.version(),
                        "inputSchema", p.toolDefinitions()))
                .collect(java.util.stream.Collectors.toList());

        return Map.of(
                "capabilities", Map.of("tools", Map.of()),
                "tools", mcpTools);
    }
}
