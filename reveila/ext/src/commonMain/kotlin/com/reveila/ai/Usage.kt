package com.reveila.ai

open class Usage {
    var promptTokens: Int = 0
    var completionTokens: Int = 0
    var totalTokens: Int = 0
    var cachedPromptTokens: Int = 0
    var reasoningTokens: Int? = null
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
