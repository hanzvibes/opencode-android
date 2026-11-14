package dev.bidzz.opencode.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The 429 path (audit-200 C10, M-152): the server's `Retry-After` is
 * captured instead of dropped, and a 429 is a rate limit in the app's
 * language instead of a raw English server message.
 */
class RateLimitTest {

    @Test
    fun `M-152 a delay in seconds parses as-is`() {
        assertEquals(120, parseRetryAfter("120"))
        assertEquals(0, parseRetryAfter("0"))
    }

    @Test
    fun `M-152 a negative delay waits zero seconds, not a negative one`() {
        assertEquals(0, parseRetryAfter("-5"))
    }

    @Test
    fun `M-152 an HTTP-date parses as the seconds until then`() {
        // Wed, 21 Oct 2015 07:28:00 GMT is 1445412480000 ms.
        val at = 1445412480000L
        assertEquals(90, parseRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT", nowMillis = at - 90_000))
        assertEquals(0, parseRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT", nowMillis = at + 1_000))
    }

    @Test
    fun `M-152 absent or unparseable Retry-After is null`() {
        assertNull(parseRetryAfter(null))
        assertNull(parseRetryAfter(""))
        assertNull(parseRetryAfter("  "))
        assertNull(parseRetryAfter("soon"))
        assertNull(parseRetryAfter("Wed, 21 Oct 2015 25:28:00 GMT"))
    }

    @Test
    fun `M-152 a 429 is a rate limit, even with a server message`() {
        assertEquals(
            FailureReason.RATE_LIMITED,
            ApiResult.Failure(code = 429, message = "slow down").reason,
        )
        assertEquals(
            FailureReason.RATE_LIMITED,
            ApiResult.Failure(code = 429, retryAfterSeconds = 30).reason,
        )
    }
}
