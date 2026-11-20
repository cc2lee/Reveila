package com.reveila.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ResponsePresentationServiceTest {

    @Test
    fun testParseMermaidGanttAndNormalizeTimeline() {
        val gantt = """
            gantt
                title Operations Major Milestones
                dateFormat YYYY-MM-DD
                section Q1 Planning
                Vendor Audit :done, audit, 2026-01-05, 14d
                Budget Signoff :milestone, signoff, after audit, 0d
                section Q2 Rollout
                Warehouse Migration :active, mig, 2026-02-01, 2026-03-15
                Disaster Recovery Drill :crit, dr, after mig, 10d
        """.trimIndent()

        val spec = ResponsePresentationService.parseMermaidGantt(gantt)
        assertNotNull(spec)
        assertEquals("Operations Major Milestones", spec.title)
        assertEquals(4, spec.tasks.size)
        assertEquals("Q1 Planning", spec.tasks[0].section)
        assertEquals("done", spec.tasks[0].status)
        assertEquals("milestone", spec.tasks[1].status)
        assertEquals("Q2 Rollout", spec.tasks[2].section)
        assertEquals("active", spec.tasks[2].status)
        assertEquals("crit", spec.tasks[3].status)
        assertTrue(spec.tasks[0].startFraction < spec.tasks[2].startFraction)
        assertTrue(spec.tasks[2].startFraction < spec.tasks[3].startFraction)
    }

    @Test
    fun testParseMarkdownTableAndMermaidViewerHtml() {
        val table = ResponsePresentationService.parseMarkdownTable(
            listOf(
                "| Category | Vendor | Amount |",
                "| :--- | :--- | ---: |",
                "| Cloud | AWS | $640.50 |",
                "| Travel | Delta | $910.00 |"
            )
        )
        assertNotNull(table)
        assertEquals(listOf("Category", "Vendor", "Amount"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals("AWS", table.rows[0][1])

        val html = ResponsePresentationService.buildMermaidViewerHtml("flowchart TD\nA-->B", "Flow")
        assertTrue(html.contains(ResponsePresentationService.MERMAID_HTML_MARKER))
        assertTrue(html.contains("mermaid"))

        val mobileGuidance = ResponsePresentationService.formatPromptGuidance(DeviceFormFactor.COMPACT_MOBILE)
        assertTrue(mobileGuidance.contains("mermaid", ignoreCase = true))
        assertTrue(mobileGuidance.contains("ASCII", ignoreCase = true))
    }
}
