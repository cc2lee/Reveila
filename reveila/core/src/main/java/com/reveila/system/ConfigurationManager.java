package com.reveila.system;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigurationManager extends SystemComponent {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void onStart() throws Exception {
        logger.info("ConfigurationManager started.");
    }

    @Override
    protected void onStop() throws Exception {
    }

    public String getSettings(String tab) throws Exception {
        String home = context.getProperties().getProperty("system.home");
        if (home == null) home = ".";
        
        Path tabFile = Paths.get(home).resolve("configs/settings").resolve(tab);
        if (!Files.exists(tabFile)) {
            return "{}";
        }
        return Files.readString(tabFile);
    }

    public void saveSettings(String tab, String jsonConfig) throws Exception {
        String home = context.getProperties().getProperty("system.home");
        if (home == null) home = ".";
        
        Path settingsDir = Paths.get(home).resolve("configs/settings");
        if (!Files.exists(settingsDir)) {
            Files.createDirectories(settingsDir);
        }

        Path tabFile = settingsDir.resolve(tab);
        
        // Write the raw JSON to the file
        try (OutputStream os = new FileOutputStream(tabFile.toFile())) {
            os.write(jsonConfig.getBytes());
        }
        
        // Merge into main properties
        Map<String, Object> configMap = mapper.readValue(jsonConfig, new TypeReference<Map<String, Object>>() {});
        Path mainFile = Paths.get(home).resolve("configs/reveila.properties");
        
        Properties mainProps = new Properties();
        if (Files.exists(mainFile)) {
            try (InputStream is = Files.newInputStream(mainFile)) {
                mainProps.load(is);
            }
        }
        
        for (Map.Entry<String, Object> entry : configMap.entrySet()) {
            mainProps.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        
        try (OutputStream os = new FileOutputStream(mainFile.toFile())) {
            mainProps.store(os, "Merged from Settings tab: " + tab);
        }

        // Reload properties
        context.getPlatformAdapter().reloadProperties();
        logger.info("Saved and reloaded settings for tab: " + tab);
    }
}
