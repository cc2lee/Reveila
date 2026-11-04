package com.reveila.ai

interface ToolScoringModel {
    @Throws(Exception::class)
    fun scoreAll(query: String, candidates: List<String>): List<Double>
}
