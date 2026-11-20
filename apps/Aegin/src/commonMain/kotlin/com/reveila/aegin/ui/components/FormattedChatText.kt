package com.reveila.aegin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reveila.aegin.ui.theme.LocalAeginColors
import com.reveila.ai.GanttChartSpec
import com.reveila.ai.MarkdownTableSpec
import com.reveila.ai.ResponsePresentationService

private sealed interface ChatBlock {
    data class Heading(val level: Int, val text: String) : ChatBlock
    data class BulletItem(val marker: String, val text: String) : ChatBlock
    data class GanttBlock(val spec: GanttChartSpec, val rawSource: String) : ChatBlock
    data class MermaidBlock(val diagramType: String, val source: String) : ChatBlock
    data class TableBlock(val spec: MarkdownTableSpec) : ChatBlock
    data class CodeBlock(val language: String, val code: String) : ChatBlock
    data class Paragraph(val text: String) : ChatBlock
    data object Divider : ChatBlock
}

private val INLINE_TOKEN_REGEX = Regex(
    """(\[([^\]]+)\]\((https?://[^\s)]+)\))|(\*\*(.+?)\*\*)|(`([^`]+)`)|(\*([^*]+)\*)|(https?://[^\s<>()]+)"""
)

private fun parseChatBlocks(raw: String): List<ChatBlock> {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<ChatBlock>()
    val paragraphBuf = mutableListOf<String>()
    var inCodeBlock = false
    var codeLang = ""
    val codeBuf = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuf.isNotEmpty()) {
            val joined = paragraphBuf.joinToString("\n").trim()
            if (joined.isNotEmpty()) {
                blocks.add(ChatBlock.Paragraph(joined))
            }
            paragraphBuf.clear()
        }
    }

    fun emitFencedBlock(lang: String, body: String) {
        val cleanLang = lang.trim().lowercase()
        val trimmedBody = body.trim()
        if (trimmedBody.isEmpty()) return
        if (cleanLang == "mermaid" || cleanLang == "gantt" || trimmedBody.startsWith("gantt\n", ignoreCase = true)) {
            val ganttSpec = ResponsePresentationService.parseMermaidGantt(trimmedBody)
            if (ganttSpec != null) {
                blocks.add(ChatBlock.GanttBlock(ganttSpec, trimmedBody))
                return
            }
            val diagramType = ResponsePresentationService.detectMermaidType(trimmedBody)
            blocks.add(ChatBlock.MermaidBlock(diagramType, trimmedBody))
            return
        }
        blocks.add(ChatBlock.CodeBlock(cleanLang, body))
    }

    var idx = 0
    while (idx < lines.size) {
        val line = lines[idx]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            flushParagraph()
            if (inCodeBlock) {
                emitFencedBlock(codeLang, codeBuf.joinToString("\n"))
                codeBuf.clear()
                codeLang = ""
                inCodeBlock = false
            } else {
                codeLang = trimmed.removePrefix("```").trim()
                inCodeBlock = true
            }
            idx++
            continue
        }
        if (inCodeBlock) {
            codeBuf.add(line)
            idx++
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            idx++
            continue
        }

        // Detect Markdown pipe tables (header row + separator row)
        if (trimmed.contains("|") && idx + 1 < lines.size) {
            val nextTrimmed = lines[idx + 1].trim()
            if (nextTrimmed.contains("|") && nextTrimmed.contains("-")) {
                val tableLines = mutableListOf<String>()
                var j = idx
                while (j < lines.size && lines[j].trim().contains("|")) {
                    tableLines.add(lines[j])
                    j++
                }
                val parsedTable = ResponsePresentationService.parseMarkdownTable(tableLines)
                if (parsedTable != null) {
                    flushParagraph()
                    blocks.add(ChatBlock.TableBlock(parsedTable))
                    idx = j
                    continue
                }
            }
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks.add(ChatBlock.Divider)
            idx++
            continue
        }

        val headingMatch = Regex("^(#{1,3})\\s+(.+)$").matchEntire(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks.add(ChatBlock.Heading(headingMatch.groupValues[1].length, headingMatch.groupValues[2].trim()))
            idx++
            continue
        }

        val bulletMatch = Regex("^([-*•])\\s+(.+)$").matchEntire(trimmed)
        if (bulletMatch != null) {
            flushParagraph()
            blocks.add(ChatBlock.BulletItem("•", bulletMatch.groupValues[2].trim()))
            idx++
            continue
        }

        val numberedMatch = Regex("^(\\d+[.)])\\s+(.+)$").matchEntire(trimmed)
        if (numberedMatch != null) {
            flushParagraph()
            blocks.add(ChatBlock.BulletItem(numberedMatch.groupValues[1], numberedMatch.groupValues[2].trim()))
            idx++
            continue
        }

        paragraphBuf.add(line)
        idx++
    }

    if (inCodeBlock && codeBuf.isNotEmpty()) {
        emitFencedBlock(codeLang, codeBuf.joinToString("\n"))
    }
    flushParagraph()
    return blocks
}

