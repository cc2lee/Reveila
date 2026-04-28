package com.reveila.ai;

import org.json.JSONObject;

import com.reveila.error.LlmException;
import com.reveila.util.json.JsonUtil;

public class TrackedLlmProvider implements LlmProvider {
    private final LlmProvider delegate;
    private final UsageTracker tracker;

    public TrackedLlmProvider(LlmProvider delegate, UsageTracker tracker) {
        this.delegate = delegate;
        this.tracker = tracker;
    }

    @Override
    public LlmResponse invoke(LlmRequest request) throws LlmException {
        long startTime = System.currentTimeMillis();
        LlmResponse response = delegate.invoke(request);
        long latency = System.currentTimeMillis() - startTime;

        String tenantId = request.getMetadata().getOrDefault("tenantId", "default").toString();
        String requestId = response.getRequestId();
        String modelId = request.getModelId();

        // Asynchronously log usage to avoid blocking the Agent's thought loop
        new Thread(() -> {
            String content = response.getContent();
            JSONObject securityData = new JSONObject();
            try {
                String cleanJson = JsonUtil.clean(content);
                if (cleanJson != null && cleanJson.startsWith("{") && cleanJson.endsWith("}")) {
                    securityData = new JSONObject(cleanJson);
                } else {
                    securityData.put("raw_response", content);
                }
            } catch (Exception e) {
                securityData.put("raw_response", content);
            }
            tracker.logUsage(tenantId, requestId, modelId, response.getUsage(), latency, securityData);
        }).start();

        return response;
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @Override
    public boolean isConfigured() {
        return delegate.isConfigured();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}