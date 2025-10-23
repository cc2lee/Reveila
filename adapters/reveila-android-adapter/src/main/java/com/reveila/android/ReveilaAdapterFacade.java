package com.reveila.android;

import com.google.gson.Gson;
import com.reveila.android.ReveilaService;

import reveila.Reveila;

public class ReveilaAdapterFacade {

    public static String invoke(String payload) throws Exception {
        if (!ReveilaService.isRunning()) {
            throw new Exception("Reveila service is not running.");
        }
        Reveila reveila = ReveilaService.getReveilaInstance();
        if (reveila == null) {
            throw new Exception("Reveila instance is not available.");
        }

        // Parse the JSON payload
        Gson gson = new Gson();
        java.util.Map<String, Object> request = gson.fromJson(payload, java.util.Map.class);
        String componentName = (String) request.get("componentName");
        String methodName = (String) request.get("methodName");
        Object[] methodArguments = request.containsKey("methodArguments") ? ((java.util.List<?>) request.get("methodArguments")).toArray() : null;

        if (componentName == null || methodName == null) {
            throw new Exception("Invalid payload: componentName and methodName are required.");
        }

        Object result = reveila.invoke(componentName, methodName, methodArguments);
        return gson.toJson(result);
    }
}
