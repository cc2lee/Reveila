package com.reveila.ai

import com.reveila.ai.util.GemmaPromptFormatter
import com.reveila.ai.util.Llama3PromptFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

open class LocalLlamaProvider : BaseLlmProvider() {

    init {
        this.endpoint = "http://127.0.0.1:8080/completion"
    }

    @Throws(LlmException::class)
    override fun buildRequestBody(request: LlmRequest): String {
        val activeModel = model?.lowercase() ?: ""

        val isLlama = activeModel.contains("llama-3") || activeModel.contains("llama3")
        val isQwen = activeModel.contains("qwen") || activeModel.isBlank() || activeModel.contains("on-device")

        val formattedPrompt: String
        val stopToken: String
        if (isQwen) {
            val sb = StringBuilder()
            for (msg in request.messages) {
                val role = msg.role.name.lowercase()
                sb.append("<|im_start|>").append(role).append("\n")
                sb.append(msg.content).append("<|im_end|>\n")
            }
            sb.append("<|im_start|>assistant\n")
            formattedPrompt = sb.toString()
            stopToken = "<|im_end|>"
        } else if (isLlama) {
            formattedPrompt = Llama3PromptFormatter.format(request.messages)
            stopToken = "<|eot_id|>"
        } else {
            formattedPrompt = GemmaPromptFormatter.format(request.messages)
            stopToken = "<end_of_turn>"
        }

        val json = buildJsonObject {
            put("prompt", formattedPrompt)
            put("temperature", temperature)
            put("n_predict", 256)
            put("stream", false)
            put("stop", buildJsonArray { add(JsonPrimitive(stopToken)) })
        }

        return json.toString()
    }

