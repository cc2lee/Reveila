package com.reveila.ai

open class LlmResponse {
    var content: String? = null
    var finishReason: String? = null
    var usage: Usage? = null
    var toolCalls: List<ToolCall>? = null
    var requestId: String? = null

    constructor()

    constructor(
        content: String?,
        finishReason: String?,
        usage: Usage?,
        toolCalls: List<ToolCall>?,
        requestId: String?
    ) {
        this.content = content
        this.finishReason = finishReason
        this.usage = usage
        this.toolCalls = toolCalls
        this.requestId = requestId
    }
}
