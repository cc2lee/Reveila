package com.reveila.ai;

/**
 * AI Provider Interface for multi-model governance.
 * 
 * @author CL
 */
public interface LlmProvider {
    /**
     * Required by LlmProviderFactory to retrieve the instance via Proxy.
     * 
     * @return The provider instance.
     */
    LlmProvider getInstance();

    /**
     * Generates a response based on the provided prompt and context.
     *
     * @param prompt        The user or system-generated prompt.
     * @param systemContext The context/role for the LLM.
     * @return The model's response.
     */
    String generateResponse(String prompt, String systemContext);

    /**
     * Generates a JSON response based on the provided prompts.
     *
     * @param systemPrompt The system prompt defining the persona and constraints.
     * @param userPrompt   The user prompt containing the task details.
     * @return The model's JSON response string.
     */
    String generateJson(String systemPrompt, String userPrompt);
}
