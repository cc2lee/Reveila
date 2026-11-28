package com.reveila.channels

import com.reveila.ai.AgentInstructionStore
import com.reveila.ai.AgentSessionManager
import com.reveila.ai.AgenticFabric
import com.reveila.ai.LlmProvider
import com.reveila.ai.LlmProviderFactory
import com.reveila.ai.LlmRequest
import com.reveila.ai.LlmResponse
import com.reveila.ai.LlmRole
import com.reveila.ai.OrchestrationService
import com.reveila.ai.TaskRoutingCategory
import com.reveila.crypto.Cryptographer
import com.reveila.safety.ManagedInvocation
import com.reveila.safety.MetadataRegistry
import com.reveila.service.DataService
import com.reveila.system.Context
import com.reveila.system.PlatformAdapter
import com.reveila.system.Properties
import com.reveila.system.Proxy
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.logging.PlatformLogger
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.json.JsonUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InboundChannelGatewayTest {

    private class RecordingLlmProvider(
        var replyText: String = """{"status":"completed","reasoning":"ok","result":"1. Review deck\n2. Email team","confidence-score":0.95}"""
    ) : LlmProvider {
        val capturedRequests = mutableListOf<LlmRequest>()
        override fun invoke(request: LlmRequest): LlmResponse {
            capturedRequests.add(request)
            return LlmResponse(replyText, "stop", null, null, null)
        }
        override fun isEnabled(): Boolean = true
        override fun isConfigured(): Boolean = true
        override fun getName(): String = "TestCloudLLM"
        override fun getSupportedIntents(): List<String> = TaskRoutingCategory.entries.map { it.name }
    }

    private class StubLlmFactory(private val provider: LlmProvider) : LlmProviderFactory() {
        override fun onStart() {}
        override fun getActiveProvider(): LlmProvider = provider
        override fun getProvider(name: String?): LlmProvider = provider
        override fun isProviderConfigured(name: String?): Boolean = true
        override fun getProviderForIntent(intentCategory: String?): LlmProvider = provider
        override fun resolveWorkerProviderForIntent(intentCategory: String?, forceAuto: Boolean): ProviderSelectionResult {
            return ProviderSelectionResult(provider, provider.getName(), forceAuto, true, TaskRoutingCategory.fromRawOrSafeDefault(intentCategory).name)
        }
        override fun resolveExplicitProviderForIntent(providerName: String, intentCategory: String?): ProviderSelectionResult {
            return ProviderSelectionResult(provider, provider.getName(), false, true, TaskRoutingCategory.fromRawOrSafeDefault(intentCategory).name)
        }
        override fun resolveGovernanceProviderForIntent(intentCategory: String?): ProviderSelectionResult {
            return ProviderSelectionResult(null, "Disable", false, true, TaskRoutingCategory.fromRawOrSafeDefault(intentCategory).name)
        }
    }

    private class SimpleProxy(private val name: String, private val target: Any) : Proxy {
        override fun getName(): String = name
        override fun getRequiredRoles(): List<String> = emptyList()
        override fun invoke(methodName: String, args: Array<out Any?>?): Any? = null
        override fun getInstance(): Any = target
    }

    private fun freshDir(tag: String): String {
        val dir = "./build/tmp/channel-gw-$tag-${PlatformSystem.currentTimeMillis()}"
        val fs = PlatformFileSystem()
        if (fs.exists(dir)) fs.delete(dir, recursive = true)
        fs.createDirectories(dir)
        return dir
    }

    private fun buildGatewayEnv(
        dir: String,
        provider: RecordingLlmProvider,
        httpPost: ChannelHttpPoster = { _, _, _ -> """{"ok":true}""" }
    ): Triple<InboundChannelGateway, AgenticFabric, OrchestrationService> {
        val props = Properties().apply {
            setProperty("data.dir", dir)
            setProperty("system.home", dir)
            setProperty("ai.worker.llm", provider.getName())
            setProperty("ai.assistant.llm", provider.getName())
            setProperty("ai.governance.llm", "Disable")
            setProperty("channels.whatsapp.verify.token", "my-wa-verify-secret")
        }
        val proxies = mutableMapOf<String, Proxy>()
        val ctx = object : Context {
            override fun getLogger(): PlatformLogger? = null
            override val properties: Properties = props
            override val cryptographer: Cryptographer? = null
            override val platformAdapter: PlatformAdapter? = null
            override fun getProxy(name: String): Proxy = proxies[name] ?: throw IllegalArgumentException("Missing $name")
        }

        val ds = DataService().apply { context = ctx; start() }
        proxies["DataService"] = SimpleProxy("DataService", ds)
        val sm = AgentSessionManager().apply { context = ctx; start() }
        proxies["AgentSessionManager"] = SimpleProxy("AgentSessionManager", sm)
        val orch = OrchestrationService().apply { context = ctx; start() }
        proxies["OrchestrationService"] = SimpleProxy("OrchestrationService", orch)
        val mr = MetadataRegistry().apply { context = ctx; start() }
        proxies["MetadataRegistry"] = SimpleProxy("MetadataRegistry", mr)
        val mi = object : ManagedInvocation() { override fun onStart() {} }.apply { context = ctx; start() }
        proxies["ManagedInvocation"] = SimpleProxy("ManagedInvocation", mi)
        val lf = StubLlmFactory(provider).apply { context = ctx; start() }
        proxies["LlmProviderFactory"] = SimpleProxy("LlmProviderFactory", lf)
        val instStore = AgentInstructionStore().apply { context = ctx; start() }
        proxies[AgentInstructionStore.COMPONENT_NAME] = SimpleProxy(AgentInstructionStore.COMPONENT_NAME, instStore)
        val fabric = AgenticFabric().apply { context = ctx; start() }
        proxies[AgenticFabric.COMPONENT_NAME] = SimpleProxy(AgenticFabric.COMPONENT_NAME, fabric)

        val hub = EnterpriseChannelHub(httpPost = httpPost)
        val gateway = InboundChannelGateway(channelHub = hub).apply { context = ctx; start() }
        return Triple(gateway, fabric, orch)
    }

    @Test
    fun teamsInboundWebhookAppliesStandingInstructionsAndStampsReplyChannel() {
        val dir = freshDir("teams")
        val provider = RecordingLlmProvider()
        val (gateway, fabric, orch) = buildGatewayEnv(dir, provider)

        // Save standing instruction for Teams user in tenant-contoso
        fabric.saveStandingInstruction(
            text = "Always format your response in a numbered list if they're tasks.",
            tenantId = "tenant-contoso",
            userId = "aad-user-alice"
        )

        val teamsPayload = mapOf(
            "id" to "teams-msg-1",
            "text" to "<at>Reveila</at> Give me my action items for today",
            "from" to mapOf("id" to "29:123", "aadObjectId" to "aad-user-alice"),
            "conversation" to mapOf("id" to "19:meeting_abc@thread.v2"),
            "channelData" to mapOf("tenant" to mapOf("id" to "tenant-contoso"))
        )

        val result = gateway.handleInboundWebhook("teams", teamsPayload)
        assertEquals(true, result["ok"])
        assertEquals("message", result["type"])
        assertTrue(result["text"].toString().contains("1. Review deck"))

        // Verify standing instruction was injected into the system prompt for this Teams user
        val systemMsg = provider.capturedRequests.last().messages.first { it.role == LlmRole.SYSTEM }.content
        assertTrue(systemMsg.contains("Always format your response in a numbered list if they're tasks."))

        // Verify session carries reply routing metadata for follow-up reminders/tasks
        val session = orch.getSession(result["sessionId"].toString())
        assertEquals("teams", session?.get("_reply_channel"))
        assertEquals("19:meeting_abc@thread.v2", session?.get("_reply_target"))
    }

    @Test
    fun whatsappInboundWebhookChallengeAndOutboundCloudApiDelivery() {
        val dir = freshDir("whatsapp")
        val provider = RecordingLlmProvider()
        var postedUrl = ""
        var postedBody = ""
        var postedAuth = ""

        val (gateway, _, _) = buildGatewayEnv(dir, provider) { url, payload, headers ->
            postedUrl = url
            postedBody = payload
            postedAuth = headers["Authorization"].orEmpty()
            """{"messages":[{"id":"wamid.out.1"}]}"""
        }

        // 1. Meta GET verification challenge
        val badVerify = gateway.verifyWebhookChallenge("whatsapp", "wrong-token", "challenge-123")
        assertEquals(false, badVerify["ok"])
        val okVerify = gateway.verifyWebhookChallenge("whatsapp", "my-wa-verify-secret", "challenge-123")
        assertEquals(true, okVerify["ok"])
        assertEquals("challenge-123", okVerify["challenge"])

        // 2. Register outbound WhatsApp Cloud API channel
        gateway.channelHub.registerChannel(
            EnterpriseChannelConfig(
                id = "whatsapp",
                type = ChannelType.WHATSAPP,
                enabled = true,
                endpointUrl = "https://graph.facebook.com/v20.0/123456789/messages",
                secretRef = "wa-bearer-token-xyz"
            )
        )

        // 3. Inbound Meta WhatsApp Cloud API webhook payload
        val waPayload = mapOf(
            "entry" to listOf(
                mapOf(
                    "changes" to listOf(
                        mapOf(
                            "value" to mapOf(
                                "messages" to listOf(
                                    mapOf(
                                        "id" to "wamid.in.99",
                                        "from" to "15550199",
                                        "type" to "text",
                                        "text" to mapOf("body" to "List my tasks for today")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val result = gateway.handleInboundWebhook("whatsapp", waPayload, autoDispatchReply = true)
        assertEquals(true, result["ok"])
        assertEquals(1, result["dispatchedCount"])
        assertEquals("https://graph.facebook.com/v20.0/123456789/messages", postedUrl)
        assertEquals("Bearer wa-bearer-token-xyz", postedAuth)

        val sentMap = JsonUtil.parseJsonStringToMap(postedBody)
        assertEquals("whatsapp", sentMap["messaging_product"])
        assertEquals("15550199", sentMap["to"])
    }

    @Test
    fun slackChallengeAndBotMessageFiltering() {
        val dir = freshDir("slack")
        val provider = RecordingLlmProvider()
        val (gateway, _, _) = buildGatewayEnv(dir, provider)

        val challengeRes = gateway.handleInboundWebhook(
            "slack",
            mapOf("type" to "url_verification", "challenge" to "slack-xyz")
        )
        assertEquals("slack-xyz", challengeRes["challenge"])

        // Bot echoes should be ignored
        val botEvent = gateway.parseInboundPayload(
            "slack",
            ChannelType.SLACK,
            mapOf("event" to mapOf("bot_id" to "B123", "text" to "echo"))
        )
        assertNull(botEvent)
    }
}
