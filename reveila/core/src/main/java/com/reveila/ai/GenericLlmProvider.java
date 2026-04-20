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
            endpoint = "http://localhost:11434/v1/chat/completions";
        }
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
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

    protected String resolveApiKey() throws Exception {
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

    protected HttpClientService getHttpClientService() {
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
            
            // Fix Google Gemini model alias bug for v1beta openai compatibility endpoint
            if (activeModel != null && (activeModel.equals("gemini-1.5-pro") || activeModel.equals("gemini-1.5-pro-latest") || activeModel.equals("gemini-2.0-flash"))) {
                activeModel = "gemini-3-flash";
            }
            
            String url = endpoint;
            if (url == null || url.isBlank()) url = "http://localhost:11434/v1/chat/completions";

            boolean isInteractionApi = url.contains("interactions");

            JSONObject body = new JSONObject();
            body.put("model", activeModel);
            
            if (isInteractionApi) {
                JSONArray input = new JSONArray();
                for (ReveilaMessage msg : request.getMessages()) {
                    JSONObject msgJson = new JSONObject();
                    String role = msg.role().name().toLowerCase();
                    if (role.equals("assistant")) role = "model";
                    msgJson.put("role", role);
                    msgJson.put("content", msg.content());
                    input.put(msgJson);
                }
                body.put("input", input);
            } else {
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
            }

            Map<String, String> headers = new HashMap<>();
            if (activeApiKey != null && !activeApiKey.isBlank()) {
                if (name != null && name.toLowerCase().contains("gemini")) {
                    headers.put("x-goog-api-key", activeApiKey);
                } else {
                    headers.put("Authorization", "Bearer " + activeApiKey);
                }
            }
            headers.put("Content-Type", "application/json");

            String responseJson = httpService.invokeRest(url, "POST", body.toString(), HttpClientService.JSON, headers);
            return parseResponse(responseJson);

        } catch (Exception e) {
            if (this.logger != null) {
                this.logger.severe("ERROR invoking " + name + ": " + e.getMessage());
            }
            throw new com.reveila.error.LlmException("ERROR invoking " + name + ": " + e.getMessage(), e);
        }
    }

    protected LlmResponse parseResponse(String json) {
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
        } else if (resp.optJSONArray("candidates") != null) {
            // Handle Google Gemini native format (from /v1beta/interactions or generateContent)
            JSONObject candidate = resp.getJSONArray("candidates").optJSONObject(0);
            if (candidate != null && candidate.has("content")) {
                JSONObject contentObj = candidate.getJSONObject("content");
                if (contentObj.has("parts")) {
                    JSONArray parts = contentObj.getJSONArray("parts");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject part = parts.optJSONObject(i);
                        if (part != null && part.has("text")) {
                            sb.append(part.getString("text"));
                        }
                    }
                    content = sb.toString();
                } else {
                    content = contentObj.optString("content", "");
                }
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
