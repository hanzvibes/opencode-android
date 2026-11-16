package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D12 (navigasi / back / deep-link): scanner guards for the
 * fixes whose behaviour lives in a view model or a composable that a JVM
 * test cannot drive — the source must keep the shape the fix gave it.
 */
class AuditD12Test {

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

    private fun count(src: String, needle: String): Int =
        src.split(needle).size - 1

    private val app = "src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt"
    private val chatVm = "src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatViewModel.kt"
    private val chatScreen = "src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt"
    private val commandSuggestions = "src/main/kotlin/dev/bidzz/opencode/ui/chat/CommandSuggestions.kt"
    private val pendingStore = "src/main/kotlin/dev/bidzz/opencode/ui/chat/PendingOpenStore.kt"
    private val browser = "src/main/kotlin/dev/bidzz/opencode/ui/browser/InAppBrowserScreen.kt"
    private val shortcuts = "src/main/kotlin/dev/bidzz/opencode/ui/SessionShortcuts.kt"
    private val onboarding = "src/main/kotlin/dev/bidzz/opencode/ui/onboarding/OnboardingScreen.kt"
    private val mainActivity = "src/main/kotlin/dev/bidzz/opencode/MainActivity.kt"
    private val application = "src/main/kotlin/dev/bidzz/opencode/OpenCodeApplication.kt"
    private val exportScreen = "src/main/kotlin/dev/bidzz/opencode/ui/export/ExportScreen.kt"

    // ---- m-410: the export screen alone triggers the load -----------

    @Test
    fun `m-410 no onExport handler loads the export view model`() {
        val src = read(app)
        assertFalse(src.contains("exportViewModel.load(row.id)"))
        // The screen's own refresh is the sole trigger.
        assertTrue(src.contains("onRefresh = { exportViewModel.load(target.sessionID) }"))
        val screen = read(exportScreen)
        val body = funBody(screen, "LaunchedEffect(sessionID)")
        assertTrue(body.contains("onRefresh()"))
    }

    // ---- m-440: a deleted session clears its shortcut -------------

    @Test
    fun `m-440 the shortcut effect clears the stale last-session shortcut`() {
        val src = read(app)
        assertTrue(src.contains("lastPublishedSessionID"))
        assertTrue(src.contains("clearLastSessionShortcut(localized)"))
        val shortcutsSrc = read(shortcuts)
        assertTrue(shortcutsSrc.contains("removeDynamicShortcuts(listOf(\"last_session\"))"))
    }

    // ---- m-194: drawer close-then-action is single-flight ----------

    @Test
    fun `m-194 closeThen cancels the previous tap's job`() {
        val src = read(chatScreen)
        val body = funBody(src, "fun closeThen(action: () -> Unit)")
        assertTrue(body.contains("closeThenJob?.cancel()"))
        assertTrue(body.contains("closeThenJob = scope.launch {"))
    }

    // ---- m-264: system Back completes onboarding -------------------

    @Test
    fun `m-264 Back inside onboarding runs the dismiss action`() {
        val src = read(app)
        assertTrue(src.contains("BackHandler(enabled = true, onBack = dismissOnboarding)"))
        assertTrue(src.contains("onDismiss = dismissOnboarding"))
    }

    // ---- m-377: a malformed deep link says so -----------------------

    @Test
    fun `m-377 every invalid session link shows the invalid-link snackbar`() {
        val src = read(mainActivity)
        // Segments, blank segment, rejected VIEW link, rejected shortcut.
        assertEquals(4, count(src, "R.string.session_link_invalid"))
    }

    // ---- m-132: palette selection follows the typed query -----------

    @Test
    fun `m-132 the palette reset is keyed on the draft not the matches`() {
        val src = read(commandSuggestions)
        assertTrue(src.contains("LaunchedEffect(draft)"))
        assertFalse(src.contains("LaunchedEffect(matches)"))
    }

    // ---- m-327: onboarding state survives process death -------------

    @Test
    fun `m-327 onboarding visibility and step are saveable`() {
        val src = read(app)
        assertTrue(src.contains("var showOnboarding by rememberSaveable"))
        val onboardingSrc = read(onboarding)
        assertTrue(onboardingSrc.contains("var step by rememberSaveable"))
        assertTrue(onboardingSrc.contains("var samplePath by rememberSaveable"))
        assertTrue(onboardingSrc.contains("var sampleError by rememberSaveable"))
    }

    // ---- m-382: no early notification cancel on a deep link --------

    @Test
    fun `m-382 deliverSessionOpen never cancels the agent-done notification`() {
        val src = read(application)
        val body = funBody(src, "fun deliverSessionOpen(")
        assertFalse(body.contains("cancelAgentDone"))
        // The notification is cancelled when the session actually opens.
        assertTrue(read(app).contains("cancelAgentDone(app, it)"))
    }

    // ---- m-422: browser restore compares against the live URL -------

    @Test
    fun `m-422 the restore guard compares against the current URL`() {
        val src = read(browser)
        assertTrue(src.contains("savedWebViewUrl == currentUrl"))
        assertFalse(src.contains("savedWebViewUrl == url"))
    }

    // ---- m-442: subframe finishes do not rewrite the URL -----------

    @Test
    fun `m-442 onPageFinished ignores finishes for another URL`() {
        val src = read(browser)
        val body = funBody(src, "override fun onPageFinished(view: WebView, finishedUrl: String)")
        assertTrue(body.contains("finishedUrl != view.url"))
    }

    // ---- m-378 / m-381: verification requests are ordered -----------

    @Test
    fun `m-378 m-381 verifying GETs carry a sequence and a flight count`() {
        val src = read(chatVm)
        assertTrue(src.contains("private var openVerificationSeq = 0L"))
        assertTrue(src.contains("private var openVerificationInFlight = 0"))
        // Both GET result branches drop a superseded request.
        assertEquals(2, count(src, "mySeq != openVerificationSeq"))
        // The restore yields while a deep-link verification is in flight.
        assertTrue(src.contains("openVerificationInFlight == 0 && shouldOpenAfterResume("))
    }

    // ---- m-379: pending opens survive process death -----------------

    @Test
    fun `m-379 pending opens persist in their own store`() {
        val src = read(pendingStore)
        assertTrue(src.contains("class SharedPrefsPendingOpenStore"))
        assertTrue(src.contains("\"pending_open_ids\""))
        assertTrue(src.contains("\"pending_restore_id\""))
        val vm = read(chatVm)
        assertTrue(vm.contains("private val pendingOpenStore: PendingOpenStore = InMemoryPendingOpenStore()"))
        assertTrue(vm.contains("pendingOpenStore.loadPendingOpens()"))
        assertTrue(vm.contains("pendingOpenStore.loadPendingRestore()"))
        // The app wires the disk-backed store into the factory.
        assertTrue(read(app).contains("pendingOpenStore = SharedPrefsPendingOpenStore(app)"))
    }
}
