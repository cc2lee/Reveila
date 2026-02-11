package com.reveila.spring.system;

import com.reveila.spring.model.OpenAiTool;
import com.reveila.spring.model.PluginManifest;
import com.reveila.spring.service.MetadataRegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for the Metadata Registry.
 */
@RestController
@RequestMapping("/api/v1/registry")
public class MetadataRegistryController {

    private final MetadataRegistryService registryService;

    public MetadataRegistryController(MetadataRegistryService registryService) {
        this.registryService = registryService;
    }

    @PostMapping("/plugins")
    public void registerPlugin(@RequestBody PluginManifest manifest) {
        registryService.registerPlugin(manifest);
    }

    @GetMapping("/plugins")
    public List<PluginManifest> listPlugins() {
        return registryService.getAllPlugins();
    }

    @GetMapping("/tools/openai")
    public List<OpenAiTool> getOpenAiTools(@RequestParam(required = false) String pluginId) {
        if (pluginId != null) {
            return registryService.getToolsForPlugin(pluginId);
        }
        return registryService.generateOpenAiTools();
    }
}
