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
    fun `a NoImageModel tag becomes NO_IMAGE_MODEL, not UNREACHABLE`() {
        // Audit-200 D2 (m-1217): the stale-model branch used to build a
        // bare code-0 failure, so the user read "Cannot reach the engine"
        // while the engine was alive — the real problem was that no image
        // model was selected. The tag must win over the code.
        val failure = ApiResult.Failure(code = 0, tag = "NoImageModel")
        assertEquals(FailureReason.NO_IMAGE_MODEL, failure.reason)
    }

    @Test
    fun `a bare code-0 failure is still UNREACHABLE`() {
        // The control for the test above: the engine-down shape
        // (engineDownFailure()) still routes to UNREACHABLE.
        val failure = ApiResult.Failure(code = 0)
        assertEquals(FailureReason.UNREACHABLE, failure.reason)
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
