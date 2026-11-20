package com.reveila.ai

import com.reveila.system.SystemComponent

/**
 * Target device form factor used to adapt AI response structure and visual presentation.
 */
enum class DeviceFormFactor {
    COMPACT_MOBILE,
    TABLET,
    DESKTOP,
    TERMINAL
}

data class GanttTaskItem(
    val section: String,
    val label: String,
    val id: String = "",
    val status: String = "normal", // "done" | "active" | "crit" | "milestone" | "normal"
    val start: String = "",
    val endOrDuration: String = "",
    val startFraction: Float = 0f,
    val widthFraction: Float = 0.25f
)

data class GanttChartSpec(
    val title: String,
    val dateFormat: String = "YYYY-MM-DD",
    val tasks: List<GanttTaskItem>,
    val timelineStartLabel: String = "",
    val timelineEndLabel: String = ""
)

data class MarkdownTableSpec(
    val headers: List<String>,
    val rows: List<List<String>>
)

/**
 * Sovereign Reveila presentation service that adapts AI output guidance for the target device
 * and parses structured visual blocks (Mermaid Gantt charts, general Mermaid diagrams,
 * Markdown tables) into device-friendly interactive mini-card specs and HTML/SVG viewers.
 */
open class ResponsePresentationService : SystemComponent() {

    var defaultFormFactor: DeviceFormFactor = DeviceFormFactor.COMPACT_MOBILE

    override fun onStart() {
        logger.info("[ResponsePresentationService] Started (defaultFormFactor=$defaultFormFactor).")
    }

    override fun onStop() {
        logger.info("[ResponsePresentationService] Stopped.")
    }

    companion object {
        const val MERMAID_HTML_MARKER: String = "<!-- reveila-mermaid-html -->"

        /**
         * Returns concise formatting guidance injected into LLM system prompts so responses
         * render cleanly on the target device instead of wrapping unreadable ASCII art.
         */
        fun formatPromptGuidance(formFactor: DeviceFormFactor = DeviceFormFactor.COMPACT_MOBILE): String {
            return when (formFactor) {
                DeviceFormFactor.COMPACT_MOBILE -> """
                    Display formatting rules (small mobile screen):
                    - Lead with a concise 2-4 bullet executive summary.
                    - NEVER draw wide ASCII art Gantt charts, timelines, or box diagrams (they wrap unreadably on phones).
                    - For Gantt charts, schedules, timelines, flowcharts, or sequence diagrams, ALWAYS emit a fenced ```mermaid code block (e.g. ```mermaid with `gantt`, `flowchart TD`, `sequenceDiagram`, or `pie`).
                    - For structured comparisons, rankings, or expense breakdowns, use a clean Markdown table (`| Col | Col |`).
                """.trimIndent()

                DeviceFormFactor.TABLET, DeviceFormFactor.DESKTOP -> """
                    Display formatting rules:
                    - Prefer fenced ```mermaid blocks for Gantt charts, timelines, and diagrams instead of ASCII art.
                    - Use clean Markdown tables for structured comparisons and ranked breakdowns.
                """.trimIndent()

                DeviceFormFactor.TERMINAL -> """
                    Display formatting rules (terminal):
                    - Keep lines under 80 characters and use concise bullet points.
                """.trimIndent()
            }
        }

        /**
         * Detects the Mermaid diagram type (e.g. "gantt", "flowchart", "sequenceDiagram", "pie").
         */
        fun detectMermaidType(source: String): String {
            val firstContentLine = source.lines()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("%%") }
                .orEmpty()
            val token = firstContentLine.split(Regex("\\s+")).firstOrNull().orEmpty()
            return when {
                token.equals("gantt", ignoreCase = true) -> "gantt"
                token.equals("flowchart", ignoreCase = true) || token.equals("graph", ignoreCase = true) -> "flowchart"
                token.equals("sequenceDiagram", ignoreCase = true) -> "sequenceDiagram"
                token.equals("stateDiagram", ignoreCase = true) || token.equals("stateDiagram-v2", ignoreCase = true) -> "stateDiagram"
                token.equals("erDiagram", ignoreCase = true) -> "erDiagram"
                token.equals("classDiagram", ignoreCase = true) -> "classDiagram"
                token.equals("pie", ignoreCase = true) -> "pie"
                else -> token.ifBlank { "diagram" }
            }
        }

