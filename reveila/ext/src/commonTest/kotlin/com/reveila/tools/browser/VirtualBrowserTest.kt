package com.reveila.tools.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VirtualBrowserTest {

    @Test
    fun testHtmlParserFormsAndElements() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>Reveila Portal Login</title></head>
            <body>
                <h1>Welcome Back</h1>
                <p>Please enter your credentials to access your dashboard.</p>
                <form id="loginForm" action="/auth/login" method="POST">
                    <input type="text" name="username" placeholder="Username or Email" id="user-input" />
                    <input type="password" name="password" placeholder="Password" />
                    <input type="checkbox" name="rememberMe" value="true" /> Remember Me
                    <button type="submit" id="submit-btn">Sign In</button>
                </form>
                <a href="/reset-password">Forgot Password?</a>
            </body>
            </html>
        """.trimIndent()

        val page = HtmlParser.parse("https://portal.reveila.com/login", 200, html, emptyMap())

        assertEquals("Reveila Portal Login", page.title)
        assertFalse(page.isSecurityChallenge)

        // Verify forms
        assertEquals(1, page.forms.size)
        val form = page.forms[0]
        assertEquals("/auth/login", form.action)
        assertEquals("POST", form.method)

        // Verify elements
        assertTrue(page.elements.isNotEmpty())
        val userInput = page.elements.find { it.name == "username" }
        assertNotNull(userInput)
        assertEquals("user-input", userInput.elementId)
        assertTrue(userInput.isInputField)

        val passInput = page.elements.find { it.name == "password" }
        assertNotNull(passInput)
        assertTrue(passInput.isSecret)

        val submitBtn = page.elements.find { it.elementId == "submit-btn" }
        assertNotNull(submitBtn)
        assertTrue(submitBtn.isClickable)

        val link = page.elements.find { it.tagName == "a" }
        assertNotNull(link)
        assertEquals("/reset-password", link.href)
    }

    @Test
    fun testSecurityChallengeDetection() {
        val captchaHtml = """
            <html>
            <head><title>Security Check</title></head>
            <body>
                <p>Verify you are human to proceed.</p>
                <div class="g-recaptcha" data-sitekey="12345"></div>
            </body>
            </html>
        """.trimIndent()

        val page = HtmlParser.parse("https://protected.com/challenge", 403, captchaHtml, emptyMap())
        assertTrue(page.isSecurityChallenge)
        assertEquals(TakeoverReason.CAPTCHA_DETECTED, page.detectedChallengeType)

        val mfaHtml = """
            <html>
            <head><title>Two-Factor Authentication</title></head>
            <body>
                <p>Please enter the 6-digit code from your authenticator app.</p>
                <input type="text" name="otp" />
            </body>
            </html>
        """.trimIndent()

        val mfaPage = HtmlParser.parse("https://protected.com/2fa", 200, mfaHtml, emptyMap())
        assertTrue(mfaPage.isSecurityChallenge)
        assertEquals(TakeoverReason.MFA_OTP_REQUIRED, mfaPage.detectedChallengeType)
    }

    @Test
    fun testCookieJarManagement() {
        val jar = CookieJar()
        val headers = mapOf(
            "Set-Cookie" to listOf(
                "session_id=reveila_sess_999; Domain=reveila.com; Path=/; Secure; HttpOnly",
                "pref_theme=dark; Domain=.reveila.com; Path=/"
            )
        )

        jar.parseResponseCookies("https://reveila.com/dashboard", headers)

        val cookieHeader = jar.getCookieHeader("https://portal.reveila.com/api/data")
        assertNotNull(cookieHeader)
        assertTrue(cookieHeader.contains("pref_theme=dark"))

        // JSON serialization
        val json = jar.toJson()
        assertTrue(json.contains("reveila_sess_999"))

        val restoredJar = CookieJar()
        restoredJar.fromJson(json)
        assertEquals(2, restoredJar.getAllCookies().size)
    }

    @Test
    fun testVirtualBrowserToolExecution() {
        val tool = VirtualBrowserTool()
        assertEquals("virtual_browser", tool.getName())

        // Test snapshot on empty browser
        val snapshot = tool.execute(mapOf("action" to "snapshot"))
        assertTrue(snapshot.contains("No page loaded"))

        // Test user takeover escalation command
        val takeoverResult = tool.execute(
            mapOf(
                "action" to "request_takeover",
                "reason" to "CAPTCHA_DETECTED",
                "message" to "Cloudflare Turnstile challenge encountered."
            )
        )
        assertTrue(takeoverResult.contains("USER TAKEOVER INITIATED"))
        assertTrue(takeoverResult.contains("CAPTCHA_DETECTED"))
    }
}
