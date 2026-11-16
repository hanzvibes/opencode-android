package dev.bidzz.opencode.ui

import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.WebSearchResponse
import dev.bidzz.opencode.ui.files.isAnimatedCandidate
import dev.bidzz.opencode.ui.websearch.WebSearchUiState
import dev.bidzz.opencode.ui.websearch.withQuery
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D9 (tombol / aksi / chip): behaviour tests for the fixes
 * whose logic is pure, plus scanner guards for the fixes whose
 * behaviour lives in a composable or a view model that a JVM test
 * cannot drive — the source must keep the shape the fix gave it.
 * Behaviour tests for the other pure helpers live beside these:
 * [dev.bidzz.opencode.ui.chat.QuestionDataTest] (m-117),
 * [dev.bidzz.opencode.ui.chat.QuickRepliesTest] (m-446/m-447),
 * [dev.bidzz.opencode.ui.sessions.SessionListPageTest] (m-1116),
 * [HatchBatch1Test] (m-687/m-693/m-913).
 */
class AuditD9Test {

    private fun read(path: String): String = File(path).readText()

    private fun count(src: String, needle: String): Int = src.split(needle).size - 1

    // ---- m-718: animated image candidates --------------------------------

    @Test
    fun `m-718 only gif and webp can animate`() {
        assertTrue(isAnimatedCandidate("a.gif"))
        assertTrue(isAnimatedCandidate("A.GIF"))
        assertTrue(isAnimatedCandidate("b.webp"))
        assertTrue(isAnimatedCandidate("B.WEBP"))
        assertFalse(isAnimatedCandidate("c.png"))
        assertFalse(isAnimatedCandidate("d.jpg"))
        assertFalse(isAnimatedCandidate("e.jpeg"))
        assertFalse(isAnimatedCandidate("f.pdf"))
        assertFalse(isAnimatedCandidate("photo.gif.txt"))
        assertFalse(isAnimatedCandidate("noext"))
    }

