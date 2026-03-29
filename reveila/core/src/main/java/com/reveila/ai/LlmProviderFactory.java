package com.reveila.ai;

import java.util.LinkedHashMap;
import java.util.Map;

import com.reveila.system.AbstractService;

/**
 * Factory to resolve LLM providers by name.
 * 
 * @author CL
 */
public class LlmProviderFactory extends AbstractService {

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
        providers.put("openai", (LlmProvider) context.getProxy("OpenAiProvider").orElseThrow().invoke("getInstance", null));
        providers.put("gemini", (LlmProvider) context.getProxy("GeminiProvider").orElseThrow().invoke("getInstance", null));
        providers.put("ollama", (LlmProvider) context.getProxy("OllamaProvider").orElseThrow().invoke("getInstance", null));
    }

    @Override
    protected void onStop() throws Exception {
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
