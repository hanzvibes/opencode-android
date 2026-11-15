package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D6 (snackbar / banner / toast): the guards that keep this
 * batch's banner, drawer, config-read and snackbar-queue fixes in place.
 * The behaviour lives in composables and the application class, so — like
 * the D2/D3/D4/D5 guards — it is pinned by scanning the source; the queue
 * itself is pure and unit-tested directly in [SnackbarQueueTest].
 */
class BannerD6Test {

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

    // ---- History banners (m-011 / m-408) ----

    @Test
    fun `delete rename and load banners are three independent ifs, never else-if`() {
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionListScreen.kt")
        assertFalse(screen.contains("} else if (state.renameFailure"))
        assertFalse(screen.contains("} else if (state.rows.isNotEmpty())"))
        assertTrue(screen.contains("if (state.renameFailure != null || state.renameFailureKey != null) {"))
        assertTrue(screen.contains("if (state.rows.isNotEmpty()) {"))
    }

    // ---- load() must not wipe operation failures (m-481) ----

    @Test
    fun `load keeps delete and rename failures, only the dismiss callbacks clear them`() {
        val vm = read("src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionListViewModel.kt")
        val load = funBody(vm, "fun load(")
        assertFalse(load.contains("deleteFailure = null"))
        assertFalse(load.contains("renameFailure = null"))
        assertTrue(vm.contains("fun clearDeleteFailure()"))
        assertTrue(vm.contains("fun clearRenameFailure()"))
    }

    // ---- Drawer banners (m-477) ----

    @Test
    fun `the drawer renders both operation banners independently`() {
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        // The params are threaded through ChatScreen, ChatDrawer,
        // ChatSidebar and ChatDrawerContent.
        assertTrue(screen.contains("sessionsDeleteFailure: ApiResult.Failure? = null,"))
        assertTrue(screen.contains("sessionsRenameFailure: ApiResult.Failure? = null,"))
        assertTrue(screen.contains("if (sessionsDeleteFailure != null || sessionsDeleteFailureKey != null) {"))
        assertTrue(screen.contains("if (sessionsRenameFailure != null || sessionsRenameFailureKey != null) {"))
        assertTrue(screen.contains("message = sessionsDeleteFailure?.message()"))
        assertTrue(screen.contains("message = sessionsRenameFailure?.message()"))
        assertTrue(screen.contains("onDismissDeleteFailure"))
        assertTrue(screen.contains("onDismissRenameFailure"))
    }

    @Test
    fun `OpenCodeApp passes the session failures into the drawer`() {
        val app = read("src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt")
        assertTrue(app.contains("sessionsDeleteFailure = sessionsState.deleteFailure"))
        assertTrue(app.contains("sessionsRenameFailure = sessionsState.renameFailure"))
    }

    // ---- Config read (m-271) ----

    @Test
    fun `a missing config clears the stale read problem and an unreadable one is recorded`() {
        val store = read("src/main/kotlin/dev/bidzz/opencode/engine/EngineConfigStore.kt")
        val body = funBody(store, "private fun readRootAndSource(")
        // The no-file branch (m-271 direction 1).
        assertTrue(body.contains("lastConfigReadProblem = null"))
        // The read is guarded (m-271 direction 2).
        assertTrue(body.contains("target.readText()"))
        assertTrue(body.contains("catch (e: IOException)"))
        assertTrue(body.contains("lastConfigReadProblem = ConfigReadProblem(target.name"))
    }

    // ---- Camera + clipboard (m-148 / m-407) ----

    @Test
    fun `the camera insert is guarded and a failed launch reports its own failure`() {
        val activity = read("src/main/kotlin/dev/bidzz/opencode/MainActivity.kt")
        assertTrue(activity.contains("runCatching { contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }"))
        assertTrue(activity.contains("R.string.camera_open_failed"))
    }

    @Test
    fun `the browser copy uses the safe helper and reports failure honestly`() {
        val browser = read("src/main/kotlin/dev/bidzz/opencode/ui/browser/InAppBrowserScreen.kt")
        assertFalse(browser.contains("as ClipboardManager"))
        assertTrue(browser.contains("copyPlainText(context, \"url\", currentUrl)"))
        assertTrue(browser.contains("R.string.copy_failed"))
    }

    // ---- Snackbar host (m-439 / m-195) ----

    @Test
    fun `the snackbar host clears the navigation bar`() {
        val app = read("src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt")
        val at = app.indexOf("SnackbarHost(")
        require(at >= 0) { "missing SnackbarHost" }
        val window = app.substring(maxOf(0, at - 400), minOf(app.length, at + 400))
        assertTrue(window.contains("navigationBarsPadding()"))
    }

    // ---- Snackbar queue (m-593) ----

    @Test
    fun `the five action-bearing sites use the retry helper`() {
        val activity = read("src/main/kotlin/dev/bidzz/opencode/MainActivity.kt")
        assertEquals(2, activity.split("showSnackbarWithRetry(").size - 1)
        val app = read("src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt")
        assertEquals(1, app.split("showSnackbarWithRetry(").size - 1)
        val diagnostics = read("src/main/kotlin/dev/bidzz/opencode/ui/diagnostics/DiagnosticsScreen.kt")
        assertEquals(2, diagnostics.split("showSnackbarWithRetry(").size - 1)
        val application = read("src/main/kotlin/dev/bidzz/opencode/OpenCodeApplication.kt")
        assertTrue(application.contains("fun showSnackbarWithRetry("))
        assertTrue(application.contains("SNACKBAR_RETRY_DELAY_MS"))
    }
}
