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

    @GetMapping
    public ResponseEntity<List<String>> listTabs() throws IOException {
        Path dir = getSettingsDir();
        if (!Files.exists(dir)) return ResponseEntity.ok(Collections.emptyList());
        
        List<String> tabs = Files.list(dir)
                .filter(p -> p.toString().endsWith(".properties"))
                .map(p -> p.getFileName().toString().replace(".properties", ""))
                .collect(Collectors.toList());
        return ResponseEntity.ok(tabs);
    }

    @GetMapping("/{tab}")
    public ResponseEntity<Map<String, String>> getSettings(@PathVariable String tab) throws IOException {
        Path file = getSettingsDir().resolve(tab + ".properties");
        if (!Files.exists(file)) return ResponseEntity.notFound().build();

        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(file)) {
            props.load(is);
        }
        
        Map<String, String> map = new HashMap<>();
        props.forEach((k, v) -> map.put(k.toString(), v.toString()));
        return ResponseEntity.ok(map);
    }

    @PostMapping("/{tab}")
    public ResponseEntity<Void> saveSettings(@PathVariable String tab, @RequestBody Map<String, String> updates) throws Exception {
        Path tabFile = getSettingsDir().resolve(tab + ".properties");
        Path mainFile = getMainConfigFile();

        // 1. Update the tab-specific file
        Properties tabProps = new Properties();
        if (Files.exists(tabFile)) {
            try (InputStream is = Files.newInputStream(tabFile)) {
                tabProps.load(is);
            }
        }
        tabProps.putAll(updates);
        try (OutputStream os = Files.newOutputStream(tabFile)) {
            tabProps.store(os, "Updated via Dashboard Settings");
        }

        // 2. Merge into the main reveila.properties
        Properties mainProps = new Properties();
        if (Files.exists(mainFile)) {
            try (InputStream is = Files.newInputStream(mainFile)) {
                mainProps.load(is);
            }
        }
        mainProps.putAll(updates);
        try (OutputStream os = Files.newOutputStream(mainFile)) {
            mainProps.store(os, "Merged from Settings tab: " + tab);
        }

        // 3. Update the Global Settings table (Triggers Postgres Pulse)
        updates.forEach((key, value) -> {
            GlobalSetting setting = new GlobalSetting(key, value);
            globalSettingRepository.save(setting);
        });

        // 4. Trigger Hot Reload in the engine locally
        // (Other nodes will be triggered via ClusterSyncService notification)
        reveila.getSystemContext().getPlatformAdapter().reloadProperties();

        return ResponseEntity.ok().build();
    }

    @GetMapping("/plugins")
    public ResponseEntity<List<PluginRegistry>> listPlugins() {
        return ResponseEntity.ok(pluginRepository.findAll());
    }

    @PostMapping("/plugins")
    public ResponseEntity<PluginRegistry> registerPlugin(@RequestBody PluginRegistry plugin) throws IOException {
        if (plugin.getStatus() == null) plugin.setStatus("ACTIVE");
        
        // 1. Persist to Sovereign Ledger (Database) - triggers Pulse for plugins
        PluginRegistry saved = pluginRepository.save(plugin);
        
        // 2. Export manifest to Centralized Repository (Filesystem)
        String repoPath = reveila.getSystemContext().getProperties().getProperty("plugin.repository.path");
        if (repoPath != null) {
            Path dir = Path.of(String.valueOf(repoPath));
            if (!Files.exists(dir)) Files.createDirectories(dir);
            
            Path manifestPath = dir.resolve(String.valueOf(plugin.getPluginId()) + "-manifest.json");
            mapper.writeValue(manifestPath.toFile(), saved);
            System.out.println("Sovereign Registry: Exported manifest to " + manifestPath);
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
