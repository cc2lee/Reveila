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
 * Measured hardware & storage environment used to select a suitable default local GGUF model.
 */
data class EnvironmentProfile(
    val target: String = "android",
    val totalRamGb: Double,
    val availableRamGb: Double? = null,
    val freeDiskGb: Double? = null,
    val gpuVramGb: Double = 0.0,
    val hasDiscreteGpu: Boolean = false,
    val hasUnifiedMemoryGpu: Boolean = false,
    val cpuCores: Int = 0,
    val isLowMemory: Boolean = false,
    val diskBufferGb: Double = 1.5
)

/**
 * Environment-validated default local GGUF recommendation, including any automatic step-down
 * caused by GPU/VRAM absence, tight free disk space, or constrained available RAM.
 */
data class ModelRecommendation(
    val hardwareTier: String,
    val recommendedModel: String,
    val recommendedModelSizeMb: Int,
    val hasEnoughStorage: Boolean,
    val downgradedReason: String = ""
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

    private data class CandidateSpec(
        val model: String,
        val sizeMb: Int,
        val minRamGb: Double,
        val minAvailRamGb: Double,
        val tierLabel: String
    )

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
        val rec = recommendModel(
            EnvironmentProfile(
                target = target,
                totalRamGb = totalRamGb,
                // Preserve legacy RAM-only callers by assuming adequate GPU/disk when not supplied
                gpuVramGb = if (isDesktop && totalRamGb >= 23.0) 12.0 else if (isDesktop && totalRamGb >= 11.5) 8.0 else 0.0,
                hasDiscreteGpu = isDesktop && totalRamGb >= 11.5
            )
        )
        return Triple(rec.hardwareTier, rec.recommendedModel, rec.recommendedModelSizeMb)
    }

    /**
     * Selects the highest-capability local GGUF model that is genuinely suitable for the target
     * device's RAM, GPU/VRAM accelerator, CPU cores, and free disk space, automatically stepping
     * down rather than failing when a smaller model fits comfortably.
     */
    fun recommendModel(profile: EnvironmentProfile): ModelRecommendation {
        val isDesktop = profile.target.equals("desktop", ignoreCase = true)
        if (profile.totalRamGb < 5.5) {
            return ModelRecommendation(
                hardwareTier = "BLOCKED (< 6 GB RAM)",
                recommendedModel = "Cloud / Self-Hosted Fallback Required",
                recommendedModelSizeMb = 0,
                hasEnoughStorage = false
            )
        }

        val candidates = if (isDesktop) {
            listOf(
                CandidateSpec(MODEL_14B, 8570, 23.0, 10.5, "POWER DESKTOP (24 GB+ RAM)"),
                CandidateSpec(MODEL_7B, 4470, 11.5, 5.5, "STANDARD DESKTOP (12 – 24 GB RAM)"),
                CandidateSpec(MODEL_3B, 2010, 7.6, 2.8, "LIGHT DESKTOP (8 – 12 GB RAM)"),
                CandidateSpec(MODEL_GEMMA_2B, 1630, 5.5, 2.2, "ENTRY DESKTOP (6 – 8 GB RAM)"),
                CandidateSpec(MODEL_1_5B, 1070, 5.5, 1.5, "COMPACT DESKTOP (Storage/RAM Constrained)"),
                CandidateSpec(MODEL_0_5B, 470, 5.5, 0.9, "MINIMAL DESKTOP (Ultra-Low Storage/RAM)")
            )
        } else {
            listOf(
                CandidateSpec(MODEL_3B, 2010, 11.5, 3.0, "FLAGSHIP MOBILE (12 GB+ RAM)"),
                CandidateSpec(MODEL_GEMMA_2B, 1630, 7.6, 2.2, "STANDARD MOBILE (8 – 12 GB RAM)"),
                CandidateSpec(MODEL_1_5B, 1070, 5.5, 1.5, "ENTRY MOBILE (6 – 8 GB RAM)"),
                CandidateSpec(MODEL_0_5B, 470, 5.5, 0.9, "COMPACT MOBILE (Storage/RAM Constrained)")
            )
        }

        val ramEligible = candidates.filter { profile.totalRamGb >= it.minRamGb }
        val idealByRam = ramEligible.first()

        fun isAcceleratorSuitable(spec: CandidateSpec): Pair<Boolean, String?> {
            if (!isDesktop) return true to null
            val effectiveVramGb = when {
                profile.hasUnifiedMemoryGpu -> profile.gpuVramGb.coerceAtLeast(profile.totalRamGb * 0.7)
                profile.hasDiscreteGpu || profile.gpuVramGb > 0.0 -> profile.gpuVramGb
                else -> 0.0
            }
            return when (spec.model) {
                MODEL_14B -> {
                    val ok = effectiveVramGb >= 10.0 || (profile.hasUnifiedMemoryGpu && profile.totalRamGb >= 23.0)
                    if (ok) true to null
                    else false to "No GPU with >= 10 GB VRAM detected for 14B acceleration"
                }
                MODEL_7B -> {
                    val hasStrongCpu = profile.totalRamGb >= 15.5 && (profile.cpuCores == 0 || profile.cpuCores >= 6)
                    val ok = effectiveVramGb >= 5.5 || (profile.hasUnifiedMemoryGpu && profile.totalRamGb >= 15.0) || hasStrongCpu
                    if (ok) true to null
                    else false to "GPU VRAM (< 5.5 GB) / CPU headroom insufficient for smooth 7B inference"
                }
                else -> true to null
            }
        }

        fun hasEnoughDisk(spec: CandidateSpec): Boolean {
            val freeGb = profile.freeDiskGb ?: return true
            val requiredGb = (spec.sizeMb / 1024.0) + profile.diskBufferGb
            return freeGb >= requiredGb
        }

        fun hasEnoughAvailRam(spec: CandidateSpec): Boolean {
            if (profile.isLowMemory) return spec.model == MODEL_0_5B || spec.model == MODEL_1_5B
            val avail = profile.availableRamGb ?: return true
            return avail >= spec.minAvailRamGb
        }

        val reasons = mutableListOf<String>()
        var chosen: CandidateSpec? = null

        for (spec in ramEligible) {
            val (accelOk, accelReason) = isAcceleratorSuitable(spec)
            if (!accelOk) {
                if (spec == idealByRam && accelReason != null) reasons.add(accelReason)
                continue
            }
            if (!hasEnoughDisk(spec)) {
                if (reasons.none { it.contains("disk", ignoreCase = true) }) {
                    val freeRounded = profile.freeDiskGb?.let { (kotlin.math.round(it * 10.0) / 10.0) }
                    reasons.add("Free disk space (${freeRounded} GB) below ${spec.sizeMb} MB + ${profile.diskBufferGb} GB buffer")
                }
                continue
            }
            if (!hasEnoughAvailRam(spec)) {
                if (reasons.none { it.contains("RAM", ignoreCase = true) }) {
                    val availRounded = profile.availableRamGb?.let { (kotlin.math.round(it * 10.0) / 10.0) }
                    reasons.add("Available RAM (${availRounded} GB) currently constrained")
                }
                continue
            }
            chosen = spec
            break
        }

        if (chosen != null) {
            val downgraded = if (chosen.model != idealByRam.model && reasons.isNotEmpty()) {
                "Stepped down from ${idealByRam.model}: ${reasons.joinToString("; ")}"
            } else {
                ""
            }
            val tierLabel = if (isDesktop && profile.hasDiscreteGpu && profile.gpuVramGb >= 5.5) {
                "${chosen.tierLabel} • GPU ${(kotlin.math.round(profile.gpuVramGb * 10.0) / 10.0)} GB VRAM"
            } else if (isDesktop && profile.hasUnifiedMemoryGpu) {
                "${chosen.tierLabel} • Unified GPU"
            } else if (isDesktop) {
                "${chosen.tierLabel} • CPU"
            } else {
                chosen.tierLabel
            }
            return ModelRecommendation(
                hardwareTier = tierLabel,
                recommendedModel = chosen.model,
                recommendedModelSizeMb = chosen.sizeMb,
                hasEnoughStorage = true,
                downgradedReason = downgraded
            )
        }

        // Even the smallest model does not satisfy disk/RAM constraints — report smallest candidate and block storage.
        val smallest = ramEligible.last()
        return ModelRecommendation(
            hardwareTier = smallest.tierLabel,
            recommendedModel = smallest.model,
            recommendedModelSizeMb = smallest.sizeMb,
            hasEnoughStorage = hasEnoughDisk(smallest),
            downgradedReason = reasons.joinToString("; ")
        )
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
