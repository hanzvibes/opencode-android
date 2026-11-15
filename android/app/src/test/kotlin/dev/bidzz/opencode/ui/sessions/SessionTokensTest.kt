package dev.bidzz.opencode.ui.sessions

import dev.bidzz.opencode.core.api.dto.CacheUsage
import dev.bidzz.opencode.core.api.dto.TokenUsage
import dev.bidzz.opencode.ui.formatCompactTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Per-session token totals for the history list.
 *
 * The total is what the row shows next to the cost; getting it wrong would
 * either hide usage (a null where the engine did report tokens) or
 * undercount it (the engine's `input` excludes both cache buckets —
 * audit-200 C13, M-185 — so the cache reads and writes belong in the sum).
 */
class SessionTokensTest {

    @Test
    fun `no usage is null, a reported zero stays zero`() {
        assertNull(totalTokens(null))
        // A present object with no bucket values still sums to zero —
        // absence is the null object itself, the same rule Stats uses
        // (67-F5: one zero rule everywhere).
        assertEquals(0L, totalTokens(TokenUsage()))
        assertEquals(0L, totalTokens(TokenUsage(input = 0, output = 0, reasoning = 0)))
    }

    @Test
    fun `sums input output and reasoning`() {
        assertEquals(4124L, totalTokens(TokenUsage(input = 4032, output = 9, reasoning = 83)))
        assertEquals(4032L, totalTokens(TokenUsage(input = 4032)))
    }

    @Test
    fun `cache reads and writes are added, input excludes them`() {
        // Audit-200 C13 (M-185): 4032 + 9 + 2393 + 0 = 6434, not 4041.
        val usage = TokenUsage(
            input = 4032,
            output = 9,
            cache = CacheUsage(read = 2393, write = 0),
        )
        assertEquals(6434L, totalTokens(usage))
    }

    @Test
    fun `compact form matches the turn footer and rounds instead of truncating`() {
        assertEquals("842", formatCompactTokens(842))
        assertEquals("999", formatCompactTokens(999))
        assertEquals("1.0k", formatCompactTokens(1_000))
        assertEquals("12.4k", formatCompactTokens(12_400))
        assertEquals("1.3k", formatCompactTokens(1_299))
        assertEquals("2.0k", formatCompactTokens(1_999))
        assertEquals("999.9k", formatCompactTokens(999_949))
        assertEquals("1.0M", formatCompactTokens(999_999))
        assertEquals("1.3M", formatCompactTokens(1_250_000))
        assertEquals("2.0M", formatCompactTokens(1_999_999))
    }
}
