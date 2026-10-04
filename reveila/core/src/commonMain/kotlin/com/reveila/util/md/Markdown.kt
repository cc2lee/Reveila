package com.reveila.util.md

/**
 * Pure Kotlin Multiplatform Markdown processing utility.
 * Converts standard Markdown text into sanitized HTML.
 */
class Markdown private constructor() {

    companion object {

        fun toHtml(markdown: String?, escapeHtml: Boolean = true): String {
            if (markdown.isNullOrBlank()) {
                return ""
            }

            val lines = markdown.lines()
            val sb = StringBuilder()
            var inCodeBlock = false
            var inUl = false
            var inOl = false
            var inBlockquote = false

            fun closeLists() {
                if (inUl) {
                    sb.append("</ul>\n")
                    inUl = false
                }
                if (inOl) {
                    sb.append("</ol>\n")
                    inOl = false
                }
            }

            fun closeBlockquote() {
                if (inBlockquote) {
                    sb.append("</blockquote>\n")
                    inBlockquote = false
                }
            }

            for (rawLine in lines) {
                val trimmed = rawLine.trim()

                // Code block toggle
                if (trimmed.startsWith("```")) {
                    closeLists()
                    closeBlockquote()
                    if (inCodeBlock) {
                        sb.append("</code></pre>\n")
                        inCodeBlock = false
                    } else {
                        val lang = trimmed.removePrefix("```").trim()
                        val classAttr = if (lang.isNotEmpty()) " class=\"language-$lang\"" else ""
                        sb.append("<pre><code$classAttr>")
                        inCodeBlock = true
                    }
                    continue
                }

                if (inCodeBlock) {
                    sb.append(escape(rawLine)).append("\n")
                    continue
                }

                if (trimmed.isEmpty()) {
                    closeLists()
                    closeBlockquote()
                    continue
                }

                // Blockquote
                if (trimmed.startsWith(">")) {
                    closeLists()
                    if (!inBlockquote) {
                        sb.append("<blockquote>")
                        inBlockquote = true
                    }
                    val quoteContent = trimmed.removePrefix(">").trim()
                    sb.append("<p>").append(formatInline(quoteContent, escapeHtml)).append("</p>\n")
                    continue
                } else {
                    closeBlockquote()
                }

                // Headings
                val headingLevel = countHeadingLevel(trimmed)
                if (headingLevel in 1..6) {
                    closeLists()
                    val headingText = trimmed.drop(headingLevel).trim()
                    sb.append("<h$headingLevel>")
                        .append(formatInline(headingText, escapeHtml))
                        .append("</h$headingLevel>\n")
                    continue
                }

                // Unordered list item
                if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                    if (inOl) {
                        sb.append("</ol>\n")
                        inOl = false
                    }
                    if (!inUl) {
                        sb.append("<ul>\n")
                        inUl = true
                    }
                    val itemText = trimmed.drop(2).trim()
                    sb.append("  <li>").append(formatInline(itemText, escapeHtml)).append("</li>\n")
                    continue
                }

                // Ordered list item
                val olMatch = isOrderedListItem(trimmed)
                if (olMatch != null) {
                    if (inUl) {
                        sb.append("</ul>\n")
                        inUl = false
                    }
                    if (!inOl) {
                        sb.append("<ol>\n")
                        inOl = true
                    }
                    sb.append("  <li>").append(formatInline(olMatch, escapeHtml)).append("</li>\n")
                    continue
                }

                // Normal paragraph
                closeLists()
                sb.append("<p>").append(formatInline(trimmed, escapeHtml)).append("</p>\n")
            }

            if (inCodeBlock) {
                sb.append("</code></pre>\n")
            }
            closeLists()
            closeBlockquote()

            return sanitize(sb.toString())
        }

        private fun countHeadingLevel(line: String): Int {
            var count = 0
            while (count < line.length && line[count] == '#') {
                count++
            }
            return if (count in 1..6 && count < line.length && line[count] == ' ') count else 0
        }

        private fun isOrderedListItem(line: String): String? {
            val dotIdx = line.indexOf(". ")
            if (dotIdx in 1..9) {
                val numPart = line.substring(0, dotIdx)
                if (numPart.all { it.isDigit() }) {
                    return line.substring(dotIdx + 2).trim()
                }
            }
            return null
        }

        private fun formatInline(text: String, escapeHtml: Boolean): String {
            var s = if (escapeHtml) escape(text) else text

            // Inline code: `code`
            s = replacePattern(s, "`([^`]+)`") { "<code>${it.groupValues[1]}</code>" }

            // Bold: **text** or __text__
            s = replacePattern(s, "\\*\\*(.+?)\\*\\*") { "<strong>${it.groupValues[1]}</strong>" }
            s = replacePattern(s, "__(.+?)__") { "<strong>${it.groupValues[1]}</strong>" }

            // Italic: *text* or _text_
            s = replacePattern(s, "\\*(.+?)\\*") { "<em>${it.groupValues[1]}</em>" }
            s = replacePattern(s, "_(.+?)_") { "<em>${it.groupValues[1]}</em>" }

            // Images: ![alt](url)
            s = replacePattern(s, "!\\[(.*?)\\]\\((.*?)\\)") {
                "<img src=\"${sanitizeUrl(it.groupValues[2])}\" alt=\"${escape(it.groupValues[1])}\" />"
            }

            // Links: [text](url)
            s = replacePattern(s, "\\[(.*?)\\]\\((.*?)\\)") {
                "<a href=\"${sanitizeUrl(it.groupValues[2])}\">${it.groupValues[1]}</a>"
            }

            return s
        }

        private fun replacePattern(input: String, regexStr: String, transform: (MatchResult) -> CharSequence): String {
            val regex = Regex(regexStr)
            return regex.replace(input, transform)
        }

        private fun escape(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
        }

        private fun sanitizeUrl(url: String): String {
            val trimmed = url.trim()
            val lower = trimmed.lowercase()
            if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
                return "#"
            }
            return escape(trimmed)
        }

        private fun sanitize(html: String): String {
            // Strip any <script> tags or harmful event handlers
            var clean = Regex("<script.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(html, "")
            clean = Regex("<iframe.*?</iframe>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(clean, "")
            clean = Regex("on\\w+\\s*=", RegexOption.IGNORE_CASE).replace(clean, "data-blocked=")
            return clean
        }
    }
}
