package com.reveila.ai

open class LlmScoringModel(provider: LlmProvider) : ToolScoringModel {

    private val provider: LlmProvider = provider

    companion object {
        private val SCORE_PATTERN: Regex = Regex("Score:\\s*(\\d+\\.?\\d*)")
    }

    @Throws(Exception::class)
    override fun scoreAll(query: String, candidates: List<String>): List<Double> {
        val scores = mutableListOf<Double>()
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
            val match = SCORE_PATTERN.find(content)
            if (match != null) {
                val groupVal = match.groupValues.getOrNull(1)
                if (groupVal != null) {
                    return groupVal.toDoubleOrNull() ?: 0.0
                }
            }
        }
        return 0.0
    }
}
