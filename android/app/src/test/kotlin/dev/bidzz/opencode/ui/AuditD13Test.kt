package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D13 (navigasi / back / deep-link, bagian 2): scanner guards
 * for the fixes whose behaviour lives in a view model or a composable
 * that a JVM test cannot drive — the source must keep the shape the
 * fix gave it.
 */
class AuditD13Test {

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
    private val pendingStore = "src/main/kotlin/dev/bidzz/opencode/ui/chat/PendingOpenStore.kt"
    private val browser = "src/main/kotlin/dev/bidzz/opencode/ui/browser/InAppBrowserScreen.kt"
    private val widget = "src/main/kotlin/dev/bidzz/opencode/ui/widget/SessionWidgetProvider.kt"
    private val notifier = "src/main/kotlin/dev/bidzz/opencode/ui/AgentDoneNotifier.kt"
    private val mainActivity = "src/main/kotlin/dev/bidzz/opencode/MainActivity.kt"
    private val application = "src/main/kotlin/dev/bidzz/opencode/OpenCodeApplication.kt"
    private val apiResult = "../core/api/src/main/kotlin/dev/bidzz/opencode/core/api/ApiResult.kt"

    // ---- m-525/m-948: persist at the deliver point ------------------

    @Test
    fun `m-525 the deliver point persists the link before emitting it`() {
        val src = read(application)
        assertTrue(src.contains("fun deliverSessionOpen(sessionID: String): SessionOpenDelivery"))
        val body = funBody(src, "fun deliverSessionOpen(sessionID: String)")
        assertTrue(body.contains("pendingOpenStore.addPendingOpen(sessionID)"))
        // Persist first, emit second — the store is the second copy
        // that survives a process death in the in-memory window.
        assertTrue(body.indexOf("addPendingOpen(sessionID)") < body.indexOf("tryEmit(sessionID)"))
        assertTrue(body.contains("SessionOpenDelivery.BUFFER_FULL"))
    }

    @Test
    fun `m-532 only an invalid link shows the invalid-link snackbar`() {
        val src = read(mainActivity)
        // Both deliver sites distinguish INVALID from BUFFER_FULL; a
        // full buffer is queued on disk, not an error to report.
        assertEquals(2, count(src, "SessionOpenDelivery.INVALID"))
        assertFalse(src.contains("!app.deliverSessionOpen"))
        assertEquals(4, count(src, "R.string.session_link_invalid"))
    }

    @Test
    fun `m-525 a restore from recents still handles the launch intent`() {
        val src = read(mainActivity)
        assertTrue(src.contains("activityCreatedOnce"))
        assertTrue(src.contains("savedInstanceState == null || isFirstCreationInProcess"))
    }

    // ---- m-1004: a pending deep link expires ------------------------

    @Test
    fun `m-1004 pending opens carry a TTL and the drain re-syncs with the store`() {
        val store = read(pendingStore)
        assertTrue(store.contains("PENDING_OPEN_TTL_MS"))
        assertTrue(store.contains("pruneExpired()"))
        assertTrue(store.contains("class TimestampedOpens"))
        val vm = read(chatVm)
        val consume = funBody(vm, "private fun consumePendingOpen()")
        assertTrue(consume.contains("pendingOpenStore.loadPendingOpens()"))
        assertTrue(consume.contains("retainAll(fresh)"))
    }

    // ---- m-977: verification timeout, indicator, Retry --------------

    @Test
    fun `m-977 the verification GET has its own timeout and indicator`() {
        val src = read(chatVm)
        assertTrue(src.contains("OPEN_VERIFICATION_TIMEOUT_MS = 10_000L"))
        val open = funBody(src, "private fun openSessionById(sessionID: String, fetchMeta: Boolean")
        assertTrue(open.contains("withTimeout(OPEN_VERIFICATION_TIMEOUT_MS)"))
        assertTrue(open.contains("TimeoutCancellationException"))
        assertTrue(open.contains("openingSession = true"))
        assertTrue(open.contains("openingSession = false"))
        // The 120s client timeout is untouched — a prompt needs it.
        assertFalse(open.contains("callTimeout"))
    }

