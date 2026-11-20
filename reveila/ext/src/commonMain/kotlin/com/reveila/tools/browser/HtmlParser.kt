package com.reveila.tools.browser

/**
 * Pure Kotlin Multiplatform HTML parser and DOM extractor.
 * Converts raw web documents into semantic interactive elements and clean text
 * tailored for LLM autonomous browser interaction and Human-In-The-Loop detection.
 */
object HtmlParser {

    private val SCRIPT_REGEX = Regex("(?si)<script\\b[^>]*>.*?</script>")
    private val STYLE_REGEX = Regex("(?si)<style\\b[^>]*>.*?</style>")
    private val SVG_REGEX = Regex("(?si)<svg\\b[^>]*>.*?</svg>")
    private val COMMENT_REGEX = Regex("(?si)<!--.*?-->")
    private val NOSCRIPT_REGEX = Regex("(?si)<noscript\\b[^>]*>.*?</noscript>")
    private val TITLE_REGEX = Regex("(?si)<title[^>]*>(.*?)</title>")
    private val TAG_ATTR_REGEX = Regex("([a-zA-Z0-9_-]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+)))?")

    /**
     * Parses raw HTML (or RSS/Atom XML feeds) into a structured WebPage model.
     */
    fun parse(url: String, statusCode: Int, rawHtml: String, headers: Map<String, List<String>>): WebPage {
        if (isRssOrAtomFeed(rawHtml, headers)) {
            return parseFeedAsHtmlPage(url, statusCode, rawHtml, headers)
        }

        val title = TITLE_REGEX.find(rawHtml)?.groupValues?.get(1)?.trim()?.let { decodeHtmlEntities(it) } ?: "Untitled Page"

        // Strip non-visual code blocks for clean text & element extraction
        var cleaned = SCRIPT_REGEX.replace(rawHtml, " ")
        cleaned = STYLE_REGEX.replace(cleaned, " ")
        cleaned = SVG_REGEX.replace(cleaned, " ")
        cleaned = COMMENT_REGEX.replace(cleaned, " ")
        cleaned = NOSCRIPT_REGEX.replace(cleaned, " ")

        val elements = mutableListOf<InteractiveElement>()
        val forms = mutableListOf<WebForm>()

        extractFormsAndInputs(cleaned, elements, forms)
        extractButtons(cleaned, elements)
        extractLinks(cleaned, elements)

        val contentText = extractReadableText(cleaned)

        val (isChallenge, challengeType) = detectSecurityChallenge(rawHtml, title, contentText)

        return WebPage(
            url = url,
            statusCode = statusCode,
            title = title,
            contentText = contentText,
            elements = elements,
            forms = forms,
            rawHtml = rawHtml,
            headers = headers,
            isSecurityChallenge = isChallenge,
            detectedChallengeType = challengeType
        )
    }

