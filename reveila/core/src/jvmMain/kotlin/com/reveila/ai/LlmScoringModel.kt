package com.reveila.ai

import java.util.ArrayList
import java.util.Objects
import java.util.regex.Pattern

open class LlmScoringModel(provider: LlmProvider) : ToolScoringModel {

    private val provider: LlmProvider = Objects.requireNonNull(provider, "provider must not be null")

    companion object {
        private val SCORE_PATTERN: Pattern = Pattern.compile("Score:\\s*(\\d+\\.?\\d*)")
    }

    @Throws(Exception::class)
    override fun scoreAll(query: String, candidates: List<String>): List<Double> {
        val scores = ArrayList<Double>()
        for (candidate in candidates) {
            scores.add(scoreCandidate(query, candidate))
        }
        return scores
    }

    @Throws(Exception::class)
    private fun scoreCandidate(query: String, candidate: String): Double {
        val request = LlmRequest.builder()
            .addMessage(
                ReveilaMessage.system(
                    "You are a surgical tool reranker. Your task is to evaluate the relevance of a tool to a user's intent. " +
                        "Respond ONLY with 'Score: X.X' where X.X is between 0.0 (irrelevant) and 1.0 (perfect match)."
                )
            )
            .addMessage(ReveilaMessage.user("User Intent: $query\nTool Manifest: $candidate\nEvaluation:"))
            .temperature(0.0)
            .build()

        val response = provider.invoke(request)
        val content = response.content
        if (content != null) {
            val matcher = SCORE_PATTERN.matcher(content)
            if (matcher.find()) {
                val groupVal = matcher.group(1)
                if (groupVal != null) {
                    return groupVal.toDouble()
                }
            }
        }
        return 0.0
    }
}
