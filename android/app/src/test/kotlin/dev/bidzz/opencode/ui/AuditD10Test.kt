package dev.bidzz.opencode.ui

import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineTransition
import dev.bidzz.opencode.ui.chat.prependedHeadCount
import dev.bidzz.opencode.ui.diagnostics.capDiagnosticsText
import dev.bidzz.opencode.ui.diagnostics.formatEngineTransition
import dev.bidzz.opencode.ui.diagnostics.shortenDiagnosticPath
import dev.bidzz.opencode.ui.sessions.SessionDetail
import dev.bidzz.opencode.ui.sessions.SessionListUiState
import dev.bidzz.opencode.ui.sessions.SessionPreviewNote
import dev.bidzz.opencode.ui.sessions.SessionRow
import dev.bidzz.opencode.ui.sessions.resolveSessionPreviewNote
import dev.bidzz.opencode.ui.websearch.MAX_RECENT_QUERIES
import dev.bidzz.opencode.ui.websearch.WebSearchUiState
import dev.bidzz.opencode.ui.websearch.withRecentQuery
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D10 (empty / loading / error states): behaviour tests for
 * the fixes whose logic is pure, plus scanner guards for the fixes
 * whose behaviour lives in a composable or a view model that a JVM
 * test cannot drive — the source must keep the shape the fix gave it.
 */
class AuditD10Test {

    private fun read(path: String): String = File(path).readText()

    private fun count(src: String, needle: String): Int = src.split(needle).size - 1

    private fun row(id: String) = SessionRow(
        id = id,
        title = "t",
        directory = null,
        age = null,
        cost = null,
    )

    // ---- m-173/m-1115: the preview note ---------------------------------

    @Test
    fun `m-173 no note when every listed row is known`() {
        val state = SessionListUiState(
            rows = listOf(row("a"), row("b")),
            details = mapOf(
                "a" to SessionDetail(previewKnown = true),
                "b" to SessionDetail(previewKnown = true),
            ),
        )
        assertEquals(SessionPreviewNote.NONE, resolveSessionPreviewNote(state))
    }

    @Test
    fun `m-173 no note when no rows are listed`() {
        val state = SessionListUiState(
            rows = emptyList(),
            details = mapOf("a" to SessionDetail(previewKnown = false)),
        )
        assertEquals(SessionPreviewNote.NONE, resolveSessionPreviewNote(state))
    }

    @Test
    fun `m-173 loading while the walk is still running`() {
        val state = SessionListUiState(
            rows = listOf(row("a")),
            details = mapOf("a" to SessionDetail(previewKnown = false)),
            detailsLoading = true,
        )
        assertEquals(SessionPreviewNote.LOADING, resolveSessionPreviewNote(state))
    }

    @Test
    fun `m-173 incomplete when the walk finished with a failed lookup`() {
        val state = SessionListUiState(
            rows = listOf(row("a")),
            details = mapOf("a" to SessionDetail(previewKnown = false)),
            previewsIncomplete = true,
        )
        assertEquals(SessionPreviewNote.INCOMPLETE, resolveSessionPreviewNote(state))
    }

    @Test
    fun `m-173 no note when the walk has not run yet`() {
        val state = SessionListUiState(
            rows = listOf(row("a")),
            details = mapOf("a" to SessionDetail(previewKnown = false)),
        )
        assertEquals(SessionPreviewNote.NONE, resolveSessionPreviewNote(state))
    }

    @Test
    fun `m-173 loading wins over incomplete`() {
        val state = SessionListUiState(
            rows = listOf(row("a")),
            details = mapOf("a" to SessionDetail(previewKnown = false)),
            detailsLoading = true,
            previewsIncomplete = true,
        )
        assertEquals(SessionPreviewNote.LOADING, resolveSessionPreviewNote(state))
    }

