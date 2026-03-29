package com.reveila.ai;

import java.util.concurrent.*;

public class AiWatchdog {
    private static final int REMOTE_TIMEOUT_SECONDS = 10;

    public String getResponse(String userPrompt) {
        CompletableFuture<String> remoteCall = CompletableFuture.supplyAsync(() -> {
            return remoteLlmProvider.call(userPrompt); // Your slow Roo/OpenAI call
        });

        try {
            // We only wait for the "Patience Threshold"
            return remoteCall.get(REMOTE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.err.println("Remote LLM is slow. Activating UX Recovery...");
            remoteCall.cancel(true); // Kill the slow request to save tokens/energy
            return fallbackToLocal(userPrompt);
        } catch (Exception e) {
            return "Error connecting to AI: " + e.getMessage();
        }
    }

    private String fallbackToLocal(String prompt) {
        // Switch to a local Ollama instance (Llama 3 / Mistral)
        // Fast, 0ms latency, works offline.
        return "NOTICE: Remote AI is slow. Using Local Model: " + localOllama.call(prompt);
    }
}