package com.reveila.util.md;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Utility class for Markdown processing using commonmark-java.
 * This class provides a centralized way to convert Markdown text to HTML.
 *
 * @author Charles Lee
 */
public final class Markdown {

    /**
     * Shared Parser instance.
     * Commonmark Parsers are thread-safe and can be reused for better performance.
     */
    private static final Parser PARSER = Parser.builder().build();

    /**
     * Shared HtmlRenderer instance.
     * Commonmark Renderers are thread-safe and can be reused for better performance.
     */
    private static final HtmlRenderer RENDERER_ESCAPE = HtmlRenderer.builder().escapeHtml(true).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().escapeHtml(false).build();

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private Markdown() {
        // Utility class
    }

    /**
     * Converts a Markdown string to its HTML representation.
     *
     * @param markdown The Markdown source string.
     * @return The rendered HTML string, or an empty string if input is null or blank.
     */
    public static String toHtml(String markdown, boolean escapeHtml) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        Node document = PARSER.parse(markdown);
        String unsafeHtml;
        if (escapeHtml) {
            unsafeHtml = RENDERER_ESCAPE.render(document);
        } else {
            unsafeHtml = RENDERER.render(document);
        }

        // Sanitize HTML to prevent Cross-Site Scripting (XSS) attacks
        return Jsoup.clean(unsafeHtml, Safelist.relaxed());
    }
}
