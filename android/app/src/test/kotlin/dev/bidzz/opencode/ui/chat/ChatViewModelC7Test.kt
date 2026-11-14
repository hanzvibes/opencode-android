package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.dto.ModelRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit-200 Fase C7: the busy guard on model/agent picks (M-116).
 * The switches themselves need an engine (the POST, the mutex wait,
 * the re-read), so only the guard is pinned here: a pick tapped while
 * a run is going must not move the chip, and an idle pick with no
 * session yet still applies optimistically for createSession.
 *
 * Same no-engine harness as C3/C5: the view model runs without a
 * repository, so no send is ever issued.
 */
class ChatViewModelC7Test {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    private val model = ModelRef(id = "claude-sonnet-4", providerID = "anthropic")

    @Test
    fun `M-116 a model pick while busy does not move the chip`() {
        val vm = viewModel()
        vm.onRepoState(ChatState(busy = true))
        vm.selectModel(model)
        assertNull(vm.state.value.activeModel)
    }

    @Test
    fun `M-116 an idle model pick applies with no session yet`() {
        val vm = viewModel()
        vm.onRepoState(ChatState(busy = false))
        vm.selectModel(model)
        assertEquals(model, vm.state.value.activeModel)
    }

    @Test
    fun `M-116 an agent pick while busy does not move the chip`() {
        val vm = viewModel()
        vm.onRepoState(ChatState(busy = true))
        vm.selectAgent("plan")
        assertNull(vm.state.value.activeAgent)
    }

    @Test
    fun `M-116 an idle agent pick applies with no session yet`() {
        val vm = viewModel()
        vm.onRepoState(ChatState(busy = false))
        vm.selectAgent("plan")
        assertEquals("plan", vm.state.value.activeAgent)
    }
}
