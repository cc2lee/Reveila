package com.reveila.channels

import com.reveila.ai.ReveilaTool
import com.reveila.safety.SecretManager
import com.reveila.service.HttpClientService
import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil

/**
 * Central dispatcher for enterprise proactive push alerts across Slack, Microsoft Teams, and Email.
 *
 * Configuration is loaded from `<systemHome>/configs/settings/channels.json` (and/or registered
 * programmatically). Credentials and webhook URLs referenced with `REF:` or `SEC:` are resolved
 * blindly at send time through [SecretManager].
 */
class EnterpriseChannelHub(
    private val systemHomeProvider: () -> String? = { null },
    private val secretManagerProvider: () -> SecretManager? = { null },
    private val allowCustomChannelsProvider: () -> Boolean = { true },
    private val httpPost: ChannelHttpPoster = defaultHttpPoster(),
    private val fs: PlatformFileSystem = PlatformFileSystem()
) {
    private val adapters: Map<ChannelType, EnterpriseChannelAdapter> = mapOf(
        ChannelType.SLACK to SlackChannelAdapter(),
        ChannelType.MICROSOFT_TEAMS to TeamsChannelAdapter(),
        ChannelType.EMAIL to EmailChannelAdapter(),
        ChannelType.CUSTOM_WEBHOOK to CustomWebhookChannelAdapter()
    )

    private val programmaticChannels: MutableList<EnterpriseChannelConfig> = mutableListOf()

    fun registerChannel(config: EnterpriseChannelConfig) {
        programmaticChannels.removeAll { it.id.equals(config.id, ignoreCase = true) }
        programmaticChannels.add(config)
    }

    fun clearProgrammaticChannels() {
        programmaticChannels.clear()
    }

    fun loadConfiguredChannels(): List<EnterpriseChannelConfig> {
        val merged = linkedMapOf<String, EnterpriseChannelConfig>()
        for (cfg in loadFromDisk()) {
            merged[cfg.id.lowercase()] = cfg
        }
        for (cfg in programmaticChannels) {
            merged[cfg.id.lowercase()] = cfg
        }
        return merged.values.toList()
    }

    /**
     * Dispatches [alert] to all enabled channels whose severity threshold and optional taskId
     * filter match. Returns one [ChannelDeliveryResult] per matched channel.
     */
    fun dispatchAlert(
        alert: ProactiveAlertMessage,
        onlyChannelIds: Set<String> = emptySet()
    ): List<ChannelDeliveryResult> {
        val allowCustom = allowCustomChannelsProvider()
        val channels = loadConfiguredChannels().filter { cfg ->
            if (!cfg.enabled) return@filter false
            if (cfg.type == ChannelType.CUSTOM_WEBHOOK && !allowCustom) return@filter false
            if (onlyChannelIds.isNotEmpty() && onlyChannelIds.none { it.equals(cfg.id, ignoreCase = true) }) {
                return@filter false
            }
            if (alert.severity.rank < cfg.minSeverity.rank) return@filter false
            if (cfg.taskIds.isNotEmpty() && cfg.taskIds.none { it.equals(alert.taskId, ignoreCase = true) }) {
                return@filter false
            }
            true
        }

        if (channels.isEmpty()) return emptyList()

        return channels.map { cfg ->
            val adapter = adapters[cfg.type]
                ?: return@map ChannelDeliveryResult(cfg.id, cfg.type, false, "No adapter for ${cfg.type}")
            val secret = resolveSecret(cfg.secretRef)
            val resolvedEndpoint = resolveSecret(cfg.endpointUrl)
            val effectiveConfig = if (resolvedEndpoint != cfg.endpointUrl) {
                cfg.copy(endpointUrl = resolvedEndpoint)
            } else {
                cfg
            }
            adapter.send(
                alert = alert,
                config = effectiveConfig,
                resolvedSecret = secret,
                httpPost = httpPost
            )
        }
    }

    private fun resolveSecret(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val key = when {
            trimmed.startsWith("REF:", ignoreCase = true) -> trimmed.substring(4).trim()
            trimmed.startsWith("SEC:", ignoreCase = true) -> trimmed.substring(4).trim()
            else -> return trimmed
        }
        if (key.isEmpty()) return ""
        return try {
            secretManagerProvider()?.getSecret(key).orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun loadFromDisk(): List<EnterpriseChannelConfig> {
        val home = systemHomeProvider()?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val path = fs.resolve(home, "configs/settings/channels.json")
        if (!fs.exists(path)) return emptyList()
        return try {
            parseChannelsJson(fs.readText(path))
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        fun parseChannelsJson(rawJson: String): List<EnterpriseChannelConfig> {
            if (rawJson.isBlank()) return emptyList()
            val root = JsonUtil.parseJsonStringToMap(rawJson)
            val rawList = (root["channels"] ?: root["Channels"]) as? List<*> ?: return emptyList()
            val result = mutableListOf<EnterpriseChannelConfig>()
            for (item in rawList) {
                val map = item as? Map<*, *> ?: continue
                val id = ((map["Id"] ?: map["id"]) as? String)?.trim().orEmpty()
                val type = ChannelType.fromRaw((map["Type"] ?: map["type"]) as? String) ?: continue
                if (id.isBlank()) continue
                val enabled = (map["Enabled"] ?: map["enabled"])?.let {
                    it == true || it.toString().equals("true", ignoreCase = true)
                } ?: true
                val endpoint = ((map["Endpoint"] ?: map["endpoint"] ?: map["Webhook.Url"] ?: map["webhookUrl"]) as? String)
                    ?.trim().orEmpty()
                val secretRef = ((map["Api.Key"] ?: map["Secret.Ref"] ?: map["secretRef"] ?: map["Token"]) as? String)
                    ?.trim().orEmpty()
                val targetChannel = ((map["Channel"] ?: map["channel"] ?: map["Target.Channel"]) as? String)
                    ?.trim().orEmpty()
                val fromAddr = ((map["From"] ?: map["from"]) as? String)?.trim().orEmpty()
                val toRaw = map["To"] ?: map["to"] ?: map["Recipients"]
                val toList = when (toRaw) {
                    is List<*> -> toRaw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                    is String -> toRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    else -> emptyList()
                }
                val minSeverity = AlertSeverity.fromRaw((map["Min.Severity"] ?: map["minSeverity"]) as? String)
                val taskIdsRaw = map["Task.Ids"] ?: map["taskIds"]
                val taskIds = when (taskIdsRaw) {
                    is List<*> -> taskIdsRaw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                    is String -> taskIdsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    else -> emptyList()
                }
                result.add(
                    EnterpriseChannelConfig(
                        id = id,
                        type = type,
                        enabled = enabled,
                        endpointUrl = endpoint,
                        secretRef = secretRef,
                        targetChannel = targetChannel,
                        fromAddress = fromAddr,
                        toAddresses = toList,
                        minSeverity = minSeverity,
                        taskIds = taskIds
                    )
                )
            }
            return result
        }

        private fun defaultHttpPoster(): ChannelHttpPoster = { url, payload, headers ->
            val service = HttpClientService()
            service.invokeRest(
                url = url,
                method = "POST",
                payload = payload,
                payloadFormat = HttpClientService.JSON,
                headers = headers
            ).orEmpty()
        }
    }
}

/**
 * ReveilaTool allowing governed agents to send notifications to configured enterprise channels
 * (Slack, Microsoft Teams, Email) on demand.
 */
class EnterpriseChannelTool(
    private val hubProvider: () -> EnterpriseChannelHub
) : ReveilaTool {

    override fun getName(): String = "enterprise_notify"

    override fun getDescription(): String = """
        Sends governed proactive alerts or summaries to configured enterprise channels (Slack, Microsoft Teams, Email).
        Actions:
        - send: { "action": "send", "title": "...", "summary": "...", "severity": "INFO|SUCCESS|WARNING|ACTION_REQUIRED|CRITICAL", "channelIds": ["slack-ops"] }
        - list_channels: { "action": "list_channels" }
    """.trimIndent()

    override fun execute(args: Map<String, Any?>): String {
        val hub = hubProvider()
        val action = (args["action"] as? String)?.trim()?.lowercase() ?: "send"
        return when (action) {
            "list_channels", "list" -> {
                val channels = hub.loadConfiguredChannels().map { cfg ->
                    mapOf(
                        "id" to cfg.id,
                        "type" to cfg.type.name,
                        "enabled" to cfg.enabled,
                        "minSeverity" to cfg.minSeverity.name,
                        "targetChannel" to cfg.targetChannel
                    )
                }
                JsonUtil.toJsonString(mapOf("ok" to true, "channels" to channels))
            }
            "send", "notify" -> {
                val title = (args["title"] as? String)?.trim().orEmpty().ifBlank { "Reveila Notification" }
                val summary = (args["summary"] as? String)?.trim().orEmpty()
                val taskId = (args["taskId"] as? String)?.trim().orEmpty().ifBlank { "manual" }
                val status = (args["status"] as? String)?.trim().orEmpty().ifBlank { "INFO" }
                val severity = AlertSeverity.fromRaw((args["severity"] as? String) ?: status)
                val channelIds = when (val raw = args["channelIds"]) {
                    is List<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }.toSet()
                    is String -> raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    else -> emptySet()
                }
                val results = hub.dispatchAlert(
                    alert = ProactiveAlertMessage(
                        eventId = "manual-${com.reveila.system.platform.PlatformSystem.currentTimeMillis()}",
                        taskId = taskId,
                        title = title,
                        status = status,
                        summary = summary,
                        severity = severity,
                        timestampMs = com.reveila.system.platform.PlatformSystem.currentTimeMillis()
                    ),
                    onlyChannelIds = channelIds
                )
                JsonUtil.toJsonString(
                    mapOf(
                        "ok" to results.all { it.ok },
                        "deliveredCount" to results.count { it.ok },
                        "results" to results.map {
                            mapOf(
                                "channelId" to it.channelId,
                                "type" to it.channelType.name,
                                "ok" to it.ok,
                                "message" to it.message
                            )
                        }
                    )
                )
            }
            else -> JsonUtil.toJsonString(mapOf("ok" to false, "error" to "Unsupported enterprise_notify action '$action'."))
        }
    }
}
