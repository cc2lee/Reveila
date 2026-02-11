package com.reveila.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A metadata registry where plugins register their capabilities.
 * Supports Model Context Protocol (MCP) concepts.
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
        AgencyPerimeter defaultPerimeter
    ) {}
}
