package com.reveila.channels

import com.reveila.ai.AgentInstruction
import com.reveila.ai.AgentSession
import com.reveila.ai.AgenticFabric
import com.reveila.ai.OrchestrationService
import com.reveila.system.SystemComponent
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil

/**
 * Normalized inbound natural-language chat message from any connected channel
 * (Microsoft Teams, WhatsApp, Slack, or custom chat webhook).
 */
data class InboundChatMessage(
    val channelType: ChannelType,
    val channelId: String,
    val senderId: String,
    val conversationId: String,
    val tenantId: String = AgentInstruction.DEFAULT_TENANT_ID,
    val spaceId: String = AgentSession.DEFAULT_SPACE_ID,
    val text: String,
    val messageId: String = Uuid.randomUuid(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Outbound adapter for Meta WhatsApp Cloud API (`/{phoneNumberId}/messages`).
 */
class WhatsAppChannelAdapter : EnterpriseChannelAdapter {
    override val type: ChannelType = ChannelType.WHATSAPP

    override fun formatPayload(alert: ProactiveAlertMessage, config: EnterpriseChannelConfig): String {
        val recipient = alert.metadata["replyTarget"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: config.targetChannel.trim().takeIf { it.isNotEmpty() }
            ?: config.toAddresses.firstOrNull()?.trim().orEmpty()
        val bodyText = buildString {
            if (alert.title.isNotBlank()) {
                append("*${alert.title}*\n")
            }
            append(alert.summary.take(3800).ifBlank { "Status: ${alert.status}" })
        }
        val payload = linkedMapOf<String, Any?>(
            "messaging_product" to "whatsapp",
            "recipient_type" to "individual",
            "to" to recipient,
            "type" to "text",
            "text" to mapOf(
                "preview_url" to false,
                "body" to bodyText
            )
        )
        return JsonUtil.toJsonString(payload)
    }

    override fun send(
        alert: ProactiveAlertMessage,
        config: EnterpriseChannelConfig,
        resolvedSecret: String,
        httpPost: ChannelHttpPoster
    ): ChannelDeliveryResult {
        val url = config.endpointUrl.trim()
        if (url.isBlank()) {
            return ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = false,
                message = "WhatsApp Cloud API endpoint URL is not configured."
            )
        }
        val headers = linkedMapOf("Content-Type" to "application/json; charset=utf-8")
        if (resolvedSecret.isNotBlank()) {
            headers["Authorization"] = "Bearer $resolvedSecret"
        }
        val payload = formatPayload(alert, config)
        return try {
            val response = httpPost(url, payload, headers)
            ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = true,
                message = "Delivered to WhatsApp (${response.take(120).ifBlank { "ok" }})"
            )
        } catch (e: Exception) {
            ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = false,
                message = "WhatsApp delivery failed: ${e.message}"
            )
        }
    }
}

/**
 * Channel-agnostic inbound chat gateway that routes natural-language messages from
 * Microsoft Teams, WhatsApp, Slack, and custom chat apps directly into [AgenticFabric]
 * while preserving per-tenant/per-user standing instructions and originating reply-channel routing.
 */
