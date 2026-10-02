package com.reveila.ai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
open class ToolCall {
    var functionName: String? = null
    var arguments: Any? = null
}
