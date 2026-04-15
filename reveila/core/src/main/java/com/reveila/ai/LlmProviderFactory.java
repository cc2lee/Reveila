package com.reveila.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.system.SystemComponent;
import com.reveila.system.PluginContext;
import com.reveila.system.Constants;

/**
 * Factory to resolve LLM providers by name.
 * 
 * @author CL
 */
public class LlmProviderFactory extends SystemComponent {

    private final Map<String, LlmProvider> providers = new LinkedHashMap<>(); // Use LinkedHashMap to preserve order
    private int currentProviderIndex = 0;
    
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
        // Dynamically load ALL providers directly from llm.json using GenericLlmProvider
        try {
            String jsonConfig = (String) context.getProxy("ConfigurationManager").invoke("getSettings", new Object[]{"llm.json"});
            Map<String, Object> configMap = new ObjectMapper().readValue(jsonConfig, new TypeReference<Map<String, Object>>() {});
            Object onboardedObj = configMap.get("onboarded_providers");
            if (onboardedObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> onboarded = (List<Map<String, Object>>) onboardedObj;
                for (Map<String, Object> p : onboarded) {
                    String name = (String) p.get("name");
                    String endpoint = (String) p.get("defaultEndpoint");
                    String apiKey = (String) p.get("apiKey");
                    
                    if (name != null) {
                        GenericLlmProvider generic = new GenericLlmProvider();
                        generic.setContext(new PluginContext(context, null, new Properties()));
                        generic.setName(name);
                        generic.setEndpoint(endpoint);
                        generic.setApiKey(apiKey);
                        
                        try {
                            generic.onStart(); // Start it so it's ready
                            providers.put(name.toLowerCase(), generic);
                            
                            // Backward-compatible alias mapping for system keys
                            if (name.equalsIgnoreCase(Constants.LLM_PROVIDER_OPENAI)) providers.put("openai", generic);
                            if (name.equalsIgnoreCase(Constants.LLM_PROVIDER_GEMINI)) providers.put("gemini", generic);
                            if (name.startsWith("Gemma-3-1b") || name.equalsIgnoreCase(Constants.LLM_PROVIDER_OLLAMA)) providers.put("ollama", generic);

                        } catch (Exception e) {
                            logger.warning("Failed to start generic provider '" + name + "': " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to parse onboarded_providers for dynamic generic instantiation: " + e.getMessage());
        }

        // Ensure Ollama exists as an ultimate system fallback
        if (!providers.containsKey("ollama")) {
            GenericLlmProvider fallbackOllama = new GenericLlmProvider();
            fallbackOllama.setContext(new PluginContext(context, null, new Properties()));
            fallbackOllama.setName(Constants.LLM_PROVIDER_OLLAMA);
            fallbackOllama.setEndpoint("http://ollama-service:11434");
            try {
                fallbackOllama.onStart();
                providers.put("ollama", fallbackOllama);
            } catch (Exception e) {
                logger.warning("Failed to start fallback Ollama provider: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onStop() throws Exception {
    }

    /**
     * Retrieves the best available provider, respecting the user's configuration
     * and the "local first" policy.
     * 
     * @return The best provider instance.
     */
    public LlmProvider getBestProvider() {
        // 1. Try user selected worker provider if it's available
        String selected = context.getProperties().getProperty("ai.worker.llm");
        if (selected != null && !selected.isBlank()) {
             try {
                 LlmProvider provider = getProvider(selected);
                 if (provider != null && provider.isEnabled() && provider.isConfigured()) {
                     return provider;
                 }
             } catch (IllegalArgumentException e) {
                 // Fallback if not found
             }
        }

        // 2. Fallback to Ollama (local) if it's enabled
        LlmProvider ollama = providers.get("ollama");
        if (ollama != null && ollama.isEnabled()) {
            return ollama;
        }

        // 3. Fallback to any enabled and configured provider
        for (LlmProvider p : providers.values()) {
            if (p.isEnabled() && p.isConfigured()) {
                return p;
            }
        }

        // 4. Ultimate fallback to Ollama even if not "enabled" as a last resort for system logic
        return ollama;
    }

    /**
     * Alias for getBestProvider(). Returns the user chosen active provider.
     * 
     * @return The active LLM provider.
     */
    public LlmProvider getActiveProvider() {
        return getBestProvider();
    }

    /**
     * Retrieves a provider by its unique slug or name.
     *
     * @param slug The provider slug (e.g., "openai", "gemini", or "My Mistral").
     * @return The provider instance.
     * @throws IllegalArgumentException If the provider is not found.
     */
    public LlmProvider getProvider(String slug) {
        String key = slug.toLowerCase();
        
        // Backward compatibility mappings
        if (key.equals("openaiprovider") || key.equals("openai")) key = "openai";
        else if (key.equals("geminiprovider") || key.equals("google gemini") || key.equals("gemini")) key = "gemini";
        else if (key.equals("ollamaprovider") || key.startsWith("gemma-3-1b")) key = "ollama";
        else if (key.equals("anthropicprovider") || key.equals("anthropic")) key = "anthropic";

        LlmProvider provider = providers.get(key);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown LLM Provider: " + slug);
        }
        return provider;
    }
}
