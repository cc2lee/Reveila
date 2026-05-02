package com.reveila.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.system.SystemComponent;
import com.reveila.system.SystemProxy;

public class LlmProviderFactory extends SystemComponent {

    private final Map<String, LlmProvider> providers = new LinkedHashMap<>();
    private LlmProvider activeProvider = null;
    
    public synchronized void setActiveProvider(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("LLMProvider name cannot be null or blank.");
        if (!providers.containsKey(name.toLowerCase())) throw new IllegalArgumentException("No such LLMProvider: " + name);
        this.activeProvider = providers.get(name.toLowerCase());
    }

    @Override
    public void onStart() throws Exception {
        loadProviders();
    }

    public synchronized void loadProviders() {
        providers.clear();
        activeProvider = null;

        try {
            String jsonConfig = (String) context.getProxy("ConfigurationManager")
                    .invoke("getSettings", new Object[] { "llm.json" });
            
            Map<String, Object> configMap = new ObjectMapper().readValue(jsonConfig, 
                    new TypeReference<Map<String, Object>>() {});
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> onboarded = (List<Map<String, Object>>) configMap.get("onboarded.providers");

            for (Map<String, Object> p : onboarded) {
                String type = (String) p.getOrDefault("type", "openai");
                String name = (String) p.get("name");
                BaseLlmProvider provider = createProviderInstance(type);
                configureProvider(provider, p);
                startAndAddProvider(name, provider);
            }
        } catch (Exception e) {
            logger.severe("Critical error loading llm.json: " + e.getMessage());
        }
    }

    private void startAndAddProvider(String name, BaseLlmProvider provider) {
        try {
            provider.start();
            providers.put(name.toLowerCase(), wrapWithTracker(provider));
        } catch (Exception e) {
            logger.warning("Failed to start provider [" + name + "]: " + e.getMessage());
        }
    }

    private BaseLlmProvider createProviderInstance(String type) {
        BaseLlmProvider provider;
        switch (type.toLowerCase()) {
            case "local":  provider = new LocalLlamaProvider();
                break;
            case "gemini": provider = new GeminiLlmProvider();
                break;
            case "openai": provider = new OpenAiLlmProvider();
                break;
            default:       provider = new OpenAiLlmProvider();
        }
        provider.setContext(context); // Inject context for potential use in providers
        return provider;
    }

    private void configureProvider(BaseLlmProvider provider, Map<String, Object> params) {
        String name = (String) params.get("name");
        String endpoint = (String) params.get("endpoint");
        
        // Handle Android Emulator Loopback
        if (endpoint != null && (endpoint.contains("localhost") || endpoint.contains("127.0.0.1"))) {
            if (isAndroid()) {
                endpoint = endpoint.replace("localhost", "10.0.2.2").replace("127.0.0.1", "10.0.2.2");
            }
        }

        provider.setName(name);
        provider.setEndpoint(endpoint);
        provider.setModel((String) params.get("model"));
        provider.setApiKey((String) params.get("api.key"));
        provider.setTemperature(Double.parseDouble(String.valueOf(params.getOrDefault("temperature", 0.7))));
    }

    private LlmProvider wrapWithTracker(LlmProvider provider) {
        try {
            SystemProxy sp = context.getProxy("UsageTracker");
            UsageTracker tracker = (UsageTracker) sp.getInstance();
            return new TrackedLlmProvider(provider, tracker);
        } catch (Exception e) {
            logger.warning("Failed to create UsageTracker: " + e.getMessage());
            return provider;
        }
    }

    public synchronized LlmProvider getActiveProvider() {
        if (activeProvider == null) {
            String configured = context.getProperties().getProperty("ai.worker.llm");
            activeProvider = getProvider(configured);
        }
        return activeProvider;
    }

    public LlmProvider getProvider(String name) {
        return (name == null) ? null : providers.get(name.toLowerCase());
    }

    private boolean isAndroid() {
        String p = context.getProperties().getProperty("platform");
        return "android".equalsIgnoreCase(p) || "mobile".equalsIgnoreCase(p);
    }

    @Override
    protected void onStop() throws Exception {
        // No long-running resources to clean up in this factory, but we could stop providers if needed
    }
}