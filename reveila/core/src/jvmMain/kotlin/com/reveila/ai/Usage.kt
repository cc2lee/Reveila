package com.reveila.ai

open class Usage {
    // 1. RAW THROUGHPUT
    var promptTokens: Int = 0
    var completionTokens: Int = 0
    var totalTokens: Int = 0

    // 2. EFFICIENCY METRICS (Critical for 2026)
    var cachedPromptTokens: Int = 0

    // 3. AGENTIC OVERHEAD
    var reasoningTokens: Int? = null

    // 4. FINANCIAL DATA
    var estimatedCost: Double = 0.0

    constructor()

    constructor(
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        cachedPromptTokens: Int,
        reasoningTokens: Int?,
        estimatedCost: Double
    ) {
        this.promptTokens = promptTokens
        this.completionTokens = completionTokens
        this.totalTokens = totalTokens
        this.cachedPromptTokens = cachedPromptTokens
        this.reasoningTokens = reasoningTokens
        this.estimatedCost = estimatedCost
    }
}
