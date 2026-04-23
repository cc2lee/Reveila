package com.reveila.system;

import java.util.Map;

import com.reveila.util.json.JsonUtil;

/**
 * The Mini-Reveila Runtime (Reveila Worker Agent).
 * ADR 0006: Executes plugin code in total isolation within a container.
 * 
 * @author CL
 */
public class PluginRunner {
    public static void main(String[] args) {
        String pluginId = System.getenv("PLUGIN_ID");
        String pluginClass = System.getenv("PLUGIN_CLASS");
        String methodName = System.getenv("METHOD_NAME");
        String traceId = System.getenv("TRACE_ID");
        String argsJson = System.getenv("PLUGIN_ARGS_JSON");
        String callbackUrl = System.getenv("REVEILA_CALLBACK_URL");

        System.out.println("--- Reveila Worker Agent Starting ---");
        System.out.println("Plugin ID: " + pluginId);
        System.out.println("Class: " + pluginClass);
        System.out.println("Method: " + methodName);
        System.out.println("Trace: " + traceId);

        if (pluginClass == null || pluginClass.isEmpty()) {
            System.err.println("Error: PLUGIN_CLASS environment variable is not set.");
            System.exit(1);
        }

        try {
            // 1. Parse Arguments
            Object[] methodArgs = new Object[0];
            if (argsJson != null && !argsJson.isEmpty()) {
                try {
                    Map<String, Object> argsMap = JsonUtil.parseJsonStringToMap(argsJson);
                    // Standardizing: method arguments are mapped positionally 
                    // as we removed the "method" key upstream
                    methodArgs = argsMap.values().toArray();
                } catch (Exception e) {
                    System.err.println("Warning: Failed to parse PLUGIN_ARGS_JSON. Using empty arguments.");
                }
            }

            // 2. Load Plugin Class
            Class<?> clazz = Class.forName(pluginClass);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            // 3. Find and Invoke Method
            // We use the system's ReflectionMethod utility for robust matching
            java.lang.reflect.Method method = ReflectionMethod.findBestMethod(clazz, methodName, methodArgs);
            
            if (method == null) {
                throw new NoSuchMethodException("Could not find method " + methodName + " on class " + pluginClass);
            }

            System.out.println("Executing " + methodName + "...");
            Object result = method.invoke(instance, ReflectionMethod.coerceArguments(method, methodArgs));
            
            System.out.println("--- Execution Completed Successfully ---");
            System.out.println("Result: " + result);

            // 4. Report Result via Callback
            reportResult(callbackUrl, traceId, pluginId, methodName, result);

            System.exit(0);
        } catch (Exception e) {
            System.err.println("--- Execution Failed ---");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void reportResult(String callbackUrl, String traceId, String pluginId, String methodName, Object result) {
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            System.out.println("No callback URL provided. Result not reported.");
            return;
        }

        try {
            String jitToken = System.getenv("REVEILA_JIT_TOKEN");
            Map<String, Object> payload = Map.of(
                "trace_id", traceId,
                "plugin_id", pluginId,
                "method", methodName,
                "status", "SUCCESS",
                "data", result != null ? result : "null"
            );

            String json = JsonUtil.toJsonString(payload);
            
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                json, 
                okhttp3.MediaType.get("application/json; charset=utf-8")
            );
            
            okhttp3.Request request = new okhttp3.Request.Builder()
                .url(callbackUrl + "/api/system/callback/result")
                .post(body)
                .header("Authorization", "Bearer " + jitToken)
                .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    System.out.println("Result reported successfully to " + callbackUrl);
                } else {
                    System.err.println("Failed to report result. Status: " + response.code());
                }
            }
        } catch (Exception e) {
            System.err.println("Error reporting result: " + e.getMessage());
        }
    }
}
