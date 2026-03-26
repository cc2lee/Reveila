package com.reveila.ai;

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
}