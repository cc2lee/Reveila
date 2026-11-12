package com.reveila.ai

import com.reveila.safety.InvocationRequest
import kotlin.jvm.JvmOverloads

/**
 * Represents an autonomous tool call emitted by an LLM or AI agent within the Agentic AI Fabric.
 */
open class ToolCall @JvmOverloads constructor(
    override var functionName: String? = null,
    override var arguments: Any? = null
) : InvocationRequest(functionName, arguments)
