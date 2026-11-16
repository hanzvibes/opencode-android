package dev.bidzz.opencode.ui

import dev.bidzz.opencode.ui.sessions.SessionDetail
import dev.bidzz.opencode.ui.sessions.SessionRow
import dev.bidzz.opencode.ui.sessions.idsToWalkForEmptySearch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D11 (ikon / gambar / preview / PDF): scanner guards for the
 * fixes whose behaviour lives in a view model or a composable that a JVM
 * test cannot drive — the source must keep the shape the fix gave it.
 */
class AuditD11Test {

    private fun read(path: String): String = File(path).readText()

    /** Body of the function whose signature starts at [marker], brace-matched (D3 pattern). */
    private fun funBody(src: String, marker: String): String {
        val start = src.indexOf(marker)
        require(start >= 0) { "missing $marker" }
        var i = src.indexOf('{', start)
        require(i >= 0) { "no body after $marker" }
        var depth = 0
        val bodyStart = i
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(bodyStart, i + 1)
                }
            }
            i++
        }
        error("unbalanced braces after $marker")
    }

    private val sessionsVm = "src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionListViewModel.kt"
    private val explorerVm = "src/main/kotlin/dev/bidzz/opencode/ui/files/ExplorerViewModel.kt"
    private val filePreview = "src/main/kotlin/dev/bidzz/opencode/ui/files/FilePreview.kt"
    private val repository = "../core/api/src/main/kotlin/dev/bidzz/opencode/core/api/ChatRepository.kt"
    private val toolRow = "src/main/kotlin/dev/bidzz/opencode/ui/chat/ToolRow.kt"

    // ---- m-812: loading more must not cancel the preview walk ----------

    @Test
    fun `m-812 loadMore never cancels the preview job`() {
        val src = read(sessionsVm)
        val body = funBody(src, "fun loadMore()")
        assertFalse(body.contains("previewJob?.cancel()"))
        assertFalse(body.contains("previewJob = null"))
        // The new page still joins the walk instead of restarting it.
        assertTrue(body.contains("loadDetails(page.data, generation)"))
    }

    @Test
    fun `m-812 starting a preview job while one runs is a no-op`() {
        val src = read(sessionsVm)
        val body = funBody(src, "private fun startPreviewJob()")
        assertTrue(body.contains("if (previewJob?.isActive == true) return"))
    }

    @Test
    fun `m-812 loadDetails seeds the queue and starts the job`() {
        val src = read(sessionsVm)
        val body = funBody(src, "private fun loadDetails(")
        assertFalse(body.contains("previewJob?.cancel()"))
        assertTrue(body.contains("previewWantedIds.addAll(wanted)"))
        assertTrue(body.contains("startPreviewJob()"))
    }

    // ---- m-388: previews skip the text download -------------------------

    @Test
    fun `m-388 a preview load returns before any readText`() {
        val src = read(explorerVm)
        val body = funBody(src, "private fun load(path: String, allowFull: Boolean)")
        val skip = body.indexOf("if (previewKindForPath(path) != FilePreviewKind.NONE)")
        val read = body.indexOf("FileApi(handle.client).readText(")
        assertTrue(skip >= 0)
        assertTrue(read >= 0)
        assertTrue(skip < read)
    }

    // ---- m-714: a preview is not editable -------------------------------

    @Test
    fun `m-714 startEditing refuses a preview file`() {
        val src = read(explorerVm)
        val body = funBody(src, "fun startEditing()")
        assertTrue(body.contains("if (previewKindForPath(s.path) != FilePreviewKind.NONE) return"))
    }

    @Test
    fun `m-714 save refuses a preview file even if editing slipped in`() {
        val src = read(explorerVm)
        val body = funBody(src, "fun save(overwrite: Boolean = false)")
        assertTrue(body.contains("if (previewKindForPath(s.path) != FilePreviewKind.NONE) return"))
    }

    // ---- m-715: the footer shows the original dimensions ---------------

    @Test
    fun `m-715 decodeSampledBitmap returns the bounds dimensions`() {
        val src = read(filePreview)
        assertTrue(src.contains("val width = bounds.outWidth"))
        assertTrue(src.contains("val height = bounds.outHeight"))
    }

    @Test
    fun `m-715 the animated decode captures the size before target-sizing`() {
        val src = read(filePreview)
        val capture = src.indexOf("originalWidth = size.width")
        val target = src.indexOf("decoder.setTargetSize(")
        assertTrue(capture >= 0)
        assertTrue(target >= 0)
        assertTrue(capture < target)
    }

    // ---- m-355: the preview walk is capped ------------------------------

    @Test
    fun `m-355 the walk stops incomplete at the page cap`() {
        val src = read(sessionsVm)
        val body = funBody(src, "private suspend fun fetchFirstUserPreview(")
        assertTrue(body.contains("var pagesWalked = 0"))
        assertTrue(body.contains("pagesWalked++"))
        assertTrue(body.contains("if (pagesWalked >= MAX_PREVIEW_WALK_PAGES) return PreviewLookup(candidate, searchText, complete = false)"))
    }

    private fun row(id: String) = SessionRow(
        id = id,
        title = "t",
        directory = null,
        age = null,
        cost = null,
    )

    // ---- m-485: a zero-result search walks the unwalked sessions -------

    @Test
    fun `m-485 a blank query and a search with results owe no walk`() {
        val rows = listOf(row("a"))
        val details = mapOf("a" to SessionDetail(previewKnown = false))
        assertEquals(emptyList<String>(), idsToWalkForEmptySearch("", false, rows, details))
        assertEquals(emptyList<String>(), idsToWalkForEmptySearch("fix", true, rows, details))
    }

    @Test
    fun `m-485 a zero-result search owes only the unwalked sessions`() {
        val rows = listOf(row("a"), row("b"), row("c"))
        val details = mapOf(
            "a" to SessionDetail(previewKnown = true),
            "b" to SessionDetail(previewKnown = false),
        )
        // "c" has no detail entry at all — never walked either.
        assertEquals(listOf("b", "c"), idsToWalkForEmptySearch("fix", false, rows, details))
    }

    @Test
    fun `m-485 a zero-result search with everything known owes no walk`() {
        val rows = listOf(row("a"), row("b"))
        val details = mapOf(
            "a" to SessionDetail(previewKnown = true),
            "b" to SessionDetail(previewKnown = true),
        )
        assertEquals(emptyList<String>(), idsToWalkForEmptySearch("fix", false, rows, details))
    }

    @Test
    fun `m-485 the pane fires the follow-up walk once per query`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionListScreen.kt")
        assertTrue(src.contains("idsToWalkForEmptySearch(query, filteredRows.isNotEmpty(), state.rows, details)"))
        assertTrue(src.contains("if (toWalk.isEmpty() || searchWalkQuery == query) return@LaunchedEffect"))
        assertTrue(src.contains("onVisibleSessions(toWalk)"))
    }

    // ---- m-103/m-1134: a decode failure is flagged, not swallowed ------

    @Test
    fun `m-103 a successful tool with an undecodable output is flagged`() {
        val src = read(repository)
        assertTrue(src.contains("outputDecodeFailed = parsed.decodeFailed"))
    }

    @Test
    fun `m-103 the timeline shows the failure instead of a bare Success`() {
        val src = read(toolRow)
        // The step stays expandable, the pill names the problem, and the
        // expanded body says so where the STDOUT block would be.
        assertTrue(src.contains("|| call.outputDecodeFailed"))
        assertTrue(src.contains("if (call.outputDecodeFailed)"))
        assertTrue(src.contains("R.string.tool_output_unreadable"))
    }
}
