package com.reveila.ai;

import com.reveila.tools.SystemTool;
import com.reveila.tools.VSCtool;
import com.reveila.util.json.JsonUtil;

// Simplified logic for a Reveila Tool Executor
public class ToolDispatcher {
    public String handleAiRequest(String jsonResponseFromLlm) {
        // 1. Parse JSON into a 'ToolCall' object
        ToolCall call = parseJson(jsonResponseFromLlm); 

        // 2. Execute based on the tool name
        return switch (call.getFunctionName()) {
            case "check_health" -> SystemTool.runPowerShell("CheckHealth.ps1");
            case "clear_cache" -> VSCtool.clearWorkspaceStorage();
            default -> "Error: Unknown tool";
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