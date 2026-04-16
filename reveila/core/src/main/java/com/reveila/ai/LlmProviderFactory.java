package com.reveila.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.error.SystemException;
import com.reveila.system.Manifest;
import com.reveila.system.PluginContext;
import com.reveila.system.SystemComponent;
import com.reveila.system.SystemProxy;

/**
 * Factory to resolve LLM providers by name.
 * 
 * @author CL
 */
public class LlmProviderFactory extends SystemComponent {

    private final Map<String, LlmProvider> providers = new LinkedHashMap<>(); // Use LinkedHashMap to preserve order
    private int currentProviderIndex = 0;
    private LlmProvider backupProvider = null;
    private LlmProvider activeProvider = null;

    public LlmProviderFactory() {
    }

    public LlmProvider nextProvider() {
        currentProviderIndex = currentProviderIndex + 1;
        if (currentProviderIndex >= providers.size()) {
            currentProviderIndex = 0;
        }
        return getProvider(providers.keySet().toArray(new String[0])[currentProviderIndex]);
    }

    @Override
    public void onStart() throws Exception {
        loadProviders();
    }

    public synchronized void loadProviders() {
        activeProvider = null; 
        providers.clear();
        // Dynamically load LLM providers from llm.json using GenericLlmProvider
        try {
            String jsonConfig = (String) context.getProxy("ConfigurationManager").invoke("getSettings",
                    new Object[] { "llm.json" });
            Map<String, Object> configMap = new ObjectMapper().readValue(jsonConfig,
                    new TypeReference<Map<String, Object>>() {
                    });
            Object onboardedObj = configMap.get("onboarded.providers");
            if (onboardedObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> onboarded = (List<Map<String, Object>>) onboardedObj;
                for (Map<String, Object> p : onboarded) {
                    String name = (String) p.get("name");
                    String endpoint = (String) p.get("endpoint");
                    String model = (String) p.get("model");
                    Double temperature;
                    try {
                        temperature = Double.valueOf((String.valueOf(p.get("temperature"))));
                    } catch (Exception e) {
                        temperature = 0.7;
                    }
                    String quantization = (String) p.get("quantization");
                    String apiKey = (String) p.get("api.key");

                    if (name != null) {
                        Manifest manifest = new Manifest();
                        manifest.setComponentType("plugin");
                        manifest.setName(name);
                        manifest.setImplementationClass(GenericLlmProvider.class.getName());

                        GenericLlmProvider generic = new GenericLlmProvider();
                        generic.setContext(new PluginContext(context, manifest, new Properties()));
                        generic.setName(name);
                        
                        // Fix for Android emulator networking
                        if (endpoint != null && (endpoint.contains("localhost") || endpoint.contains("127.0.0.1"))) {
                            String platform = context.getProperties().getProperty(com.reveila.system.Constants.PLATFORM);
                            if ("android".equalsIgnoreCase(platform) || "mobile".equalsIgnoreCase(platform)) {
                                endpoint = endpoint.replace("localhost", "10.0.2.2").replace("127.0.0.1", "10.0.2.2");
                            }
                        }
                        
                        generic.setEndpoint(endpoint);
                        generic.setModel(model);
                        generic.setTemperature(temperature);
                        generic.setQuantization(quantization);
                        generic.setApiKey(apiKey);

                        try {
                            generic.start();

                            LlmProvider provider;
                            try {
                                SystemProxy sp = (SystemProxy) context.getProxy("UsageTracker");
                                UsageTracker tracker = (UsageTracker) sp.getInstance();
                                provider = new TrackedLlmProvider(generic, tracker);
                            } catch (Exception e) {
                                provider = generic;
                                if (logger != null)
                                    logger.warning("Usage Tracker not found, skipping tracking for " + name);
                            }

                            providers.put(name.toLowerCase(), provider);
                        } catch (Exception e) {
                            logger.warning("Failed to start LLM provider '" + name + "': " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to parse LLM providers from " + "llm.json: " + e.getMessage());
        }
    }

    private LlmProvider instanciateBackupProvider() throws SystemException {
        String name = "Backup LLM Provider";
        Manifest manifest = new Manifest();
        manifest.setComponentType("plugin");
        manifest.setName(name);
        manifest.setImplementationClass(GenericLlmProvider.class.getName());

        /*
         * 
         * "name": "Ollama (Local)",
         * "endpoint": "http://localhost:11434",
         * "model": "Gemma-3-1b",
         * "temperature": 0.7,
         * "quantization": "Q4_K_M",
         * "quantization.options": [
         * "Q4_K_M",
         * "F16"
         * ]
         * 
         */
        GenericLlmProvider fallbackOllama = new GenericLlmProvider();
        fallbackOllama.setContext(new PluginContext(context, manifest, new Properties()));
        fallbackOllama.setName(name);

        String platform = context.getProperties().getProperty(com.reveila.system.Constants.PLATFORM);
        if (platform == null || platform.isBlank()) {
            throw new SystemException("Unknown platform. Please set " + com.reveila.system.Constants.PLATFORM + " property in reveila.properties.");
        }
        if ("android".equalsIgnoreCase(platform) || "mobile".equalsIgnoreCase(platform)
                || "ios".equalsIgnoreCase(platform)) {
            fallbackOllama.setEndpoint("http://10.0.2.2:11434");
            fallbackOllama.setModel("llama3");
            fallbackOllama.setTemperature(0.7);
            fallbackOllama.setQuantization("Q4_K_M");

        } else if ("server".equalsIgnoreCase(platform) || "docker".equalsIgnoreCase(platform)
                || "spring".equalsIgnoreCase(platform)) {
            fallbackOllama.setEndpoint("http://ollama-service:11434");
            fallbackOllama.setModel("llama3");
            fallbackOllama.setTemperature(0.7);
            
        } else {
            fallbackOllama.setEndpoint("http://localhost:11434");
            fallbackOllama.setModel("llama3");
            fallbackOllama.setTemperature(0.7);
            
        }

        try {
            fallbackOllama.start();
            return fallbackOllama;
        } catch (Exception e) {
            throw new SystemException("Failed to start fallback LLM provider.", e);
        }
    }

    @Override
    protected void onStop() throws Exception {
    }

    /**
     * Alias for getBestProvider(). Returns the user chosen active provider.
     * 
     * @return The active LLM provider.
     */
    public LlmProvider getActiveProvider() {
        if (activeProvider == null) {
            activeProvider = getBestProvider();
        }
        return activeProvider;
    }

    private LlmProvider getBestProvider() {
        // 1. Try user selected provider
        String selected = context.getProperties().getProperty("ai.worker.llm");
        if (selected != null && !selected.isBlank()) {
            LlmProvider provider = getProvider(selected);
            if (provider != null && provider.isEnabled() && provider.isConfigured()) {
                return provider;
            } else {
                logger.warning("The selected LLM Provider " + selected + " either does not exist or is not useable.");
            }
        }

        // 2. Fallback to the backup provider
        if (backupProvider != null) {
            return backupProvider;
        } else {
            synchronized (this) {
                if (backupProvider == null) {
                    try {
                        backupProvider = instanciateBackupProvider();
                        return backupProvider;
                    } catch (Exception e) {
                        logger.warning("Failed to instanciate backup LLM provider: " + e.getMessage());
                    }
                }
            }
        }

        // 3. Fallback to any enabled and configured provider
        for (LlmProvider p : providers.values()) {
            if (p.isEnabled() && p.isConfigured()) {
                return p;
            }
        }

        // 4. No providers available
        return null;
    }

    /**
     * Retrieves a provider by its unique slug or name.
     *
     * @param slug The provider slug (e.g., "openai", "gemini", or "My Mistral").
     * @return The provider instance.
     * @throws IllegalArgumentException If the provider is not found.
     */
    public LlmProvider getProvider(String slug) {
        if (slug == null || slug.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider slug cannot be null or blank.");
        }
        String key = slug.toLowerCase();
        return providers.get(key);
    }
}
