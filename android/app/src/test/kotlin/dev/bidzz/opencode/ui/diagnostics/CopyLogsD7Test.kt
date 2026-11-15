package dev.bidzz.opencode.ui.diagnostics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D7 (m-1019/m-1093): "Copy Logs" keeps what the viewer knows —
 * the display-sequence number and the inferred level of each line, plus a
 * header naming the tab, the filter, and how many of the retained lines
 * were copied — and the label says how many lines the filter leaves.
 */
class CopyLogsD7Test {

    private fun read(path: String): String = File(path).readText()

    // ---- formatCopiedLogLine (m-1019) ----

    @Test
    fun `m-1019 a copied line carries its display number and inferred level`() {
        val line = IndexedLogLine(number = 1234, text = "connection refused", level = LogLevel.ERROR)
        assertEquals("1234 [ERROR] connection refused", formatCopiedLogLine(line))
    }

    @Test
    fun `m-1019 the level is the heuristic guess, never ALL`() {
        val line = IndexedLogLine(number = 1, text = "all good", level = LogLevel.INFO)
        assertEquals("1 [INFO] all good", formatCopiedLogLine(line))
    }

    // ---- describeLogFilter (m-1093) ----

    @Test
    fun `m-1093 no filter yields no description`() {
        assertNull(describeLogFilter("", LogLevel.ALL))
    }

    @Test
    fun `m-1093 a level chip alone is described`() {
        assertEquals("level=ERROR", describeLogFilter("", LogLevel.ERROR))
    }

    @Test
    fun `m-1093 a query alone is described`() {
        assertEquals("query=\"foo\"", describeLogFilter("foo", LogLevel.ALL))
    }

    @Test
    fun `m-1093 level and query combine in one description`() {
        assertEquals("level=ERROR, query=\"foo\"", describeLogFilter("foo", LogLevel.ERROR))
    }

    // ---- formatCopiedLogsHeader (m-1019/m-1093) ----

    @Test
    fun `m-1093 the header names the tab, filter and counts`() {
        assertEquals(
            "# OpenCode Logs — tab CONSOLE, filter: level=ERROR, query=\"foo\" (12 of 2000 lines)",
            formatCopiedLogsHeader("CONSOLE", "level=ERROR, query=\"foo\"", 12, 2000),
        )
    }

    @Test
    fun `m-1093 the header without a filter omits the filter clause`() {
        assertEquals(
            "# OpenCode Logs — tab ALL (2000 of 2000 lines)",
            formatCopiedLogsHeader("ALL", null, 2000, 2000),
        )
    }

    // ---- call site (m-1019/m-1093) ----

    @Test
    fun `m-1019 the copy call site passes whole lines, not bare text`() {
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/diagnostics/DiagnosticsScreen.kt")
        assertTrue(screen.contains("onClick = { copyLogs(filteredLogs) }"))
        assertTrue(screen.contains("val copyLogs = { lines: List<IndexedLogLine> ->"))
        assertTrue(screen.contains("formatCopiedLogLine(it)"))
        assertTrue(screen.contains("formatCopiedLogsHeader("))
    }

    @Test
    fun `m-1093 the label and toast name the filtered count`() {
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/diagnostics/DiagnosticsScreen.kt")
        assertTrue(screen.contains("pluralStringResource(R.plurals.diag_copy_logs_filtered, filteredLogs.size, filteredLogs.size)"))
        assertTrue(screen.contains("context.resources.getQuantityString(R.plurals.diag_copied_logs_filtered, lines.size, lines.size, indexedLogs.size)"))
    }
}
