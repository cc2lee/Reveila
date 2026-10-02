package com.reveila.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class ScoreTracker(val name: String) {

    private data class State(
        val scores: Map<String, Long> = emptyMap()
    )

    private val state = MutableStateFlow(State())

    fun getBest(): String? {
        val scores = state.value.scores
        if (scores.isEmpty()) return null
        return scores.maxByOrNull { it.value }?.key
    }

    fun getWorst(): String? {
        val scores = state.value.scores
        if (scores.isEmpty()) return null
        return scores.minByOrNull { it.value }?.key
    }

    fun applyPoints(points: Long?, name: String?) {
        if (points == null || name == null) return

        state.update { current ->
            val oldPoints = current.scores[name] ?: 0L
            val newScores = current.scores.toMutableMap()
            newScores[name] = oldPoints + points
            current.copy(scores = newScores)
        }
    }
}
