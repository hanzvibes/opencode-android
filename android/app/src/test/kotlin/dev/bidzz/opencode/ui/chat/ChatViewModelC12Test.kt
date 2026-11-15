package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.SessionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The transcript merge runs once per changed input (audit-200 C12, M-016):
 * `mergeContiguousAssistant` used to re-join the whole transcript's text on
 * every repository emission, including the 100 ms publish ticks where
 * nothing but the clock moved.
 *
 * The view model is driven directly through its internal [ChatViewModel.onRepoState]
 * with synthetic [ChatState]s, like [ChatViewModelTest]: the repository itself
 * needs a live engine, but the caching rule lives entirely in the fold.
 */
class ChatViewModelC12Test {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    private fun user(id: String, text: String) = SessionMessage(id = id, type = "user", text = text)

    private fun assistant(id: String, text: String) = SessionMessage(
        id = id,
        type = "assistant",
        content = listOf(MessageContent(type = "text", text = text)),
    )

    private fun repoState(messages: List<SessionMessage>, busy: Boolean = false) =
        ChatState(messages = messages, busy = busy)

    @Test
    fun `a repeated identical emission reuses the merged transcript`() {
        val vm = viewModel()
        val state = repoState(listOf(user("m1", "halo"), assistant("m2", "hai")))

        vm.onRepoState(state)
        val first = vm.state.value.messages
        assertEquals(2, first.size)

        vm.onRepoState(state)
        val second = vm.state.value.messages

        // Same input, same merged list — no re-merge, no re-allocation.
        assertSame(first, second)
    }

    @Test
    fun `growing streaming text rebuilds the merged transcript`() {
        val vm = viewModel()
        val messages = listOf(user("m1", "halo"))
        val firstEmission = ChatState(messages = messages, busy = true, streaming = mapOf("m2" to "ha"))
        val secondEmission = ChatState(messages = messages, busy = true, streaming = mapOf("m2" to "halo world"))

        vm.onRepoState(firstEmission)
        val first = vm.state.value.messages
        assertEquals(2, first.size)
        assertEquals("ha", first[1].text)

        vm.onRepoState(secondEmission)
        val second = vm.state.value.messages

        // The overlay input moved, so the merge ran again — with the new text.
        assertNotSame(first, second)
        assertEquals(2, second.size)
        assertEquals("halo world", second[1].text)
    }
}
