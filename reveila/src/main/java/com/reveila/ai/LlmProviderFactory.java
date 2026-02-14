package com.reveila.ai;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory to resolve LLM providers by name.
 * 
 * @author CL
 */
public class LlmProviderFactory {
    private final Map<String, LlmProvider> providers = new HashMap<>();

    public LlmProviderFactory(OpenAiProvider openAi, GeminiProvider gemini) {
        providers.put("openai", openAi);
        providers.put("gemini", gemini);
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
