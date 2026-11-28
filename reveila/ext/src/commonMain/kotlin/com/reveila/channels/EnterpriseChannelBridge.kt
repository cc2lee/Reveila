package com.reveila.channels

import com.reveila.util.json.JsonUtil

enum class ChannelType {
    SLACK,
    MICROSOFT_TEAMS,
    WHATSAPP,
    EMAIL,
    CUSTOM_WEBHOOK;

    companion object {
        fun fromRaw(raw: String?): ChannelType? {
            return when (raw?.trim()?.uppercase()?.replace("-", "_")) {
                "SLACK" -> SLACK
                "TEAMS", "MS_TEAMS", "MICROSOFT_TEAMS", "MSTEAMS" -> MICROSOFT_TEAMS
                "WHATSAPP", "WA", "WHATSAPP_CLOUD" -> WHATSAPP
                "EMAIL", "MAIL", "SMTP", "EMAIL_WEBHOOK" -> EMAIL
                "CUSTOM", "CUSTOM_WEBHOOK", "WEBHOOK", "DISCORD", "TELEGRAM", "NTFY" -> CUSTOM_WEBHOOK
                else -> null
            }
        }
    }
}

enum class AlertSeverity(val rank: Int) {
    INFO(0),
    SUCCESS(1),
    WARNING(2),
    ACTION_REQUIRED(3),
    CRITICAL(4);

    companion object {
        fun fromStatus(status: String): AlertSeverity {
            return when (status.trim().uppercase()) {
                "WAITING_APPROVAL", "ESCALATED" -> ACTION_REQUIRED
                "FAILED", "ERROR", "KILLED", "BLOCKED" -> CRITICAL
                "WARNING", "PAUSED", "CAPABILITY_CONFLICT" -> WARNING
                "COMPLETED", "SUCCESS" -> SUCCESS
                else -> INFO
            }
        }

        fun fromRaw(raw: String?): AlertSeverity {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: INFO
        }
    }
}

data class ProactiveAlertMessage(
    val eventId: String,
    val taskId: String,
    val title: String,
    val status: String,
    val summary: String,
    val severity: AlertSeverity = AlertSeverity.fromStatus(status),
    val timestampMs: Long = 0L,
    val metadata: Map<String, String> = emptyMap()
)

data class EnterpriseChannelConfig(
    val id: String,
    val type: ChannelType,
    val enabled: Boolean = true,
    val endpointUrl: String = "",
    val secretRef: String = "",
    val targetChannel: String = "",
    val fromAddress: String = "",
    val toAddresses: List<String> = emptyList(),
    val minSeverity: AlertSeverity = AlertSeverity.INFO,
    val taskIds: List<String> = emptyList()
)

data class ChannelDeliveryResult(
    val channelId: String,
    val channelType: ChannelType,
    val ok: Boolean,
    val message: String
)

typealias ChannelHttpPoster = (url: String, payload: String, headers: Map<String, String>) -> String

interface EnterpriseChannelAdapter {
    val type: ChannelType
    fun formatPayload(alert: ProactiveAlertMessage, config: EnterpriseChannelConfig): String
    fun send(
        alert: ProactiveAlertMessage,
        config: EnterpriseChannelConfig,
        resolvedSecret: String,
        httpPost: ChannelHttpPoster
    ): ChannelDeliveryResult
}

/**
 * Slack enterprise channel adapter supporting both Incoming Webhooks and Slack Web API (`chat.postMessage`).
 */
class SlackChannelAdapter : EnterpriseChannelAdapter {
    override val type: ChannelType = ChannelType.SLACK

