package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the engine dies, the session id is zeroed — and the transcript
 * must go with it (audit-100 69-F1). Leaving the messages while the id
 * is null strands the UI: `onResume` refuses to restore the remembered
 * session while messages are non-empty, so the stale transcript blocks
 * the restore forever. The restore re-opens the session and reloads the
 * messages from the engine.
 */
class EngineDeathClearsMessagesTest {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    private fun openSessionWithMessages(): ChatViewModel {
        val vm = viewModel()
        vm.onRepoState(
            ChatState(
                sessionID = "ses-1",
                messages = listOf(
                    SessionMessage(id = "m1", type = "user", text = "hello"),
                    SessionMessage(
                        id = "m2",
                        type = "assistant",
                        content = listOf(MessageContent(type = "text", text = "hi")),
                    ),
                ),
            ),
        )
        return vm
    }

    @Test
    fun `a failed engine clears the session id and the messages`() {
        val vm = openSessionWithMessages()

        vm.onEngineStatus(EngineStatus(EngineState.FAILED))

        assertNull(vm.state.value.sessionID)
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `a stopped engine clears the session id and the messages`() {
        val vm = openSessionWithMessages()

        vm.onEngineStatus(EngineStatus(EngineState.STOPPED))

        assertNull(vm.state.value.sessionID)
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `a running engine leaves the transcript alone`() {
        val vm = openSessionWithMessages()

        vm.onEngineStatus(EngineStatus(EngineState.RUNNING))

        assertEquals("ses-1", vm.state.value.sessionID)
        assertEquals(2, vm.state.value.messages.size)
    }
}
