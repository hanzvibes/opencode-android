package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.ui.diff.DiffHunk
import dev.bidzz.opencode.ui.diff.DiffLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffDecisionTest {

    private fun call(name: String, status: String) =
        ToolCall(id = "1", name = name, status = status)

    @Test
    fun `only completed edits and writes are reviewable`() {
        assertTrue(isReviewableEdit(call("edit", "completed")))
        assertTrue(isReviewableEdit(call("write", "completed")))
        assertTrue(isReviewableEdit(call("multiedit", "completed")))
        assertFalse(isReviewableEdit(call("edit", "running")))
        assertFalse(isReviewableEdit(call("edit", "error")))
        assertFalse(isReviewableEdit(call("read", "completed")))
        assertFalse(isReviewableEdit(call("bash", "completed")))
    }

    @Test
    fun `revert prompt names the relative path and asks for the prior state`() {
        assertEquals(
            "Please revert src/chat/ChatScreen.kt to how it was before your last edit.",
            revertPromptFor("src/chat/ChatScreen.kt"),
        )
    }

    @Test
    fun `revert prompt with a change names the edit itself`() {
        val prompt = revertPromptFor(
            "src/Foo.kt",
            "@@ -10,2 +10,3 @@\n- old line\n+ new line",
        )
        assertTrue(prompt.startsWith("Please revert src/Foo.kt to how it was before your last edit."))
        assertTrue(prompt.contains("- old line"))
        assertTrue(prompt.contains("+ new line"))
    }

    @Test
    fun `two edits to the same file describe different changes`() {
        // The 07-F2 failure: two rejects of one file sent identical prompts,
        // so the agent could not tell the edits apart — and the queue dedup
        // dropped the second one while the UI claimed it was requested.
        val first = revertChangeDescription(
            listOf(
                DiffHunk(
                    "@@ -10,2 +10,2 @@",
                    listOf(
                        DiffLine(DiffLine.Kind.REMOVED, "fun old() = 1"),
                        DiffLine(DiffLine.Kind.ADDED, "fun old() = 2"),
                    ),
                ),
            ),
        )
        val second = revertChangeDescription(
            listOf(
                DiffHunk(
                    "@@ -40,2 +40,2 @@",
                    listOf(
                        DiffLine(DiffLine.Kind.REMOVED, "fun other() = 1"),
                        DiffLine(DiffLine.Kind.ADDED, "fun other() = 2"),
                    ),
                ),
            ),
        )
        assertNotEquals(first, second)
        assertTrue(first!!.contains("fun old() = 1"))
        assertTrue(second!!.contains("fun other() = 1"))
    }

    @Test
    fun `a hunk with no changed lines describes nothing`() {
        assertNull(
            revertChangeDescription(
                listOf(
                    DiffHunk(
                        "@@ -1,1 +1,1 @@",
                        listOf(DiffLine(DiffLine.Kind.CONTEXT, "unchanged")),
                    ),
                ),
            ),
        )
        assertNull(revertChangeDescription(emptyList()))
    }
}