open class InboundChannelGateway(
    private var configuredFabric: AgenticFabric? = null,
    private var configuredOrchestration: OrchestrationService? = null,
    var channelHub: EnterpriseChannelHub = EnterpriseChannelHub()
) : SystemComponent() {

    companion object {
        const val COMPONENT_NAME: String = "InboundChannelGateway"
    }

    override fun onStart() {
        resolveFabric()
        resolveOrchestration()
        logger.info("InboundChannelGateway started (Teams, WhatsApp, Slack, Custom).")
    }

    override fun onStop() {
    }

    private fun resolveFabric(): AgenticFabric? {
        if (configuredFabric == null) {
            try {
                val p = context?.getProxy(AgenticFabric.COMPONENT_NAME)
                configuredFabric = (p?.getInstance() as? AgenticFabric) ?: p as? AgenticFabric
            } catch (_: Exception) {
            }
        }
        return configuredFabric
    }

    private fun resolveOrchestration(): OrchestrationService? {
        if (configuredOrchestration == null) {
            try {
                val p = context?.getProxy("OrchestrationService")
                configuredOrchestration = (p?.getInstance() as? OrchestrationService) ?: p as? OrchestrationService
            } catch (_: Exception) {
            }
        }
        return configuredOrchestration
    }

    /**
     * Handles webhook verification handshakes (e.g. Meta WhatsApp `hub.challenge` GET verification).
     */
    open fun verifyWebhookChallenge(
        channel: String,
        verifyToken: String?,
        challenge: String?
    ): Map<String, Any?> {
        val expected = context?.properties?.getProperty("channels.${channel.trim().lowercase()}.verify.token")?.trim().orEmpty()
        if (expected.isNotEmpty() && verifyToken?.trim() != expected) {
            return mapOf("ok" to false, "status" to 403, "error" to "Invalid webhook verify token.")
        }
        return mapOf(
            "ok" to true,
            "status" to 200,
            "challenge" to (challenge ?: "")
        )
    }

    /**
     * Unified entry point invoked by Spring `ChannelWebhookController` and Aegin `DesktopClusterHttpServer`.
     * Parses platform-native webhook JSON (Teams, WhatsApp, Slack, or generic), executes the user's
     * natural-language request in [AgenticFabric], and returns a channel-appropriate response payload.
     */
    open fun handleInboundWebhook(
        channel: String,
        payload: Map<String, Any?>,
        autoDispatchReply: Boolean = false
    ): Map<String, Any?> {
        val channelType = ChannelType.fromRaw(channel) ?: ChannelType.CUSTOM_WEBHOOK

        // Handle Slack Events API URL verification challenge immediately
        if (channelType == ChannelType.SLACK && payload["type"]?.toString() == "url_verification") {
            return mapOf("challenge" to (payload["challenge"]?.toString() ?: ""))
        }

        val parsed = parseInboundPayload(channel, channelType, payload)
            ?: return mapOf(
                "ok" to true,
                "ignored" to true,
                "message" to "No actionable user message in webhook payload."
            )

        return processMessage(parsed, autoDispatchReply = autoDispatchReply)
    }

    /**
     * Processes a normalized [InboundChatMessage] through [AgenticFabric] with authenticated
     * `(tenantId, userId)` and `_reply_channel` / `_reply_target` stamped onto the session.
     */
    open fun processMessage(
        message: InboundChatMessage,
        autoDispatchReply: Boolean = false
    ): Map<String, Any?> {
        val fabric = resolveFabric()
            ?: return mapOf("ok" to false, "error" to "AgenticFabric is not available.")

        val normTenant = AgentInstruction.normalizeTenantId(message.tenantId)
        val normUser = AgentInstruction.normalizeUserId(message.senderId)
        val normSpace = AgentSession.normalizeSpaceId(message.spaceId)
        val sessionId = "ch-${message.channelId.lowercase()}-$normUser-$normSpace"

        val orch = resolveOrchestration()
        val session = orch?.getOrCreateSpaceSession(
            spaceId = normSpace,
            spaceName = AgentSession.displayNameForSpace(normSpace),
            preferredSessionId = sessionId
        )
        session?.put("_tenant_id", normTenant)
        session?.put("_user_id", normUser)
        session?.put("_reply_channel", message.channelId)
        session?.put("_reply_target", message.conversationId)

        val response = fabric.askAgentInSpace(
            userIntent = message.text,
            spaceId = normSpace,
            sessionId = session?.sessionId ?: sessionId
        )
        val answer = response["answer"]?.toString().orEmpty()

        val deliveries = if (autoDispatchReply && answer.isNotBlank()) {
            try {
                val alert = ProactiveAlertMessage(
                    eventId = "reply-${PlatformSystem.currentTimeMillis()}-${Uuid.randomUuid().take(4)}",
                    taskId = "chat-reply",
                    title = "",
                    status = "COMPLETED",
                    summary = answer,
                    severity = AlertSeverity.INFO,
                    timestampMs = PlatformSystem.currentTimeMillis(),
                    metadata = linkedMapOf(
                        "replyChannel" to message.channelId,
                        "replyTarget" to message.conversationId,
                        "inReplyTo" to message.messageId
                    )
                )
                channelHub.dispatchAlert(alert, onlyChannelIds = setOf(message.channelId))
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Format response so Microsoft Teams Outgoing Webhooks can render `type` + `text` directly
        return linkedMapOf(
            "ok" to true,
            "type" to "message",
            "text" to answer,
            "answer" to answer,
            "status" to (response["status"] ?: "completed"),
            "sessionId" to (response["sessionId"] ?: sessionId),
            "spaceId" to normSpace,
            "channel" to message.channelId,
            "replyTarget" to message.conversationId,
            "dispatchedCount" to deliveries.count { it.ok }
        )
    }

    /**
     * Parses native webhook payloads from Microsoft Teams, WhatsApp Cloud API, Slack Events API,
     * or generic JSON into a unified [InboundChatMessage].
     */
    @Suppress("UNCHECKED_CAST")
    fun parseInboundPayload(
        channel: String,
        channelType: ChannelType,
        payload: Map<String, Any?>
    ): InboundChatMessage? {
        val defaultTenant = context?.properties?.getProperty("reveila.tenant.id")?.takeIf { it.isNotBlank() }
            ?: AgentInstruction.DEFAULT_TENANT_ID
        val channelId = channel.trim().lowercase().ifBlank { channelType.name.lowercase() }

        return when (channelType) {
            ChannelType.MICROSOFT_TEAMS -> {
                val rawText = payload["text"]?.toString().orEmpty()
                val cleanedText = rawText.replace(Regex("<at>.*?</at>", RegexOption.IGNORE_CASE), "").trim()
                if (cleanedText.isEmpty()) return null
                val fromMap = payload["from"] as? Map<*, *>
                val convMap = payload["conversation"] as? Map<*, *>
                val channelData = payload["channelData"] as? Map<*, *>
                val tenantMap = channelData?.get("tenant") as? Map<*, *>

                val sender = fromMap?.get("aadObjectId")?.toString()?.takeIf { it.isNotBlank() }
                    ?: fromMap?.get("id")?.toString()?.takeIf { it.isNotBlank() }
                    ?: payload["userId"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: "teams-user"
                val conversationId = convMap?.get("id")?.toString()?.takeIf { it.isNotBlank() }
                    ?: sender
                val tenantId = tenantMap?.get("id")?.toString()?.takeIf { it.isNotBlank() }
                    ?: payload["tenantId"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: defaultTenant
                val msgId = payload["id"]?.toString()?.takeIf { it.isNotBlank() } ?: Uuid.randomUuid()

                InboundChatMessage(
                    channelType = ChannelType.MICROSOFT_TEAMS,
                    channelId = channelId,
                    senderId = sender,
                    conversationId = conversationId,
                    tenantId = tenantId,
                    spaceId = payload["spaceId"]?.toString()?.takeIf { it.isNotBlank() } ?: AgentSession.DEFAULT_SPACE_ID,
                    text = cleanedText,
                    messageId = msgId
                )
            }

            ChannelType.WHATSAPP -> {
                // Meta WhatsApp Cloud API format: entry[0].changes[0].value.messages[0]
                val entries = payload["entry"] as? List<*>
                val firstEntry = entries?.firstOrNull() as? Map<*, *>
                val changes = firstEntry?.get("changes") as? List<*>
                val firstChange = changes?.firstOrNull() as? Map<*, *>
                val value = firstChange?.get("value") as? Map<*, *>
                val messages = value?.get("messages") as? List<*>
                val firstMsg = messages?.firstOrNull() as? Map<*, *>

                if (firstMsg != null) {
                    val textObj = firstMsg["text"] as? Map<*, *>
                    val body = textObj?.get("body")?.toString()?.trim().orEmpty()
                    if (body.isEmpty()) return null
                    val fromPhone = firstMsg["from"]?.toString()?.trim().orEmpty().ifBlank { "whatsapp-user" }
                    val msgId = firstMsg["id"]?.toString()?.trim().orEmpty().ifBlank { Uuid.randomUuid() }
                    return InboundChatMessage(
                        channelType = ChannelType.WHATSAPP,
                        channelId = channelId,
                        senderId = fromPhone,
                        conversationId = fromPhone,
                        tenantId = payload["tenantId"]?.toString()?.takeIf { it.isNotBlank() } ?: defaultTenant,
                        spaceId = payload["spaceId"]?.toString()?.takeIf { it.isNotBlank() } ?: AgentSession.DEFAULT_SPACE_ID,
                        text = body,
                        messageId = msgId
                    )
                }

                // Fallback flat JSON format (e.g. Twilio / custom WhatsApp bridge)
                val body = (payload["Body"] ?: payload["text"] ?: payload["message"])?.toString()?.trim().orEmpty()
                if (body.isEmpty()) return null
                val from = (payload["From"] ?: payload["from"] ?: payload["userId"])?.toString()?.trim().orEmpty().ifBlank { "whatsapp-user" }
                InboundChatMessage(
                    channelType = ChannelType.WHATSAPP,
                    channelId = channelId,
                    senderId = from,
                    conversationId = from,
                    tenantId = payload["tenantId"]?.toString()?.takeIf { it.isNotBlank() } ?: defaultTenant,
                    spaceId = payload["spaceId"]?.toString()?.takeIf { it.isNotBlank() } ?: AgentSession.DEFAULT_SPACE_ID,
                    text = body
                )
            }

            ChannelType.SLACK -> {
                val event = payload["event"] as? Map<*, *>
                if (event != null && event["bot_id"] != null) return null
                val rawText = (event?.get("text") ?: payload["text"] ?: payload["message"])?.toString().orEmpty()
                val cleaned = rawText.replace(Regex("<@[A-Z0-9]+>"), "").trim()
                if (cleaned.isEmpty()) return null
                val sender = (event?.get("user") ?: payload["user_id"] ?: payload["userId"])?.toString()?.takeIf { it.isNotBlank() }
                    ?: "slack-user"
                val conv = (event?.get("channel") ?: payload["channel_id"] ?: payload["conversationId"])?.toString()?.takeIf { it.isNotBlank() }
                    ?: sender
                val tenant = (payload["team_id"] ?: payload["tenantId"])?.toString()?.takeIf { it.isNotBlank() }
                    ?: defaultTenant
                InboundChatMessage(
                    channelType = ChannelType.SLACK,
                    channelId = channelId,
                    senderId = sender,
                    conversationId = conv,
                    tenantId = tenant,
                    spaceId = payload["spaceId"]?.toString()?.takeIf { it.isNotBlank() } ?: AgentSession.DEFAULT_SPACE_ID,
                    text = cleaned,
                    messageId = (event?.get("client_msg_id") ?: payload["event_id"])?.toString() ?: Uuid.randomUuid()
                )
            }

            else -> {
                val text = (payload["text"] ?: payload["message"] ?: payload["prompt"])?.toString()?.trim().orEmpty()
                if (text.isEmpty()) return null
                val sender = (payload["userId"] ?: payload["senderId"] ?: payload["from"])?.toString()?.takeIf { it.isNotBlank() }
                    ?: AgentInstruction.DEFAULT_USER_ID
                val conv = (payload["conversationId"] ?: payload["replyTarget"] ?: sender).toString()
                val tenant = payload["tenantId"]?.toString()?.takeIf { it.isNotBlank() } ?: defaultTenant
                val space = payload["spaceId"]?.toString()?.takeIf { it.isNotBlank() } ?: AgentSession.DEFAULT_SPACE_ID
                InboundChatMessage(
                    channelType = channelType,
                    channelId = channelId,
                    senderId = sender,
                    conversationId = conv,
                    tenantId = tenant,
                    spaceId = space,
                    text = text
                )
            }
        }
    }
}
