package com.reveila.spring.model;

import java.util.Map;

/**
 * OpenAI-compatible Tool Definition.
 */
public record OpenAiTool(
    String type,
    FunctionDefinition function
) {
    public static OpenAiTool function(String name, String description, Map<String, Object> parameters) {
        return new OpenAiTool("function", new FunctionDefinition(name, description, parameters));
    }

    public record FunctionDefinition(
        String name,
        String description,
        Map<String, Object> parameters
    ) {}
}
