package com.reveila.ai;

import java.util.Map;

import com.reveila.error.LlmException;
import com.reveila.service.HttpClientService;
import com.reveila.system.SystemComponent;
import com.reveila.system.SystemProxy;

public abstract class BaseLlmProvider extends SystemComponent implements LlmProvider {
    protected String name;
    protected String apiKey;
    protected String resolvedApiKey;
    protected String endpoint;
    protected String model;
    protected double temperature = 0.7;
    protected boolean enabled = true;

    // Abstract methods to be implemented by specific providers
    protected abstract String buildRequestBody(LlmRequest request) throws LlmException;
    protected abstract LlmResponse parseResponse(String json) throws LlmException;
    protected abstract Map<String, String> getHeaders() throws LlmException;

    @Override
    protected void onStart() throws Exception {}

    @Override
    protected void onStop() throws Exception {}

    @Override
    public LlmResponse invoke(LlmRequest request) throws com.reveila.error.LlmException {
        try {
            HttpClientService httpService = getHttpClientService();
            if (httpService == null) throw new com.reveila.error.LlmException("HttpClientService unavailable");

            String url = getEndpoint();
            String body = buildRequestBody(request);
            Map<String, String> headers = getHeaders();

            String responseJson = httpService.invokeRest(url, "POST", body, HttpClientService.JSON, headers);
            return parseResponse(responseJson);

        } catch (Exception e) {
            logger.severe("Invoke failed for " + name + ": " + e.getMessage());
            throw new com.reveila.error.LlmException(e.getMessage(), e);
        }
    }

    protected String resolveApiKey() throws Exception {
        if (resolvedApiKey != null) return resolvedApiKey;
        if (apiKey != null && apiKey.startsWith("REF:")) {
            resolvedApiKey = (String) this.context.getProxy("SecretManager")
                    .invoke("getSecret", new Object[] { apiKey.substring(4) });
        } else {
            resolvedApiKey = apiKey;
        }
        return resolvedApiKey;
    }

    protected HttpClientService getHttpClientService() {
        try {
            SystemProxy sp = context.getProxy("HttpClientService");
            return (HttpClientService) sp.getInstance();
        } catch (Exception e) {
            return null; // Lifecycle will handle transient fallback if needed
        }
    }

    // Common Getters/Setters
    public String getName() { return name; }
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Argument 'name' cannot be null or empty.");
        this.name = name;
    }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) throw new IllegalArgumentException("Argument 'endpoint' cannot be null or empty.");
        this.endpoint = endpoint;
    }
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        this.resolvedApiKey = null; // Reset resolved key to allow re-resolution if API key changes
    }
    public void setModel(String model) {
        if (model == null || model.trim().isEmpty()) throw new IllegalArgumentException("Argument 'model' cannot be null or empty.");
        this.model = model;
    }
    public void setTemperature(double temp) {
        if (temp < 0 || temp > 1) throw new IllegalArgumentException("Argument 'temp' must be between 0 and 1.");
        this.temperature = temp;
    }
    public synchronized boolean isEnabled() { return enabled; }
    public synchronized void setEnabled(boolean enabled) { this.enabled = enabled; }
}