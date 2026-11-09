//noinspection PackageDirectoryMismatch
package com.reveila.tools.browser

import com.reveila.ai.ReveilaTool

/**
 * ReveilaTool integration for VirtualBrowser, allowing AutonomousAgent and AgenticFabric
 * to autonomously navigate web applications and coordinate user takeover.
 */
class VirtualBrowserTool(
    val browser: VirtualBrowser = VirtualBrowser()
) : ReveilaTool {

    override fun getName(): String = "virtual_browser"

    override fun getDescription(): String {
        return """
            Autonomous virtual browser that interacts with web pages in the background.
            Commands:
            - navigate: { "action": "navigate", "url": "https://example.com" }
            - type: { "action": "type", "target": "1", "text": "user@example.com" } (target is element index or #id)
            - click: { "action": "click", "target": "3" } (clicks links or buttons)
            - submit: { "action": "submit", "form": "1" } (submits form with current values)
            - snapshot: { "action": "snapshot" } (returns current page snapshot with interactive elements)
            - login / autofill: { "action": "login", "domain": "example.com", "submit": true } (auto-fills stored credentials and signs in without exposing password)
            - back: { "action": "back" } (navigates back)
            - request_takeover: { "action": "request_takeover", "reason": "CAPTCHA_DETECTED", "message": "Please solve captcha" }
        """.trimIndent()
    }

    override fun execute(args: Map<String, Any?>): String {
        val action = (args["action"] as? String)?.lowercase() ?: (args["command"] as? String)?.lowercase() ?: "snapshot"

        return try {
            when (action) {
                "navigate", "open", "goto" -> {
                    val url = (args["url"] as? String) ?: (args["target"] as? String)
                        ?: return "Error: 'url' parameter is required for navigate action."
                    browser.navigate(url)
                    browser.getPageSnapshotForLlm()
                }

                "type", "fill", "input" -> {
                    val target = (args["target"] as? String) ?: (args["element"] as? String) ?: (args["id"] as? String)
                        ?: return "Error: 'target' (element index or selector) is required for type action."
                    val text = (args["text"] as? String) ?: (args["value"] as? String) ?: ""
                    browser.type(target, text)
                    "Successfully typed into element [$target].\n\n" + browser.getPageSnapshotForLlm()
                }

                "click", "press" -> {
                    val target = (args["target"] as? String) ?: (args["element"] as? String) ?: (args["id"] as? String)
                        ?: return "Error: 'target' (element index or selector) is required for click action."
                    browser.click(target)
                    "Successfully clicked [$target].\n\n" + browser.getPageSnapshotForLlm()
                }

                "submit" -> {
                    val formTarget = (args["form"] as? String) ?: (args["target"] as? String)
                    browser.submit(formTarget)
                    "Form submitted successfully.\n\n" + browser.getPageSnapshotForLlm()
                }

                "login", "autofill", "signin" -> {
                    val domain = (args["domain"] as? String) ?: (args["url"] as? String)
                    val autoSubmit = (args["submit"] as? Boolean) ?: (args["auto_submit"] as? Boolean) ?: true
                    val formTarget = (args["form"] as? String) ?: (args["target"] as? String)
                    browser.autofillLogin(domain, formTarget, autoSubmit)
                    "Credentials securely auto-filled and submitted.\n\n" + browser.getPageSnapshotForLlm()
                }

                "back" -> {
                    browser.back()
                    browser.getPageSnapshotForLlm()
                }

                "reload", "refresh" -> {
                    browser.reload()
                    browser.getPageSnapshotForLlm()
                }

                "request_takeover", "handoff", "escalate" -> {
                    val reasonStr = (args["reason"] as? String)?.uppercase() ?: "MANUAL_TAKEOVER"
                    val reason = try {
                        TakeoverReason.valueOf(reasonStr)
                    } catch (_: Exception) {
                        TakeoverReason.MANUAL_TAKEOVER
                    }
                    val message = (args["message"] as? String) ?: "User takeover requested by autonomous browser agent."
                    val state = browser.requestUserTakeover(reason, message)
                    "USER TAKEOVER INITIATED:\nStatus: ${state.status}\nReason: ${state.reason}\nMessage: ${state.message}\nCurrent URL: ${state.currentUrl}\nSession ID: ${state.activeSessionId}"
                }

                "snapshot", "status", "read" -> {
                    browser.getPageSnapshotForLlm()
                }

                else -> {
                    "Unknown browser action: '$action'. Available actions: navigate, type, click, submit, login, autofill, snapshot, back, reload, request_takeover."
                }
            }
        } catch (e: Exception) {
            "Browser Action Error [$action]: ${e.message}"
        }
    }
}
