package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.ChatState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D7 (snackbar / banner / toast, part 2): the sticky-banner arms
 * (m-758 stop-timeout, m-981 session-link refusal) in [resolveFailureKey],
 * the same-kind combine (m-801) in [combineSameKindFailure], and the
 * refusal behaviour through the no-engine harness like C13/C15/D6.
 */
class ChatViewModelD7Test {

    private fun viewModel(store: ParkedComposerStore = InMemoryParkedComposerStore()) =
        ChatViewModel(EngineApi { "/tmp" }, { "/tmp" }, parkedStore = store)

    private fun failure(code: Int = 0, message: String = "boom") =
        ApiResult.Failure(code = code, message = message)

    private fun key(
        selectionFailure: ApiResult.Failure? = null,
        guidance: Boolean = false,
        previousKey: Int? = null,
        lastFailure: ApiResult.Failure? = null,
        sessionGone: Boolean = false,
        busy: Boolean = false,
        sessionChanged: Boolean = false,
    ): Int? = resolveFailureKey(
        selection = selectionFailure,
        rateLimitGuidanceShown = guidance,
        previousFailureKey = previousKey,
        lastFailure = lastFailure,
        sessionGone = sessionGone,
        busy = busy,
        sessionChanged = sessionChanged,
    )

    // ---- resolveFailureKey: m-758 stop-timeout ----

    @Test
    fun `m-758 a stop-timeout banner survives a healthy emission while the run is still going`() {
        assertEquals(
            R.string.stop_unconfirmed,
            key(previousKey = R.string.stop_unconfirmed, lastFailure = null, busy = true),
        )
    }

    @Test
    fun `m-758 a stop-timeout banner dies the moment the run ends`() {
        assertNull(
            key(previousKey = R.string.stop_unconfirmed, lastFailure = null, busy = false),
        )
        assertNull(
            key(previousKey = R.string.stop_unconfirmed, lastFailure = failure(), busy = false),
        )
    }

    @Test
    fun `m-758 a stop-timeout banner dies on a session change`() {
        assertNull(
            key(previousKey = R.string.stop_unconfirmed, lastFailure = null, busy = true, sessionChanged = true),
        )
    }

    // ---- resolveFailureKey: m-981 session-link refusal ----

    @Test
    fun `m-981 a refusal survives a healthy emission`() {
        assertEquals(
            R.string.session_link_gone,
            key(previousKey = R.string.session_link_gone, lastFailure = null),
        )
        assertEquals(
            R.string.session_link_verify_failed,
            key(previousKey = R.string.session_link_verify_failed, lastFailure = null),
        )
    }

    @Test
    fun `m-981 a refusal dies on a session change`() {
        assertNull(
            key(previousKey = R.string.session_link_gone, lastFailure = null, sessionChanged = true),
        )
    }

    // ---- resolveFailureKey: the arms it must not break ----

    @Test
    fun `resolveFailureKey keeps the D6 arms intact`() {
        // Selection failure still wins over everything.
        assertNull(key(selectionFailure = failure(), previousKey = R.string.stop_unconfirmed, busy = true))
        // The 429 guidance still wins over a healthy emission.
        assertEquals(
            R.string.chat_rate_limited_guidance,
            key(guidance = true, lastFailure = null),
        )
        // An ordinary healthy emission still clears an ordinary key.
        assertNull(key(previousKey = R.string.error_engine_not_running, lastFailure = null))
        // A session-gone failure still offers its guidance.
        assertEquals(
            R.string.chat_session_gone_guidance,
            key(lastFailure = failure(), sessionGone = true),
        )
        // Any other failure still keeps the previous key (else arm).
        assertEquals(
            R.string.error_engine_not_running,
            key(previousKey = R.string.error_engine_not_running, lastFailure = failure()),
        )
    }

    // ---- combineSameKindFailure: m-801 ----

    @Test
    fun `m-801 a first rejection passes through unchanged`() {
        val first = failure(message = "agent A refused")
        assertEquals(first, combineSameKindFailure(null, first))
    }

    @Test
    fun `m-801 two rejections of the same kind combine into two lines`() {
        val combined = combineSameKindFailure(failure(message = "agent A refused"), failure(message = "agent B refused"))
        assertEquals("agent A refused\nagent B refused", combined.message)
    }

    @Test
    fun `m-801 an identical repeat is not duplicated`() {
        val first = failure(message = "agent A refused")
        assertEquals(first, combineSameKindFailure(first, failure(message = "agent A refused")))
    }

    @Test
    fun `m-801 a third rejection keeps only the newest two lines`() {
        val two = combineSameKindFailure(failure(message = "A refused"), failure(message = "B refused"))
        val three = combineSameKindFailure(two, failure(message = "C refused"))
        assertEquals("B refused\nC refused", three.message)
    }

    @Test
    fun `m-801 an empty new message keeps the old one`() {
        val first = failure(message = "agent A refused")
        assertEquals(first, combineSameKindFailure(first, failure(message = "")))
    }

    // ---- m-981 behaviour through the harness ----

    @Test
    fun `m-981 a refusal banner survives the next healthy repo emission`() {
        val vm = viewModel()
        vm.refuseSessionOpen(R.string.session_link_gone, "gone-id")
        assertEquals(R.string.session_link_gone, vm.state.value.failureKey)
        // A healthy emission (the current session streaming on) used to
        // wipe the refusal via the `lastFailure == null -> null` arm.
        vm.onRepoState(ChatState(lastFailure = null))
        assertEquals(R.string.session_link_gone, vm.state.value.failureKey)
        vm.dismissError()
        assertNull(vm.state.value.failureKey)
        // A later emission must not resurrect it.
        vm.onRepoState(ChatState(lastFailure = null))
        assertNull(vm.state.value.failureKey)
    }

    @Test
    fun `m-981 a deferred open survives the next healthy repo emission too`() {
        val vm = viewModel()
        vm.deferSessionOpen()
        assertEquals(R.string.session_link_verify_failed, vm.state.value.failureKey)
        vm.onRepoState(ChatState(lastFailure = null))
        assertEquals(R.string.session_link_verify_failed, vm.state.value.failureKey)
    }

    @Test
    fun `m-981 a fresh non-429 failure supersedes a refusal only in the rendered failure`() {
        // The refusal key is a permanent fact about the gone id, so it is
        // kept — but the rendered banner is the new failure, not the old
        // refusal (failure wins over failureKey in the banner).
        val vm = viewModel()
        vm.refuseSessionOpen(R.string.session_link_gone, "gone-id")
        vm.onRepoState(ChatState(lastFailure = failure(message = "boom")))
        assertEquals("boom", vm.state.value.failure?.message)
        assertEquals(R.string.session_link_gone, vm.state.value.failureKey)
        assertTrue(vm.state.value.failureKey != null)
    }
}