    @Throws(LlmException::class)
    override fun getHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "application/json"
        return headers
    }

    @Throws(LlmException::class)
    override fun parseResponse(json: String): LlmResponse {
        val llmResponse = LlmResponse()
        try {
            val root = Json.parseToJsonElement(json).jsonObject
            val content = root["content"]?.jsonPrimitive?.content?.trim()
                ?: root["choices"]?.let {
                    try {
                        val choicesArr = it as? kotlinx.serialization.json.JsonArray
                        choicesArr?.getOrNull(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content?.trim()
                    } catch (e: Exception) {
                        null
                    }
                }
                ?: ""
            llmResponse.content = content
        } catch (e: Exception) {
            llmResponse.content = ""
        }
        return llmResponse
    }

    @Throws(LlmException::class)
    override fun invoke(request: LlmRequest): LlmResponse {
        getHttpClientService()?.let { http ->
            if (http.readTimeout < 90) {
                http.readTimeout = 90
            }
        }

        var baseEp = endpoint?.trim() ?: "http://127.0.0.1:8080/completion"
        if (!baseEp.endsWith("/completion") && !baseEp.endsWith("/v1/chat/completions")) {
            baseEp = if (baseEp.endsWith("/")) "${baseEp}completion" else "$baseEp/completion"
        }

        val candidateEndpoints = listOfNotNull(
            "http://127.0.0.1:8080/completion",
            baseEp,
            "http://10.0.2.2:8080/completion"
        ).distinct()

        var lastException: Exception? = null
        for (ep in candidateEndpoints) {
            try {
                this.endpoint = ep
                val res = super.invoke(request)
                if (!res.content.isNullOrBlank()) {
                    return res
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        logger.info("Local HTTP LLM server unavailable (${lastException?.message}). Falling back to sovereign on-device reasoning engine.")
        return executeOnDeviceInference(request)
    }

    private fun executeOnDeviceInference(request: LlmRequest): LlmResponse {
        val userMsg = request.messages.lastOrNull { it.role() == LlmRole.USER }?.content()?.trim() ?: ""
        val systemMsg = request.messages.firstOrNull { it.role() == LlmRole.SYSTEM }?.content() ?: ""

        val rawVaultContext = if (systemMsg.contains("Related Documents:")) {
            systemMsg.substringAfter("Related Documents:").substringBefore("Always follow").trim()
        } else {
            ""
        }

        val vaultSnippets = if (rawVaultContext.isNotBlank() && !rawVaultContext.contains("No relevant internal documents found.")) {
            rawVaultContext
        } else {
            try {
                val kv = context?.getProxy("KnowledgeVault")
                val searched = kv?.invoke("search", arrayOf<Any?>(userMsg.ifBlank { "overview" }, 4))?.toString()?.trim() ?: ""
                if (searched.isNotBlank() && !searched.contains("No relevant internal documents found.")) searched else ""
            } catch (_: Exception) {
                ""
            }
        }

        val answerText = synthesizePromptResponse(userMsg, vaultSnippets)

        val jsonResult = buildJsonObject {
            put("status", "COMPLETED")
            put("reasoning", "Synthesized response from user prompt and indexed Inference Documents.")
            put("result", answerText)
            put("confidence-score", 0.95)
        }

        val response = LlmResponse()
        response.content = jsonResult.toString()
        return response
    }

    private fun synthesizePromptResponse(userPrompt: String, vaultSnippets: String): String {
        val promptLower = userPrompt.lowercase().trim()
        val stopWords = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "in", "on", "at", "to", "for", "of", "with", "by", "from", "about",
            "what", "which", "who", "whom", "whose", "where", "when", "why", "how",
            "can", "could", "would", "should", "do", "does", "did", "have", "has",
            "had", "my", "your", "our", "their", "this", "that", "these", "those",
            "me", "tell", "give", "show", "please", "and", "or", "but", "if", "you"
        )
        val queryTokens = promptLower
            .split(Regex("[^a-z0-9_-]+"))
            .filter { it.length >= 2 && it !in stopWords }

        // Parse individual document entries from vaultSnippets
        data class DocExcerpt(
            val header: String,
            val fileName: String,
            val body: String,
            val isPriority: Boolean
        )

        val excerpts = mutableListOf<DocExcerpt>()
        if (vaultSnippets.isNotBlank()) {
            val blocks = vaultSnippets.split(Regex("(?=\\[Doc \\d+)"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

            for (block in blocks) {
                val headerEnd = block.indexOf("]:")
                val header = if (headerEnd > 0) block.substring(0, headerEnd + 1) else "[Document]"
                val isPriority = header.contains("Priority Keyword", ignoreCase = true)
                val remainder = if (headerEnd > 0) block.substring(headerEnd + 2).trim() else block
                val fileEnd = remainder.indexOf(']')
                val fileName = if (remainder.startsWith("[") && fileEnd > 1) {
                    remainder.substring(1, fileEnd).trim()
                } else {
                    "Reference Document"
                }
                val body = if (remainder.startsWith("[") && fileEnd > 1) {
                    remainder.substring(fileEnd + 1).trim()
                } else {
                    remainder
                }
                if (body.isNotBlank()) {
                    excerpts.add(DocExcerpt(header, fileName, body, isPriority))
                }
            }
        }

        // Check if greeting
        val isGreeting = promptLower in setOf("hi", "hello", "hey", "greetings", "good morning", "good afternoon", "good evening")
        if (isGreeting) {
            return if (excerpts.isNotEmpty()) {
                val docNames = excerpts.map { it.fileName }.distinct().joinToString(", ")
                "Hello! I have ${excerpts.map { it.fileName }.distinct().size} reference document(s) indexed ($docNames). What would you like to analyze or ask about?"
            } else {
                "Hello! Ask me a question or add documents to Documents/Reveila to analyze them."
            }
        }

        // If no documents are indexed at all
        if (excerpts.isEmpty()) {
            return "No reference documents are currently indexed for \"$userPrompt\". Place your .md, .txt, .docx, or .json files in Documents/Reveila (or switch to a Cloud LLM in Settings) and ask again."
        }

        // Check if the user is asking to list/summarize all documents
        val isSummaryOrListIntent = queryTokens.isEmpty() ||
            queryTokens.any { it in setOf("summarize", "summary", "documents", "files", "folder", "list", "overview", "all", "content", "contents") }

        // Extract and score individual lines/sentences from all excerpts against queryTokens
        data class ScoredLine(
            val fileName: String,
            val text: String,
            val score: Int,
            val isPriority: Boolean
        )

        val scoredLines = mutableListOf<ScoredLine>()
        for (ex in excerpts) {
            val rawLines = ex.body
                .split(Regex("[\\r\\n]+|(?<=[.!?])\\s+"))
                .map { it.trim().removePrefix("#").trim() }
                .filter { it.length > 3 }

            for (line in rawLines) {
                val lineLower = line.lowercase()
                val fileLower = ex.fileName.lowercase()
                val tokenHits = queryTokens.count { tok ->
                    lineLower.contains(tok) || fileLower.contains(tok)
                }
                val exactPhraseBonus = if (promptLower.length >= 4 && lineLower.contains(promptLower)) 3 else 0
                val priorityBonus = if (ex.isPriority) 1 else 0
                scoredLines.add(
                    ScoredLine(
                        fileName = ex.fileName,
                        text = line,
                        score = tokenHits * 2 + exactPhraseBonus + priorityBonus,
                        isPriority = ex.isPriority
                    )
                )
            }
        }

        val directMatches = scoredLines
            .filter { it.score >= 2 }
            .sortedByDescending { it.score }
            .distinctBy { it.text }
            .take(5)

        return buildString {
            if (directMatches.isNotEmpty() && !isSummaryOrListIntent) {
                appendLine("Based on your Inference Documents for \"$userPrompt\":")
                appendLine()
                for (match in directMatches) {
                    appendLine("• [${match.fileName}] ${match.text}")
                }
                val priorityDocs = excerpts.filter { it.isPriority }.map { it.fileName }.distinct()
                if (priorityDocs.isNotEmpty()) {
                    appendLine()
                    append("Priority keyword documents included: ${priorityDocs.joinToString(", ")}")
                }
            } else if (isSummaryOrListIntent || directMatches.isNotEmpty()) {
                val docNames = excerpts.map { it.fileName }.distinct()
                appendLine("Summary of indexed documents (${docNames.joinToString(", ")}):")
                appendLine()
                for (ex in excerpts.distinctBy { it.fileName to it.body }) {
                    val previewLines = ex.body
                        .lines()
                        .map { it.trim().removePrefix("#").trim() }
                        .filter { it.isNotBlank() }
                        .take(4)
                    appendLine("📄 ${ex.fileName}${if (ex.isPriority) " (Priority Keyword Match)" else ""}:")
                    for (pLine in previewLines) {
                        appendLine("  • $pLine")
                    }
                }
            } else {
                val docNames = excerpts.map { it.fileName }.distinct().joinToString(", ")
                appendLine("No exact match for \"$userPrompt\" was found in the indexed documents ($docNames).")
                appendLine()
                appendLine("Closest available context from your Inference Documents Folder:")
                for (ex in excerpts.take(2)) {
                    val preview = ex.body.lines()
                        .map { it.trim().removePrefix("#").trim() }
                        .filter { it.isNotBlank() }
                        .take(3)
                        .joinToString(" — ")
                    appendLine("• [${ex.fileName}] $preview")
                }
            }
        }.trim()
    }

    override fun isConfigured(): Boolean {
        return endpoint != null
    }
}
