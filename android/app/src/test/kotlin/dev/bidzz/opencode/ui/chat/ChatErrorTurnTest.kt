package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.ToolError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6c: a failed run must not look complete (01-F4), a failed
 * resync must not leave a ghost streaming bubble (03-F5), and a failed
 * resync must not offer to re-send the finished prompt (03-F2).
 *
 * The view model is driven through [ChatViewModel.onRepoState] with no
 * engine, following the ChatPendingOpenTest pattern.
 */
class ChatErrorTurnTest {

    private fun viewModel() =
        ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    private fun assistant(
        id: String,
        text: String?,
        error: ToolError? = null,
    ) = SessionMessage(
        id = id,
        type = "assistant",
        content = if (text == null) emptyList() else listOf(MessageContent(type = "text", text = text)),
        error = error,
    )

    @Test
    fun `01-F4 an assistant turn with partial content and an error keeps the text and names the error`() {
        val vm = viewModel()
        val failed = ToolError(type = "ProviderError", message = "Provider overloaded")

        vm.onRepoState(ChatState(messages = listOf(assistant("m1", "Partial ", failed))))

        val messages = vm.state.value.messages
        assertEquals(1, messages.size)
        assertEquals(Role.ASSISTANT, messages[0].role)
        assertEquals("Partial ", messages[0].text)
        assertEquals("Provider overloaded", messages[0].errorText)
    }

    @Test
    fun `01-F4 an assistant turn with no content and an error is still an error bubble`() {
        val vm = viewModel()
        val failed = ToolError(type = "ProviderError", message = "Provider overloaded")

        vm.onRepoState(ChatState(messages = listOf(assistant("m1", null, failed))))

        val messages = vm.state.value.messages
        assertEquals(1, messages.size)
        assertEquals(Role.ERROR, messages[0].role)
        assertEquals("Provider overloaded", messages[0].text)
        assertNull(messages[0].errorText)
    }

    @Test
    fun `01-F4 the error survives merging contiguous assistant chunks`() {
        val vm = viewModel()
        val failed = ToolError(type = "ProviderError", message = "Provider overloaded")

        vm.onRepoState(
            ChatState(
                messages = listOf(
                    assistant("m1", "Part one "),
                    assistant("m2", "Part two", failed),
                ),
            ),
        )

        val messages = vm.state.value.messages
        assertEquals(1, messages.size)
        assertEquals(Role.ASSISTANT, messages[0].role)
        assertEquals("Provider overloaded", messages[0].errorText)
    }

    @Test
    fun `03-F5 a streaming-only bubble vanishes once its resync failed and cleared the map`() {
        val vm = viewModel()
        val history = emptyList<SessionMessage>()

        vm.onRepoState(ChatState(messages = history, streaming = mapOf("m1" to "partial")))
        assertTrue(vm.state.value.messages.any { it.id == "m1" })

        // Same history reference, streaming map now empty: the incremental
        // path runs, and the ghost must go with it.
        vm.onRepoState(ChatState(messages = history, streaming = emptyMap()))
        assertTrue(vm.state.value.messages.none { it.id == "m1" })
    }

    @Test
    fun `03-F2 a failed resync offers reload-session, never resend`() {
        val vm = viewModel()

        vm.onRepoState(
            ChatState(
                lastFailure = ApiResult.Failure(code = 500, message = "boom"),
                resyncFailed = true,
            ),
        )

        assertEquals(RetryKind.RELOAD_SESSION, vm.state.value.retryKind)
    }

    @Test
    fun `03-F2 a non-resync failure without a sent prompt offers no retry`() {
        val vm = viewModel()

        vm.onRepoState(
            ChatState(
                lastFailure = ApiResult.Failure(code = 500, message = "boom"),
                resyncFailed = false,
            ),
        )

        assertEquals(RetryKind.NONE, vm.state.value.retryKind)
    }
}
