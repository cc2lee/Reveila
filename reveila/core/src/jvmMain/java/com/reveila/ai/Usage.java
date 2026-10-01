package com.reveila.ai;

public class Usage {
    // 1. RAW THROUGHPUT
    private int promptTokens;      // What you sent
    private int completionTokens;  // What you got back
    private int totalTokens;       // The sum (often used for rate-limiting)

    // 2. EFFICIENCY METRICS (Critical for 2026)
    private int cachedPromptTokens; // Tokens saved via Semantic Caching/Prompt Caching
    
    // 3. AGENTIC OVERHEAD
    // For "Thinking" models (like Gemini 3 Ultra or GPT-5), 
    // these are internal tokens used for reasoning that don't appear in the final text.
    private Integer reasoningTokens; 

    // 4. FINANCIAL DATA
    private double estimatedCost;   // Calculated based on current provider rates (USD)

    public Usage() {
    }

    public Usage(int promptTokens, int completionTokens, int totalTokens, int cachedPromptTokens, Integer reasoningTokens, double estimatedCost) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.cachedPromptTokens = cachedPromptTokens;
        this.reasoningTokens = reasoningTokens;
        this.estimatedCost = estimatedCost;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public int getCachedPromptTokens() {
        return cachedPromptTokens;
    }

    public void setCachedPromptTokens(int cachedPromptTokens) {
        this.cachedPromptTokens = cachedPromptTokens;
    }

    public Integer getReasoningTokens() {
        return reasoningTokens;
    }

    public void setReasoningTokens(Integer reasoningTokens) {
        this.reasoningTokens = reasoningTokens;
    }

    public double getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(double estimatedCost) {
        this.estimatedCost = estimatedCost;
    }
}