private fun buildInlineFormattedString(
    text: String,
    linkColor: Color,
    codeBgColor: Color,
    codeTextColor: Color,
    onOpenUrl: ((String) -> Unit)?
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        for (match in INLINE_TOKEN_REGEX.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val mdLinkLabel = match.groupValues[2]
            val mdLinkUrl = match.groupValues[3]
            val boldText = match.groupValues[5]
            val codeText = match.groupValues[7]
            val italicText = match.groupValues[9]
            val bareUrl = match.groupValues[10]

            when {
                mdLinkUrl.isNotEmpty() -> {
                    val cleanUrl = mdLinkUrl.trimEnd('.', ',', ';')
                    if (onOpenUrl != null) {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "URL",
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = TextDecoration.Underline
                                    )
                                ),
                                linkInteractionListener = { onOpenUrl(cleanUrl) }
                            )
                        ) {
                            append(mdLinkLabel)
                        }
                    } else {
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            append(mdLinkLabel)
                        }
                    }
                }
                boldText.isNotEmpty() -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldText)
                    }
                }
                codeText.isNotEmpty() -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBgColor,
                            color = codeTextColor
                        )
                    ) {
                        append(" $codeText ")
                    }
                }
                italicText.isNotEmpty() -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(italicText)
                    }
                }
                bareUrl.isNotEmpty() -> {
                    val cleanUrl = bareUrl.trimEnd('.', ',', ';', ')')
                    val trailing = bareUrl.removePrefix(cleanUrl)
                    if (onOpenUrl != null) {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "URL",
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = TextDecoration.Underline
                                    )
                                ),
                                linkInteractionListener = { onOpenUrl(cleanUrl) }
                            )
                        ) {
                            append(cleanUrl)
                        }
                    } else {
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            append(cleanUrl)
                        }
                    }
                    if (trailing.isNotEmpty()) append(trailing)
                }
                else -> append(match.value)
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

/**
 * Content-aware Markdown & rich-text renderer for chat history messages.
 * Formats headings, bullet/numbered lists, bold/italic/code spans, and clickable URLs
 * that can open directly inside the interactive Virtual Browser.
 */
@Composable
fun FormattedChatText(
    text: String,
    modifier: Modifier = Modifier,
    baseColor: Color = LocalAeginColors.current.text,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 22.sp,
    onOpenUrl: ((String) -> Unit)? = null
) {
    val theme = LocalAeginColors.current
    val blocks = remember(text) { parseChatBlocks(text) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is ChatBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 17.sp
                        2 -> 16.sp
                        else -> 15.sp
                    }
                    val formatted = remember(block.text, theme.cyan) {
                        buildInlineFormattedString(block.text, theme.cyan, theme.cardAlt, theme.cyan, onOpenUrl)
                    }
                    Text(
                        text = formatted,
                        color = baseColor,
                        fontSize = size,
                        lineHeight = (size.value + 6f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                is ChatBlock.BulletItem -> {
                    val formatted = remember(block.text, theme.cyan) {
                        buildInlineFormattedString(block.text, theme.cyan, theme.cardAlt, theme.cyan, onOpenUrl)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.marker,
                            color = theme.cyan,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = formatted,
                            color = baseColor,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is ChatBlock.GanttBlock -> {
                    GanttTimelineMiniCard(spec = block.spec)
                }

                is ChatBlock.MermaidBlock -> {
                    MermaidDiagramMiniCard(
                        diagramType = block.diagramType,
                        source = block.source
                    )
                }

                is ChatBlock.TableBlock -> {
                    MarkdownTableMiniCard(spec = block.spec)
                }

                is ChatBlock.CodeBlock -> {
                    ExpandableCodeMiniCard(
                        language = block.language,
                        code = block.code
                    )
                }

                is ChatBlock.Divider -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(theme.glassBorder)
                            .padding(top = 1.dp)
                    )
                }

                is ChatBlock.Paragraph -> {
                    val formatted = remember(block.text, theme.cyan) {
                        buildInlineFormattedString(block.text, theme.cyan, theme.cardAlt, theme.cyan, onOpenUrl)
                    }
                    Text(
                        text = formatted,
                        color = baseColor,
                        fontSize = fontSize,
                        lineHeight = lineHeight
                    )
                }
            }
        }
    }
}
