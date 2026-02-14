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
            String id,
            String name,
            String version,
            Map<String, Object> toolDefinitions,
            AgencyPerimeter defaultPerimeter,
            java.util.Set<String> hitlRequiredIntents,
            java.util.Set<String> secretParameters,
            java.util.Set<String> maskedParameters) {
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
