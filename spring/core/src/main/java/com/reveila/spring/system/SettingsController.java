package com.reveila.spring.system;

import com.reveila.spring.model.jpa.PluginRegistry;
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
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public SettingsController(Reveila reveila, PluginRegistryRepository pluginRepository) {
        this.reveila = reveila;
        this.pluginRepository = pluginRepository;
    }

    private Path getSettingsDir() {
        String home = reveila.getSystemContext().getProperties().getProperty("system.home");
        return Path.of(home).resolve("configs/settings");
    }

    private Path getMainConfigFile() {
        String home = reveila.getSystemContext().getProperties().getProperty("system.home");
        return Path.of(home).resolve("configs/reveila.properties");
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

        // 3. Trigger Hot Reload in the engine
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
        
        // 1. Persist to Sovereign Ledger (Database)
        PluginRegistry saved = pluginRepository.save(plugin);
        
        // 2. Export manifest to Centralized Repository (Filesystem)
        String repoPath = reveila.getSystemContext().getProperties().getProperty("plugin.repository.path");
        if (repoPath != null) {
            Path dir = Path.of(repoPath);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            
            Path manifestPath = dir.resolve(plugin.getPluginId() + "-manifest.json");
            mapper.writeValue(manifestPath.toFile(), saved);
            System.out.println("Sovereign Registry: Exported manifest to " + manifestPath);
        }

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/plugins/{pluginId}")
    public ResponseEntity<Void> deletePlugin(@PathVariable String pluginId) {
        pluginRepository.deleteById(pluginId);
        return ResponseEntity.ok().build();
    }
}
