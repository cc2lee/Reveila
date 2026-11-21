package com.reveila.aegin.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModelManifestTest {

    @Test
    fun testManifestParsingAndHuggingFaceUrlResolution() {
        val raw = """
            {
              "downloadBaseUrl": "https://huggingface.co",
              "models": [
                {
                  "modelId": "qwen2.5-0.5b-instruct-q4_k_m",
                  "role": "preprocessor-router",
                  "repoPath": "Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                  "approxSizeBytes": 491400000,
                  "requiresAuthToken": false
                },
                {
                  "modelId": "gemma-2-2b-it-Q4_K_M",
                  "role": "local-executor",
                  "repoPath": "bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                  "approxSizeBytes": 1708582752,
                  "requiresAuthToken": false
                }
              ]
            }
        """.trimIndent()

        val manifest = AeginBridge.parseModelManifest(raw)
        assertEquals("https://huggingface.co", manifest.downloadBaseUrl)
        assertEquals(2, manifest.models.size)

        val qwen = AeginBridge.resolveManifestEntry(manifest, "qwen2.5-0.5b-instruct-q4_k_m.gguf")
        assertNotNull(qwen)
        assertEquals("qwen2.5-0.5b-instruct-q4_k_m.gguf", qwen.effectiveFilename())
        assertEquals(
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true",
            AeginBridge.buildManifestDownloadUrl(manifest, qwen)
        )

        // Case-insensitive match works whether UI passes lowercase or exact-case filename
        val gemma = AeginBridge.resolveManifestEntry(manifest, "gemma-2-2b-it-q4_k_m.gguf")
        assertNotNull(gemma)
        assertEquals("gemma-2-2b-it-Q4_K_M.gguf", gemma.effectiveFilename())
        assertEquals(
            "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf?download=true",
            AeginBridge.buildManifestDownloadUrl(manifest, gemma)
        )

        assertEquals("1.6 GB", AeginBridge.formatByteSize(1708582752L))
        assertEquals("469 MB", AeginBridge.formatByteSize(491400000L))
    }

    @Test
    fun testParseLlmSettingsPreservesDeletionAndSelfHostedType() {
        val customJson = """
            {
              "ai.worker.llm": "On-Device Model",
              "ai.governance.llm": "Disable",
              "onboarded.providers": [
                {
                  "Name": "On-Device Model",
                  "Type": "Local",
                  "Endpoint": "local://on-device",
                  "Model": "gemma-2-2b-it-Q4_K_M.gguf",
                  "Intent.Options": ["LOCAL_TEXT_TRANSFORM"]
                },
                {
                  "Name": "Ollama LAN",
                  "Type": "Self-Hosted",
                  "Provider": "OpenAI",
                  "Endpoint": "http://192.168.1.50:11434/v1/chat/completions",
                  "Model": "qwen2.5:14b",
                  "Intent.Options": ["CLOUD_DEEP_REASONING"]
                }
              ]
            }
        """.trimIndent()

        val (providers, _, _) = AeginBridge.parseLlmSettingsJson(customJson)
        assertEquals(2, providers.size)
        assertEquals("Ollama LAN", providers[1].name)
        assertEquals("Self-Hosted", providers[1].type)

        val rebuilt = AeginBridge.buildLlmSettingsJson(
            providers = providers.mapIndexed { idx, p ->
                if (idx == 1) p.copy(tested = true, testedFingerprint = "fp-ollama") else p
            },
            workerProvider = "Ollama LAN",
            governanceProvider = "Disable",
            assistantProvider = "Auto"
        )
        val (reparsed, worker, gov) = AeginBridge.parseLlmSettingsJson(rebuilt)
        assertEquals("Ollama LAN", worker)
        assertEquals("Disable", gov)
        assertEquals("Auto", AeginBridge.extractAssistantLlm(rebuilt))
        assertEquals(2, reparsed.size)
        assertEquals(false, reparsed[0].tested)
        assertEquals(true, reparsed[1].tested)
        assertEquals("fp-ollama", reparsed[1].testedFingerprint)
    }

    @Test
    fun testV2ManifestTargetFilteringRamGatingAndCautionMessages() {
        val raw = """
            {
              "schemaVersion": 2,
              "downloadBaseUrl": "https://huggingface.co",
              "models": [
                {
                  "modelId": "qwen2.5-0.5b-instruct-q4_k_m",
                  "displayName": "Qwen 2.5 0.5B",
                  "filename": "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                  "format": "gguf",
                  "role": "preprocessor-router",
                  "targets": ["android"],
                  "isDefaultFor": [],
                  "minRamGb": 4,
                  "recommendedRamGb": 6,
                  "capabilityTier": "tiny",
                  "safetyReviewCapable": false,
                  "cautionMessage": "Tiny models come with reduced capabilities.",
                  "repoPath": "Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                  "approxSizeBytes": 491400000
                },
                {
                  "modelId": "qwen2.5-1.5b-instruct-q4_k_m",
                  "displayName": "Qwen 2.5 1.5B",
                  "filename": "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                  "format": "gguf",
                  "role": "local-executor",
                  "targets": ["android"],
                  "isDefaultFor": ["android"],
                  "minRamGb": 6,
                  "recommendedRamGb": 8,
                  "capabilityTier": "tiny",
                  "safetyReviewCapable": false,
                  "repoPath": "Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                  "approxSizeBytes": 1117320704
                },
                {
                  "modelId": "qwen2.5-7b-instruct-q4_k_m",
                  "displayName": "Qwen 2.5 7B",
                  "filename": "qwen2.5-7b-instruct-q4_k_m.gguf",
                  "format": "gguf",
                  "role": "local-executor",
                  "targets": ["desktop"],
                  "isDefaultFor": ["desktop"],
                  "minRamGb": 12,
                  "recommendedRamGb": 16,
                  "capabilityTier": "capable",
                  "safetyReviewCapable": true,
                  "repoPath": "Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
                  "approxSizeBytes": 4683073952
                }
              ]
            }
        """.trimIndent()

        val manifest = AeginBridge.parseModelManifest(raw)
        assertEquals(2, manifest.schemaVersion)

        val androidModels = AeginBridge.modelsForTarget(manifest, "android")
        val desktopModels = AeginBridge.modelsForTarget(manifest, "desktop")
        assertEquals(2, androidModels.size)
        assertEquals(1, desktopModels.size)
        assertEquals("qwen2.5-7b-instruct-q4_k_m.gguf", desktopModels[0].effectiveFilename())

        val androidRec = AeginBridge.recommendedEntryForHardware(manifest, "android", 8.0)
        assertNotNull(androidRec)
        assertEquals("qwen2.5-1.5b-instruct-q4_k_m.gguf", androidRec.effectiveFilename())
        assertTrue(androidRec.isTiny())
        assertFalse(androidRec.safetyReviewCapable)
        assertTrue(androidRec.resolvedCautionMessage().isNotBlank())

        val desktopRec = AeginBridge.recommendedEntryForHardware(manifest, "desktop", 16.0)
        assertNotNull(desktopRec)
        assertEquals("qwen2.5-7b-instruct-q4_k_m.gguf", desktopRec.effectiveFilename())
        assertTrue(desktopRec.safetyReviewCapable)
        assertFalse(desktopRec.isCompatibleWithRam(8.0))
        assertTrue(desktopRec.isCompatibleWithRam(12.0))

        // Verify ModelTierCatalog target-aware tiers & safety review heuristics
        assertEquals(ModelTierCatalog.MODEL_7B, ModelTierCatalog.resolveTier(16.0, "desktop").second)
        assertEquals(ModelTierCatalog.MODEL_14B, ModelTierCatalog.resolveTier(32.0, "desktop").second)
        assertEquals(ModelTierCatalog.MODEL_GEMMA_2B, ModelTierCatalog.resolveTier(8.0, "android").second)
        assertFalse(ModelTierCatalog.isSafetyReviewCapableModel(ModelTierCatalog.MODEL_0_5B))
        assertFalse(ModelTierCatalog.isSafetyReviewCapableModel(ModelTierCatalog.MODEL_GEMMA_2B))
        assertTrue(ModelTierCatalog.isSafetyReviewCapableModel(ModelTierCatalog.MODEL_7B))
        assertTrue(ModelTierCatalog.isSafetyReviewCapableModel("llama-guard-3-1b-q4_k_m.gguf"))
    }
}
