package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.ToolState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6c (01-F5): the streaming text joins every text block of
 * the turn, so after a reconnect the history already shows the earlier
 * blocks — appending the whole stream repeats them. Only the suffix past
 * what is shown is new.
 */
class StreamingTextSuffixTest {

    @Test
    fun `01-F5 nothing shown yet returns the whole stream`() {
        assertEquals("Hello world", streamingTextSuffix("Hello world", ""))
    }

    @Test
    fun `01-F5 stream equal to what is shown returns empty`() {
        assertEquals("", streamingTextSuffix("Hello", "Hello"))
    }

    @Test
    fun `01-F5 stream past shown blocks returns only the new part`() {
        assertEquals("B", streamingTextSuffix("A\n\nB", "A"))
        assertEquals("B\n\nC", streamingTextSuffix("A\n\nB\n\nC", "A"))
    }

    @Test
    fun `01-F5 a stream that disagrees with history falls back to the whole stream`() {
        // The buffer and the history disagree: repeating the text is
        // safer than dropping the new part.
        assertEquals("Different text", streamingTextSuffix("Different text", "A"))
    }

    @Test
    fun `01-F5 surrounding whitespace does not break the prefix match`() {
        assertEquals("B", streamingTextSuffix("  A\n\nB  ", "A"))
    }

    @Test
    fun `01-F5 text before a tool is not repeated when the stream resumes after it`() {
        val blocks = listOf(
            MessageContent(type = "text", text = "A"),
            MessageContent(
                type = "tool",
                id = "t1",
                name = "read",
                state = ToolState(status = "completed"),
            ),
        )

        val parts = buildTurnParts(content = blocks, streamingText = "A\n\nB")

        assertEquals(3, parts.size)
        assertTrue(parts[0] is ChatTurnPart.Text)
        assertEquals("A", (parts[0] as ChatTurnPart.Text).markdown)
        assertTrue(parts[1] is ChatTurnPart.ToolGroup)
        assertTrue(parts[2] is ChatTurnPart.Text)
        assertEquals("B", (parts[2] as ChatTurnPart.Text).markdown)
    }

    @Test
    fun `01-F5 a trailing text block is replaced by the stream past the earlier blocks`() {
        val blocks = listOf(
            MessageContent(type = "text", text = "A"),
            MessageContent(
                type = "tool",
                id = "t1",
                name = "read",
                state = ToolState(status = "completed"),
            ),
            MessageContent(type = "text", text = "B"),
        )

        val parts = buildTurnParts(content = blocks, streamingText = "A\n\nB\n\nC")

        assertEquals(3, parts.size)
        assertTrue(parts[2] is ChatTurnPart.Text)
        assertEquals("B\n\nC", (parts[2] as ChatTurnPart.Text).markdown)
    }
}
