package com.reveila.ai

import kotlin.jvm.JvmStatic

open class LlmRequest private constructor(builder: Builder) {
    val modelId: String? = builder.modelId
    val messages: List<ReveilaMessage> = builder.messages
    val temperature: Double? = builder.temperature
    val metadata: Map<String, Any?> = builder.metadata
    val tools: List<LlmTool> = builder.tools

    override fun toString(): String {
        return "LlmRequest [modelId=$modelId, messages=$messages, temperature=$temperature, metadata=$metadata, tools=$tools]"
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    open class Builder {
        var modelId: String? = null
            private set
        var messages: MutableList<ReveilaMessage> = mutableListOf()
            private set
        var temperature: Double? = null
            private set
        var metadata: MutableMap<String, Any?> = mutableMapOf()
            private set
        var tools: MutableList<LlmTool> = mutableListOf()
            private set

        fun modelId(modelId: String?): Builder {
            this.modelId = modelId
            return this
        }

        fun messages(messages: List<ReveilaMessage>?): Builder {
            this.messages = if (messages != null) messages.toMutableList() else mutableListOf()
            return this
        }

        fun addMessage(message: ReveilaMessage?): Builder {
            if (message != null) {
                this.messages.add(message)
            }
            return this
        }

        fun temperature(temperature: Double?): Builder {
            this.temperature = temperature
            return this
        }

        fun metadata(metadata: Map<String, Any?>?): Builder {
            this.metadata = if (metadata != null) metadata.toMutableMap() else mutableMapOf()
            return this
        }

        fun addMetadata(key: String?, value: Any?): Builder {
            if (key == null || value == null) {
                return this
            }
            this.metadata[key] = value
            return this
        }

        fun tools(tools: List<LlmTool>?): Builder {
            this.tools = if (tools != null) tools.toMutableList() else mutableListOf()
            return this
        }

        fun addTool(tool: LlmTool?): Builder {
            if (tool != null) {
                this.tools.add(tool)
            }
            return this
        }

        override fun toString(): String {
            return "Builder [modelId=$modelId, messages=$messages, temperature=$temperature, metadata=$metadata, tools=$tools]"
        }

        fun build(): LlmRequest = LlmRequest(this)
    }
}
