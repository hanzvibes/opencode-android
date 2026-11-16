package dev.bidzz.opencode.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D8 (dialog / konfirmasi): scanner guards for the fixes whose
 * behaviour lives in a composable or a view model that a JVM test cannot
 * drive — the source must keep the shape the fix gave it. Behaviour
 * tests live beside these: [SheetSnapTest] (m-950),
 * [dev.bidzz.opencode.ui.mcp.McpShellCommandD8Test] (m-578),
 * [dev.bidzz.opencode.ui.generate.GenerateModeD8Test] (m-990),
 * [dev.bidzz.opencode.NotificationGateD8Test] (m-267), and the m-537
 * case in [dev.bidzz.opencode.ui.files.QuickOpenFilterTest].
 */
class DialogsD8Test {

    private fun read(path: String): String = File(path).readText()

    private fun count(src: String, needle: String): Int = src.split(needle).size - 1

    @Test
    fun `m-682 m-958 a delete message is never cut off at two lines`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/HatchDialogs.kt")
        assertFalse(src.contains("maxLines = 2"))
        assertFalse(src.contains("TextOverflow.Ellipsis"))
    }

    @Test
    fun `m-676 the light sheet border is the Aura border, not the card border`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/DraggableBottomSheet.kt")
        assertFalse(src.contains("colors.cardBorder"))
        assertTrue(src.contains("AuraPalette.auraBorder"))
    }

    @Test
    fun `m-1050 the soft keyboard Search key picks the selected result`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/AttachFilePicker.kt")
        assertTrue(src.contains("keyboardActions = KeyboardActions(onSearch = { pickSelected() })"))
    }

    @Test
    fun `m-200 STOPPING has its own description, not the stopped one`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        assertTrue(src.contains("EngineState.STOPPING -> R.string.chat_status_desc_stopping"))
        assertFalse(src.contains("STOPPING,\n        dev.bidzz.opencode.core.engine.EngineState.STOPPED -> R.string.chat_status_desc_stopped"))
    }

    @Test
    fun `m-1216 removing an MCP server uses the shared delete sheet`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/mcp/McpServersScreen.kt")
        assertTrue(src.contains("HatchDeleteConfirmationDialog("))
        // The centered dialog is gone from this screen: restart, add and
        // re-auth are the only AlertDialogs left.
        assertEquals(3, count(src, "LocalizedAlertDialog("))
    }

    @Test
    fun `m-1228 a session title is capped at 200 characters`() {
        val dialogs = read("src/main/kotlin/dev/bidzz/opencode/ui/HatchDialogs.kt")
        assertTrue(dialogs.contains("MAX_SESSION_TITLE_LENGTH = 200"))
        assertTrue(dialogs.contains("take(MAX_SESSION_TITLE_LENGTH)"))
        val vm = read("src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionListViewModel.kt")
        assertTrue(vm.contains("take(MAX_SESSION_TITLE_LENGTH)"))
    }

    @Test
    fun `m-425 m-946 m-1006 dialog triggers survive a process death`() {
        val chat = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        assertTrue(chat.contains("showCompactDialog by rememberSaveable"))
        assertTrue(chat.contains("pendingRun by rememberSaveable"))
        assertTrue(chat.contains("revertTargetId by rememberSaveable"))
        assertTrue(chat.contains("showStatusDialog by rememberSaveable"))
        assertTrue(chat.contains("showBranches by rememberSaveable"))
        assertTrue(chat.contains("sessionToRenameId by rememberSaveable"))
        assertTrue(chat.contains("sessionToDeleteId by rememberSaveable"))
        val list = read("src/main/kotlin/dev/bidzz/opencode/ui/sessions/SessionListScreen.kt")
        assertTrue(list.contains("sessionToRenameId by rememberSaveable"))
        assertTrue(list.contains("sessionToDeleteId by rememberSaveable"))
        val artifacts = read("src/main/kotlin/dev/bidzz/opencode/ui/artifacts/ArtifactsScreen.kt")
        assertTrue(artifacts.contains("pendingDeleteId by rememberSaveable"))
        val mcp = read("src/main/kotlin/dev/bidzz/opencode/ui/mcp/McpServersScreen.kt")
        assertTrue(mcp.contains("showAddForm by rememberSaveable"))
        assertTrue(mcp.contains("showRestartConfirm by rememberSaveable"))
        assertTrue(mcp.contains("removeTarget by rememberSaveable"))
    }

    @Test
    fun `m-1006 the lock does not re-arm on return from a picker or the camera`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/MainActivity.kt")
        assertTrue(src.contains("expectingActivityResult = true"))
        assertTrue(src.contains("if (!expectingActivityResult) {"))
        assertTrue(src.contains("launchForResult { takePhoto.launch(uri) }"))
        assertTrue(src.contains("launchForResult { pickDirectory.launch(null) }"))
        assertTrue(src.contains("launchForResult { pickSafFile.launch(arrayOf(\"*/*\")) }"))
        assertTrue(src.contains("launchForResult { saveCodeDocument.launch(pending.filename) }"))
    }

    @Test
    fun `m-534 a reload carries a held file switch instead of dropping it`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/files/ExplorerViewModel.kt")
        assertTrue(src.contains("val pending = s.confirmSwitchTo"))
        assertTrue(src.contains("if (pending != null) load(pending) else load(s.path)"))
    }

    @Test
    fun `m-537 discarding clears a held switch and a same-path tap never holds one`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/files/ExplorerViewModel.kt")
        assertTrue(src.contains("draftStale = false, confirmSwitchTo = null"))
        assertTrue(src.contains("if (path == s.path) {"))
    }
}
