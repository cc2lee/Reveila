package com.reveila.system;

import okhttp3.*;
import java.io.IOException;
import java.util.Map;
import com.reveila.util.json.JsonUtil;

/**
 * Secure client for containerized plugins to call back into the main Reveila node.
 * Used for tool execution and proxy invocation from isolated environments.
 * 
 * @author CL
 */
public class ReveilaClient {
    private final String callbackUrl;
    private final String jitToken;
    private final OkHttpClient httpClient;

    public ReveilaClient() {
        this.callbackUrl = System.getenv("REVEILA_CALLBACK_URL");
        this.jitToken = System.getenv("REVEILA_JIT_TOKEN");
        this.httpClient = new OkHttpClient();
    }

    /**
     * Invokes a system tool or proxy via the host's callback bridge.
     * 
     * @param component The name of the target component.
     * @param method    The method name to invoke.
     * @param arguments The arguments map.
     * @return The result of the invocation.
     * @throws IOException If the network call fails.
     */
    public Object invokeHost(String component, String method, Map<String, Object> arguments) throws IOException {
        if (callbackUrl == null || jitToken == null) {
            throw new IllegalStateException("Reveila callback environment not configured.");
        }

        Map<String, Object> requestBody = Map.of(
            "component", component,
            "method", method,
            "arguments", arguments != null ? arguments : Map.of(),
            "jit_token", jitToken
        );

        try {
            RequestBody body = RequestBody.create(
                JsonUtil.toJsonString(requestBody),
                MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                .url(callbackUrl + "/api/system/callback")
                .post(body)
                .header("Authorization", "Bearer " + jitToken)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Host invocation failed: " + response.code() + " " + (response.body() != null ? response.body().string() : ""));
                }
                String resultJson = response.body() != null ? response.body().string() : "{}";
                Map<String, Object> resultMap = JsonUtil.parseJsonStringToMap(resultJson);
                return resultMap.get("data");
            }
        } catch (Exception e) {
            throw new IOException("Callback execution error: " + e.getMessage(), e);
        }
    }
}
