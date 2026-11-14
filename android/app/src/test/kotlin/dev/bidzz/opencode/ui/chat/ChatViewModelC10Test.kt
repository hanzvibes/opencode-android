package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit-200 Fase C10: M-155 (no repair path when the engine is down)
 * and M-156 (a startup failure is swallowed). Same no-engine harness
 * as C3/C5: the view model runs without a repository, so the tests pin
 * down the state the banner and the status dialog read.
 */
class ChatViewModelC10Test {

    private fun viewModel() =
        ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    @Test
    fun `M-155 a failed engine offers a restart`() {
        val vm = viewModel()
        vm.onEngineStatus(EngineStatus(EngineState.FAILED, lastError = "engine exited (code 1)"))
        val state = vm.state.value
        assertEquals(RetryKind.RESTART_ENGINE, state.retryKind)
        assertEquals(R.string.error_engine_not_running, state.failureKey)
        assertEquals("engine exited (code 1)", state.lastError)
    }

    @Test
    fun `M-156 a stopped engine with a reason names it in the banner`() {
        val vm = viewModel()
        // The initial state is already STOPPED, so the branch only runs
        // on a change: go RUNNING first, then stop with a reason.
        vm.onEngineStatus(EngineStatus(EngineState.RUNNING))
        vm.onEngineStatus(EngineStatus(EngineState.STOPPED, lastError = "binary missing"))
        val state = vm.state.value
        assertEquals(RetryKind.RESTART_ENGINE, state.retryKind)
        assertEquals("binary missing", state.failure?.message)
        assertNull(state.failureKey)
        assertEquals("binary missing", state.lastError)
    }

    @Test
    fun `M-155 a stopped engine without a reason still offers a restart`() {
        val vm = viewModel()
        vm.onEngineStatus(EngineStatus(EngineState.RUNNING))
        vm.onEngineStatus(EngineStatus(EngineState.STOPPED))
        val state = vm.state.value
        assertEquals(RetryKind.RESTART_ENGINE, state.retryKind)
        assertNull(state.failure)
        assertNull(state.failureKey)
        assertNull(state.lastError)
    }

    @Test
    fun `M-155 the old reason clears once the engine is back`() {
        val vm = viewModel()
        vm.onEngineStatus(EngineStatus(EngineState.FAILED, lastError = "engine exited (code 1)"))
        assertEquals("engine exited (code 1)", vm.state.value.lastError)
        vm.onEngineStatus(EngineStatus(EngineState.RUNNING))
        assertNull(vm.state.value.lastError)
    }

    @Test
    fun `M-155 retryError leaves a restart-engine failure to the screen`() {
        // Like REAUTH: the view model does nothing; the screen owns the
        // restart call, so the state must survive retryError untouched.
        val vm = viewModel()
        vm.onEngineStatus(EngineStatus(EngineState.FAILED))
        vm.retryError()
        assertEquals(RetryKind.RESTART_ENGINE, vm.state.value.retryKind)
        assertEquals(R.string.error_engine_not_running, vm.state.value.failureKey)
    }
}
