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
        
        // Prevent path traversal
        if (tab.contains("..") || tab.contains("/") || tab.contains("\\")) {
            throw new IllegalArgumentException("Invalid tab name");
        }
        
        Path tabFile = Paths.get(home).resolve("configs/settings").resolve(tab);
        String jsonContent = "{}";
        if (Files.exists(tabFile)) {
            jsonContent = new String(Files.readAllBytes(tabFile), java.nio.charset.StandardCharsets.UTF_8);
        }

        if ("llm.json".equals(tab)) {
            Map<String, Object> configMap = mapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {});
            String workerLlm = context.getProperties().getProperty("ai.worker.llm");
            String govLlm = context.getProperties().getProperty("ai.governance.llm");
            
            if (workerLlm != null && !workerLlm.trim().isEmpty()) {
                configMap.put("ai.worker.llm", workerLlm);
            }
            if (govLlm != null) {
                configMap.put("ai.governance.llm", govLlm); // Empty string is valid for 'Disable'
            }
            
            return mapper.writeValueAsString(configMap);
        }
        
        return jsonContent;
    }

    public void saveSettings(String tab, String jsonConfig) throws Exception {
        String home = context.getProperties().getProperty("system.home");
        if (home == null) home = ".";
        
        // Prevent path traversal
        if (tab.contains("..") || tab.contains("/") || tab.contains("\\")) {
            throw new IllegalArgumentException("Invalid tab name");
        }
        
        Path settingsDir = Paths.get(home).resolve("configs/settings");
        if (!Files.exists(settingsDir)) {
            Files.createDirectories(settingsDir);
        }

        Map<String, Object> configMap = mapper.readValue(jsonConfig, new TypeReference<Map<String, Object>>() {});
        boolean modifiedConfig = false;

        if ("llm.json".equals(tab)) {
            Object onboardedObj = configMap.get("onboarded.providers");
            if (onboardedObj instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> providers = (java.util.List<Map<String, Object>>) onboardedObj;
                for (Map<String, Object> p : providers) {
                    String pName = (String) p.get("name");
                    String pKey = (String) p.get("api.key");
                    String pEndpoint = (String) p.get("endpoint");
                    
                    if (pName != null && pKey != null && !pKey.isBlank() && !pKey.startsWith("REF:")) {
                        String sKey = pName.replaceAll("\\s+", "_").toUpperCase() + "_API_KEY";
                        try {
                            context.getProxy("SecretManager").invoke("storeSecret", new Object[] { sKey, pKey });
                            p.put("api.key", "REF:" + sKey);
                            modifiedConfig = true;
                        } catch (Exception e) {
                            logger.warning("Failed to store provider API key in SecretManager: " + e.getMessage());
                        }
                    }
                    
                    // Expose specific legacy properties to DI container
                    if (pName != null && pEndpoint != null) {
                        if (pName.startsWith("Gemma") || pName.equalsIgnoreCase("Ollama")) {
                            configMap.put("plugin.OllamaProvider.apiUrl", pEndpoint);
                        }
                    }
                }
            }
        }

        if (modifiedConfig) {
            jsonConfig = mapper.writeValueAsString(configMap);
        }

        Path tabFile = settingsDir.resolve(tab);
        
        // Write the raw JSON to the file
        try (OutputStream os = new FileOutputStream(tabFile.toFile())) {
            os.write(jsonConfig.getBytes());
        }
        
        // Merge into main properties
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
        
        // If llm.json changed, reload the providers factory
        if ("llm.json".equals(tab)) {
            try {
                Proxy factory = context.getProxy("LlmProviderFactory");
                factory.invoke("loadProviders", null);
                logger.info("LLM Providers factory reloaded successfully.");
            } catch (Exception e) {
                logger.warning("Failed to reload LlmProviderFactory: " + e.getMessage());
            }
        }
        
        logger.info("Saved and reloaded settings for tab: " + tab);
    }
}