        /**
         * Parses a Mermaid `gantt` block into a structured [GanttChartSpec] with normalized
         * horizontal bar positions (`startFraction`, `widthFraction`) for native Compose rendering.
         */
        fun parseMermaidGantt(source: String): GanttChartSpec? {
            val lines = source.replace("\r\n", "\n").lines()
            val firstNonComment = lines.map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("%%") }
                ?: return null
            if (!firstNonComment.startsWith("gantt", ignoreCase = true)) return null

            var title = "Milestone Timeline"
            var dateFormat = "YYYY-MM-DD"
            var currentSection = "Milestones"

            data class RawTask(
                val section: String,
                val label: String,
                val id: String,
                val status: String,
                val startRaw: String,
                val endRaw: String,
                val startEpochDay: Long?,
                val endEpochDay: Long?
            )

            val rawTasks = mutableListOf<RawTask>()
            val taskEndById = mutableMapOf<String, Long>()
            var lastEndDay: Long? = null

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("%%") || line.equals("gantt", ignoreCase = true)) continue
                when {
                    line.startsWith("title ", ignoreCase = true) -> {
                        title = line.substringAfter("title ").trim().ifBlank { title }
                    }
                    line.startsWith("dateFormat ", ignoreCase = true) -> {
                        dateFormat = line.substringAfter("dateFormat ").trim()
                    }
                    line.startsWith("axisFormat ", ignoreCase = true) ||
                        line.startsWith("excludes ", ignoreCase = true) ||
                        line.startsWith("todayMarker", ignoreCase = true) ||
                        line.startsWith("tickInterval", ignoreCase = true) -> continue
                    line.startsWith("section ", ignoreCase = true) -> {
                        currentSection = line.substringAfter("section ").trim().ifBlank { "General" }
                    }
                    line.contains(":") -> {
                        val label = line.substringBefore(":").trim()
                        val metaRaw = line.substringAfter(":").trim()
                        if (label.isBlank() || metaRaw.isBlank()) continue
                        val parts = metaRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                        var status = "normal"
                        var id = ""
                        val remaining = mutableListOf<String>()
                        for (p in parts) {
                            when (p.lowercase()) {
                                "done", "active", "crit", "milestone" -> {
                                    if (status == "normal" || p.equals("milestone", ignoreCase = true)) {
                                        status = p.lowercase()
                                    }
                                }
                                else -> remaining.add(p)
                            }
                        }

                        var startRaw = ""
                        var endRaw = ""
                        when (remaining.size) {
                            0 -> Unit
                            1 -> {
                                if (isDateOrAfterToken(remaining[0])) startRaw = remaining[0]
                                else endRaw = remaining[0]
                            }
                            2 -> {
                                if (isDateOrAfterToken(remaining[0])) {
                                    startRaw = remaining[0]
                                    endRaw = remaining[1]
                                } else {
                                    id = remaining[0]
                                    startRaw = remaining[1]
                                }
                            }
                            else -> {
                                id = remaining[0]
                                startRaw = remaining[1]
                                endRaw = remaining[2]
                            }
                        }

                        val startDay = resolveStartDay(startRaw, taskEndById, lastEndDay)
                        val endDay = resolveEndDay(startDay, endRaw, status == "milestone")
                        if (id.isNotBlank() && endDay != null) {
                            taskEndById[id] = endDay
                        }
                        if (endDay != null) {
                            lastEndDay = endDay
                        } else if (startDay != null) {
                            lastEndDay = startDay + 7L
                        }

                        rawTasks.add(
                            RawTask(
                                section = currentSection,
                                label = label,
                                id = id,
                                status = status,
                                startRaw = startRaw,
                                endRaw = endRaw,
                                startEpochDay = startDay,
                                endEpochDay = endDay
                            )
                        )
                    }
                }
            }

            if (rawTasks.isEmpty()) return null

            val datedStarts = rawTasks.mapNotNull { it.startEpochDay }
            val datedEnds = rawTasks.mapNotNull { it.endEpochDay }
            val minDay = datedStarts.minOrNull()
            val maxDay = datedEnds.maxOrNull()?.let { max ->
                if (minDay != null && max <= minDay) minDay + 14L else max
            }
            val totalSpan = if (minDay != null && maxDay != null) (maxDay - minDay).coerceAtLeast(1L) else null

            val items = rawTasks.mapIndexed { idx, rt ->
                val (sf, wf) = if (totalSpan != null && minDay != null && rt.startEpochDay != null) {
                    val s = ((rt.startEpochDay - minDay).toFloat() / totalSpan.toFloat()).coerceIn(0f, 0.94f)
                    val endD = rt.endEpochDay ?: (rt.startEpochDay + 3L)
                    val rawW = ((endD - rt.startEpochDay).coerceAtLeast(1L).toFloat() / totalSpan.toFloat())
                    val minW = if (rt.status == "milestone") 0.04f else 0.08f
                    s to rawW.coerceIn(minW, (1f - s).coerceAtLeast(minW))
                } else {
                    val step = 1f / rawTasks.size.coerceAtLeast(1)
                    val s = (idx * step * 0.72f).coerceIn(0f, 0.80f)
                    s to (step * 1.15f).coerceIn(0.14f, (1f - s).coerceAtLeast(0.14f))
                }
                GanttTaskItem(
                    section = rt.section,
                    label = rt.label,
                    id = rt.id,
                    status = rt.status,
                    start = rt.startRaw,
                    endOrDuration = rt.endRaw,
                    startFraction = sf,
                    widthFraction = wf
                )
            }

            val startLabel = rawTasks.firstOrNull { it.startRaw.isNotBlank() && !it.startRaw.startsWith("after ", ignoreCase = true) }?.startRaw.orEmpty()
            val endLabel = rawTasks.lastOrNull { it.endRaw.isNotBlank() }?.endRaw
                ?: rawTasks.lastOrNull { it.startRaw.isNotBlank() }?.startRaw.orEmpty()

            return GanttChartSpec(
                title = title,
                dateFormat = dateFormat,
                tasks = items,
                timelineStartLabel = startLabel,
                timelineEndLabel = endLabel
            )
        }

        private fun isDateOrAfterToken(token: String): Boolean {
            val t = token.trim()
            return t.startsWith("after ", ignoreCase = true) ||
                Regex("^\\d{4}[-/]\\d{1,2}([-/]\\d{1,2})?$").matches(t) ||
                Regex("^Q[1-4]\\s*\\d{4}$", RegexOption.IGNORE_CASE).matches(t)
        }

        private fun resolveStartDay(raw: String, taskEndById: Map<String, Long>, fallback: Long?): Long? {
            val t = raw.trim()
            if (t.isBlank()) return fallback
            if (t.startsWith("after ", ignoreCase = true)) {
                val refId = t.substringAfter("after ").trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                return taskEndById[refId] ?: fallback
            }
            return parseDateToApproxDay(t) ?: fallback
        }

        private fun resolveEndDay(startDay: Long?, endRaw: String, isMilestone: Boolean): Long? {
            if (startDay == null) return null
            if (isMilestone) return startDay + 1L
            val t = endRaw.trim()
            if (t.isBlank()) return startDay + 7L
            parseDateToApproxDay(t)?.let { return it.coerceAtLeast(startDay + 1L) }
            val durMatch = Regex("^(\\d+)\\s*([dwmyh])$", RegexOption.IGNORE_CASE).matchEntire(t)
            if (durMatch != null) {
                val qty = durMatch.groupValues[1].toLongOrNull() ?: 1L
                val days = when (durMatch.groupValues[2].lowercase()) {
                    "h" -> 1L
                    "d" -> qty
                    "w" -> qty * 7L
                    "m" -> qty * 30L
                    "y" -> qty * 365L
                    else -> qty
                }
                return startDay + days.coerceAtLeast(1L)
            }
            return startDay + 7L
        }

        private fun parseDateToApproxDay(dateStr: String): Long? {
            val ymd = Regex("^(\\d{4})[-/](\\d{1,2})(?:[-/](\\d{1,2}))?$").matchEntire(dateStr.trim())
            if (ymd != null) {
                val y = ymd.groupValues[1].toLongOrNull() ?: return null
                val m = ymd.groupValues[2].toLongOrNull()?.coerceIn(1L, 12L) ?: 1L
                val d = ymd.groupValues[3].toLongOrNull()?.coerceIn(1L, 31L) ?: 1L
                return (y * 365L) + ((m - 1L) * 30L) + d
            }
            val qMatch = Regex("^Q([1-4])\\s*(\\d{4})$", RegexOption.IGNORE_CASE).matchEntire(dateStr.trim())
            if (qMatch != null) {
                val q = qMatch.groupValues[1].toLongOrNull() ?: 1L
                val y = qMatch.groupValues[2].toLongOrNull() ?: return null
                return (y * 365L) + ((q - 1L) * 90L) + 1L
            }
            return null
        }

        /**
         * Parses a standard Markdown pipe table into a [MarkdownTableSpec].
         */
        fun parseMarkdownTable(lines: List<String>): MarkdownTableSpec? {
            if (lines.size < 2) return null
            fun splitRow(line: String): List<String> = line.trim()
                .removePrefix("|")
                .removeSuffix("|")
                .split("|")
                .map { it.trim() }

            val headers = splitRow(lines[0])
            if (headers.size < 2) return null
            val sepCells = splitRow(lines[1])
            val isSeparator = sepCells.isNotEmpty() && sepCells.all { cell ->
                cell.replace(":", "").replace("-", "").isBlank() && cell.contains("-")
            }
            if (!isSeparator) return null

            val rows = lines.drop(2)
                .map { splitRow(it) }
                .filter { row -> row.any { it.isNotBlank() } }
                .map { row ->
                    if (row.size < headers.size) row + List(headers.size - row.size) { "" }
                    else row.take(headers.size)
                }
            return MarkdownTableSpec(headers = headers, rows = rows)
        }

        /**
         * Generates a self-contained interactive HTML page that renders a Mermaid diagram
         * using Mermaid.js (with graceful readable fallback when offline).
         */
        fun buildMermaidViewerHtml(
            mermaidSource: String,
            title: String = "Interactive Diagram",
            isDark: Boolean = true
        ): String {
            fun esc(s: String) = s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")

            val bg = if (isDark) "#0d1117" else "#f8fafc"
            val cardBg = if (isDark) "#161b26" else "#ffffff"
            val text = if (isDark) "#e2e8f0" else "#0f172a"
            val border = if (isDark) "#2a3347" else "#cbd5e1"
            val mermaidTheme = if (isDark) "dark" else "default"

            return """
                <!DOCTYPE html>
                $MERMAID_HTML_MARKER
                <html>
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=4.0, user-scalable=yes" />
                  <title>${esc(title)}</title>
                  <style>
                    body { margin: 0; padding: 12px; background: $bg; color: $text; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                    .wrap { background: $cardBg; border: 1px solid $border; border-radius: 12px; padding: 12px; overflow-x: auto; }
                    .mermaid svg { max-width: none !important; height: auto; }
                    pre.fallback { margin: 0; white-space: pre; overflow-x: auto; font-size: 12px; line-height: 1.45; color: $text; }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <div class="mermaid" id="mmd-root">${esc(mermaidSource)}</div>
                  </div>
                  <script type="module">
                    try {
                      const { default: mermaid } = await import('https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs');
                      mermaid.initialize({ startOnLoad: false, theme: '$mermaidTheme', securityLevel: 'loose' });
                      await mermaid.run({ nodes: [document.getElementById('mmd-root')] });
                    } catch (e) {
                      const el = document.getElementById('mmd-root');
                      if (el) { el.className = ''; el.innerHTML = '<pre class="fallback">' + el.innerHTML + '</pre>'; }
                    }
                  </script>
                </body>
                </html>
            """.trimIndent()
        }
    }
}
