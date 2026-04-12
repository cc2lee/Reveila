package com.reveila.ai;

import java.util.LinkedHashMap;
import java.util.Map;

import com.reveila.system.SystemComponent;

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
        providers.put("openai", (LlmProvider) context.getProxy("OpenAiProvider").getInstance());
        providers.put("gemini", (LlmProvider) context.getProxy("GeminiProvider").getInstance());
        providers.put("ollama", (LlmProvider) context.getProxy("OllamaProvider").getInstance());
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
        // 1. Try user selected provider if it's available
        String selected = context.getProperties().getProperty("activeProvider");
        if (selected != null && !selected.isBlank()) {
             LlmProvider provider = providers.get(selected.toLowerCase());
             if (provider != null && provider.isEnabled() && provider.isConfigured()) {
                 return provider;
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
     * Retrieves a provider by its unique slug.
     *
     * @param slug The provider slug (e.g., "openai", "gemini").
     * @return The provider instance.
     * @throws IllegalArgumentException If the provider is not found.
     */
    public LlmProvider getProvider(String slug) {
        LlmProvider provider = providers.get(slug.toLowerCase());
        if (provider == null) {
            throw new IllegalArgumentException("Unknown LLM Provider: " + slug);
        }
        return provider;
    }
}
