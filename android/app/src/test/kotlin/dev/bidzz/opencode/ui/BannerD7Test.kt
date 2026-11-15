package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D7 (snackbar / banner / toast, part 2): the guards that keep
 * this batch's fixes in place. The behaviour lives in composables, the
 * application class and view-model coroutines, so — like the D2–D6
 * guards — it is pinned by scanning the source; the pure pieces are
 * unit-tested directly (ChatViewModelD7Test, StagedRevertD7Test,
 * McpUrlD7Test, CopyLogsD7Test, WebSearchGenerateD7Test).
 */
class BannerD7Test {

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

    private val vm = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatViewModel.kt")
    private val chatScreen = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
    private val stringsEn = read("src/main/res/values/strings.xml")
    private val stringsId = read("src/main/res/values-in/strings.xml")

    // ---- m-606: a staged revert dies with the engine ----

    @Test
    fun `m-606 onEngineStatus clears the staged revert with the engine`() {
        val body = funBody(vm, "fun onEngineStatus(")
        assertTrue(body.contains("stagedRevert = null"))
        assertTrue(body.contains("stagedRevertSessionID = null"))
    }

    @Test
    fun `m-606 the banner never renders without a session`() {
        assertTrue(chatScreen.contains("state.stagedRevert?.takeIf { state.sessionID != null }"))
    }

    // ---- m-617: an artifacts delete with no engine is a failure, not a success ----

