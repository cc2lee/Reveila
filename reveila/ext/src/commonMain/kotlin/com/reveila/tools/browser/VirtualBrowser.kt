package com.reveila.tools.browser

import com.reveila.service.PlatformHttpEngine
import com.reveila.service.PlatformHttpResponse
import com.reveila.system.SystemComponent
import com.reveila.util.Uuid
import kotlinx.datetime.Clock

/**
 * Sovereign Virtual Browser implementation for background autonomous agent interaction.
 * Provides headless DOM navigation, form population, authentication state persistence,
 * and Human-In-The-Loop (HITL) handoff for security checkpoints (e.g., Captcha, 2FA/MFA).
 */
open class VirtualBrowser : SystemComponent() {

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        const val MAX_REDIRECTS = 7
    }

    private val httpEngine = PlatformHttpEngine()
    val cookieJar = CookieJar()

    var userAgent: String = DEFAULT_USER_AGENT
    var timeoutSeconds: Long = 30L

    private var currentPage: WebPage? = null
    private val historyStack = mutableListOf<String>()

    var takeoverState: UserTakeoverState? = null
        private set

    var onTakeoverCallback: ((UserTakeoverState) -> Unit)? = null

    @Throws(Exception::class)
    override fun onStart() {
        logger.info("[VirtualBrowser] Started Virtual Browser engine.")
    }

    @Throws(Exception::class)
    override fun onStop() {
        cookieJar.clear()
        historyStack.clear()
        currentPage = null
        takeoverState = null
        logger.info("[VirtualBrowser] Stopped Virtual Browser engine.")
    }

    /**
     * Navigates to a specific URL, automatically managing cookies and following redirects.
     */
    fun navigate(url: String): WebPage {
        val targetUrl = normalizeUrl(url)
        val page = executeRequestWithRedirects(targetUrl, method = "GET", payload = null, contentType = null)
        synchronized(historyStack) {
            historyStack.add(page.url)
        }
        currentPage = page

        checkAndTriggerTakeoverIfNeeded(page)
        return page
    }

    /**
     * Types text into a form input or textarea identified by element index, selector, or name.
     */
    fun type(target: String, text: String): WebPage {
        val page = currentPage ?: throw IllegalStateException("No active page loaded in browser. Call navigate() first.")
        val elem = findElement(page, target) ?: throw IllegalArgumentException("Element not found on page for target: '$target'")

        elem.value = text

        // Update corresponding field in parent form if present
        for (form in page.forms) {
            val field = form.fields.find { it.name == elem.name }
            if (field != null) {
                field.value = text
            }
        }

        return page
    }

    /**
     * Clicks an element. If it's a link, navigates to its destination.
     * If it's a submit button, submits its enclosing form.
     */
    fun click(target: String): WebPage {
        val page = currentPage ?: throw IllegalStateException("No active page loaded in browser. Call navigate() first.")
        val elem = findElement(page, target) ?: throw IllegalArgumentException("Element not found on page for target: '$target'")

        if (!elem.href.isNullOrBlank()) {
            val resolvedUrl = resolveRelativeUrl(page.url, elem.href)
            return navigate(resolvedUrl)
        }

        if (elem.type == "submit" || elem.tagName == "button") {
            return submit(elem.formId)
        }

        return page
    }

    /**
     * Submits a form on the current page with all populated field values.
     */
    fun submit(formTarget: String? = null): WebPage {
        val page = currentPage ?: throw IllegalStateException("No active page loaded in browser. Call navigate() first.")
        val form = if (!formTarget.isNullOrBlank()) {
            page.forms.find { it.id == formTarget || it.index.toString() == formTarget }
                ?: page.forms.firstOrNull()
        } else {
            page.forms.firstOrNull()
        } ?: throw IllegalStateException("No form available to submit on page: ${page.url}")

        val targetUrl = resolveRelativeUrl(page.url, form.action.ifBlank { page.url })

        val formData = form.fields.joinToString("&") { field ->
            val encodedName = urlEncode(field.name)
            val encodedValue = urlEncode(field.value)
            "$encodedName=$encodedValue"
        }

        val submittedPage = if (form.method.equals("GET", ignoreCase = true)) {
            val separator = if (targetUrl.contains("?")) "&" else "?"
            val fullUrl = if (formData.isNotBlank()) "$targetUrl$separator$formData" else targetUrl
            navigate(fullUrl)
        } else {
            val pageResult = executeRequestWithRedirects(
                targetUrl = targetUrl,
                method = "POST",
                payload = formData,
                contentType = "application/x-www-form-urlencoded"
            )
            synchronized(historyStack) {
                historyStack.add(pageResult.url)
            }
            currentPage = pageResult
            checkAndTriggerTakeoverIfNeeded(pageResult)
            pageResult
        }

        return submittedPage
    }

    /**
     * Navigates back in history stack.
     */
    fun back(): WebPage? {
        synchronized(historyStack) {
            if (historyStack.size > 1) {
                historyStack.removeAt(historyStack.lastIndex) // Remove current
                val prevUrl = historyStack.removeAt(historyStack.lastIndex) // Pop previous to re-navigate
                return navigate(prevUrl)
            }
        }
        return currentPage
    }

    /**
     * Reloads current page.
     */
    fun reload(): WebPage? {
        return currentPage?.url?.let { navigate(it) }
    }

    /**
     * Returns the current loaded page model.
     */
    fun getCurrentPage(): WebPage? = currentPage

    /**
     * Produces a token-efficient text snapshot of the current page optimized for LLM reasoning.
     */
    fun getPageSnapshotForLlm(): String {
        val page = currentPage ?: return "No page loaded in VirtualBrowser. Use browser_navigate to load a web page."
        val sb = StringBuilder()

        sb.appendLine("=== BROWSER SNAPSHOT ===")
        sb.appendLine("Title: ${page.title}")
        sb.appendLine("URL: ${page.url}")
        sb.appendLine("Status: ${page.statusCode}")

        if (page.isSecurityChallenge) {
            sb.appendLine("⚠️ SECURITY CHALLENGE DETECTED: ${page.detectedChallengeType?.name}")
            sb.appendLine("Action required: Agent must request user takeover to solve challenge.")
        }

        sb.appendLine("\n--- INTERACTIVE ELEMENTS ---")
        if (page.elements.isEmpty()) {
            sb.appendLine("(No interactive elements found on this page)")
        } else {
            for (elem in page.elements) {
                val tagInfo = elem.tagName.uppercase()
                val typeInfo = if (elem.type != null) " (${elem.type})" else ""
                val nameInfo = if (elem.name != null) " name=\"${elem.name}\"" else ""
                val idInfo = if (elem.elementId != null) " id=\"${elem.elementId}\"" else ""
                val phInfo = if (!elem.placeholder.isNullOrBlank()) " placeholder=\"${elem.placeholder}\"" else ""
                val valInfo = if (elem.isSecret) " [SECRET]" else if (!elem.value.isNullOrBlank()) " value=\"${elem.value}\"" else ""
                val textInfo = if (elem.text.isNotBlank() && elem.text != elem.placeholder) " text=\"${elem.text.take(40)}\"" else ""
                val hrefInfo = if (!elem.href.isNullOrBlank()) " href=\"${elem.href}\"" else ""

                sb.appendLine("[${elem.index}] $tagInfo$typeInfo$nameInfo$idInfo$phInfo$valInfo$textInfo$hrefInfo")
            }
        }

        sb.appendLine("\n--- MAIN PAGE CONTENT ---")
        val lines = page.contentText.lines().take(40)
        for (l in lines) {
            if (l.isNotBlank()) sb.appendLine(l)
        }
        if (page.contentText.lines().size > 40) {
            sb.appendLine("... [Content truncated for brevity] ...")
        }

        return sb.toString()
    }

    /**
     * Escalates control to the user when facing CAPTCHA, 2FA/MFA, or ambiguous barriers.
     */
    fun requestUserTakeover(reason: TakeoverReason, message: String): UserTakeoverState {
        val page = currentPage
        val state = UserTakeoverState(
            status = TakeoverStatus.WAITING_FOR_USER_CONTROL.name,
            reason = reason.name,
            message = message,
            currentUrl = page?.url ?: "",
            targetDomain = page?.url?.let { CookieJar.extractDomain(it) } ?: "",
            timestampMs = Clock.System.now().toEpochMilliseconds(),
            activeSessionId = Uuid.randomUuid()
        )
        this.takeoverState = state
        onTakeoverCallback?.invoke(state)
        logger.warning("[VirtualBrowser] Handoff initiated to User for reason: ${reason.name} - $message")
        return state
    }

    /**
     * Resumes autonomous execution after the user has completed verification in the interactive webview/browser.
     */
    fun resumeFromUserTakeover(newCookies: List<BrowserCookie>? = null, resumeUrl: String? = null): WebPage {
        if (!newCookies.isNullOrEmpty()) {
            cookieJar.addAll(newCookies)
            logger.info("[VirtualBrowser] Ingested ${newCookies.size} updated session cookies from user takeover.")
        }

        takeoverState = null
        val target = resumeUrl ?: currentPage?.url ?: throw IllegalStateException("No URL to resume browsing.")
        logger.info("[VirtualBrowser] Resuming autonomous agent execution at: $target")
        return navigate(target)
    }

    private fun executeRequestWithRedirects(
        targetUrl: String,
        method: String,
        payload: String?,
        contentType: String?
    ): WebPage {
        var currentUrl = targetUrl
        var currentMethod = method
        var currentPayload = payload
        var currentContentType = contentType
        var redirectCount = 0

        var lastResponse: PlatformHttpResponse? = null

        while (redirectCount < MAX_REDIRECTS) {
            val headers = mutableMapOf(
                "User-Agent" to userAgent,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "en-US,en;q=0.9"
            )

            val cookieHeader = cookieJar.getCookieHeader(currentUrl)
            if (!cookieHeader.isNullOrBlank()) {
                headers["Cookie"] = cookieHeader
            }

            val response = httpEngine.execute(
                url = currentUrl,
                method = currentMethod,
                payload = currentPayload,
                contentType = currentContentType,
                headers = headers,
                timeoutSeconds = timeoutSeconds
            )
            lastResponse = response

            // Record any new cookies from response
            cookieJar.parseResponseCookies(currentUrl, response.headers)

            // Follow HTTP Redirects (301, 302, 303, 307, 308)
            if (response.statusCode in listOf(301, 302, 303, 307, 308)) {
                val location = response.headers.entries
                    .firstOrNull { it.key.equals("Location", ignoreCase = true) }
                    ?.value
                    ?.firstOrNull()

                if (!location.isNullOrBlank()) {
                    val nextUrl = resolveRelativeUrl(currentUrl, location)
                    redirectCount++
                    currentUrl = nextUrl

                    // 303 always converts to GET. 301 and 302 typically convert POST to GET in browser standards.
                    if (response.statusCode == 303 || ((response.statusCode == 301 || response.statusCode == 302) && currentMethod == "POST")) {
                        currentMethod = "GET"
                        currentPayload = null
                        currentContentType = null
                    }
                    continue
                }
            }

            break
        }

        val resp = lastResponse ?: throw Exception("Failed to receive response from: $targetUrl")
        val rawHtml = resp.body ?: ""
        return HtmlParser.parse(currentUrl, resp.statusCode, rawHtml, resp.headers)
    }

    private fun findElement(page: WebPage, target: String): InteractiveElement? {
        val trimmed = target.trim().removePrefix("[").removeSuffix("]")

        // 1. By Index
        val index = trimmed.toIntOrNull()
        if (index != null) {
            return page.elements.find { it.index == index }
        }

        // 2. By ID (#submit)
        val idTarget = if (trimmed.startsWith("#")) trimmed.substring(1) else trimmed
        val byId = page.elements.find { it.elementId.equals(idTarget, ignoreCase = true) }
        if (byId != null) return byId

        // 3. By Name
        val byName = page.elements.find { it.name.equals(trimmed, ignoreCase = true) }
        if (byName != null) return byName

        // 4. By Text
        return page.elements.find { it.text.contains(trimmed, ignoreCase = true) }
    }

    private fun checkAndTriggerTakeoverIfNeeded(page: WebPage) {
        if (page.isSecurityChallenge && page.detectedChallengeType != null) {
            requestUserTakeover(
                reason = page.detectedChallengeType,
                message = "Automated browsing encountered a ${page.detectedChallengeType.name} challenge on ${page.url}. Please complete verification in browser."
            )
        }
    }

    private fun normalizeUrl(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }

    private fun resolveRelativeUrl(baseUrl: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) {
            return relative
        }
        val baseClean = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
        val domain = CookieJar.extractDomain(baseUrl)
        val scheme = if (baseUrl.startsWith("http://")) "http://" else "https://"

        return if (relative.startsWith("/")) {
            "$scheme$domain$relative"
        } else {
            val lastSlash = baseClean.lastIndexOf('/')
            val parentPath = if (lastSlash > 8) baseClean.substring(0, lastSlash + 1) else "$scheme$domain/"
            "$parentPath$relative"
        }
    }

    private fun urlEncode(value: String): String {
        return value
            .replace("%", "%25")
            .replace(" ", "%20")
            .replace("&", "%26")
            .replace("=", "%3D")
            .replace("+", "%2B")
            .replace("?", "%3F")
            .replace("#", "%23")
    }
}
