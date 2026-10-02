package com.reveila.util.md

import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * Utility class for Markdown processing using commonmark-java.
 * This class provides a centralized way to convert Markdown text to HTML.
 */
class Markdown private constructor() {

    companion object {
        private val PARSER: Parser = Parser.builder().build()
        private val RENDERER_ESCAPE: HtmlRenderer = HtmlRenderer.builder().escapeHtml(true).build()
        private val RENDERER: HtmlRenderer = HtmlRenderer.builder().escapeHtml(false).build()

        /**
         * Converts a Markdown string to its HTML representation.
         */
        @JvmStatic
        fun toHtml(markdown: String?, escapeHtml: Boolean): String {
            if (markdown.isNullOrBlank()) {
                return ""
            }

            val document: Node = PARSER.parse(markdown)
            val renderedHtml = (if (escapeHtml) RENDERER_ESCAPE else RENDERER).render(document)

            val unsafeHtml = renderedHtml ?: ""
            return Jsoup.clean(unsafeHtml, Safelist.relaxed())
        }
    }
}
