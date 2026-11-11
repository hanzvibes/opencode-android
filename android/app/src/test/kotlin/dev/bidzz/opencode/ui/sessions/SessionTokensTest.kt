package dev.bidzz.opencode.ui.sessions

import dev.bidzz.opencode.core.api.dto.CacheUsage
import dev.bidzz.opencode.core.api.dto.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Per-session token totals for the history list.
 *
 * The total is what the row shows next to the cost; getting it wrong would
 * either hide usage (a null where the engine did report tokens) or inflate
 * it (counting cache reads, which are already inside input, twice).
 */
class SessionTokensTest {

    @Test
    fun `no usage is null`() {
        assertNull(totalTokens(null))
        assertNull(totalTokens(TokenUsage()))
        assertNull(totalTokens(TokenUsage(input = 0, output = 0, reasoning = 0)))
    }

    @Test
    fun `sums input output and reasoning`() {
        assertEquals(4124L, totalTokens(TokenUsage(input = 4032, output = 9, reasoning = 83)))
        assertEquals(4032L, totalTokens(TokenUsage(input = 4032)))
    }

    @Test
    fun `cache is not added on top of input`() {
        val usage = TokenUsage(
            input = 4032,
            output = 9,
            cache = CacheUsage(read = 2393, write = 0),
        )
        assertEquals(4041L, totalTokens(usage))
    }

    @Test
    fun `compact form matches the turn footer`() {
        assertEquals("842", formatCompactTokens(842))
        assertEquals("999", formatCompactTokens(999))
        assertEquals("1.0k", formatCompactTokens(1_000))
        assertEquals("12.4k", formatCompactTokens(12_400))
        assertEquals("1.2M", formatCompactTokens(1_250_000))
    }
}
