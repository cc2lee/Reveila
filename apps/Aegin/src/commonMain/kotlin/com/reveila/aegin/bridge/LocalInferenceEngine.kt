package com.reveila.aegin.bridge

import kotlinx.coroutines.flow.Flow

/**
 * Hardware and storage preflight verification outcome prior to loading a local GGUF model.
 */
data class PreflightCheckResult(
    val allowed: Boolean,
    val tierLabel: String,
    val recommendedProfile: String,
    val reason: String
)

/**
 * Unified hardware-tier model catalog across Android, Desktop, iOS, and Web.
 */
object ModelTierCatalog {
    const val MODEL_1_5B = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val MODEL_3B = "qwen2.5-3b-instruct-q4_k_m.gguf"
    const val MODEL_7B = "qwen2.5-7b-instruct-q4_k_m.gguf"

    val standardProfiles: List<String> = listOf(MODEL_1_5B, MODEL_3B, MODEL_7B)

    fun resolveTier(totalRamGb: Double): Triple<String, String, Int> = when {
        totalRamGb < 5.5 -> Triple(
            "BLOCKED (< 6 GB RAM)",
            "Cloud / Hybrid Fallback Required",
            0
        )
        totalRamGb < 7.6 -> Triple(
            "ENTRY TIER (6 – 8 GB RAM)",
            MODEL_1_5B,
            1050
        )
        totalRamGb < 11.5 -> Triple(
            "STANDARD TIER (8 – 12 GB RAM)",
            MODEL_3B,
            2200
        )
        else -> Triple(
            "FLAGSHIP TIER (12 GB+ RAM)",
            MODEL_7B,
            4400
        )
    }
}

/**
 * Vendor-agnostic contract for local on-device model inference engines.
 * Keeps domain and UI layers decoupled from concrete JNI, HTTP, or runtime implementations.
 */
interface LocalInferenceEngine {
    suspend fun verifyPreflight(modelSizeBytes: Long): PreflightCheckResult
    suspend fun loadModel(modelPath: String, contextSize: Int = 2048, threads: Int = 4): Boolean
    fun generateStream(prompt: String, maxTokens: Int = 256): Flow<String>
    suspend fun release()
}
