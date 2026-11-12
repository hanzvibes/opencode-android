package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.dto.SessionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The optimistic send bubble (#6a): a send answers at once, and the
 * engine's echo hands over to it — never duplicating it.
 *
 * The view model is driven directly through its internal [ChatViewModel.onRepoState]
 * with synthetic [ChatState]s: the repository itself needs a live engine,
 * but the dedup rule lives entirely in the fold, and that is what these
 * tests pin down. No engine is attached here, so no send is ever issued —
 * [ChatViewModel.stageOptimistic] stages the bubble a send would stage.
 */
class ChatViewModelTest {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    private fun user(id: String, text: String) = SessionMessage(id = id, type = "user", text = text)

    private fun repoState(messages: List<SessionMessage>, busy: Boolean = false) =
        ChatState(messages = messages, busy = busy)

    @Test
    fun `staging an optimistic bubble appends it and sets sending`() {
        val vm = viewModel()

        vm.stageOptimistic("halo")

        val messages = vm.state.value.messages
        assertEquals(1, messages.size)
        assertEquals(Role.USER, messages.single().role)
        assertEquals("halo", messages.single().text)
        assertTrue(messages.single().id.startsWith("local:"))
        assertTrue(vm.state.value.sending)
    }

    @Test
    fun `the echo replaces the optimistic bubble without duplicating`() {
        val vm = viewModel()
        vm.stageOptimistic("halo")

        vm.onRepoState(repoState(listOf(user("m1", "halo")), busy = true))

        val messages = vm.state.value.messages
        assertEquals(1, messages.size)
        assertEquals("m1", messages.single().id)
        assertEquals("halo", messages.single().text)
        assertFalse(vm.state.value.sending)
    }

    @Test
    fun `an older message with the same text is not the echo`() {
        val vm = viewModel()
        // The transcript already holds an earlier "halo".
        vm.onRepoState(repoState(listOf(user("old", "halo"))))
        // The user sends "halo" again.
        vm.stageOptimistic("halo")

        // The repo still shows only the old message: the bubble stays.
        vm.onRepoState(repoState(listOf(user("old", "halo")), busy = true))
        val pending = vm.state.value.messages
        assertEquals(2, pending.size)
        assertEquals("old", pending[0].id)
        assertTrue(pending[1].id.startsWith("local:"))
        assertTrue(vm.state.value.sending)

        // The real echo arrives under a fresh id: one old, one new, no local.
        vm.onRepoState(repoState(listOf(user("old", "halo"), user("m2", "halo")), busy = true))
        val echoed = vm.state.value.messages
        assertEquals(2, echoed.size)
        assertEquals(listOf("old", "m2"), echoed.map { it.id })
        assertFalse(vm.state.value.sending)
    }

    @Test
    fun `the bubble drops when the run ends without an echo`() {
        val vm = viewModel()
        vm.stageOptimistic("halo")

        vm.onRepoState(repoState(emptyList(), busy = true))
        assertEquals(1, vm.state.value.messages.size)
        assertTrue(vm.state.value.sending)

        vm.onRepoState(repoState(emptyList(), busy = false))
        assertEquals(emptyList<ChatMessage>(), vm.state.value.messages)
        assertFalse(vm.state.value.sending)
    }

    @Test
    fun `a second send replaces a lingering optimistic bubble`() {
        val vm = viewModel()
        vm.stageOptimistic("satu")
        vm.stageOptimistic("dua")

        val messages = vm.state.value.messages
        assertEquals(1, messages.size)
        assertEquals("dua", messages.single().text)
        assertTrue(vm.state.value.sending)
    }

    @Test
    fun `a bubble staged for another session never lands in this one`() {
        val vm = viewModel()
        // The bubble was staged while no session was open (null session);
        // the repo then reports a session the bubble was not sent to —
        // but the staged session is null (still being created), so the
        // bubble is kept: the new session IS the send's session.
        vm.stageOptimistic("halo")
        vm.onRepoState(ChatState(sessionID = "s1", messages = emptyList(), busy = true))
        assertEquals(1, vm.state.value.messages.size)
        assertTrue(vm.state.value.sending)
    }
}
