package com.reveila.channels

import com.reveila.ai.AutonomousAgent
import com.reveila.util.json.JsonUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnterpriseChannelHubTest {

    @Test
    fun parseChannelsJsonLoadsSlackTeamsAndEmailConfigs() {
        val raw = """
            {
              "channels": [
                {
                  "Id": "slack-ops",
                  "Type": "SLACK",
                  "Enabled": true,
                  "Endpoint": "https://hooks.slack.com/services/T00/B00/XXX",
                  "Channel": "#alerts",
                  "Min.Severity": "SUCCESS"
                },
                {
                  "Id": "teams-ops",
                  "Type": "MICROSOFT_TEAMS",
                  "Enabled": true,
                  "Endpoint": "https://outlook.office.com/webhook/abc",
                  "Min.Severity": "ACTION_REQUIRED"
                },
                {
                  "Id": "email-ops",
                  "Type": "EMAIL",
                  "Enabled": true,
                  "Endpoint": "https://api.resend.com/emails",
                  "Api.Key": "token-123",
                  "From": "alerts@reveila.ai",
                  "To": ["ciso@corp.com", "ops@corp.com"],
                  "Min.Severity": "WARNING"
                }
              ]
            }
        """.trimIndent()

        val channels = EnterpriseChannelHub.parseChannelsJson(raw)
        assertEquals(3, channels.size)
        assertEquals(ChannelType.SLACK, channels[0].type)
        assertEquals(ChannelType.MICROSOFT_TEAMS, channels[1].type)
        assertEquals(ChannelType.EMAIL, channels[2].type)
        assertEquals(listOf("ciso@corp.com", "ops@corp.com"), channels[2].toAddresses)
    }

    @Test
    fun hubDispatchesToSlackTeamsAndEmailWithSeverityFiltering() {
        val sentCalls = mutableListOf<Triple<String, String, Map<String, String>>>()
        val hub = EnterpriseChannelHub(
            httpPost = { url, payload, headers ->
                sentCalls.add(Triple(url, payload, headers))
                """{"ok":true}"""
            }
        )

        hub.registerChannel(
            EnterpriseChannelConfig(
                id = "slack-ops",
                type = ChannelType.SLACK,
                enabled = true,
                endpointUrl = "https://hooks.slack.com/services/T1/B1/S1",
                targetChannel = "#ops",
                minSeverity = AlertSeverity.SUCCESS
            )
        )
        hub.registerChannel(
            EnterpriseChannelConfig(
                id = "teams-escalation",
                type = ChannelType.MICROSOFT_TEAMS,
                enabled = true,
                endpointUrl = "https://teams.example.com/webhook",
                minSeverity = AlertSeverity.ACTION_REQUIRED
            )
        )
        hub.registerChannel(
            EnterpriseChannelConfig(
                id = "email-ops",
                type = ChannelType.EMAIL,
                enabled = true,
                endpointUrl = "https://mail.example.com/send",
                secretRef = "mail-secret-key",
                fromAddress = "reveila@corp.com",
                toAddresses = listOf("lead@corp.com"),
                minSeverity = AlertSeverity.SUCCESS
            )
        )

        // SUCCESS alert should hit Slack + Email, but NOT Teams (which requires ACTION_REQUIRED)
        val successResults = hub.dispatchAlert(
            ProactiveAlertMessage(
                eventId = "evt-1",
                taskId = "daily-brief",
                title = "Daily Executive Brief",
                status = "COMPLETED",
                summary = "All 12 documents summarized."
            )
        )
        assertEquals(2, successResults.size)
        assertTrue(successResults.all { it.ok })
        assertEquals(2, sentCalls.size)

        // Verify Slack Block Kit payload
        val slackPayload = JsonUtil.parseJsonStringToMap(sentCalls[0].second)
        assertEquals("#ops", slackPayload["channel"])
        assertTrue((slackPayload["blocks"] as List<*>).isNotEmpty())

        // Verify Email payload & Bearer header
        val emailPayload = JsonUtil.parseJsonStringToMap(sentCalls[1].second)
        assertEquals("reveila@corp.com", emailPayload["from"])
        assertEquals("Bearer mail-secret-key", sentCalls[1].third["Authorization"])

        // WAITING_APPROVAL alert (ACTION_REQUIRED) should hit all 3 channels including Teams Adaptive Card
        sentCalls.clear()
        val escalationResults = hub.dispatchAlert(
            ProactiveAlertMessage(
                eventId = "evt-2",
                taskId = "wire-transfer-check",
                title = "Approval Needed",
                status = "WAITING_APPROVAL",
                summary = "Requires human approval before proceeding."
            )
        )
        assertEquals(3, escalationResults.size)
        assertTrue(escalationResults.all { it.ok })
        val teamsCall = sentCalls.first { it.first.contains("teams.example.com") }
        val teamsPayload = JsonUtil.parseJsonStringToMap(teamsCall.second)
        assertEquals("message", teamsPayload["type"])
        assertTrue((teamsPayload["attachments"] as List<*>).isNotEmpty())
    }

    @Test
    fun autonomousAgentEmitTaskNotificationTriggersProactiveChannelPush() {
        val agent = AutonomousAgent()
        val deliveredTo = mutableListOf<String>()
        var listenerInvoked = false

        agent.channelHub = EnterpriseChannelHub(
            httpPost = { url, _, _ ->
                deliveredTo.add(url)
                "ok"
            }
        ).apply {
            registerChannel(
                EnterpriseChannelConfig(
                    id = "slack-proactive",
                    type = ChannelType.SLACK,
                    enabled = true,
                    endpointUrl = "https://hooks.slack.com/services/proactive",
                    minSeverity = AlertSeverity.SUCCESS
                )
            )
        }
        agent.proactiveAlertListener = { alert, results ->
            listenerInvoked = (alert.taskId == "task-99" && results.size == 1 && results.first().ok)
        }

        agent.emitTaskNotification(
            taskId = "task-99",
            title = "Morning Market Watch",
            status = "COMPLETED",
            summary = "Top 3 market updates ready."
        )

        assertEquals(listOf("https://hooks.slack.com/services/proactive"), deliveredTo)
        assertTrue(listenerInvoked)
    }

    @Test
    fun customWebhookWorksInIndividualEditionAndIsBlockedInEnterpriseEdition() {
        val sentUrls = mutableListOf<String>()
        val individualHub = EnterpriseChannelHub(
            allowCustomChannelsProvider = { true },
            httpPost = { url, _, _ ->
                sentUrls.add(url)
                "ok"
            }
        ).apply {
            registerChannel(
                EnterpriseChannelConfig(
                    id = "discord-home",
                    type = ChannelType.CUSTOM_WEBHOOK,
                    enabled = true,
                    endpointUrl = "https://discord.com/api/webhooks/123/abc",
                    minSeverity = AlertSeverity.SUCCESS
                )
            )
        }

        val alert = ProactiveAlertMessage(
            eventId = "evt-custom",
            taskId = "task-1",
            title = "Home Alert",
            status = "COMPLETED",
            summary = "Done!"
        )
        val individualRes = individualHub.dispatchAlert(alert)
        assertEquals(1, individualRes.size)
        assertTrue(individualRes.first().ok)
        assertEquals(listOf("https://discord.com/api/webhooks/123/abc"), sentUrls)

        // Enterprise edition blocks CUSTOM_WEBHOOK dispatch
        sentUrls.clear()
        val enterpriseHub = EnterpriseChannelHub(
            allowCustomChannelsProvider = { false },
            httpPost = { url, _, _ ->
                sentUrls.add(url)
                "ok"
            }
        ).apply {
            registerChannel(
                EnterpriseChannelConfig(
                    id = "discord-home",
                    type = ChannelType.CUSTOM_WEBHOOK,
                    enabled = true,
                    endpointUrl = "https://discord.com/api/webhooks/123/abc",
                    minSeverity = AlertSeverity.SUCCESS
                )
            )
        }
        val enterpriseRes = enterpriseHub.dispatchAlert(alert)
        assertTrue(enterpriseRes.isEmpty())
        assertTrue(sentUrls.isEmpty())
    }

    @Test
    fun autonomousAgentRejectsCustomChannelsInEnterpriseEdition() {
        val enterpriseAgent = object : AutonomousAgent() {
            override fun getAppEdition(): String = "enterprise"
        }
        val payload = """
            {
              "channels": [
                {
                  "Id": "discord-personal",
                  "Type": "CUSTOM_WEBHOOK",
                  "Enabled": true,
                  "Endpoint": "https://discord.com/api/webhooks/999/secret"
                }
              ]
            }
        """.trimIndent()
        val resp = enterpriseAgent.saveChannelsConfigJson(payload)
        assertTrue(resp.startsWith("Error:"), "Expected enterprise rejection, got: $resp")
        assertTrue(resp.contains("enterprise", ignoreCase = true))
    }

    @Test
    fun autonomousAgentLocksGovernancePolicyInEnterpriseEdition() {
        val enterpriseAgent = object : AutonomousAgent() {
            override fun getAppEdition(): String = "enterprise"
        }
        val saveResp = enterpriseAgent.saveGovernancePolicyJson("""{"restrictedKeywords":["ONLY_ONE"]}""")
        assertTrue(saveResp.startsWith("Error:"), "Expected enterprise lock, got: $saveResp")
        assertTrue(saveResp.contains("enterprise", ignoreCase = true))

        val resetResp = enterpriseAgent.resetGovernancePolicyToDefaults()
        assertTrue(resetResp.startsWith("Error:"), "Expected enterprise reset lock, got: $resetResp")
    }
}
