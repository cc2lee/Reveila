package com.reveila.ai;

import java.util.concurrent.*;

public class AiWatchdog {
    private static final int REMOTE_TIMEOUT_SECONDS = 10;
    private final LlmProvider remoteLlmProvider;
    private final LlmProvider localOllama;

    public AiWatchdog(LlmProvider remoteLlmProvider, LlmProvider localOllama) {
        this.remoteLlmProvider = remoteLlmProvider;
        this.localOllama = localOllama;
    }

    public String getResponse(String userPrompt) {
        CompletableFuture<String> remoteCall = CompletableFuture.supplyAsync(() -> {
            try {
                LlmRequest request = LlmRequest.builder()
                        .addMessage(ReveilaMessage.system("You are a helpful assistant."))
                        .addMessage(ReveilaMessage.user(userPrompt))
                        .build();
                return remoteLlmProvider.invoke(request).getContent(); // Your slow Roo/OpenAI call
            } catch (Exception e) {
                throw new CompletionException(e);
            }
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
        try {
            LlmRequest request = LlmRequest.builder()
                    .addMessage(ReveilaMessage.system("You are a helpful assistant."))
                    .addMessage(ReveilaMessage.user(prompt))
                    .build();
            return "NOTICE: Remote AI is slow. Using Local Model: " + localOllama.invoke(request).getContent();
        } catch (Exception e) {
            return "NOTICE: Remote AI is slow. Using Local Model: Error - " + e.getMessage();
        }
    }
}