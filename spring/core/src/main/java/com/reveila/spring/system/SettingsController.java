package com.reveila.spring.system;

import com.reveila.spring.model.jpa.GlobalSetting;
import com.reveila.spring.model.jpa.PluginRegistry;
import com.reveila.spring.repository.jpa.GlobalSettingRepository;
import com.reveila.spring.repository.jpa.PluginRegistryRepository;
import com.reveila.system.Reveila;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final Reveila reveila;
    private final PluginRegistryRepository pluginRepository;
    private final GlobalSettingRepository globalSettingRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public SettingsController(Reveila reveila, 
                              PluginRegistryRepository pluginRepository,
                              GlobalSettingRepository globalSettingRepository) {
        this.reveila = reveila;
        this.pluginRepository = pluginRepository;
        this.globalSettingRepository = globalSettingRepository;
    }

    private Path getSettingsDir() {
        String home = reveila.getSystemContext().getProperties().getProperty("system.home");
        return Path.of(String.valueOf(home != null ? home : ".")).resolve("configs/settings");
    }

    private Path getMainConfigFile() {
        String home = reveila.getSystemContext().getProperties().getProperty("system.home");
        return Path.of(String.valueOf(home != null ? home : ".")).resolve("configs/reveila.properties");
    }

    public static class SettingDefinition {
        private Object value;
        private boolean editable = true;
        private String type = "string";
        private String description;
        private String propertyKey;

        public SettingDefinition() {}

        public SettingDefinition(Object value, boolean editable, String type) {
            this.value = value;
            this.editable = editable;
            this.type = type;
        }

        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
        public boolean isEditable() { return editable; }
        public void setEditable(boolean editable) { this.editable = editable; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPropertyKey() { return propertyKey; }
        public void setPropertyKey(String propertyKey) { this.propertyKey = propertyKey; }
    }

    @GetMapping
    public ResponseEntity<List<String>> listTabs() throws IOException {
        Path dir = getSettingsDir();
        if (!Files.exists(dir)) return ResponseEntity.ok(Collections.emptyList());
        
        List<String> tabs = Files.list(dir)
                .filter(p -> p.toString().endsWith(".properties") || p.toString().endsWith(".json"))
                .map(p -> p.getFileName().toString().replaceAll("\\.(properties|json)$", ""))
                .distinct()
                .collect(Collectors.toList());
        return ResponseEntity.ok(tabs);
    }

    @GetMapping("/{tab}")
    public ResponseEntity<Map<String, SettingDefinition>> getSettings(@PathVariable String tab) throws IOException {
        Path propFile = getSettingsDir().resolve(tab + ".properties");
        Path jsonFile = getSettingsDir().resolve(tab + ".json");

        if (!Files.exists(propFile) && !Files.exists(jsonFile)) {
            return ResponseEntity.notFound().build();
        }

        Map<String, SettingDefinition> map = new HashMap<>();

        if (Files.exists(propFile)) {
            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(propFile)) {
                props.load(is);
            }
            props.forEach((k, v) -> map.put(k.toString(), new SettingDefinition(v.toString(), true, "string")));
        }

        if (Files.exists(jsonFile)) {
            com.fasterxml.jackson.core.type.TypeReference<Map<String, SettingDefinition>> typeRef = 
                new com.fasterxml.jackson.core.type.TypeReference<>() {};
            Map<String, SettingDefinition> jsonSettings = mapper.readValue(jsonFile.toFile(), typeRef);
            map.putAll(jsonSettings);
        }
        
        return ResponseEntity.ok(map);
    }

    @PostMapping("/{tab}")
    public ResponseEntity<Void> saveSettings(@PathVariable String tab, @RequestBody Map<String, Object> updates) throws Exception {
        Path propFile = getSettingsDir().resolve(tab + ".properties");
        Path jsonFile = getSettingsDir().resolve(tab + ".json");
        Path mainFile = getMainConfigFile();

        boolean isJson = Files.exists(jsonFile);
        
        // 1. Update the tab-specific file
        Map<String, String> flattenedUpdates = new HashMap<>();
        
        if (isJson) {
            com.fasterxml.jackson.core.type.TypeReference<Map<String, SettingDefinition>> typeRef = 
                new com.fasterxml.jackson.core.type.TypeReference<>() {};
            Map<String, SettingDefinition> existingSettings = mapper.readValue(jsonFile.toFile(), typeRef);
            
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                SettingDefinition def = existingSettings.get(entry.getKey());
                if (def == null) {
                    def = new SettingDefinition(entry.getValue(), true, "string");
                    existingSettings.put(entry.getKey(), def);
                } else if (!def.isEditable()) {
                    continue; // Skip read-only fields
                } else {
                    def.setValue(entry.getValue());
                }
                
                // Flatten for Reveila Properties compatibility
                String actualKey = (def.getPropertyKey() != null && !def.getPropertyKey().trim().isEmpty()) 
                                   ? def.getPropertyKey() : entry.getKey();
                                   
                if (def.getValue() instanceof List) {
                    flattenedUpdates.put(actualKey, ((List<?>) def.getValue()).stream().map(Object::toString).collect(Collectors.joining(",")));
                } else {
                    flattenedUpdates.put(actualKey, String.valueOf(def.getValue()));
                }
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile.toFile(), existingSettings);
        } else {
            Properties tabProps = new Properties();
            if (Files.exists(propFile)) {
                try (InputStream is = Files.newInputStream(propFile)) {
                    tabProps.load(is);
                }
            }
            updates.forEach((k, v) -> {
                String valStr = String.valueOf(v);
                tabProps.put(k, valStr);
                flattenedUpdates.put(k, valStr);
            });
            try (OutputStream os = Files.newOutputStream(propFile)) {
                tabProps.store(os, "Updated via Dashboard Settings");
            }
        }

        // 2. Merge into the main reveila.properties
        Properties mainProps = new Properties();
        if (Files.exists(mainFile)) {
            try (InputStream is = Files.newInputStream(mainFile)) {
                mainProps.load(is);
            }
        }
        mainProps.putAll(flattenedUpdates);
        try (OutputStream os = Files.newOutputStream(mainFile)) {
            mainProps.store(os, "Merged from Settings tab: " + tab);
        }

        // 3. Update the Global Settings table (Triggers Postgres Pulse)
        flattenedUpdates.forEach((key, value) -> {
            GlobalSetting setting = new GlobalSetting(key, value);
            globalSettingRepository.save(setting);
        });

        // 4. Trigger Hot Reload in the engine locally
        reveila.getSystemContext().getPlatformAdapter().reloadProperties();

        return ResponseEntity.ok().build();
    }

    @GetMapping("/plugins")
    public ResponseEntity<List<PluginRegistry>> listPlugins() {
        return ResponseEntity.ok(pluginRepository.findAll());
    }

    public static class PluginRegistrationRequest {
        private PluginRegistry metadata;
        private Map<String, Object> manifest;

        public PluginRegistry getMetadata() { return metadata; }
        public void setMetadata(PluginRegistry metadata) { this.metadata = metadata; }
        public Map<String, Object> getManifest() { return manifest; }
        public void setManifest(Map<String, Object> manifest) { this.manifest = manifest; }
    }

    @PostMapping("/plugins")
    public ResponseEntity<PluginRegistry> registerPlugin(@RequestBody PluginRegistrationRequest request) throws IOException {
        PluginRegistry plugin = request.getMetadata();
        if (plugin.getStatus() == null) plugin.setStatus("ACTIVE");
        
        String home = reveila.getSystemContext().getProperties().getProperty("system.home");
        Path systemHome = Path.of(home != null ? home : ".");

        // Merge the perimeter settings if manifest is provided
        Map<String, Object> manifest = request.getManifest();
        if (manifest != null) {
            Path configPath = systemHome.resolve("configs/global-perimeter.json");
            com.fasterxml.jackson.databind.JsonNode perimeterNode = com.reveila.system.PerimeterEnforcementMerger.getAgencyPerimeterNode(configPath);
            
            com.fasterxml.jackson.databind.node.ObjectNode root = mapper.valueToTree(manifest);
            if (perimeterNode != null) {
                if (root.has("plugin") && root.get("plugin").isObject()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) root.get("plugin")).set("agency_perimeter", perimeterNode);
                } else {
                    root.set("agency_perimeter", perimeterNode);
                }
            }
            
            // Save securely merged JSON to active configs/plugins directory
            Path configsPluginsDir = systemHome.resolve("configs/plugins");
            if (!Files.exists(configsPluginsDir)) Files.createDirectories(configsPluginsDir);
            
            Path activeManifestPath = configsPluginsDir.resolve(plugin.getPluginId() + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(activeManifestPath.toFile(), root);
            System.out.println("Saved active plugin manifest (with enforced perimeters) to " + activeManifestPath);
        }

        // 1. Persist to Sovereign Ledger (Database) - triggers Pulse for plugins
        PluginRegistry saved = pluginRepository.save(plugin);
        
        // 2. Export manifest to Centralized Repository (Filesystem)
        String repoPath = reveila.getSystemContext().getProperties().getProperty("plugin.repository.path");
        if (repoPath != null) {
            Path dir = Path.of(String.valueOf(repoPath));
            if (!Files.exists(dir)) Files.createDirectories(dir);
            
            Path manifestPath = dir.resolve(String.valueOf(plugin.getPluginId()) + "-manifest.json");
            mapper.writeValue(manifestPath.toFile(), saved);
            System.out.println("Sovereign Registry: Exported DB metadata to " + manifestPath);
        }

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/plugins/{pluginId}")
    public ResponseEntity<Void> deletePlugin(@PathVariable String pluginId) {
        if (pluginId != null) pluginRepository.deleteById(pluginId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/ui/text")
    public ResponseEntity<Map<String, String>> getUiText(@RequestParam(defaultValue = "en") String lang) throws IOException {
        String home = reveila.getSystemContext().getProperties().getProperty("system.home");
        if (home == null) {
            home = ".";
        }
        Path file = Path.of(String.valueOf(home)).resolve("resources/ui/" + lang + "/text." + lang + ".properties");
        
        if (!Files.exists(file)) {
            file = Path.of(String.valueOf(home)).resolve("resources/ui/en/text.en.properties");
        }

        if (!Files.exists(file)) return ResponseEntity.notFound().build();

        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(file)) {
            props.load(is);
        }

        Map<String, String> map = new HashMap<>();
        props.forEach((k, v) -> map.put(k.toString(), v.toString()));
        return ResponseEntity.ok(map);
    }
}
