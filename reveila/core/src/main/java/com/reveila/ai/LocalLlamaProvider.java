package com.reveila.ai;

import com.reveila.ai.util.GemmaPromptFormatter;
import com.reveila.ai.util.Llama3PromptFormatter;
import com.reveila.error.LlmException;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class LocalLlamaProvider extends BaseLlmProvider {

    public LocalLlamaProvider() {
        super();
        // Default to the localhost server we set up in the ReveilaLlmService
        this.endpoint = "http://localhost:8888/completion";
    }

    @Override
    protected String buildRequestBody(LlmRequest request) throws LlmException {
        JSONObject body = new JSONObject();

        String formattedPrompt;
        String activeModel = (model != null) ? model.toLowerCase() : "";

        // Strategic Selection based on the model name
        if (activeModel.contains("llama-3") || activeModel.contains("llama3")) {
            formattedPrompt = Llama3PromptFormatter.format(request.getMessages());
            // Llama 3 uses <|eot_id|> as the stop token
            body.put("stop", new org.json.JSONArray().put("<|eot_id|>"));
        } else {
            // Default to Gemma formatting
            formattedPrompt = GemmaPromptFormatter.format(request.getMessages());
            body.put("stop", new org.json.JSONArray().put("<end_of_turn>"));
        }

        body.put("prompt", formattedPrompt);
        body.put("temperature", temperature);
        body.put("n_predict", 1024);
        body.put("stream", false);

        return body.toString();
    }

    @Override
    protected Map<String, String> getHeaders() throws LlmException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return headers;
    }

    @Override
    protected LlmResponse parseResponse(String json) throws LlmException {
        JSONObject resp = new JSONObject(json);
        LlmResponse llmResponse = new LlmResponse();

        // llama-server /completion returns text in "content"
        String content = resp.optString("content", "").trim();
        llmResponse.setContent(content);

        return llmResponse;
    }

    @Override
    public boolean isConfigured() {
        return endpoint != null;
    }
}