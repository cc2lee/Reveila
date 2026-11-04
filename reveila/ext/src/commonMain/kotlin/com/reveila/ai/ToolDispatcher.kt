package com.reveila.ai

import com.reveila.util.json.JsonUtil

open class ToolDispatcher {

    open fun handleAiRequest(jsonResponseFromLlm: String?): String {
        val call = parseJson(jsonResponseFromLlm)

        return when (call.functionName) {
            "functionName" -> "TODO: Implement function logic"
            else -> "Unknown tool: ${call.functionName}"
        }
    }

    private fun parseJson(json: String?): ToolCall {
        return try {
            val map = JsonUtil.parseJsonStringToMap(json)
            val call = ToolCall()
            call.functionName = (map["functionName"] ?: map["function"] ?: map["name"]) as? String
            call.arguments = map["arguments"] ?: map["args"]
            call
        } catch (e: Exception) {
            val errorCall = ToolCall()
            errorCall.functionName = "error"
            errorCall
        }
    }
}
