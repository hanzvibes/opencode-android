package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.CacheUsage
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Turn-footer token totals.
 *
 * The footer must agree with the session list for the same usage: both add
 * input + output + reasoning, and neither adds cache reads (they sit inside
 * input). The footer used to add input + output only, undercounting every
 * turn the model spent thinking.
 */
class TurnTokensTest {

    @Test
    fun `no usage is null`() {
        assertNull(turnUsageTokens(null))
        assertNull(turnUsageTokens(TokenUsage()))
    }

    @Test
    fun `sums input output and reasoning`() {
        assertEquals(4124L, turnUsageTokens(TokenUsage(input = 4032, output = 9, reasoning = 83)))
        assertEquals(4032L, turnUsageTokens(TokenUsage(input = 4032)))
    }

    @Test
    fun `reasoning alone still shows`() {
        assertEquals(83L, turnUsageTokens(TokenUsage(reasoning = 83)))
    }

    @Test
    fun `cache is not added on top of input`() {
        val usage = TokenUsage(
            input = 4032,
            output = 9,
            cache = CacheUsage(read = 2393, write = 0),
        )
        assertEquals(4041L, turnUsageTokens(usage))
    }

    @Test
    fun `reported zero stays zero`() {
        assertEquals(0L, turnUsageTokens(TokenUsage(input = 0, output = 0, reasoning = 0)))
    }

    @Test
    fun `context usage takes the last assistant message that reported usage`() {
        // 23-F1: each assistant message's input is the whole prompt that
        // call sent, so summing them counts the same history once per
        // turn. The meter takes the last call's usage: 100 + 50, not
        // 4124 + 150.
        val messages = listOf(
            SessionMessage(id = "msg_a", type = "user", text = "hi"),
            SessionMessage(
                id = "msg_b",
                type = "assistant",
                tokens = TokenUsage(input = 4032, output = 9, reasoning = 83),
            ),
            SessionMessage(
                id = "msg_c",
                type = "assistant",
                tokens = TokenUsage(input = 100, output = 50),
            ),
            SessionMessage(id = "msg_d", type = "idle", outcome = "succeeded"),
        )
        assertEquals(150L, contextUsageTokens(messages))
    }

    @Test
    fun `context usage falls back to an earlier message when the last has none`() {
        val messages = listOf(
            SessionMessage(
                id = "msg_b",
                type = "assistant",
                tokens = TokenUsage(input = 4032, output = 9, reasoning = 83),
            ),
            SessionMessage(id = "msg_c", type = "assistant"),
        )
        assertEquals(4124L, contextUsageTokens(messages))
    }

    @Test
    fun `context usage carries the model that produced the last usage`() {
        val ref = dev.bidzz.opencode.core.api.dto.ModelRef(id = "big-pickle", providerID = "opencode")
        val messages = listOf(
            SessionMessage(
                id = "msg_b",
                type = "assistant",
                model = dev.bidzz.opencode.core.api.dto.ModelRef(id = "old", providerID = "opencode"),
                tokens = TokenUsage(input = 4032),
            ),
            SessionMessage(
                id = "msg_c",
                type = "assistant",
                model = ref,
                tokens = TokenUsage(input = 100, output = 50),
            ),
        )
        val usage = contextUsage(messages)
        assertEquals(150L, usage.tokens)
        assertEquals(ref, usage.model)
    }

    @Test
    fun `context usage is null when no assistant reported usage`() {
        val messages = listOf(
            SessionMessage(id = "msg_a", type = "user", text = "hi"),
            SessionMessage(id = "msg_b", type = "assistant"),
        )
        assertNull(contextUsageTokens(messages))
        assertNull(contextUsageTokens(emptyList()))
    }

    @Test
    fun `context usage keeps a reported zero`() {
        val messages = listOf(
            SessionMessage(
                id = "msg_b",
                type = "assistant",
                tokens = TokenUsage(input = 0, output = 0, reasoning = 0),
            ),
        )
        assertEquals(0L, contextUsageTokens(messages))
    }

    @Test
    fun `busy-run meter refresh is due only after the throttle gap`() {
        assertEquals(false, contextRefreshDue(nowMs = 10_000L, lastMs = 0L))
        assertEquals(false, contextRefreshDue(nowMs = 29_999L, lastMs = 0L))
        assertEquals(true, contextRefreshDue(nowMs = 30_000L, lastMs = 0L))
        assertEquals(true, contextRefreshDue(nowMs = 45_000L, lastMs = 10_000L))
    }
}
