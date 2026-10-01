package com.reveila.ai;

import com.reveila.util.json.JsonUtil;

// Simplified logic for a Reveila Tool Executor
public class ToolDispatcher {
    public String handleAiRequest(String jsonResponseFromLlm) {
        // 1. Parse JSON into a 'ToolCall' object
        ToolCall call = parseJson(jsonResponseFromLlm); 

        // 2. Execute based on the tool name
        return switch (call.getFunctionName()) {
            case "functionName" -> "TODO: Implement function logic";
            default -> "Unknown tool: " + call.getFunctionName();
        };
    }

    private ToolCall parseJson(String json) {
        try {
            return JsonUtil.toObject(json, ToolCall.class);
        } catch (Exception e) {
            ToolCall errorCall = new ToolCall();
            errorCall.setFunctionName("error");
            return errorCall;
        }
    }
}