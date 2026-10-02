package com.reveila.ai

import com.reveila.util.json.JsonException
import com.reveila.util.json.JsonUtil
import java.util.ArrayList
import java.util.HashMap

open class LlmTool {

    var name: String? = null
    var description: String? = null
    var parameterSchema: Map<String, Any?>? = null
        set(value) {
            field = value ?: HashMap()
        }
    var reveilaMetadata: MutableMap<String, Any?> = HashMap()

    fun setReveilaMetadata(tier: String?, humanInTheLoop: List<String>?, accessScopes: List<String>?) {
        reveilaMetadata["tier"] = tier
        reveilaMetadata["human_in_the_loop"] = humanInTheLoop ?: ArrayList<String>()
        reveilaMetadata["access_scopes"] = accessScopes ?: ArrayList<String>()
    }

    @Throws(JsonException::class)
    open fun toJsonString(): String {
        val functionMap = HashMap<String, Any?>()
        functionMap["name"] = name
        functionMap["description"] = description
        if (parameterSchema != null) {
            functionMap["parameters"] = parameterSchema
        }

        val rootMap = HashMap<String, Any?>()
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
                sb.append(JsonUtil.PRETTY_WRITER.writeValueAsString(parameterSchema)).append("\n")
            } catch (e: Exception) {
                sb.append(parameterSchema).append("\n")
            }
        }
        if (reveilaMetadata.isNotEmpty()) {
            sb.append("Reveila Metadata:\n")
            try {
                sb.append(JsonUtil.PRETTY_WRITER.writeValueAsString(reveilaMetadata)).append("\n")
            } catch (e: Exception) {
                sb.append(reveilaMetadata).append("\n")
            }
        }
        return sb.toString()
    }
}
