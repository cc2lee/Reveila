package com.reveila.ai;

import java.util.Map;

import com.reveila.service.HttpClientService;
import com.reveila.system.PluginComponent;
import com.reveila.system.SystemProxy;

public abstract class BaseLlmProvider extends PluginComponent implements LlmProvider {
    protected String name;
    protected String apiKey;
    protected String resolvedApiKey;
    protected String endpoint;
    protected String model;
    protected double temperature = 0.7;
    protected boolean enabled = true;

    // Abstract methods to be implemented by specific providers
    protected abstract String buildRequestBody(LlmRequest request) throws Exception;
    protected abstract LlmResponse parseResponse(String json) throws Exception;
    protected abstract Map<String, String> getHeaders() throws Exception;

    @Override
    protected void onStop() throws Exception {}

    @Override
    protected void onStart() throws Exception {}

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
            SystemProxy sp = (SystemProxy) context.getProxy("HttpClientService");
            return (HttpClientService) sp.getInstance();
        } catch (Exception e) {
            return null; // Lifecycle will handle transient fallback if needed
        }
    }

    // Common Getters/Setters
    @Override public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getEndpoint() { return endpoint; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setModel(String model) { this.model = model; }
    public void setTemperature(double temp) { this.temperature = temp; }
    @Override public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}