    override fun formatPayload(alert: ProactiveAlertMessage, config: EnterpriseChannelConfig): String {
        val badge = when (alert.severity) {
            AlertSeverity.CRITICAL -> "[CRITICAL]"
            AlertSeverity.ACTION_REQUIRED -> "[ACTION REQUIRED]"
            AlertSeverity.WARNING -> "[WARNING]"
            AlertSeverity.SUCCESS -> "[COMPLETED]"
            AlertSeverity.INFO -> "[INFO]"
        }
        val headerText = "$badge ${alert.title}"
        val fallbackText = "$headerText (${alert.status}): ${alert.summary.take(500)}"
        val payload = linkedMapOf<String, Any?>(
            "text" to fallbackText,
            "blocks" to listOf(
                mapOf(
                    "type" to "header",
                    "text" to mapOf(
                        "type" to "plain_text",
                        "text" to headerText.take(150)
                    )
                ),
                mapOf(
                    "type" to "section",
                    "fields" to listOf(
                        mapOf("type" to "mrkdwn", "text" to "*Task:*\n`${alert.taskId}`"),
                        mapOf("type" to "mrkdwn", "text" to "*Status:*\n`${alert.status}`")
                    )
                ),
                mapOf(
                    "type" to "section",
                    "text" to mapOf(
                        "type" to "mrkdwn",
                        "text" to alert.summary.take(2800).ifBlank { "_No summary provided._" }
                    )
                )
            )
        )
        if (config.targetChannel.isNotBlank()) {
            payload["channel"] = config.targetChannel
        }
        return JsonUtil.toJsonString(payload)
    }

    override fun send(
        alert: ProactiveAlertMessage,
        config: EnterpriseChannelConfig,
        resolvedSecret: String,
        httpPost: ChannelHttpPoster
    ): ChannelDeliveryResult {
        val url = config.endpointUrl.trim().ifBlank {
            if (resolvedSecret.startsWith("https://", ignoreCase = true)) resolvedSecret
            else "https://slack.com/api/chat.postMessage"
        }
        val headers = linkedMapOf("Content-Type" to "application/json; charset=utf-8")
        if (resolvedSecret.isNotBlank() && !resolvedSecret.startsWith("https://", ignoreCase = true)) {
            headers["Authorization"] = "Bearer $resolvedSecret"
        }
        val payload = formatPayload(alert, config)
        return try {
            val response = httpPost(url, payload, headers)
            ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = true,
                message = "Delivered to Slack (${response.take(120).ifBlank { "ok" }})"
            )
        } catch (e: Exception) {
            ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = false,
                message = "Slack delivery failed: ${e.message}"
            )
        }
    }
}

/**
 * Microsoft Teams enterprise channel adapter formatting Adaptive Card v1.4 webhook payloads
 * compatible with Teams Incoming Webhooks and Power Automate Workflows.
 */
class TeamsChannelAdapter : EnterpriseChannelAdapter {
    override val type: ChannelType = ChannelType.MICROSOFT_TEAMS

    override fun formatPayload(alert: ProactiveAlertMessage, config: EnterpriseChannelConfig): String {
        val cardBody = listOf(
            mapOf(
                "type" to "TextBlock",
                "size" to "Medium",
                "weight" to "Bolder",
                "text" to "[${alert.severity.name}] ${alert.title}",
                "wrap" to true
            ),
            mapOf(
                "type" to "FactSet",
                "facts" to listOf(
                    mapOf("title" to "Task ID", "value" to alert.taskId),
                    mapOf("title" to "Status", "value" to alert.status),
                    mapOf("title" to "Event ID", "value" to alert.eventId)
                )
            ),
            mapOf(
                "type" to "TextBlock",
                "text" to alert.summary.take(3000).ifBlank { "No summary provided." },
                "wrap" to true
            )
        )
        val payload = mapOf(
            "type" to "message",
            "attachments" to listOf(
                mapOf(
                    "contentType" to "application/vnd.microsoft.card.adaptive",
                    "contentUrl" to null,
                    "content" to mapOf(
                        "\$schema" to "http://adaptivecards.io/schemas/adaptive-card.json",
                        "type" to "AdaptiveCard",
                        "version" to "1.4",
                        "body" to cardBody
                    )
                )
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
        val url = config.endpointUrl.trim().ifBlank {
            if (resolvedSecret.startsWith("https://", ignoreCase = true)) resolvedSecret else ""
        }
        if (url.isBlank()) {
            return ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = false,
                message = "Teams webhook endpoint URL is not configured."
            )
        }
        val headers = linkedMapOf("Content-Type" to "application/json; charset=utf-8")
        if (resolvedSecret.isNotBlank() && !resolvedSecret.startsWith("https://", ignoreCase = true)) {
            headers["Authorization"] = "Bearer $resolvedSecret"
        }
        val payload = formatPayload(alert, config)
        return try {
            val response = httpPost(url, payload, headers)
            ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = true,
                message = "Delivered to Microsoft Teams (${response.take(120).ifBlank { "ok" }})"
            )
        } catch (e: Exception) {
            ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = false,
                message = "Microsoft Teams delivery failed: ${e.message}"
            )
        }
    }
}

