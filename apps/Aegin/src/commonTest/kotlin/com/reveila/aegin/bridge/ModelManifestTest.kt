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

    @Test
    fun testLlmSettingsRoundTripsCostPer1MAndModelPricing() {
        val raw = """
            {
              "ai.worker.llm": "Auto",
              "ai.governance.llm": "Disable",
              "onboarded.providers": [
                {
                  "Name": "DeepSeek",
                  "Type": "Cloud",
                  "Provider": "DeepSeek",
                  "Endpoint": "https://api.deepseek.com/chat/completions",
                  "Model": "deepseek-reasoner",
                  "Model.Options": ["deepseek-chat", "deepseek-reasoner"],
                  "Cost.Per1MTokens": 0.4,
                  "Model.Pricing": {
                    "deepseek-chat": 0.4,
                    "deepseek-reasoner": 1.2
                  },
                  "Intent.Options": ["CLOUD_DEEP_REASONING"]
                }
              ]
            }
        """.trimIndent()

        val (providers, worker, gov) = AeginBridge.parseLlmSettingsJson(raw)
        val deepSeek = providers.first { it.name == "DeepSeek" }
        assertEquals(0.4, deepSeek.costPer1MTokens)
        assertEquals(0.4, deepSeek.modelPricing["deepseek-chat"])
        assertEquals(1.2, deepSeek.modelPricing["deepseek-reasoner"])

        val rebuilt = AeginBridge.buildLlmSettingsJson(providers, worker, gov, "Auto")
        val (reparsed, _, _) = AeginBridge.parseLlmSettingsJson(rebuilt)
        val reparsedDeepSeek = reparsed.first { it.name == "DeepSeek" }
        assertEquals(0.4, reparsedDeepSeek.costPer1MTokens)
        assertEquals(0.4, reparsedDeepSeek.modelPricing["deepseek-chat"])
        assertEquals(1.2, reparsedDeepSeek.modelPricing["deepseek-reasoner"])
    }

    @Test
    fun testAlertChannelsRoundTripParsingAndSerialization() {
        val channels = listOf(
            AlertChannelConfig(
                id = "slack-ops",
                type = "SLACK",
                enabled = true,
                endpoint = "REF:SLACK_OPS_WEBHOOK_URL",
                channel = "#general",
                minSeverity = "SUCCESS"
            ),
            AlertChannelConfig(
                id = "email-digest",
                type = "EMAIL",
                enabled = true,
                endpoint = "https://api.resend.com/emails",
                apiKey = "REF:EMAIL_DIGEST_API_KEY",
                from = "alerts@aegin.app",
                to = listOf("me@example.com", "team@example.com"),
                minSeverity = "WARNING"
            )
        )

        val serialized = AeginBridge.buildChannelsSettingsJson(channels)
        val reparsed = AeginBridge.parseChannelsSettingsJson(serialized)
        assertEquals(2, reparsed.size)
        assertEquals("SLACK", reparsed[0].type)
        assertTrue(reparsed[0].enabled)
        assertEquals("REF:SLACK_OPS_WEBHOOK_URL", reparsed[0].endpoint)
        assertEquals("#general", reparsed[0].channel)
        assertEquals("EMAIL", reparsed[1].type)
        assertEquals("REF:EMAIL_DIGEST_API_KEY", reparsed[1].apiKey)
        assertEquals(listOf("me@example.com", "team@example.com"), reparsed[1].to)
    }

    @Test
    fun testGovernancePolicyParsingAndUpdatePayload() {
        val sample = """
            {
              "policyName": "SOVEREIGN_ACCESS_CONTROL_v2",
              "version": "2.0.0",
              "restrictedKeywords": ["SSN", "TAX_ID"],
              "promptInjectionPatterns": ["jailbreak", "ignore previous instructions"],
              "prohibitedSystemCommands": ["rm -rf"],
              "sensitiveDataPatterns": ["\\b\\d{3}-\\d{2}-\\d{4}\\b"],
              "thresholds": {
                "alertThreshold": 0.55,
                "hitlApprovalThreshold": 0.65,
                "blockThreshold": 0.85
              },
              "dataRetention": {
                "auditLogRetentionDays": 90
              }
            }
        """.trimIndent()

        val parsed = AeginBridge.parseGovernancePolicyJson(sample)
        assertEquals(listOf("SSN", "TAX_ID"), parsed.restrictedKeywords)
        assertEquals("STRICT", parsed.detectedPreset())
        assertEquals(90, parsed.auditLogRetentionDays)
        assertEquals(2, parsed.promptInjectionPatternCount)
        assertEquals(1, parsed.prohibitedCommandCount)
        assertEquals(1, parsed.sensitiveDataPatternCount)

        val updateJson = AeginBridge.buildGovernancePolicyUpdateJson(parsed.copy(auditLogRetentionDays = 30))
        val reparsed = AeginBridge.parseGovernancePolicyJson(updateJson)
        assertEquals(30, reparsed.auditLogRetentionDays)
        assertEquals(listOf("SSN", "TAX_ID"), reparsed.restrictedKeywords)
    }
}
