package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D17 (m-315): the free-tier rate limit is matched on the
 * engine's error TYPE when the view model preserved it, and on a word
 * boundary for the code in the text — "4299" or "14290" inside a larger
 * number is not a rate limit.
 */
class RateLimitErrorD17Test {

    @Test
    fun `a code embedded in a larger number is not a rate limit`() {
        assertFalse(isRateLimitError("error 4299 while saving"))
        assertFalse(isRateLimitError("14290 tokens used"))
        assertFalse(isRateLimitError("4299"))
    }

    @Test
    fun `a standalone 429 code is a rate limit`() {
        assertTrue(isRateLimitError("HTTP 429 Too Many Requests"))
        assertTrue(isRateLimitError("429"))
        assertTrue(isRateLimitError("failed with 429, try again later"))
    }

    @Test
    fun `the free-limit phrases still match`() {
        assertTrue(isRateLimitError("provider.free-limit exceeded"))
        assertTrue(isRateLimitError("free limit reached"))
        assertTrue(isRateLimitError("rate limit exceeded"))
        assertTrue(isRateLimitError("rate_limit hit"))
    }

    @Test
    fun `the preserved error type wins over the text`() {
        assertTrue(isRateLimitError("something went wrong", "provider.free-limit"))
        assertFalse(isRateLimitError("error 4299 while saving", "provider.other"))
        assertFalse(isRateLimitError("plain failure", "provider.other"))
        assertFalse(isRateLimitError("plain failure", null))
    }
}
