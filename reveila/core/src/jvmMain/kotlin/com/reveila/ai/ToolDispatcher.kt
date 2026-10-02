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
            JsonUtil.toObject(json, ToolCall::class.java)
        } catch (e: Exception) {
            val errorCall = ToolCall()
            errorCall.functionName = "error"
            errorCall
        }
    }
}