    private fun isRssOrAtomFeed(raw: String, headers: Map<String, List<String>>): Boolean {
        val cleanedHead = raw.trimStart('\uFEFF', ' ', '\t', '\r', '\n').take(2000).lowercase()
        val contentType = headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }
            ?.value?.joinToString(";")?.lowercase().orEmpty()
        val hasFeedTags = cleanedHead.contains("<rss") ||
            cleanedHead.contains("<feed") ||
            cleanedHead.contains("<rdf:rdf") ||
            (cleanedHead.contains("<channel") && cleanedHead.contains("<item"))
        if (!hasFeedTags) return false
        if (contentType.contains("application/rss") || contentType.contains("application/atom") || contentType.contains("xml")) {
            return true
        }
        return cleanedHead.startsWith("<?xml") ||
            cleanedHead.startsWith("<rss") ||
            cleanedHead.startsWith("<feed") ||
            cleanedHead.startsWith("<rdf:rdf") ||
            (cleanedHead.contains("<channel") && cleanedHead.contains("<item"))
    }

    private fun parseFeedAsHtmlPage(
        url: String,
        statusCode: Int,
        rawXml: String,
        headers: Map<String, List<String>>
    ): WebPage {
        fun cleanTagValue(rawVal: String): String {
            val noCdata = rawVal.replace(Regex("(?s)<!\\[CDATA\\[(.*?)\\]\\]>"), "$1")
            val decodedOnce = decodeHtmlEntities(noCdata)
            val noTags = decodedOnce.replace(Regex("<[^>]+>"), " ")
            return decodeHtmlEntities(noTags).replace(Regex("\\s+"), " ").trim()
        }

        val channelTitle = Regex("(?si)<channel[^>]*>.*?<title[^>]*>(.*?)</title>")
            .find(rawXml)?.groupValues?.get(1)?.let { cleanTagValue(it) }
            ?: TITLE_REGEX.find(rawXml)?.groupValues?.get(1)?.let { cleanTagValue(it) }
            ?: "News Feed"

        val itemMatches = Regex("(?si)<item\\b[^>]*>(.*?)</item>").findAll(rawXml).toList()
        val entryMatches = if (itemMatches.isEmpty()) {
            Regex("(?si)<entry\\b[^>]*>(.*?)</entry>").findAll(rawXml).toList()
        } else {
            emptyList()
        }
        val rawEntries = (itemMatches.ifEmpty { entryMatches }).take(20)

        val elements = mutableListOf<InteractiveElement>()
        val textLines = mutableListOf<String>()
        val htmlCards = StringBuilder()

        var elemIndex = 1
        for (match in rawEntries) {
            val block = match.groupValues[1]
            val itemTitle = Regex("(?si)<title[^>]*>(.*?)</title>").find(block)?.groupValues?.get(1)?.let { cleanTagValue(it) }.orEmpty()
            val itemLink = Regex("(?si)<link[^>]*>(.*?)</link>").find(block)?.groupValues?.get(1)?.let { cleanTagValue(it) }
                ?.takeIf { it.isNotBlank() }
                ?: Regex("(?si)<link\\b[^>]*href=[\"']([^\"']+)[\"']").find(block)?.groupValues?.get(1)?.let { decodeHtmlEntities(it).trim() }.orEmpty()
            val itemDate = (Regex("(?si)<pubDate[^>]*>(.*?)</pubDate>").find(block)
                ?: Regex("(?si)<updated[^>]*>(.*?)</updated>").find(block))
                ?.groupValues?.get(1)?.let { cleanTagValue(it) }.orEmpty()
            val itemSource = Regex("(?si)<source[^>]*>(.*?)</source>").find(block)?.groupValues?.get(1)?.let { cleanTagValue(it) }.orEmpty()
            val itemDesc = (Regex("(?si)<description[^>]*>(.*?)</description>").find(block)
                ?: Regex("(?si)<summary[^>]*>(.*?)</summary>").find(block))
                ?.groupValues?.get(1)?.let { cleanTagValue(it) }.orEmpty()

            if (itemTitle.isBlank() && itemLink.isBlank()) continue

            if (itemLink.isNotBlank()) {
                elements.add(
                    InteractiveElement(
                        index = elemIndex++,
                        tagName = "a",
                        text = itemTitle.ifBlank { itemLink },
                        href = itemLink,
                        isClickable = true,
                        isInputField = false,
                        isSecret = false,
                        selector = "a[href=\"$itemLink\"]"
                    )
                )
            }

            val metaParts = listOfNotNull(
                itemSource.takeIf { it.isNotBlank() },
                itemDate.takeIf { it.isNotBlank() }
            ).joinToString(" • ")

            val distinctDesc = itemDesc.takeIf {
                it.isNotBlank() &&
                    !it.equals(itemTitle, ignoreCase = true) &&
                    !it.startsWith(itemTitle.take(40), ignoreCase = true)
            }.orEmpty()

            textLines.add(buildString {
                append("• ").append(itemTitle.ifBlank { itemLink })
                if (metaParts.isNotBlank()) append(" ($metaParts)")
                if (distinctDesc.isNotBlank()) {
                    append("\n  ").append(distinctDesc.take(220))
                }
            })

            val safeTitle = escapeHtml(itemTitle.ifBlank { itemLink })
            val safeLink = escapeHtml(itemLink)
            val safeMeta = escapeHtml(metaParts)
            val safeDesc = escapeHtml(distinctDesc.take(240))
            htmlCards.append(
                """
                <article style="background:#161b26;border:1px solid #2a3347;border-radius:10px;padding:10px 12px;margin-bottom:8px;">
                  <div style="font-size:14px;font-weight:700;line-height:1.35;margin-bottom:3px;">
                    ${if (safeLink.isNotBlank()) """<a href="$safeLink" style="color:#67e8f9;text-decoration:none;">$safeTitle</a>""" else safeTitle}
                  </div>
                  ${if (safeMeta.isNotBlank()) """<div style="font-size:11px;color:#94a3b8;margin-bottom:4px;">$safeMeta</div>""" else ""}
                  ${if (safeDesc.isNotBlank()) """<div style="font-size:12px;color:#cbd5e1;line-height:1.4;">$safeDesc</div>""" else ""}
                </article>
                """.trimIndent()
            )
        }

        val renderedHtml = """
            <!DOCTYPE html>
            <!-- reveila-feed-html -->
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>${escapeHtml(channelTitle)}</title>
            </head>
            <body style="margin:0;padding:10px 12px;background:#0d1117;color:#e2e8f0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
              <header style="margin-bottom:10px;padding-bottom:8px;border-bottom:1px solid #2a3347;">
                <div style="font-size:10px;letter-spacing:0.06em;text-transform:uppercase;color:#38bdf8;font-weight:700;">Live News Feed</div>
                <h1 style="margin:3px 0 0 0;font-size:15px;color:#f8fafc;">${escapeHtml(channelTitle)}</h1>
              </header>
              $htmlCards
            </body>
            </html>
        """.trimIndent()

        val contentText = buildString {
            appendLine(channelTitle)
            textLines.forEach { appendLine(it) }
        }.trim()

        return WebPage(
            url = url,
            statusCode = statusCode,
            title = channelTitle,
            contentText = contentText,
            elements = elements,
            forms = emptyList(),
            rawHtml = renderedHtml,
            headers = headers,
            isSecurityChallenge = false,
            detectedChallengeType = null
        )
    }

    private fun escapeHtml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun extractFormsAndInputs(
        html: String,
        elements: MutableList<InteractiveElement>,
        forms: MutableList<WebForm>
    ) {
        // Form extraction
        val formRegex = Regex("(?si)<form\\b([^>]*)>(.*?)</form>")
        var formIndex = 1
        var elemIndex = elements.size + 1

        for (match in formRegex.findAll(html)) {
            val formAttrs = parseAttributes(match.groupValues[1])
            val formInner = match.groupValues[2]

            val action = formAttrs["action"] ?: ""
            val method = formAttrs["method"]?.uppercase() ?: "POST"
            val formId = formAttrs["id"]

            val webForm = WebForm(
                index = formIndex++,
                id = formId,
                action = action,
                method = method
            )

            // Extract inputs inside form
            val inputRegex = Regex("(?si)<(input|textarea|select)\\b([^>]*)>(?:(.*?)</(?:textarea|select)>)?")
            for (inputMatch in inputRegex.findAll(formInner)) {
                val tag = inputMatch.groupValues[1].lowercase()
                val attrs = parseAttributes(inputMatch.groupValues[2])
                val innerText = inputMatch.groupValues[3]

                val type = attrs["type"]?.lowercase() ?: if (tag == "textarea") "textarea" else if (tag == "select") "select" else "text"
                if (type == "hidden") {
                    val name = attrs["name"]
                    if (name != null) {
                        webForm.fields.add(FormField(name, type, attrs["value"] ?: ""))
                    }
                    continue
                }

                val name = attrs["name"]
                val id = attrs["id"]
                val placeholder = attrs["placeholder"]
                val value = attrs["value"] ?: innerText.trim()
                val isSecret = type == "password"
                val isClickable = type in listOf("submit", "button", "checkbox", "radio")
                val isInput = type in listOf("text", "password", "email", "number", "search", "tel", "url", "textarea", "select")

                val label = attrs["aria-label"] ?: attrs["title"] ?: placeholder ?: name ?: id ?: ""
                val selector = buildSelector(tag, id, name, type)

                val elem = InteractiveElement(
                    index = elemIndex++,
                    tagName = tag,
                    type = type,
                    name = name,
                    elementId = id,
                    text = label,
                    placeholder = placeholder,
                    value = value,
                    isClickable = isClickable,
                    isInputField = isInput,
                    isSecret = isSecret,
                    selector = selector,
                    formId = formId ?: "form_${webForm.index}"
                )
                elements.add(elem)

                if (name != null) {
                    webForm.fields.add(FormField(name, type, value, isSecret))
                }
            }

            forms.add(webForm)
        }

        // Also extract standalone inputs not wrapped in <form>
        val standaloneInputRegex = Regex("(?si)<(input|textarea)\\b([^>]*)>")
        for (match in standaloneInputRegex.findAll(html)) {
            val tag = match.groupValues[1].lowercase()
            val attrs = parseAttributes(match.groupValues[2])
            val type = attrs["type"]?.lowercase() ?: if (tag == "textarea") "textarea" else "text"
            if (type == "hidden") continue

            val name = attrs["name"]
            val id = attrs["id"]

            // Check if already captured inside a form
            if (elements.any { it.elementId == id && id != null } || elements.any { it.name == name && name != null }) {
                continue
            }

            val placeholder = attrs["placeholder"]
            val value = attrs["value"] ?: ""
            val isSecret = type == "password"
            val isClickable = type in listOf("submit", "button", "checkbox", "radio")
            val isInput = type in listOf("text", "password", "email", "number", "search", "tel", "url", "textarea")
            val label = attrs["aria-label"] ?: placeholder ?: name ?: id ?: ""

            elements.add(
                InteractiveElement(
                    index = elemIndex++,
                    tagName = tag,
                    type = type,
                    name = name,
                    elementId = id,
                    text = label,
                    placeholder = placeholder,
                    value = value,
                    isClickable = isClickable,
                    isInputField = isInput,
                    isSecret = isSecret,
                    selector = buildSelector(tag, id, name, type)
                )
            )
        }
    }

    private fun extractButtons(html: String, elements: MutableList<InteractiveElement>) {
        val buttonRegex = Regex("(?si)<button\\b([^>]*)>(.*?)</button>")
        var elemIndex = elements.size + 1

        for (match in buttonRegex.findAll(html)) {
            val attrs = parseAttributes(match.groupValues[1])
            val rawText = stripTags(match.groupValues[2]).trim()
            val id = attrs["id"]
            val name = attrs["name"]
            val type = attrs["type"]?.lowercase() ?: "submit"
            val label = if (rawText.isNotBlank()) rawText else (attrs["aria-label"] ?: "Button")

            // Skip if duplicate id already recorded
            if (id != null && elements.any { it.elementId == id }) continue

            elements.add(
                InteractiveElement(
                    index = elemIndex++,
                    tagName = "button",
                    type = type,
                    name = name,
                    elementId = id,
                    text = label,
                    isClickable = true,
                    isInputField = false,
                    isSecret = false,
                    selector = buildSelector("button", id, name, type)
                )
            )
        }
    }

    private fun extractLinks(html: String, elements: MutableList<InteractiveElement>) {
        val linkRegex = Regex("(?si)<a\\b([^>]*)>(.*?)</a>")
        var elemIndex = elements.size + 1

        for (match in linkRegex.findAll(html)) {
            val attrs = parseAttributes(match.groupValues[1])
            val href = attrs["href"] ?: continue
            if (href.startsWith("javascript:") || href.startsWith("#") || href.isBlank()) continue

            val rawText = stripTags(match.groupValues[2]).trim()
            val label = if (rawText.isNotBlank()) rawText else (attrs["aria-label"] ?: attrs["title"] ?: href)
            val id = attrs["id"]

            elements.add(
                InteractiveElement(
                    index = elemIndex++,
                    tagName = "a",
                    elementId = id,
                    text = label,
                    href = href,
                    isClickable = true,
                    isInputField = false,
                    isSecret = false,
                    selector = if (!id.isNullOrBlank()) "#$id" else "a[href=\"$href\"]"
                )
            )
        }
    }

    private fun extractReadableText(html: String): String {
        var text = html
            .replace(Regex("(?si)<(br|p|div|h[1-6]|li|tr|item|title|pubDate|description)[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), " ")

        text = decodeHtmlEntities(text)
        return text.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun detectSecurityChallenge(rawHtml: String, title: String, contentText: String): Pair<Boolean, TakeoverReason?> {
        val lowerHtml = rawHtml.lowercase()
        val lowerContent = contentText.lowercase()
        val lowerTitle = title.lowercase()

        // 1. CAPTCHA & Bot Protection
        val captchaIndicators = listOf(
            "g-recaptcha", "h-captcha", "hcaptcha", "recaptcha", "cf-turnstile",
            "cf-challenge", "challenge-running", "arkoselabs", "funcaptcha",
            "verify you are human", "security check", "robot or human",
            "unusual traffic", "prove you're not a robot",
            "confirm this search was made by a human", "bots use duckduckgo",
            "select all squares", "challenge-submit"
        )
        if (captchaIndicators.any { lowerHtml.contains(it) || lowerContent.contains(it) || lowerTitle.contains(it) }) {
            return true to TakeoverReason.CAPTCHA_DETECTED
        }

        // 2. MFA / OTP / 2FA
        val mfaIndicators = listOf(
            "two-factor", "2-step verification", "authenticator app",
            "enter the 6-digit code", "verification code", "one-time password",
            "security key", "check your phone", "sms code", "enter otp"
        )
        if (mfaIndicators.any { lowerHtml.contains(it) || lowerContent.contains(it) }) {
            return true to TakeoverReason.MFA_OTP_REQUIRED
        }

        // 3. Biometric / Passkey
        val bioIndicators = listOf("passkey", "touch id", "face id", "fido2", "webauthn")
        if (bioIndicators.any { lowerHtml.contains(it) || lowerContent.contains(it) }) {
            return true to TakeoverReason.BIOMETRIC_LOGIN
        }

        return false to null
    }

    private fun buildSelector(tag: String, id: String?, name: String?, type: String?): String {
        return when {
            !id.isNullOrBlank() -> "#$id"
            !name.isNullOrBlank() -> "$tag[name=\"$name\"]"
            !type.isNullOrBlank() -> "$tag[type=\"$type\"]"
            else -> tag
        }
    }

    fun parseAttributes(rawTagContent: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        for (m in TAG_ATTR_REGEX.findAll(rawTagContent)) {
            val key = m.groupValues[1].lowercase()
            val value = when {
                m.groupValues[2].isNotEmpty() -> m.groupValues[2]
                m.groupValues[3].isNotEmpty() -> m.groupValues[3]
                m.groupValues[4].isNotEmpty() -> m.groupValues[4]
                else -> key
            }
            attrs[key] = decodeHtmlEntities(value)
        }
        return attrs
    }

    private fun stripTags(html: String): String {
        return html.replace(Regex("<[^>]+>"), " ")
    }

    fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&ndash;", "-")
            .replace("&mdash;", "--")
    }
}
