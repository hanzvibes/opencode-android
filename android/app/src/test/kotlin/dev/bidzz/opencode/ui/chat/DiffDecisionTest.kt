package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
