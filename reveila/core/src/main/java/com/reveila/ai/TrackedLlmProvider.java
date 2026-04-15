package com.reveila.ai;

import com.reveila.error.LlmException;

public class TrackedLlmProvider implements LlmProvider {
    private final LlmProvider delegate;
    private final UsageTracker tracker;

    public TrackedLlmProvider(LlmProvider delegate, UsageTracker tracker) {
        this.delegate = delegate;
        this.tracker = tracker;
    }

    @Override
    public LlmResponse invoke(LlmRequest request) throws LlmException {
        LlmResponse response = delegate.invoke(request);
        
        // Asynchronously log usage to avoid blocking the Agent's thought loop
        new Thread(() -> {
            tracker.logUsage(
                request.getMetadata().getOrDefault("tenantId", "default").toString(),
                response.getRequestId(),
                request.getModelId(),
                response.getUsage()
            );
        }).start();

        return response;
    }

    @Override
    public LlmProvider getInstance() {
        return delegate.getInstance();
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @Override
    public boolean isConfigured() {
        return delegate.isConfigured();
    }
}