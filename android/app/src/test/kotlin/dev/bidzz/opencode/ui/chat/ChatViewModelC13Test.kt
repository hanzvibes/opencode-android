package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.ChatState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dismissing the error banner keeps the retry (audit-200 C13, M-191):
 * the banner was the only caller of the retry machinery — `dismissError()`
 * cleared `retryKind` along with the failure, so swiping the banner away
 * also removed the Retry pill, and a user who wanted to retry had to
 * re-send by hand.
 */
class ChatViewModelC13Test {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    @Test
    fun `dismissing the error keeps the retry kind`() {
        val vm = viewModel()
        // A 429: retryKindFor maps it to RATE_LIMITED with no send needed.
        vm.onRepoState(ChatState(lastFailure = ApiResult.Failure(code = 429)))

        assertNotNull(vm.state.value.failure)
        assertEquals(RetryKind.RATE_LIMITED, vm.state.value.retryKind)

        vm.dismissError()

        assertNull(vm.state.value.failure)
        assertNull(vm.state.value.failureKey)
        assertEquals(
            "the banner is gone but the retry offer survives",
            RetryKind.RATE_LIMITED,
            vm.state.value.retryKind,
        )
    }

    @Test
    fun `a failure with no retry kind stays that way after dismiss`() {
        val vm = viewModel()
        // A 403 has no retry: RESEND would fail the same way, so the
        // kind is NONE — dismiss must not invent one.
        vm.onRepoState(ChatState(lastFailure = ApiResult.Failure(code = 403)))

        assertEquals(RetryKind.NONE, vm.state.value.retryKind)

        vm.dismissError()

        assertNull(vm.state.value.failure)
        assertEquals(RetryKind.NONE, vm.state.value.retryKind)
    }
}
