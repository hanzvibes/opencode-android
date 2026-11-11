package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.CacheUsage
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
}
