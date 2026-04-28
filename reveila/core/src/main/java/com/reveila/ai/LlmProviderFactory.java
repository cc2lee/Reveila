package com.reveila.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.system.*;
import java.util.*;

public class LlmProviderFactory extends SystemComponent {

    private final Map<String, LlmProvider> providers = new LinkedHashMap<>();
    private LlmProvider activeProvider = null;

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

                try {
                    provider.start();
                    // Wrap with UsageTracker if available
                    providers.put(name.toLowerCase(), wrapWithTracker(provider));
                } catch (Exception e) {
                    logger.warning("Failed to start provider [" + name + "]: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.severe("Critical error loading llm.json: " + e.getMessage());
        }
    }

    private BaseLlmProvider createProviderInstance(String type) {
        switch (type.toLowerCase()) {
            case "local":  return new LocalLlamaProvider();
            case "gemini": return new GeminiLlmProvider();
            case "openai": return new OpenAiLlmProvider();
            default:       return new OpenAiLlmProvider();
        }
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
        
        // Boilerplate context injection
        Manifest m = new Manifest();
        m.setName(name);
        provider.setContext(new PluginContext(context, m, new Properties()));
    }

    private LlmProvider wrapWithTracker(LlmProvider provider) {
        try {
            SystemProxy sp = (SystemProxy) context.getProxy("UsageTracker");
            UsageTracker tracker = (UsageTracker) sp.getInstance();
            return new TrackedLlmProvider(provider, tracker);
        } catch (Exception e) {
            return provider; 
        }
    }

    public LlmProvider getActiveProvider() {
        if (activeProvider != null) return activeProvider;

        String selected = context.getProperties().getProperty("ai.worker.llm");
        activeProvider = getProvider(selected);
        
        // Fallback to first available if selection is invalid
        if (activeProvider == null && !providers.isEmpty()) {
            activeProvider = providers.values().iterator().next();
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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onStop'");
    }
}