/**
 * Enterprise Email bridge adapter delivering transactional JSON alerts to corporate HTTP email
 * gateways (Microsoft Graph relay, SendGrid, Resend, Postmark, or internal SMTP-HTTP bridge).
 */
class EmailChannelAdapter : EnterpriseChannelAdapter {
    override val type: ChannelType = ChannelType.EMAIL

    override fun formatPayload(alert: ProactiveAlertMessage, config: EnterpriseChannelConfig): String {
        val subject = "[Reveila ${alert.severity.name}] ${alert.title} (${alert.status})"
        val textBody = buildString {
            appendLine("Task: ${alert.title} (${alert.taskId})")
            appendLine("Status: ${alert.status}")
            appendLine("Severity: ${alert.severity.name}")
            appendLine("Event ID: ${alert.eventId}")
            appendLine()
            appendLine(alert.summary)
        }
        val payload = linkedMapOf<String, Any?>(
            "from" to config.fromAddress.ifBlank { "reveila-alerts@localhost" },
            "to" to config.toAddresses,
            "subject" to subject,
            "text" to textBody,
            "metadata" to mapOf(
                "taskId" to alert.taskId,
                "eventId" to alert.eventId,
                "status" to alert.status,
                "severity" to alert.severity.name
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
                message = "Email gateway endpoint URL is not configured."
            )
        }
        if (config.toAddresses.isEmpty()) {
            return ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = false,
                message = "Email channel '${config.id}' has no recipient addresses configured."
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
                message = "Delivered email to ${config.toAddresses.joinToString(", ")} (${response.take(120).ifBlank { "ok" }})"
            )
        } catch (e: Exception) {
            ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = false,
                message = "Email delivery failed: ${e.message}"
            )
        }
    }
}

/**
 * Generic JSON webhook adapter for personal/individual edition (compatible with Discord webhooks,
 * ntfy, Zapier, Make, Home Assistant, or custom HTTP endpoints). Blocked in enterprise edition.
 */
class CustomWebhookChannelAdapter : EnterpriseChannelAdapter {
    override val type: ChannelType = ChannelType.CUSTOM_WEBHOOK

    override fun formatPayload(alert: ProactiveAlertMessage, config: EnterpriseChannelConfig): String {
        val summaryLine = "[${alert.severity.name}] ${alert.title} (${alert.status}): ${alert.summary.take(1800)}"
        val payload = linkedMapOf<String, Any?>(
            "eventId" to alert.eventId,
            "taskId" to alert.taskId,
            "title" to alert.title,
            "status" to alert.status,
            "severity" to alert.severity.name,
            "summary" to alert.summary,
            "text" to summaryLine,
            "content" to summaryLine
        )
        if (config.targetChannel.isNotBlank()) {
            payload["channel"] = config.targetChannel
        }
        return JsonUtil.toJsonString(payload)
    }

    override fun send(
        alert: ProactiveAlertMessage,
        config: EnterpriseChannelConfig,
        resolvedSecret: String,
        httpPost: ChannelHttpPoster
    ): ChannelDeliveryResult {
        val url = config.endpointUrl.trim().ifBlank {
            if (resolvedSecret.startsWith("https://", ignoreCase = true) || resolvedSecret.startsWith("http://", ignoreCase = true)) {
                resolvedSecret
            } else ""
        }
        if (url.isBlank()) {
            return ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = false,
                message = "Custom webhook URL is not configured."
            )
        }
        val headers = linkedMapOf("Content-Type" to "application/json; charset=utf-8")
        if (resolvedSecret.isNotBlank() && !resolvedSecret.startsWith("http://", ignoreCase = true) && !resolvedSecret.startsWith("https://", ignoreCase = true)) {
            headers["Authorization"] = "Bearer $resolvedSecret"
        }
        val payload = formatPayload(alert, config)
        return try {
            val response = httpPost(url, payload, headers)
            ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = true,
                message = "Delivered to custom webhook '${config.id}' (${response.take(120).ifBlank { "ok" }})"
            )
        } catch (e: Exception) {
            ChannelDeliveryResult(
                channelId = config.id,
                channelType = type,
                ok = false,
                message = "Custom webhook delivery failed: ${e.message}"
            )
        }
    }
}
