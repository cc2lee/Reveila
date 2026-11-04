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
     * Parses raw HTML into a structured WebPage model.
     */
    fun parse(url: String, statusCode: Int, rawHtml: String, headers: Map<String, List<String>>): WebPage {
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
            .replace(Regex("(?si)<(br|p|div|h[1-6]|li|tr)[^>]*>"), "\n")
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
            "unusual traffic", "prove you're not a robot"
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