    @Test
    fun `m-173 both history branches and the drawer use the note`() {
        val list = read("src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionListScreen.kt")
        assertEquals(2, count(list, "resolveSessionPreviewNote(state)"))
        assertTrue(list.contains("history_previews_incomplete"))
        val chat = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        assertTrue(chat.contains("sessionsPreviewNote"))
        assertTrue(chat.contains("history_previews_incomplete"))
    }

    // ---- m-196: the drawer states its loading and failure ---------------

    @Test
    fun `m-196 drawer loading and failure offer a retry`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        assertTrue(src.contains("sessionsLoading && sessions.isEmpty()"))
        // The loading branch and the failure branch both wire the retry.
        assertTrue(count(src, "onRetry = onRetrySessions") >= 2)
    }

    // ---- m-458: the prepend fast path ------------------------------------

    @Test
    fun `m-458 a clean prepend counts its head`() {
        val a = SessionMessage(id = "a", type = "user")
        val b = SessionMessage(id = "b", type = "assistant")
        val x = SessionMessage(id = "x", type = "user")
        assertEquals(1, prependedHeadCount(listOf(x, a, b), listOf(a, b)))
        assertEquals(2, prependedHeadCount(listOf(x, x, a, b), listOf(a, b)))
    }

    @Test
    fun `m-458 a boundary merge still counts its head`() {
        val a = SessionMessage(id = "a", type = "assistant")
        val b = SessionMessage(id = "b", type = "assistant")
        val merged = SessionMessage(id = "a", type = "assistant")
        val x = SessionMessage(id = "x", type = "user")
        // The merged first tail element is a new object, but the rest
        // of the tail is verbatim.
        assertEquals(1, prependedHeadCount(listOf(x, merged, b), listOf(a, b)))
    }

    @Test
    fun `m-458 anything else is not a prepend`() {
        val a = SessionMessage(id = "a", type = "user")
        val b = SessionMessage(id = "b", type = "assistant")
        val c = SessionMessage(id = "c", type = "assistant")
        val x = SessionMessage(id = "x", type = "user")
        assertEquals(-1, prependedHeadCount(listOf(x, a, c), listOf(a, b)))
        assertEquals(-1, prependedHeadCount(listOf(a, b), listOf(a, b)))
        assertEquals(-1, prependedHeadCount(listOf(a), listOf(a, b)))
        assertEquals(-1, prependedHeadCount(listOf(x, a, b), emptyList()))
    }

    @Test
    fun `m-458 a single old message prepends by its head alone`() {
        val a = SessionMessage(id = "a", type = "user")
        val merged = SessionMessage(id = "a", type = "assistant")
        val x = SessionMessage(id = "x", type = "user")
        assertEquals(1, prependedHeadCount(listOf(x, a), listOf(a)))
        assertEquals(1, prependedHeadCount(listOf(x, merged), listOf(a)))
    }

    @Test
    fun `m-458 buildUiMessages pins the prepend inputs`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatViewModel.kt")
        assertTrue(src.contains("prependedHeadCount(chat.messages, oldMessages)"))
        assertTrue(src.contains("chat.busy == lastBuildBusy"))
        assertTrue(src.contains("confirmedQuestionAnswers == lastAnswered"))
        assertTrue(src.contains("lastBuildBusy = chat.busy"))
        assertTrue(src.contains("lastAnswered = confirmedQuestionAnswers.toMap()"))
        // The old first element is dropped by id, not by position: an
        // idle marker produces no entry, so position 0 is not always
        // its built form.
        assertTrue(src.contains("lastUnmergedMessages.filter { it.id != oldMessages.first().id }"))
    }

    // ---- m-509/m-510: version and transitions in Diagnostics ------------

    @Test
    fun `m-510 a transition formats as time, change, reason`() {
        val plain = formatEngineTransition(
            EngineTransition(EngineState.STOPPED, EngineState.RUNNING, 0L),
        )
        assertTrue(plain.first().isDigit())
        assertTrue(plain.contains("STOPPED -> RUNNING"))
        assertFalse(plain.contains(" — "))
        val withError = formatEngineTransition(
            EngineTransition(EngineState.RUNNING, EngineState.FAILED, 0L, error = "port busy"),
        )
        assertTrue(withError.contains("RUNNING -> FAILED"))
        assertTrue(withError.endsWith(" — port busy"))
    }

    @Test
    fun `m-509 the report and the screen carry version and transitions`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/diagnostics/DiagnosticsScreen.kt")
        assertTrue(src.contains("- App Version: \$appVersion"))
        assertTrue(src.contains("- Recent Transitions:"))
        assertTrue(src.contains("formatEngineTransition"))
        assertTrue(src.contains("diag_app_version"))
        assertTrue(src.contains("diag_android_version"))
        assertTrue(src.contains("diag_transitions"))
        val engine = read("../core/engine/src/main/kotlin/dev/bidzz/opencode/core/engine/ProcessManager.kt")
        assertTrue(engine.contains("recentTransitions = appendEngineTransition"))
    }

    // ---- m-611: the artifact list survives a refresh --------------------

    @Test
    fun `m-611 the cached list branch comes before the loading branch`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/artifacts/ArtifactsScreen.kt")
        val cached = src.indexOf("state.artifacts.isNotEmpty() && state.failure == null")
        val loading = src.indexOf("state.loading ->")
        assertTrue(cached >= 0)
        assertTrue(loading >= 0)
        assertTrue(cached < loading)
    }

    // ---- m-658: web search history ---------------------------------------

    @Test
    fun `m-658 a finished query becomes the most recent one`() {
        val state = WebSearchUiState(recentQueries = listOf("old", "older"))
        val updated = state.withRecentQuery("new")
        assertEquals(listOf("new", "old", "older"), updated.recentQueries)
    }

    @Test
    fun `m-658 repeating a query moves it to the front without duplicating`() {
        val state = WebSearchUiState(recentQueries = listOf("a", "b", "c"))
        assertEquals(listOf("b", "a", "c"), state.withRecentQuery("b").recentQueries)
        assertEquals(listOf("b", "a", "c"), state.withRecentQuery("  b  ").recentQueries)
    }

    @Test
    fun `m-658 the history is capped and blank queries are ignored`() {
        val many = (1..MAX_RECENT_QUERIES).map { "q$it" }
        val state = WebSearchUiState(recentQueries = many)
        val capped = state.withRecentQuery("fresh")
        assertEquals(MAX_RECENT_QUERIES, capped.recentQueries.size)
        assertEquals("fresh", capped.recentQueries.first())
        assertFalse(capped.recentQueries.contains("q$MAX_RECENT_QUERIES"))
        assertEquals(state, state.withRecentQuery("   "))
    }

    @Test
    fun `m-658 the view model wires the saved state and the screen shows it`() {
        val vm = read("src/main/kotlin/dev/bidzz/opencode/ui/websearch/WebSearchViewModel.kt")
        assertTrue(vm.contains("withRecentQuery(state.query)"))
        assertTrue(vm.contains("savedStateHandle"))
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/websearch/WebSearchScreen.kt")
        assertTrue(screen.contains("websearch_recent"))
        assertTrue(screen.contains("state.recentQueries"))
        val app = read("src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt")
        assertTrue(app.contains("savedStateHandle = extras.createSavedStateHandle()"))
        assertTrue(count(app, "createSavedStateHandle()") >= 3)
    }

    // ---- m-667: old web results stay while the next search runs ---------

    @Test
    fun `m-667 the results branch comes before the searching branch`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/websearch/WebSearchScreen.kt")
        val results = src.indexOf("response != null && response.results.isNotEmpty()")
        val searching = src.indexOf("state.searching -> LoadingState")
        assertTrue(results >= 0)
        assertTrue(searching >= 0)
        assertTrue(results < searching)
    }

    // ---- m-817: a dead engine is reported, not swallowed -----------------

    @Test
    fun `m-817 load more and permission revoke report a dead engine`() {
        val list = read("src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionListViewModel.kt")
        assertTrue(list.contains("failure = engineDownFailure()"))
        val settings = read("src/main/kotlin/dev/bidzz/opencode/ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("savedPermissionsFailure = engineDownFailure()"))
    }

    // ---- m-902: capped copies and shortened paths ------------------------

    @Test
    fun `m-902 a short report copies unchanged`() {
        assertEquals("hello", capDiagnosticsText("hello", maxChars = 100))
    }

    @Test
    fun `m-902 a long report keeps its head and says it was cut`() {
        val text = (1..50).joinToString("\n") { "line $it" }
        val capped = capDiagnosticsText(text, maxChars = 40)
        assertTrue(capped.startsWith("line 1\nline 2\nline 3"))
        assertTrue(capped.contains("[truncated: showing the first "))
        assertTrue(capped.endsWith(" of ${text.length} characters]"))
        assertTrue(capped.length < text.length)
    }

    @Test
    fun `m-902 a long report without newlines is cut at the limit`() {
        val text = "x".repeat(100)
        val capped = capDiagnosticsText(text, maxChars = 40)
        assertEquals("x".repeat(40) + "\n\n[truncated: showing the first 40 of 100 characters]", capped)
    }

    @Test
    fun `m-902 paths under the app root become app-relative`() {
        val root = "/data/data/dev.bidzz.opencode/files"
        assertEquals("app", shortenDiagnosticPath(root, root))
        assertEquals(
            "app/logs/engine.log",
            shortenDiagnosticPath("$root/logs/engine.log", root),
        )
        assertEquals("…/a/b", shortenDiagnosticPath("/some/where/a/b", root))
        assertEquals("/a/b", shortenDiagnosticPath("/a/b", root))
        assertEquals("relative/path", shortenDiagnosticPath("relative/path", root))
    }

    @Test
    fun `m-902 both copy paths go through the capped copy`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/diagnostics/DiagnosticsScreen.kt")
        assertTrue(count(src, "copyDiagnosticsText(context") >= 2)
        assertTrue(src.contains("diag_copied_partial"))
    }

    // ---- m-1010: a failed resource catalog is a banner, not a vanish ----

    @Test
    fun `m-1010 the catalog failure is kept and shown with a retry`() {
        val vm = read("src/main/kotlin/dev/bidzz/opencode/ui/mcp/McpServersViewModel.kt")
        assertTrue(vm.contains("resourcesFailure = catalog"))
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/mcp/McpServersScreen.kt")
        assertTrue(screen.contains("\"resources-failure\""))
        assertTrue(screen.contains("mcp_resources_failed"))
    }

    // ---- m-1013: empty states say what to do next ------------------------

    @Test
    fun `m-1013 every empty state names its next step`() {
        val en = read("src/main/res/values/strings.xml")
        val id = read("src/main/res/values-in/strings.xml")
        val checks = listOf(
            "Add one with the Add button above." to "Tambahkan lewat tombol Tambah di atas.",
            "Check for updates above to find new ones." to "Periksa pembaruan di atas untuk mencari yang baru.",
            "Worktrees created by the engine appear here." to "Worktree yang dibuat oleh engine muncul di sini.",
            "Add a configuration file to the engine" to "Tambahkan berkas konfigurasi ke engine",
            "Send a message while the agent is busy and it will wait here." to "Kirim pesan saat agen sibuk dan pesan itu akan menunggu di sini.",
            "Start a chat to record activity here." to "Mulai obrolan untuk mencatat aktivitas di sini.",
            "Files created by the agent appear here." to "Berkas yang dibuat agen muncul di sini.",
            "Edit a file to see its changes here." to "Ubah berkas untuk melihat perubahannya di sini.",
            "Ask the agent to change a file to see the diff here." to "Minta agen mengubah berkas untuk melihat perubahannya di sini.",
        )
        checks.forEach { (enText, idText) ->
            assertTrue("EN missing: $enText", en.contains(enText))
            assertTrue("ID missing: $idText", id.contains(idText))
        }
    }
}
