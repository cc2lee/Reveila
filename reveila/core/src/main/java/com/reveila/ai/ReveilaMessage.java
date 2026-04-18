package com.reveila.ai;

import java.util.Objects;

/**
 * Native message type for Reveila LLM interactions.
 */
public record ReveilaMessage(LlmRole role, String content) {
    public ReveilaMessage {
        Objects.requireNonNull(role);
        Objects.requireNonNull(content);
    }

    public static ReveilaMessage system(String content) {
        return new ReveilaMessage(LlmRole.SYSTEM, content);
    }

    public static ReveilaMessage user(String content) {
        return new ReveilaMessage(LlmRole.USER, content);
    }

    public static ReveilaMessage assistant(String content) {
        return new ReveilaMessage(LlmRole.ASSISTANT, content);
    }

    public static ReveilaMessage tool(String content) {
        return new ReveilaMessage(LlmRole.TOOL, content);
    }
}