    @Test
    fun `m-617 deleting with no handle reports an action failure and never reloads`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/artifacts/ArtifactsViewModel.kt")
        val body = funBody(src, "fun delete(")
        assertTrue(body.contains("if (handle == null) {"))
        assertTrue(body.contains("actionFailure = engineDownFailure()"))
        assertTrue(body.contains("val result = handle.artifacts.delete(id)"))
        assertFalse(body.contains("handle?.artifacts?.delete(id)"))
    }

    // ---- m-686: a delete in flight owns the outcome ----

    @Test
    fun `m-686 a second delete tap returns early and a missing row never sends DELETE`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionListViewModel.kt")
        assertTrue(src.contains("val deletingIds: Set<String> = emptySet()"))
        val body = funBody(src, "fun delete(")
        val guardAt = body.indexOf("if (sessionID in _state.value.deletingIds) return")
        val missingAt = body.indexOf("if (index == -1) return")
        val launchAt = body.indexOf("viewModelScope.launch")
        assertTrue(guardAt >= 0 && missingAt >= 0 && launchAt >= 0)
        assertTrue(guardAt < launchAt && missingAt < launchAt)
        assertTrue(body.contains("deletingIds = _state.value.deletingIds + sessionID"))
    }

    @Test
    fun `m-686 a failed delete releases its in-flight marker`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionListViewModel.kt")
        val body = funBody(src, "internal fun resolveDeleteFailure(")
        assertTrue(body.contains("deletingIds = state.deletingIds - sessionID"))
    }

    // ---- m-602 / m-1104: the revert banner names its anchor and its files ----

    @Test
    fun `m-602 the banner call site resolves the anchor message`() {
        assertTrue(chatScreen.contains("firstOrNull { it.id == staged.messageID }"))
        assertTrue(chatScreen.contains("stagedRevertTargetText(it)"))
        assertTrue(chatScreen.contains("anchorText = anchorText"))
        assertTrue(chatScreen.contains("stringResource(R.string.revert_banner_target, it)"))
        assertTrue(chatScreen.contains("stringResource(R.string.revert_banner_target_unknown)"))
    }

    @Test
    fun `m-1104 the banner lists its files on demand, five plus the rest`() {
        assertTrue(chatScreen.contains("revertBannerFileLines(paths)"))
        assertTrue(chatScreen.contains("stringResource(R.string.revert_banner_and_more, fileList.remaining)"))
        // The toggle is one stringResource with a conditional id, not
        // two separate calls.
        assertTrue(chatScreen.contains("R.string.revert_banner_show_files"))
        assertTrue(chatScreen.contains("R.string.revert_banner_hide_files"))
    }

    @Test
    fun `m-602 and m-1104 the new revert strings exist in both locales`() {
        for (key in listOf("revert_banner_target", "revert_banner_target_unknown", "revert_banner_show_files", "revert_banner_hide_files", "revert_banner_and_more")) {
            assertTrue(stringsEn.contains("name=\"$key\""))
            assertTrue(stringsId.contains("name=\"$key\""))
        }
    }

    // ---- m-758 / m-981: the sticky-banner arms live in one pure function ----

    @Test
    fun `m-758 and m-981 onRepoState resolves its banner key through resolveFailureKey`() {
        assertTrue(vm.contains("failureKey = resolveFailureKey("))
        assertTrue(vm.contains("previousFailureKey = previous.failureKey"))
    }

    // ---- m-631 / m-629: MCP validation and per-server failure traces ----

    @Test
    fun `m-631 the add dialog validates the remote URL before enabling Add`() {
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/mcp/McpServersScreen.kt")
        assertTrue(screen.contains("val detailValid = if (isLocal) command.isNotBlank() else isValidMcpRemoteUrl(url)"))
        assertTrue(screen.contains("stringResource(R.string.mcp_add_url_invalid)"))
        assertTrue(stringsEn.contains("name=\"mcp_add_url_invalid\""))
        assertTrue(stringsId.contains("name=\"mcp_add_url_invalid\""))
    }

    @Test
    fun `m-629 failures are traced per server and survive the snackbar`() {
        val viewModel = read("src/main/kotlin/dev/bidzz/opencode/ui/mcp/McpServersViewModel.kt")
        assertTrue(viewModel.contains("val failedActions: Map<String, ApiResult.Failure> = emptyMap()"))
        assertTrue(viewModel.contains("failedActions = _state.value.failedActions + (name to result)"))
        assertTrue(viewModel.contains("failedActions = _state.value.failedActions - name"))
        assertTrue(viewModel.contains("fun clearFailedAction(name: String)"))
        val screen = read("src/main/kotlin/dev/bidzz/opencode/ui/mcp/McpServersScreen.kt")
        assertTrue(screen.contains("actionFailure = state.failedActions[server.name]"))
        assertTrue(screen.contains("onDismissActionFailure = { onClearFailedAction(server.name) }"))
        assertTrue(screen.contains("actionFailure?.let { failure ->"))
    }

    // ---- m-860: the widget, shortcut and snackbars follow the picked language ----

    @Test
    fun `m-860 the shortcut and widget push from the wrapped context and re-push on a language change`() {
        val app = read("src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt")
        assertTrue(app.contains("LaunchedEffect(chatState.sessionID, sessionsState.rows, language)"))
        assertTrue(app.contains("updateLastSessionShortcut(localized, id, title)"))
        assertTrue(app.contains("updateSessionWidget(localized, id, title)"))
        assertFalse(app.contains("updateSessionWidget(hostContext"))
        assertFalse(app.contains("updateLastSessionShortcut(hostContext"))
    }

    @Test
    fun `m-860 MainActivity snackbars read the stored language fresh on every call`() {
        val main = read("src/main/kotlin/dev/bidzz/opencode/MainActivity.kt")
        assertTrue(main.contains("private fun localizedString(@StringRes resId: Int): String"))
        assertTrue(main.contains("localizedString(R.string.camera_open_failed)"))
        assertFalse(main.contains("getString(R.string"))
        assertFalse(main.contains("getString(if (saved)"))
    }

    // ---- m-1164: SAF picks through a Channel, named when lost ----

    @Test
    fun `m-1164 SAF picks queue in a Channel and a lost pick names its file`() {
        val app = read("src/main/kotlin/dev/bidzz/opencode/OpenCodeApplication.kt")
        assertTrue(app.contains("SAF_PICK_BUFFER_CAPACITY = 16"))
        assertTrue(app.contains("Channel<Uri>(capacity = SAF_PICK_BUFFER_CAPACITY)"))
        assertTrue(app.contains("safFilePickedChannel.trySend(uri).isSuccess"))
        assertTrue(app.contains("getString(R.string.saf_file_dropped_named, name)"))
        assertTrue(app.contains("showSnackbarWithRetry("))
        assertFalse(app.contains("safFilePicked.tryEmit"))
        assertTrue(stringsEn.contains("name=\"saf_file_dropped_named\""))
        assertTrue(stringsId.contains("name=\"saf_file_dropped_named\""))
    }

    // ---- m-594: one dying root collector must not kill the other three ----

    @Test
    fun `m-594 the root collectors run supervised with a guard each`() {
        val app = read("src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt")
        assertTrue(app.contains("supervisorScope {"))
        assertTrue(app.contains("collectSafely(\"shared-text\")"))
        assertTrue(app.contains("collectSafely(\"session-open\")"))
        assertTrue(app.contains("collectSafely(\"new-session\")"))
        assertTrue(app.contains("collectSafely(\"snackbar\")"))
        assertTrue(app.contains("Log.e(\"OpenCodeApp\", \"Snackbar action failed\", e)"))
        val picks = funBody(vm, "fun observeSafPicks(")
        assertTrue(picks.contains("Log.e(\"ChatViewModel\", \"SAF pick failed\", e)"))
    }

    // ---- m-1139: the session switch resets its failure banner explicitly ----

    @Test
    fun `m-1139 openSessionUnchecked resets the failure banner like newSession does`() {
        val body = funBody(vm, "private fun openSessionUnchecked(")
        assertTrue(body.contains("failure = null, failureKey = null"))
    }
}
