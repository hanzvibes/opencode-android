package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.ChatState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 D6 (snackbar / banner / toast): the 429-guidance behaviour
 * (m-312), pinned through the no-engine harness like C13/C15 — the state
 * the banner reads is driven through onRepoState, retryError and
 * dismissError directly. The repository half (clearLastFailure actually
 * clearing the repo's lastFailure) is pinned by the scanner guard in
 * [SelectionFailureD6Test]: the harness view model has no repository.
 */
class ChatViewModelD6Test {

    private fun viewModel(store: ParkedComposerStore = InMemoryParkedComposerStore()) =
        ChatViewModel(EngineApi { "/tmp" }, { "/tmp" }, parkedStore = store)

    private fun showGuidance(vm: ChatViewModel) {
        vm.onRepoState(ChatState(lastFailure = ApiResult.Failure(code = 429)))
        assertEquals(RetryKind.RATE_LIMITED, vm.state.value.retryKind)
        vm.retryError()
        assertEquals(R.string.chat_rate_limited_guidance, vm.state.value.failureKey)
        assertTrue(vm.state.value.rateLimitGuidanceShown)
    }

    @Test
    fun `m-312 direction 1 the 429 guidance survives a resync success`() {
        val vm = viewModel()
        showGuidance(vm)
        // A resync success clears the repo's lastFailure — the guidance
        // used to go with it, because onRepoState nulled the key whenever
        // lastFailure was null.
        vm.onRepoState(ChatState(lastFailure = null))
        assertEquals(R.string.chat_rate_limited_guidance, vm.state.value.failureKey)
        assertNull(vm.state.value.failure)
        assertEquals(RetryKind.NONE, vm.state.value.retryKind)
    }

    @Test
    fun `m-312 direction 2 dismiss clears the guidance flag`() {
        val vm = viewModel()
        showGuidance(vm)
        vm.dismissError()
        assertFalse(vm.state.value.rateLimitGuidanceShown)
        assertNull(vm.state.value.failureKey)
        assertNull(vm.state.value.failure)
        // A later emission must not resurrect the guidance.
        vm.onRepoState(ChatState(lastFailure = null))
        assertNull(vm.state.value.failureKey)
    }

    @Test
    fun `m-312 a fresh failure supersedes the 429 guidance`() {
        val vm = viewModel()
        showGuidance(vm)
        // A new failure arrives (a send that failed): the guidance flag
        // must clear, or the next emission would bring the stale
        // guidance back over the live session.
        vm.onRepoState(ChatState(lastFailure = ApiResult.Failure(code = 0, message = "boom")))
        assertFalse(vm.state.value.rateLimitGuidanceShown)
        vm.onRepoState(ChatState(lastFailure = null))
        assertNull(vm.state.value.failureKey)
    }
}
