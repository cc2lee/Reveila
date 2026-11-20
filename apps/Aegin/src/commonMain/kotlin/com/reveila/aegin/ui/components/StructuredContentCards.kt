package com.reveila.aegin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reveila.aegin.bridge.BrowserLiveState
import com.reveila.aegin.ui.theme.LocalAeginColors
import com.reveila.ai.GanttChartSpec
import com.reveila.ai.GanttTaskItem
import com.reveila.ai.MarkdownTableSpec
import com.reveila.ai.ResponsePresentationService

@Composable
private fun MiniCardHeader(
    badge: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    onExpand: () -> Unit
) {
    val theme = LocalAeginColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.cardAlt)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    color = accentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = theme.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = theme.subText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "↗ Expand",
            color = theme.cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(CircleShape)
                .background(theme.cyan.copy(alpha = 0.12f))
                .clickable(onClick = onExpand)
                .padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FullScreenViewportFrame(
    badge: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val theme = LocalAeginColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(theme.bg)
                .border(1.dp, theme.glassBorder, RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.cardAlt)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.18f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(badge, color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text(
                            text = title,
                            color = theme.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = theme.subText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("✕ Close", color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                content()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. NATIVE COMPOSE GANTT TIMELINE MINI-CARD + FULL-SCREEN INTERACTIVE VIEWER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GanttTimelineMiniCard(
    spec: GanttChartSpec,
    modifier: Modifier = Modifier
) {
    val theme = LocalAeginColors.current
    var expanded by remember { mutableStateOf(false) }
    val sectionCount = remember(spec) { spec.tasks.map { it.section }.distinct().size }
    val subtitle = buildString {
        append("${spec.tasks.size} milestones • $sectionCount section(s)")
        if (spec.timelineStartLabel.isNotBlank() && spec.timelineEndLabel.isNotBlank()) {
            append(" • ${spec.timelineStartLabel} → ${spec.timelineEndLabel}")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.bg.copy(alpha = 0.72f))
            .border(1.dp, theme.cyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable { expanded = true }
    ) {
        MiniCardHeader(
            badge = "GANTT",
            title = spec.title,
            subtitle = subtitle,
            accentColor = theme.cyan,
            onExpand = { expanded = true }
        )

        GanttTimelineBody(
            spec = spec,
            maxVisibleTasks = 6,
            trackWidth = 220.dp,
            interactiveSelection = false
        )

        if (spec.tasks.size > 6) {
            Text(
                text = "+ ${spec.tasks.size - 6} more milestones — tap to open interactive timeline",
                color = theme.cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.cardAlt.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }

    if (expanded) {
        FullScreenViewportFrame(
            badge = "GANTT",
            title = spec.title,
            subtitle = subtitle,
            accentColor = theme.cyan,
            onDismiss = { expanded = false }
        ) {
            GanttTimelineBody(
                spec = spec,
                maxVisibleTasks = Int.MAX_VALUE,
                trackWidth = 420.dp,
                interactiveSelection = true
            )
        }
    }
}

@Composable
private fun GanttTimelineBody(
    spec: GanttChartSpec,
    maxVisibleTasks: Int,
    trackWidth: Dp,
    interactiveSelection: Boolean
) {
    val theme = LocalAeginColors.current
    var selectedTask by remember(spec) { mutableStateOf<GanttTaskItem?>(null) }
    val visibleTasks = remember(spec, maxVisibleTasks) { spec.tasks.take(maxVisibleTasks) }

    fun barColorForStatus(status: String): Color = when (status.lowercase()) {
        "done" -> theme.success
        "active" -> theme.cyan
        "crit" -> theme.danger
        "milestone" -> theme.warning
        else -> theme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (interactiveSelection) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Legend + Timeline Range
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                "Done" to theme.success,
                "Active" to theme.cyan,
                "Critical" to theme.danger,
                "Milestone" to theme.warning,
                "Planned" to theme.primary
            ).forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
                    Text(label, color = theme.subText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Selected task detail strip in expanded interactive mode
        val activeSelection = selectedTask
        if (interactiveSelection && activeSelection != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.card)
                    .border(1.dp, barColorForStatus(activeSelection.status).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "${activeSelection.section} • ${activeSelection.label}",
                    color = theme.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                val meta = listOfNotNull(
                    "Status: ${activeSelection.status.uppercase()}",
                    activeSelection.start.takeIf { it.isNotBlank() }?.let { "Start: $it" },
                    activeSelection.endOrDuration.takeIf { it.isNotBlank() }?.let { "End/Dur: $it" }
                ).joinToString("  •  ")
                Text(text = meta, color = theme.subText, fontSize = 11.sp)
            }
        }

        // Horizontally scrollable Gantt chart grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            var lastSection = ""
            visibleTasks.forEach { task ->
                if (task.section != lastSection) {
                    lastSection = task.section
                    Text(
                        text = task.section.uppercase(),
                        color = theme.cyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                val barColor = barColorForStatus(task.status)
                val isSelected = selectedTask == task
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) barColor.copy(alpha = 0.12f) else Color.Transparent)
                        .then(
                            if (interactiveSelection) Modifier.clickable { selectedTask = task }
                            else Modifier
                        )
                        .padding(vertical = 3.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = task.label,
                        color = theme.text,
                        fontSize = if (interactiveSelection) 12.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(if (interactiveSelection) 165.dp else 120.dp)
                    )

                    BoxWithConstraints(
                        modifier = Modifier
                            .width(trackWidth)
                            .height(if (interactiveSelection) 22.dp else 18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.cardAlt)
                    ) {
                        val offsetX = maxWidth * task.startFraction
                        val barW = (maxWidth * task.widthFraction).coerceAtLeast(16.dp)
                        Box(
                            modifier = Modifier
                                .offset(x = offsetX)
                                .width(barW)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(barColor.copy(alpha = 0.88f))
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val inlineLabel = listOf(task.start, task.endOrDuration)
                                .filter { it.isNotBlank() }
                                .joinToString(" → ")
                            if (inlineLabel.isNotBlank() && barW >= 54.dp) {
                                Text(
                                    text = inlineLabel,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. MERMAID DIAGRAM MINI-CARD + FULL-SCREEN INTERACTIVE WEBVIEW VIEWER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MermaidDiagramMiniCard(
    diagramType: String,
    source: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalAeginColors.current
    var expanded by remember { mutableStateOf(false) }
    var showRawSource by remember { mutableStateOf(false) }
    val title = "Mermaid ${diagramType.replaceFirstChar { it.uppercase() }}"
    val htmlPayload = remember(source, title) {
        ResponsePresentationService.buildMermaidViewerHtml(
            mermaidSource = source,
            title = title,
            isDark = true
        )
    }
    val webState = remember(source, htmlPayload) {
        BrowserLiveState(
            isVisible = true,
            url = "https://cdn.jsdelivr.net/mermaid-viewer",
            title = title,
            statusCode = 200,
            contentPreview = source,
            rawHtml = htmlPayload
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.bg.copy(alpha = 0.72f))
            .border(1.dp, theme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable { expanded = true }
    ) {
        MiniCardHeader(
            badge = "MERMAID",
            title = title,
            subtitle = "Interactive diagram • tap to zoom & pan",
            accentColor = theme.primary,
            onExpand = { expanded = true }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            PlatformLiveWebView(
                state = webState,
                isInteractive = false,
                isMiniPreview = true,
                captureTrigger = 0,
                onCapturedForReturn = { _, _, _ -> },
                onNavigateUrl = {},
                onClickElement = {},
                onTypeElement = { _, _ -> },
                onSubmitForm = {},
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { expanded = true }
            )
        }
    }

    if (expanded) {
        FullScreenViewportFrame(
            badge = "MERMAID",
            title = title,
            subtitle = "Pinch to zoom or toggle Mermaid source",
            accentColor = theme.primary,
            onDismiss = { expanded = false }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (!showRawSource) theme.cyan.copy(alpha = 0.2f) else theme.card)
                            .border(1.dp, if (!showRawSource) theme.cyan else theme.glassBorder, CircleShape)
                            .clickable { showRawSource = false }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("Visual Diagram", color = if (!showRawSource) theme.cyan else theme.subText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (showRawSource) theme.cyan.copy(alpha = 0.2f) else theme.card)
                            .border(1.dp, if (showRawSource) theme.cyan else theme.glassBorder, CircleShape)
                            .clickable { showRawSource = true }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("Mermaid Source", color = if (showRawSource) theme.cyan else theme.subText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (showRawSource) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.card)
                            .border(1.dp, theme.glassBorder, RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = source,
                            color = theme.cyan,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            softWrap = false
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, theme.glassBorder, RoundedCornerShape(12.dp))
                    ) {
                        PlatformLiveWebView(
                            state = webState,
                            isInteractive = true,
                            isMiniPreview = false,
                            captureTrigger = 0,
                            onCapturedForReturn = { _, _, _ -> },
                            onNavigateUrl = {},
                            onClickElement = {},
                            onTypeElement = { _, _ -> },
                            onSubmitForm = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. MARKDOWN TABLE MINI-CARD + FULL-SCREEN HORIZONTAL TABLE VIEWER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MarkdownTableMiniCard(
    spec: MarkdownTableSpec,
    modifier: Modifier = Modifier
) {
    val theme = LocalAeginColors.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.bg.copy(alpha = 0.72f))
            .border(1.dp, theme.glassBorder, RoundedCornerShape(14.dp))
    ) {
        MiniCardHeader(
            badge = "TABLE",
            title = spec.headers.firstOrNull().orEmpty().ifBlank { "Structured Table" },
            subtitle = "${spec.rows.size} rows • ${spec.headers.size} columns • swipe ↔",
            accentColor = theme.success,
            onExpand = { expanded = true }
        )

        TableGridContent(spec = spec, maxRows = 5, cellWidth = 118.dp)

        if (spec.rows.size > 5) {
            Text(
                text = "+ ${spec.rows.size - 5} more rows — tap ↗ Expand to view all",
                color = theme.cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .background(theme.cardAlt.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }

    if (expanded) {
        FullScreenViewportFrame(
            badge = "TABLE",
            title = "Structured Data Table",
            subtitle = "${spec.rows.size} rows • ${spec.headers.size} columns",
            accentColor = theme.success,
            onDismiss = { expanded = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                TableGridContent(spec = spec, maxRows = Int.MAX_VALUE, cellWidth = 148.dp)
            }
        }
    }
}

@Composable
private fun TableGridContent(
    spec: MarkdownTableSpec,
    maxRows: Int,
    cellWidth: Dp
) {
    val theme = LocalAeginColors.current
    val visibleRows = remember(spec, maxRows) { spec.rows.take(maxRows) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(theme.cardAlt)
                .padding(vertical = 7.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            spec.headers.forEach { h ->
                Text(
                    text = h,
                    color = theme.cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(cellWidth)
                )
            }
        }
        visibleRows.forEachIndexed { idx, row ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (idx % 2 == 0) Color.Transparent else theme.card.copy(alpha = 0.55f))
                    .padding(vertical = 6.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { cell ->
                    Text(
                        text = cell,
                        color = theme.text,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.width(cellWidth)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. NON-WRAPPING EXPANDABLE CODE / ASCII DIAGRAM MINI-CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExpandableCodeMiniCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalAeginColors.current
    var expanded by remember { mutableStateOf(false) }
    val lineCount = remember(code) { code.lines().size }
    val badge = language.uppercase().ifBlank { "CODE" }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.bg.copy(alpha = 0.78f))
            .border(1.dp, theme.glassBorder, RoundedCornerShape(12.dp))
    ) {
        MiniCardHeader(
            badge = badge,
            title = if (language.isBlank()) "Formatted Block" else "${language.uppercase()} Block",
            subtitle = "$lineCount lines • swipe ↔ to pan without wrapping",
            accentColor = theme.cyan,
            onExpand = { expanded = true }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            Text(
                text = code.lines().take(12).joinToString("\n"),
                color = theme.cyan,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
                softWrap = false
            )
        }
        if (lineCount > 12) {
            Text(
                text = "+ ${lineCount - 12} more lines — tap ↗ Expand",
                color = theme.subText,
                fontSize = 10.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .background(theme.cardAlt.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }

    if (expanded) {
        FullScreenViewportFrame(
            badge = badge,
            title = "Full Viewport ($lineCount lines)",
            subtitle = "Scroll horizontally & vertically without wrapping",
            accentColor = theme.cyan,
            onDismiss = { expanded = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.card)
                    .border(1.dp, theme.glassBorder, RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    color = theme.cyan,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false
                )
            }
        }
    }
}
