package com.reveila.tools.browser

import kotlinx.serialization.Serializable

/**
 * Represents a persistent cookie in the virtual browser cookie jar.
 */
@Serializable
data class BrowserCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val isSecure: Boolean = false,
    val isHttpOnly: Boolean = false,
    val expiryEpochMs: Long? = null
)

/**
 * State machine status for Human-In-The-Loop (HITL) browser handoff.
 */
enum class TakeoverStatus {
    AUTONOMOUS,
    WAITING_FOR_USER_CONTROL,
    USER_CONTROLLING,
    RESUMED
}

/**
 * Trigger reason for escalating to user control.
 */
enum class TakeoverReason {
    CAPTCHA_DETECTED,
    MFA_OTP_REQUIRED,
    BIOMETRIC_LOGIN,
    LOGIN_REQUIRED,
    SECURITY_CHECKPOINT,
    AMBIGUOUS_ACTION,
    MANUAL_TAKEOVER
}

/**
 * Telemetry and state snapshot delivered when the AI yields control to the user.
 */
@Serializable
data class UserTakeoverState(
    val status: String,
    val reason: String,
    val message: String,
    val currentUrl: String,
    val targetDomain: String,
    val timestampMs: Long,
    val activeSessionId: String
)

/**
 * An actionable DOM element discovered on a web page.
 */
data class InteractiveElement(
    val index: Int,
    val tagName: String,
    val type: String? = null,
    val name: String? = null,
    val elementId: String? = null,
    val text: String = "",
    val placeholder: String? = null,
    var value: String? = null,
    val href: String? = null,
    val isClickable: Boolean = false,
    val isInputField: Boolean = false,
    val isSecret: Boolean = false,
    val selector: String = "",
    val formId: String? = null
)

/**
 * Form field specification for form submission.
 */
data class FormField(
    val name: String,
    val type: String,
    var value: String = "",
    val isSecret: Boolean = false
)

/**
 * An HTML form representation.
 */
data class WebForm(
    val index: Int,
    val id: String? = null,
    val action: String,
    val method: String = "POST",
    val fields: MutableList<FormField> = mutableListOf()
)

/**
 * Complete snapshot of a loaded web page.
 */
data class WebPage(
    val url: String,
    val statusCode: Int,
    val title: String,
    val contentText: String,
    val elements: List<InteractiveElement>,
    val forms: List<WebForm>,
    val rawHtml: String,
    val headers: Map<String, List<String>> = emptyMap(),
    val isSecurityChallenge: Boolean = false,
    val detectedChallengeType: TakeoverReason? = null
)
