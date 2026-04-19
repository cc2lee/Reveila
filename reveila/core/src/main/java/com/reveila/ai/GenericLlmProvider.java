package com.reveila.ai;

import com.reveila.service.HttpClientService;
import com.reveila.system.PluginComponent;
import com.reveila.system.SystemProxy;
import com.reveila.util.json.JsonUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic LLM Provider Implementation using native Reveila networking.
 * Replaces LangChain4j with a lean, Android-compatible implementation.
 * 
 * @author CL
 */
public class GenericLlmProvider extends PluginComponent implements LlmProvider {
    private String name = null;
    private String apiKey = null;
    private String resolvedApiKey = null;
    private String endpoint = null;
    private String model = null;
    private double temperature = 0.7;
    private String quantization = null;

    public String getQuantization() {
        return quantization;
    }

    public void setQuantization(String quantization) {
        this.quantization = quantization;
    }

    private boolean enabled = true;

    public GenericLlmProvider() {
        super();
    }

    @Override
    public LlmProvider getInstance() {
        return this;
    }

    public void setName(String name) {
        this.name = name;
        // Pre-configure defaults for local providers to ensure isConfigured() returns true immediately
        if (isLocal() && (endpoint == null || endpoint.isBlank())) {
            endpoint = "http://localhost:11434";
        }
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isLocal() {
        if (name == null) return false;
        String lowerName = name.toLowerCase();
        return lowerName.contains("(local)") || lowerName.contains("ollama") || lowerName.contains("local-");
    }

    @Override
    public boolean isConfigured() {
        if (isLocal()) {
            return name != null && !name.isBlank()
                    && endpoint != null && !endpoint.isBlank();
        } else {
            // Cloud providers are configured if they have a name, endpoint, and either a direct API key or a reference
            return name != null && !name.isBlank()
                    && endpoint != null && !endpoint.isBlank()
                    && apiKey != null && !apiKey.isBlank() && !apiKey.equalsIgnoreCase("null");
        }
    }

    private String resolveApiKey() throws Exception {
        if (resolvedApiKey != null && !resolvedApiKey.isBlank()) {
            return resolvedApiKey;
        }

        if (apiKey != null && apiKey.startsWith("REF:")) {
            resolvedApiKey = (String) this.context.getProxy("SecretManager")
                    .invoke("getSecret", new Object[] { apiKey.substring(4) });
        } else {
            resolvedApiKey = apiKey;
        }

        return resolvedApiKey;
    }

    private HttpClientService getHttpClientService() {
        try {
            SystemProxy sp = (SystemProxy) context.getProxy("HttpClientService");
            return (HttpClientService) sp.getInstance();
        } catch (Exception e) {
            // If not found in context, create a transient one for bootstrapping
            HttpClientService transientService = new HttpClientService();
            try {
                transientService.start();
                return transientService;
            } catch (Exception ex) {
                return null;
            }
        }
    }

    @Override
    protected void onStart() throws Exception {
        // Validation during startup
        if (isEnabled() && !isConfigured()) {
            logger.warning("LLM Provider " + name + " is enabled but not fully configured.");
        }
    }

    @Override
    protected void onStop() throws Exception {
    }

    @Override
    public LlmResponse invoke(LlmRequest request) throws com.reveila.error.LlmException {
        try {
            HttpClientService httpService = getHttpClientService();
            if (httpService == null) {
                throw new com.reveila.error.LlmException("HttpClientService not available");
            }

            String activeApiKey = resolveApiKey();
            String activeModel = (model != null && !model.isBlank()) ? model : request.getModelId();
            
            JSONObject body = new JSONObject();
            body.put("model", activeModel);
            body.put("temperature", temperature);
            body.put("stream", false);

            JSONArray messages = new JSONArray();
            for (ReveilaMessage msg : request.getMessages()) {
                JSONObject msgJson = new JSONObject();
                msgJson.put("role", msg.role().name().toLowerCase());
                msgJson.put("content", msg.content());
                messages.put(msgJson);
            }
            body.put("messages", messages);

            // Tools (OpenAI format)
            if (request.getTools() != null && !request.getTools().isEmpty()) {
                JSONArray toolsJson = new JSONArray();
                for (LlmTool tool : request.getTools()) {
                    try {
                        toolsJson.put(new JSONObject(tool.toJsonString()));
                    } catch (Exception e) {
                        logger.warning("Failed to serialize tool " + tool.toString() + ": " + e.getMessage());
                    }
                }
                body.put("tools", toolsJson);
            }

            Map<String, String> headers = new HashMap<>();
            if (activeApiKey != null && !activeApiKey.isBlank()) {
                headers.put("Authorization", "Bearer " + activeApiKey);
            }
            headers.put("Content-Type", "application/json");

            String url = endpoint;
            if (url == null) url = "http://localhost:11434"; // Absolute fallback

            if (!url.contains("chat/completions")) {
                if (!url.endsWith("/")) url += "/";
                if (!url.contains("/v1")) url += "v1/";
                url += "chat/completions";
            }

            String responseJson = httpService.invokeRest(url, "POST", body.toString(), HttpClientService.JSON, headers);
            return parseResponse(responseJson);

        } catch (Exception e) {
            if (this.logger != null) {
                this.logger.severe("ERROR invoking " + name + ": " + e.getMessage());
            }
            throw new com.reveila.error.LlmException("ERROR invoking " + name + ": " + e.getMessage(), e);
        }
    }

    private LlmResponse parseResponse(String json) {
        json = JsonUtil.clean(json); // Remove any non-JSON content
        JSONObject resp = new JSONObject(json);
        LlmResponse llmResponse = new LlmResponse();
        
        String content = "";
        JSONObject firstChoice = resp.optJSONArray("choices") != null ? resp.getJSONArray("choices").optJSONObject(0) : null;
        if (firstChoice != null) {
            Object messageObj = firstChoice.opt("message");
            if (messageObj instanceof JSONObject) {
                content = ((JSONObject) messageObj).optString("content", "");
            } else if (messageObj instanceof String) {
                content = (String) messageObj;
            }
        } else {
            // Try Ollama native format
            Object messageObj = resp.opt("message");
            if (messageObj instanceof JSONObject) {
                content = ((JSONObject) messageObj).optString("content", "");
            } else if (messageObj instanceof String) {
                content = (String) messageObj;
            } else if (resp.has("response")) {
                content = resp.optString("response", "");
            }
        }

        llmResponse.setContent(content);

        JSONObject usage = resp.optJSONObject("usage");
        if (usage != null) {
            Usage u = new Usage();
            u.setPromptTokens(usage.optInt("prompt_tokens"));
            u.setCompletionTokens(usage.optInt("completion_tokens"));
            u.setTotalTokens(usage.optInt("total_tokens"));
            llmResponse.setUsage(u);
        }

        return llmResponse;
    }

    @Override
    public String getName() {
        return name;
    }
}
