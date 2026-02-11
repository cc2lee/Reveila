package com.reveila.spring.model;

import java.util.List;
import java.util.Map;

/**
 * DTO representing a plugin's manifest following MCP-like structure.
 */
public record PluginManifest(
    String id,
    String name,
    String version,
    String description,
    List<ToolDefinition> tools
) {
    public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema
    ) {}
}
