package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.api.dto.ToolState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTurnPartsTest {

    @Test
    fun `buildTurnParts preserves chronological interleaved order of text tools and questions`() {
        val questionInput = Json.parseToJsonElement("""{"question":"Continue?","options":["Yes","No"]}""")
        val blocks = listOf(
            MessageContent(type = "reasoning", text = "Thinking about steps"),
            MessageContent(type = "text", text = "Let me check the files."),
            MessageContent(
                type = "tool",
                id = "t1",
                name = "glob",
                state = ToolState(status = "completed"),
            ),
            MessageContent(type = "text", text = "I found the files. Which one to use?"),
            MessageContent(
                type = "tool",
                id = "t2",
                name = "question",
                state = ToolState(status = "running", input = questionInput),
            ),
            MessageContent(type = "text", text = "Waiting for your answer."),
        )

        val parts = buildTurnParts(content = blocks)

        assertEquals(6, parts.size)
        assertTrue(parts[0] is ChatTurnPart.Reasoning)
        assertEquals("Thinking about steps", (parts[0] as ChatTurnPart.Reasoning).text)

        assertTrue(parts[1] is ChatTurnPart.Text)
        assertEquals("Let me check the files.", (parts[1] as ChatTurnPart.Text).markdown)

        assertTrue(parts[2] is ChatTurnPart.ToolGroup)
        assertEquals(1, (parts[2] as ChatTurnPart.ToolGroup).tools.size)
        assertEquals("glob", (parts[2] as ChatTurnPart.ToolGroup).tools[0].name)

        assertTrue(parts[3] is ChatTurnPart.Text)
        assertEquals("I found the files. Which one to use?", (parts[3] as ChatTurnPart.Text).markdown)

        assertTrue(parts[4] is ChatTurnPart.Question)
        val qPart = parts[4] as ChatTurnPart.Question
        assertEquals("question", qPart.call.name)
        assertEquals(1, qPart.questions.size)
        assertEquals("Continue?", qPart.questions[0].question)

        assertTrue(parts[5] is ChatTurnPart.Text)
        assertEquals("Waiting for your answer.", (parts[5] as ChatTurnPart.Text).markdown)
    }

    @Test
    fun `mergeTurnParts merges adjacent text and tool groups correctly`() {
        val first = listOf(
            ChatTurnPart.Text("Part 1 text"),
            ChatTurnPart.ToolGroup(listOf(ToolCall(id = "1", name = "read", status = "completed"))),
        )
        val second = listOf(
            ChatTurnPart.ToolGroup(listOf(ToolCall(id = "2", name = "write", status = "completed"))),
            ChatTurnPart.Text("Part 2 text"),
        )

        val merged = mergeTurnParts(first, second)

        assertEquals(3, merged.size)
        assertTrue(merged[0] is ChatTurnPart.Text)
        assertEquals("Part 1 text", (merged[0] as ChatTurnPart.Text).markdown)

        assertTrue(merged[1] is ChatTurnPart.ToolGroup)
        assertEquals(2, (merged[1] as ChatTurnPart.ToolGroup).tools.size)

        assertTrue(merged[2] is ChatTurnPart.Text)
        assertEquals("Part 2 text", (merged[2] as ChatTurnPart.Text).markdown)
    }

    @Test
    fun `mergeLiveToolsIntoParts appends a tool group after streamed text instead of freezing the trace`() {
        val parts = listOf(ChatTurnPart.Text("Let me check the files."))
        val live = listOf(ToolCall(id = "t1", name = "read", status = "running"))

        val merged = mergeLiveToolsIntoParts(parts, live, isStreaming = true)

        assertEquals(2, merged.size)
        assertTrue(merged[0] is ChatTurnPart.Text)
        assertEquals("Let me check the files.", (merged[0] as ChatTurnPart.Text).markdown)
        assertTrue(merged[1] is ChatTurnPart.ToolGroup)
        val group = merged[1] as ChatTurnPart.ToolGroup
        assertEquals(1, group.tools.size)
        assertEquals("read", group.tools[0].name)
        assertTrue(group.streaming)
    }

    @Test
    fun `mergeLiveToolsIntoParts updates a tool in place by id without duplicating it`() {
        val parts = listOf(
            ChatTurnPart.Text("Let me check the files."),
            ChatTurnPart.ToolGroup(
                tools = listOf(ToolCall(id = "t1", name = "read", status = "running")),
                streaming = true,
            ),
        )
        val live = listOf(ToolCall(id = "t1", name = "read", status = "completed"))

        val merged = mergeLiveToolsIntoParts(parts, live, isStreaming = true)
        // Re-merging the same calls (every toUiMessages rebuild does) is a no-op.
        val mergedAgain = mergeLiveToolsIntoParts(merged, live, isStreaming = true)

        assertEquals(2, merged.size)
        val group = merged[1] as ChatTurnPart.ToolGroup
        assertEquals(1, group.tools.size)
        assertEquals("completed", group.tools[0].status)
        assertEquals(false, group.streaming)
        assertEquals(merged, mergedAgain)
    }

    @Test
    fun `explicit reasoning and reasoning blocks show the thinking once`() {
        // 01-F1: explicitReasoning IS the reasoning blocks
        // (SessionMessage.reasoningText joins them), so passing both
        // used to render "Thinking…\n\nThinking…" in the disclosure.
        val blocks = listOf(
            MessageContent(type = "reasoning", text = "Thinking about steps"),
            MessageContent(type = "text", text = "Done."),
        )
        val parts = buildTurnParts(content = blocks, explicitReasoning = "Thinking about steps")

        val reasoning = parts.filterIsInstance<ChatTurnPart.Reasoning>()
        assertEquals(1, reasoning.size)
        assertEquals("Thinking about steps", reasoning[0].text)
    }

    @Test
    fun `reasoning blocks alone still show the thinking once`() {
        val blocks = listOf(
            MessageContent(type = "reasoning", text = "Thinking about steps"),
            MessageContent(type = "text", text = "Done."),
        )
        val parts = buildTurnParts(content = blocks)

        val reasoning = parts.filterIsInstance<ChatTurnPart.Reasoning>()
        assertEquals(1, reasoning.size)
        assertEquals("Thinking about steps", reasoning[0].text)
    }

    @Test
    fun `reasoning-only turn does not duplicate through the fallback`() {
        // 01-F9: with no text or tool parts, the fallback parsed the
        // plain text and appended the explicit reasoning a second time.
        val blocks = listOf(
            MessageContent(type = "reasoning", text = "Thinking about steps"),
        )
        val parts = buildTurnParts(
            content = blocks,
            explicitReasoning = "Thinking about steps",
            fallbackText = "Thinking about steps",
        )

        val reasoning = parts.filterIsInstance<ChatTurnPart.Reasoning>()
        assertEquals(1, reasoning.size)
        assertEquals("Thinking about steps", reasoning[0].text)
    }
}
