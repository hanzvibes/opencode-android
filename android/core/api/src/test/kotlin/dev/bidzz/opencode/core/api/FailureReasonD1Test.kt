package dev.bidzz.opencode.core.api

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Audit-200 D1 (m-664): provider/engine errors must reach the screen as a
 * [FailureReason] the app can word in its own language — not as the
 * engine's raw English message.
 */
class FailureReasonD1Test {

    @Test
    fun `a ServiceUnavailableError tag becomes SERVICE_UNAVAILABLE, not SERVER_MESSAGE`() {
        val failure = ApiResult.Failure(
            code = 503,
            tag = "ServiceUnavailableError",
            message = "provider is down",
        )
        assertEquals(FailureReason.SERVICE_UNAVAILABLE, failure.reason)
    }

    @Test
    fun `an InvalidRequestError tag becomes INVALID_REQUEST, not SERVER_MESSAGE`() {
        val failure = ApiResult.Failure(
            code = 400,
            tag = "InvalidRequestError",
            message = "query too long",
        )
        assertEquals(FailureReason.INVALID_REQUEST, failure.reason)
    }

    @Test
    fun `a decode failure (code -1) becomes DECODE_FAILED, not SERVER_MESSAGE`() {
        val failure = ApiResult.Failure(
            code = -1,
            message = "Unexpected JSON token at offset 12",
        )
        assertEquals(FailureReason.DECODE_FAILED, failure.reason)
    }

    @Test
    fun `an unknown tag with a message still surfaces verbatim`() {
        val failure = ApiResult.Failure(
            code = 500,
            tag = "SomethingElseError",
            message = "weird engine detail",
        )
        assertEquals(FailureReason.SERVER_MESSAGE, failure.reason)
    }
}