    @Test
    fun `m-718 the animated decode is result-driven and size-capped`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/files/FilePreview.kt")
        assertTrue(src.contains("ImageDecoder.decodeDrawable"))
        assertTrue(src.contains("AnimatedImageDrawable"))
        assertTrue(src.contains("setTargetSize"))
        assertTrue(src.contains("isAnimatedCandidate"))
        // Compose 1.7.6 has no rememberDrawablePainter anywhere on the
        // classpath — the animation goes through an ImageView instead.
        assertFalse(src.contains("rememberDrawablePainter"))
        assertTrue(src.contains("AndroidView"))
    }

    // ---- m-666: clearing the web-search query clears stale results ------

    @Test
    fun `m-666 a blank query clears the response and the failure`() {
        val failure = ApiResult.Failure(code = 500, tag = null, message = "boom")
        val before = WebSearchUiState(
            query = "kucing",
            response = WebSearchResponse(providerID = "p"),
            failure = failure,
        )

        val after = before.withQuery("")

        assertEquals("", after.query)
        assertNull(after.response)
        assertNull(after.failure)
    }

    @Test
    fun `m-666 a non-blank keystroke keeps the results until the next search`() {
        val failure = ApiResult.Failure(code = 500, tag = null, message = "boom")
        val response = WebSearchResponse(providerID = "p")
        val before = WebSearchUiState(query = "kot", response = response, failure = failure)

        val after = before.withQuery("kotu")

        assertEquals("kotu", after.query)
        assertEquals(response, after.response)
        assertEquals(failure, after.failure)
    }

    // ---- m-693 / m-913: the publish sheet's dismiss label -----------------

    @Test
    fun `m-693 m-913 the dismiss label is one mapping, not two defects`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/HatchPublishSheet.kt")
        assertEquals(1, count(src, "fun hatchPublishDismissLabelRes"))
        assertTrue(src.contains("R.string.session_action_cancel"))
        assertTrue(src.contains("R.string.common_done"))
        assertTrue(src.contains("R.string.common_close"))
    }

    // ---- m-1028: the export copy toast re-arms ----------------------------

    @Test
    fun `m-1028 the copied flag resets on its own after a delay`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/export/ExportScreen.kt")
        assertTrue(src.contains("LaunchedEffect(copied)"))
        assertTrue(src.contains("delay(1_500)"))
    }

    // ---- m-488: camera + persistable SAF ----------------------------------

    @Test
    fun `m-488 the camera photo flows through a channel that keeps offers`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/OpenCodeApplication.kt")
        assertTrue(src.contains("CAMERA_PHOTO_BUFFER_CAPACITY = 16"))
        assertTrue(src.contains("cameraPhotoPickedChannel"))
        assertTrue(src.contains("cameraPhotoPickedChannel.trySend"))
        assertFalse(src.contains("cameraPhotoPicked.tryEmit"))
    }

    @Test
    fun `m-488 SAF picks ask for a persistable grant`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/MainActivity.kt")
        assertTrue(src.contains("OpenDocumentPersistable"))
        assertTrue(src.contains("takePersistableUriPermission"))
        assertTrue(src.contains("FLAG_GRANT_PERSISTABLE_URI_PERMISSION"))
    }

    // ---- m-569: a stale pending photo is validated, not trusted -----------

    @Test
    fun `m-569 a restored pending photo is validated on a fresh start only`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/MainActivity.kt")
        assertTrue(src.contains("pendingPhotoRowSize"))
        assertTrue(src.contains("savedInstanceState == null && pendingPhotoUri != null"))
    }

    // ---- m-1057: double-tap guards -----------------------------------------

    @Test
    fun `m-1057 move and save refuse a second call while one is in flight`() {
        val move = read("src/main/kotlin/dev/bidzz/opencode/ui/move/MoveSessionViewModel.kt")
        assertTrue(move.contains("if (_state.value.moving) return"))
        val save = read("src/main/kotlin/dev/bidzz/opencode/ui/instructions/InstructionsViewModel.kt")
        assertTrue(save.contains("if (_state.value.saving) return"))
    }

    // ---- m-088: the diff note names the missing Reject ---------------------

    @Test
    fun `m-088 the diff note says Reject is unavailable when it is hidden`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ToolRow.kt")
        assertTrue(src.contains("tool_diff_decision_note_no_reject"))
    }

    // ---- m-915: disabled buttons say why -----------------------------------

    @Test
    fun `m-915 disabled actions carry their reason for TalkBack`() {
        val dialogs = read("src/main/kotlin/dev/bidzz/opencode/ui/HatchDialogs.kt")
        assertTrue(dialogs.contains("stateDescription = emptyTitleReason"))
        assertTrue(dialogs.contains("announceForAccessibility(context, emptyTitleReason)"))
        val mcp = read("src/main/kotlin/dev/bidzz/opencode/ui/mcp/McpServersScreen.kt")
        assertTrue(mcp.contains("invalidReasonRes"))
        assertTrue(mcp.contains("liveRegion = LiveRegionMode.Polite"))
        val scaffold = read("src/main/kotlin/dev/bidzz/opencode/ui/Scaffold.kt")
        assertTrue(scaffold.contains("disabledReason"))
        val generate = read("src/main/kotlin/dev/bidzz/opencode/ui/generate/GenerateScreen.kt")
        assertTrue(generate.contains("generateDisabledReason"))
    }

    // ---- m-853: selector chips say whether they are radio or button --------

    @Test
    fun `m-853 single-select chips are radios, action chips are buttons`() {
        val radio = mapOf(
            "src/main/kotlin/dev/bidzz/opencode/ui/generate/GenerateScreen.kt" to 4,
            "src/main/kotlin/dev/bidzz/opencode/ui/jobs/JobsScreen.kt" to 2,
            "src/main/kotlin/dev/bidzz/opencode/ui/mcp/McpServersScreen.kt" to 2,
            "src/main/kotlin/dev/bidzz/opencode/ui/chat/ToolRow.kt" to 1,
            "src/main/kotlin/dev/bidzz/opencode/ui/chat/GenericFormCard.kt" to 1,
            "src/main/kotlin/dev/bidzz/opencode/ui/schedule/ScheduleScreen.kt" to 1,
        )
        var total = 0
        for ((path, expected) in radio) {
            val found = count(read(path), "role = Role.RadioButton")
            assertEquals("$path radio chips", expected, found)
            total += found
        }
        assertEquals(11, total)
        val chat = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        // The file imports Role as SemanticsRole (alias) and uses it all
        // over — pin the skill chip's own call site, not a total.
        assertTrue(
            chat.contains(
                "// Audit-200 D9 (m-853): this is an\n" +
                    "                                    // action, not a choice — never checked.\n" +
                    "                                    role = SemanticsRole.Button,",
            ),
        )
    }

    // ---- m-243: the Quick-open palette is a drag source ---------------------

    @Test
    fun `m-243 quick-open rows and list rows both start a drag`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/files/ExplorerScreen.kt")
        assertEquals(2, count(src, "detectDragGesturesAfterLongPress("))
        val palette = src.substring(src.indexOf("private fun QuickOpenPalette"))
        assertTrue(palette.contains("detectDragGesturesAfterLongPress("))
    }

    // ---- m-732: the drag hint covers the whole window ------------------------

    @Test
    fun `m-732 a drag outside the composer still shows a hint`() {
        val app = read("src/main/kotlin/dev/bidzz/opencode/ui/OpenCodeApp.kt")
        assertTrue(app.contains("dragActive"))
        assertTrue(app.contains("dragAccepted"))
        assertTrue(app.contains("dragOverElsewhere"))
        assertTrue(app.contains("dragRejected"))
        val chat = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        assertTrue(chat.contains("drag_drop_to_attach"))
        assertTrue(chat.contains("drag_drop_rejected"))
    }

    // ---- m-1044: IME Send never sends a raw command token --------------------

    @Test
    fun `m-1044 an open palette with no match sends nothing`() {
        val src = read("src/main/kotlin/dev/bidzz/opencode/ui/chat/ChatScreen.kt")
        assertTrue(src.contains("} else if (paletteActive || mentionActive) {"))
    }

    // ---- m-1220 + the new strings exist in both locales ----------------------

    @Test
    fun `m-1220 the unconfirmed-stop string says to try again`() {
        val id = read("src/main/res/values-in/strings.xml")
        assertTrue(
            id.contains(
                "name=\"stop_unconfirmed\">Berhenti belum terkonfirmasi — ketuk Berhenti untuk mencoba lagi.</string>",
            ),
        )
    }

    @Test
    fun `d9 the new strings exist in English and Indonesian`() {
        val newStrings = listOf(
            "tool_diff_decision_note_no_reject",
            "mcp_add_incomplete",
            "generate_prompt_empty",
            "generate_model_required",
            "rename_title_empty",
            "drag_drop_to_attach",
            "drag_drop_rejected",
            "camera_photo_dropped_named",
        )
        val en = read("src/main/res/values/strings.xml")
        val id = read("src/main/res/values-in/strings.xml")
        for (name in newStrings) {
            assertTrue("en $name", en.contains("name=\"$name\""))
            assertTrue("id $name", id.contains("name=\"$name\""))
        }
    }
}
