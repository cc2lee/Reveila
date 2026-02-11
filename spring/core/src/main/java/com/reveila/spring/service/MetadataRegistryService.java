package com.reveila.spring.service;

import com.reveila.spring.model.OpenAiTool;
import com.reveila.spring.model.PluginManifest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service to manage plugin metadata and generate OpenAI-compatible tool definitions.
 */
@Service
public class MetadataRegistryService {
    private final Map<String, PluginManifest> registry = new ConcurrentHashMap<>();

    public void registerPlugin(PluginManifest manifest) {
        registry.put(manifest.id(), manifest);
    }

    public List<PluginManifest> getAllPlugins() {
        return new ArrayList<>(registry.values());
    }

    public List<OpenAiTool> generateOpenAiTools() {
        return registry.values().stream()
                .flatMap(manifest -> manifest.tools().stream())
                .map(tool -> OpenAiTool.function(
                        tool.name(),
                        tool.description(),
                        tool.inputSchema()
                ))
                .collect(Collectors.toList());
    }

    public List<OpenAiTool> getToolsForPlugin(String pluginId) {
        PluginManifest manifest = registry.get(pluginId);
        if (manifest == null) return new ArrayList<>();

        return manifest.tools().stream()
                .map(tool -> OpenAiTool.function(
                        tool.name(),
                        tool.description(),
                        tool.inputSchema()
                ))
                .collect(Collectors.toList());
    }
}
