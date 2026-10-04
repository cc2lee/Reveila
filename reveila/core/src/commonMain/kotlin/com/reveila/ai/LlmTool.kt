package com.reveila.ai

import com.reveila.util.json.JsonException
import com.reveila.util.json.JsonUtil

open class LlmTool {

    var name: String? = null
    var description: String? = null
    var parameterSchema: Map<String, Any?>? = null
        set(value) {
            field = value ?: mutableMapOf()
        }
    var reveilaMetadata: MutableMap<String, Any?> = mutableMapOf()

    fun setReveilaMetadata(tier: String?, humanInTheLoop: List<String>?, accessScopes: List<String>?) {
        reveilaMetadata["tier"] = tier
        reveilaMetadata["human_in_the_loop"] = humanInTheLoop ?: mutableListOf<String>()
        reveilaMetadata["access_scopes"] = accessScopes ?: mutableListOf<String>()
    }

    @Throws(JsonException::class)
    open fun toJsonString(): String {
        val functionMap = mutableMapOf<String, Any?>()
        functionMap["name"] = name
        functionMap["description"] = description
        if (parameterSchema != null) {
            functionMap["parameters"] = parameterSchema
        }

        val rootMap = mutableMapOf<String, Any?>()
        rootMap["type"] = "function"
        rootMap["function"] = functionMap

        if (reveilaMetadata.isNotEmpty()) {
            rootMap["reveila_metadata"] = reveilaMetadata
        }

        return JsonUtil.toJsonString(rootMap)
    }

    open fun toPlainText(): String {
        val sb = StringBuilder()
        sb.append("Tool Name: ").append(name).append("\n")
        sb.append("Description: ").append(description).append("\n")
        if (parameterSchema != null) {
            sb.append("Parameters Schema:\n")
            try {
                sb.append(JsonUtil.toPrettyJsonString(parameterSchema)).append("\n")
            } catch (e: Exception) {
                sb.append(parameterSchema).append("\n")
            }
        }
        if (reveilaMetadata.isNotEmpty()) {
            sb.append("Reveila Metadata:\n")
            try {
                sb.append(JsonUtil.toPrettyJsonString(reveilaMetadata)).append("\n")
            } catch (e: Exception) {
                sb.append(reveilaMetadata).append("\n")
            }
        }
        return sb.toString()
    }
}
