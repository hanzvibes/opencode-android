package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.MessageTime
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Turn footer values (audit-100 Fase 6b, 66-F3): the failed-turn branch
 * shares the assistant branch's numbers, so a turn that died after
 * burning input still shows what it spent.
 */
class TurnFooterValuesTest {

    @Test
    fun `failed turn keeps tokens cost duration and stop reason`() {
        val message = SessionMessage(
            id = "m1",
            type = "assistant",
            content = emptyList(),
            tokens = TokenUsage(input = 4032, output = 9, reasoning = 83),
            cost = 0.02,
            finish = "error",
            time = MessageTime(created = 1000, streamed = 2500, completed = 9000),
        )
        val footer = turnFooterValues(message)
        assertEquals(4124L, footer.usageTokens)
        assertEquals(0.02, footer.usageCostUsd!!, 0.0001)
        assertEquals(8000L, footer.turnDurationMs)
        assertEquals("error", footer.stopReason)
        // No reasoning blocks: no thinking time, even with timestamps.
        assertNull(footer.thinkingSeconds)
    }

    @Test
    fun `thinking seconds round to the nearest second while reasoning is present`() {
        val message = SessionMessage(
            id = "m1",
            type = "assistant",
            content = listOf(MessageContent(type = "reasoning", text = "hmm")),
            time = MessageTime(created = 1000, streamed = 2900, completed = 9000),
        )
        assertEquals(2, turnFooterValues(message).thinkingSeconds)
    }

    @Test
    fun `missing timestamps leave duration and thinking null`() {
        val message = SessionMessage(
            id = "m1",
            type = "assistant",
            tokens = TokenUsage(input = 10),
        )
        val footer = turnFooterValues(message)
        assertEquals(10L, footer.usageTokens)
        assertNull(footer.turnDurationMs)
        assertNull(footer.thinkingSeconds)
        assertNull(footer.stopReason)
    }
}
