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
    const val MODEL_0_5B = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    const val MODEL_1_5B = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val MODEL_GEMMA_2B = "gemma-2-2b-it-Q4_K_M.gguf"
    const val MODEL_3B = "qwen2.5-3b-instruct-q4_k_m.gguf"
    const val MODEL_7B = "qwen2.5-7b-instruct-q4_k_m.gguf"
    const val MODEL_14B = "qwen2.5-14b-instruct-q4_k_m.gguf"

    val standardProfiles: List<String> = listOf(
        MODEL_0_5B,
        MODEL_1_5B,
        MODEL_GEMMA_2B,
        MODEL_3B,
        MODEL_7B,
        MODEL_14B
    )

    fun resolveTier(totalRamGb: Double, target: String = "android"): Triple<String, String, Int> {
        val isDesktop = target.equals("desktop", ignoreCase = true)
        return if (isDesktop) {
            when {
                totalRamGb < 5.5 -> Triple(
                    "BLOCKED (< 6 GB RAM)",
                    "Cloud / Self-Hosted Fallback Required",
                    0
                )
                totalRamGb < 7.6 -> Triple(
                    "ENTRY DESKTOP (6 – 8 GB RAM)",
                    MODEL_GEMMA_2B,
                    1630
                )
                totalRamGb < 11.5 -> Triple(
                    "LIGHT DESKTOP (8 – 12 GB RAM)",
                    MODEL_3B,
                    2010
                )
                totalRamGb < 23.0 -> Triple(
                    "STANDARD DESKTOP (12 – 24 GB RAM)",
                    MODEL_7B,
                    4470
                )
                else -> Triple(
                    "POWER DESKTOP (24 GB+ RAM)",
                    MODEL_14B,
                    8570
                )
            }
        } else {
            when {
                totalRamGb < 5.5 -> Triple(
                    "BLOCKED (< 6 GB RAM)",
                    "Cloud / Self-Hosted Fallback Required",
                    0
                )
                totalRamGb < 7.6 -> Triple(
                    "ENTRY MOBILE (6 – 8 GB RAM)",
                    MODEL_1_5B,
                    1070
                )
                totalRamGb < 11.5 -> Triple(
                    "STANDARD MOBILE (8 – 12 GB RAM)",
                    MODEL_GEMMA_2B,
                    1630
                )
                else -> Triple(
                    "FLAGSHIP MOBILE (12 GB+ RAM)",
                    MODEL_3B,
                    2010
                )
            }
        }
    }

    /**
     * Returns true if the given local GGUF model name is large enough (7B+) or a dedicated
     * safety classifier (e.g., Llama Guard / ShieldGemma) to perform reliable AI safety review.
     */
    fun isSafetyReviewCapableModel(modelName: String?): Boolean {
        val lower = modelName?.trim()?.lowercase().orEmpty()
        if (lower.isEmpty()) return false
        if (lower.contains("llama-guard") || lower.contains("llamaguard") || lower.contains("shieldgemma")) {
            return true
        }
        val paramMatch = Regex("(?<![0-9.])(\\d+(?:\\.\\d+)?)b(?![a-z])").find(lower)
        val billions = paramMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return false
        return billions >= 7.0
    }

    /**
     * Returns a user-facing caution message for tiny/compact local models (< 3B).
     */
    fun cautionMessageForModel(modelName: String?): String {
        val lower = modelName?.trim()?.lowercase().orEmpty()
        if (lower.isEmpty()) return ""
        val paramMatch = Regex("(?<![0-9.])(\\d+(?:\\.\\d+)?)b(?![a-z])").find(lower)
        val billions = paramMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return ""
        return when {
            billions < 1.0 ->
                "Tiny models (< 1B) come with reduced capabilities and performance. Best suited for lightweight offline tasks and prompt routing, not complex reasoning or AI safety review."
            billions < 2.0 ->
                "Compact models (< 2B) come with reduced capabilities and performance compared to larger desktop or cloud models. Great for offline summaries and routing, but not capable of reliable AI safety review."
            billions < 3.0 ->
                "2B on-device models offer balanced offline text handling, but are too small for reliable adversarial AI safety review."
            else -> ""
        }
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
