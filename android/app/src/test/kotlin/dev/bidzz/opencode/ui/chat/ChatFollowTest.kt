package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C1: follow/scroll — the follow effect's keys (M-037/M-038)
 * and the near-bottom tolerance (M-040).
 */
class ChatFollowTest {

    private fun message(
        text: String = "",
        reasoning: String? = null,
        tools: List<ToolCall> = emptyList(),
        parts: List<ChatTurnPart> = emptyList(),
    ) = ChatMessage(
        id = "m1",
        role = Role.ASSISTANT,
        text = text,
        reasoning = reasoning,
        tools = tools,
        parts = parts,
    )

    @Test
    fun `M-037 reasoning growth changes the follow keys`() {
        val before = followKeysOf(message(reasoning = "thinking"))
        val after = followKeysOf(message(reasoning = "thinking harder"))
        assertNotEquals(before, after)
        assertEquals(8, before.reasoningLength)
        assertEquals(15, after.reasoningLength)
    }

    @Test
    fun `M-038 a tool output growing in place changes the follow keys`() {
        val call = ToolCall(id = "t1", name = "read", status = "running", output = "ab")
        val before = followKeysOf(message(tools = listOf(call)))
        val after = followKeysOf(
            message(tools = listOf(call.copy(status = "completed", output = "abcdef"))),
        )
        assertEquals(before.toolsSize, after.toolsSize)
        assertNotEquals(before, after)
        assertEquals(2, before.toolOutputLength)
        assertEquals(6, after.toolOutputLength)
    }

    @Test
    fun `text growth still changes the follow keys`() {
        assertNotEquals(
            followKeysOf(message(text = "hi")),
            followKeysOf(message(text = "hello")),
        )
    }

    @Test
    fun `a null message has zero keys`() {
        assertEquals(FollowKeys(0, 0, 0, 0, 0), followKeysOf(null))
    }

    // M-040: viewport 1000px, 64px of bottom content padding, 48px tolerance.
    // At max scroll the last item's bottom sits at 1000 - 64 = 936.

    @Test
    fun `M-040 at max scroll the last item counts as near the bottom`() {
        assertTrue(isNearBottomOffset(936 - 100, 100, 1000, 64, 48))
    }

    @Test
    fun `M-040 scrolled up 100px is NOT near the bottom`() {
        // The old check (no padding subtraction) still counted this as
        // near: 1036 <= 1000 + 48.
        assertFalse(isNearBottomOffset(936 - 100 + 100, 100, 1000, 64, 48))
    }

    @Test
    fun `M-040 scrolled up 60px is NOT near the bottom`() {
        assertFalse(isNearBottomOffset(936 - 100 + 60, 100, 1000, 64, 48))
    }

    @Test
    fun `M-040 scrolled up 40px is still near the bottom`() {
        assertTrue(isNearBottomOffset(936 - 100 + 40, 100, 1000, 64, 48))
    }
}
