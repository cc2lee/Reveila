package com.reveila.android;

import java.util.Map;
import com.google.gson.Gson;
import com.reveila.android.ReveilaService;
import com.reveila.system.Reveila;

public class ReveilaAdapterFacade {

    private static final Gson gson = new Gson();

    private ReveilaAdapterFacade() {
        // Private constructor to prevent instantiation
    }

    public static String invoke(String payload) throws Exception {
        Reveila reveila = ReveilaService.getReveilaInstance();
        if (reveila == null || !reveila.isRunning()) {
            throw new IllegalStateException("Reveila engine is not available.");
        }

        // Parse the JSON payload
        Map<String, Object> request = gson.fromJson(payload, Map.class);
        String componentName = (String) request.get("componentName");
        String methodName = (String) request.get("methodName");
        Object[] methodArguments = request.containsKey("methodArguments")
                ? ((java.util.List<?>) request.get("methodArguments")).toArray()
                : null;

        if (componentName == null || methodName == null) {
            throw new IllegalArgumentException("Invalid payload: componentName and methodName are required.");
        }

        Object result = reveila.invoke(componentName, methodName, methodArguments, null, null);
        return gson.toJson(result);
    }
}
