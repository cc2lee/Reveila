package com.reveila.ai;

import java.util.List;

public class LlmResponse {
    private String content;           // The text output
    private String finishReason;      // stop, length, tool_calls, content_filter
    private Usage usage;              // Token tracking (essential for LLC cost monitoring)
    private List<ToolCall> toolCalls; // For agent orchestration
    private String requestId;         // The trace or request ID

    public LlmResponse() {
    }

    public LlmResponse(String content, String finishReason, Usage usage, List<ToolCall> toolCalls, String requestId) {
        this.content = content;
        this.finishReason = finishReason;
        this.usage = usage;
        this.toolCalls = toolCalls;
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }
}