    @Test
    fun `m-977 a deferred open offers Retry that re-runs the verification`() {
        val src = read(chatVm)
        assertTrue(src.contains("RETRY_OPEN,"))
        val defer = funBody(src, "internal fun deferSessionOpen()")
        assertTrue(defer.contains("retryKind = RetryKind.RETRY_OPEN"))
        val retry = funBody(src, "fun retryError()")
        assertTrue(retry.contains("RetryKind.RETRY_OPEN -> retryPendingOpen()"))
        assertTrue(src.contains("fun retryPendingOpen()"))
        val screen = read(chatScreen)
        assertTrue(screen.contains("state.openingSession"))
        assertTrue(screen.contains("R.string.chat_opening_session"))
    }

    // ---- m-980: navigate only when the session is confirmed open ----

    @Test
    fun `m-980 the session-open collector does not navigate on its own`() {
        val src = read(app)
        val collector = funBody(src, "collectSafely(\"session-open\")")
        assertFalse(collector.contains("screen = Screen.Chat"))
        assertTrue(collector.contains("pendingDeepLinkSessionID = sessionID"))
        // Navigation happens on the confirmed session id…
        assertTrue(src.contains("chatState.sessionID == pending"))
        // …and a refused link drops the pending navigation.
        assertTrue(src.contains("pendingDeepLinkSessionID = null"))
    }

    // ---- m-984: every 404 is a missing session ----------------------

    @Test
    fun `m-984 a 404 with an unfamiliar tag still counts as a missing session`() {
        val src = read(apiResult)
        assertTrue(src.contains("tag != \"LocationNotFoundError\""))
    }

    // ---- m-780: a session id has a length limit ---------------------

    @Test
    fun `m-780 session ids longer than 128 characters are rejected`() {
        val src = read(application)
        val body = funBody(src, "internal fun isValidSessionId(id: String)")
        assertTrue(body.contains("id.length > 128"))
    }

    // ---- m-788: the agent-done notification hides its title when locked

    @Test
    fun `m-788 the agent-done notification is secret while App Lock is on`() {
        val src = read(notifier)
        assertTrue(src.contains("setVisibility(agentDoneVisibility"))
        assertTrue(src.contains("setPublicVersion("))
        assertTrue(src.contains("AppLockPrefs.isEnabled(context)"))
        assertTrue(src.contains("fun agentDoneVisibility(lockEnabled: Boolean)"))
    }

    // ---- m-899: only a real tap forwards to the system --------------

    @Test
    fun `m-899 a forwardable URL needs a user gesture to leave the WebView`() {
        val src = read(browser)
        assertTrue(src.contains("internal fun shouldForwardToSystem("))
        assertTrue(src.contains("isMainFrame && hasGesture && isForwardableUrl(target)"))
        val client = funBody(src, "override fun shouldOverrideUrlLoading(")
        assertTrue(client.contains("shouldForwardToSystem("))
        assertTrue(client.contains("request.hasGesture()"))
    }

    // ---- m-777: the committed URL is saved before the page finishes --

    @Test
    fun `m-777 the WebView state is saved when the URL commits, not only on finish`() {
        val src = read(browser)
        assertTrue(src.contains("override fun doUpdateVisitedHistory("))
        val history = funBody(src, "override fun doUpdateVisitedHistory(")
        assertTrue(history.contains("savedWebViewUrl = committed"))
        assertTrue(history.contains("savedWebViewState = Bundle()"))
        // Same main-frame guard as D12 m-442: only the frame the
        // WebView itself is on may move the state.
        assertTrue(history.contains("committed != view.url"))
        // The finish handler still refreshes both when the load ends.
        val finished = funBody(src, "override fun onPageFinished(")
        assertTrue(finished.contains("savedWebViewUrl = finishedUrl"))
    }

    // ---- m-1005: the widget hides the session title when locked -----

    @Test
    fun `m-1005 the widget shows the app name instead of the title while locked`() {
        val src = read(widget)
        assertTrue(src.contains("AppLockPrefs.isEnabled(context)"))
        assertTrue(src.contains("if (locked) appLabel(context)"))
    }

    // ---- m-1167: Projects returns to the screen that opened it ------

    @Test
    fun `m-1167 Projects carries its return target like Diagnostics and the browser`() {
        val src = read(app)
        assertTrue(src.contains("data class Projects(val returnTo: Screen = Screen.Chat)"))
        assertTrue(src.contains("is Screen.Projects -> this.returnTo"))
        assertTrue(src.contains("Screen.Projects(Screen.Settings)"))
        assertTrue(src.contains("onBack = { screen = screen.parent() }"))
    }
}
