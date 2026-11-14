package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.SessionMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pre-resend check (audit-200 C10, M-153): a prompt whose POST
 * failed ambiguously is only re-sent when the transcript proves it never
 * landed. The view-model wiring (fetch, refresh, re-send) is not
 * drivable on JVM — the harness has no engine — so the decision itself
 * is the tested seam.
 */
class RetryLastSendTest {

    private fun user(id: String, text: String) = SessionMessage(id = id, type = "user", text = text)
    private fun assistant(id: String, text: String) =
        SessionMessage(id = id, type = "assistant", text = text)

    @Test
    fun `M-153 a same-text message with a different id means the prompt landed`() {
        val messages = listOf(user("m-old", "hello"), user("m-new", "hello again"))
        assertTrue(promptAlreadyLanded(messages, "hello again", baseUserMessageId = "m-old"))
    }

    @Test
    fun `M-153 the base message itself does not count as landed`() {
        // The identical prompt sent twice in a row: the earlier prompt's
        // message has the base id, so it must not count — only a genuinely
        // new message with this text does.
        val messages = listOf(user("m-old", "hello"))
        assertFalse(promptAlreadyLanded(messages, "hello", baseUserMessageId = "m-old"))
    }

    @Test
    fun `M-153 a different text does not count`() {
        val messages = listOf(user("m-new", "something else"))
        assertFalse(promptAlreadyLanded(messages, "hello", baseUserMessageId = "m-old"))
    }

    @Test
    fun `M-153 an assistant message with the same text does not count`() {
        val messages = listOf(assistant("m-a", "hello"))
        assertFalse(promptAlreadyLanded(messages, "hello", baseUserMessageId = null))
    }

    @Test
    fun `M-153 an empty transcript means nothing landed`() {
        assertFalse(promptAlreadyLanded(emptyList(), "hello", baseUserMessageId = "m-old"))
    }

    @Test
    fun `M-153 with no base id any same-text user message counts`() {
        val messages = listOf(user("m-only", "hello"))
        assertTrue(promptAlreadyLanded(messages, "hello", baseUserMessageId = null))
    }
}

/**
 * The revert repair path (audit-200 C10, M-154): a Reject whose prompt
 * definitively did not run must offer Reject again instead of claiming
 * "Revert requested" forever.
 */
class RevertDecisionClearTest {

    @Test
    fun `M-154 a direct send that failed non-transiently clears the decision`() {
        assertTrue(shouldClearRevertDecision(isTransientFailure = false, wasFlushed = false))
    }

    @Test
    fun `M-154 a send that never went out clears the decision`() {
        // No failure occurred at all (session changed, no session): the
        // prompt never ran, so there is nothing outstanding.
        assertTrue(shouldClearRevertDecision(isTransientFailure = false, wasFlushed = false))
    }

    @Test
    fun `M-154 a transient failure keeps the decision`() {
        // The prompt may have landed (see M-153); the retry path owns it.
        assertFalse(shouldClearRevertDecision(isTransientFailure = true, wasFlushed = false))
    }

    @Test
    fun `M-154 a flushed entry keeps the decision`() {
        // It stays queued for the next run end, so the request stands.
        assertFalse(shouldClearRevertDecision(isTransientFailure = false, wasFlushed = true))
        assertFalse(shouldClearRevertDecision(isTransientFailure = true, wasFlushed = true))
    }
}
