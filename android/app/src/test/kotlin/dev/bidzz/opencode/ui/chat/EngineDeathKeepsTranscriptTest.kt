package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When the engine dies, the session id is zeroed — but the transcript
 * stays readable (audit-200 B5, M-190/M-231). It used to be wiped here
 * (audit-100 69-F1) because `onResume` refused to restore while
 * messages were non-empty; that guard is now "a session is already
 * open" (sessionID != null), so the kept transcript no longer blocks
 * the restore. While the engine is dead the answers stay readable, and
 * the restore re-opens the session and reloads the messages when the
 * engine is back.
 */
class EngineDeathKeepsTranscriptTest {

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
    fun `a failed engine clears the session id but keeps the transcript`() {
        val vm = openSessionWithMessages()

        vm.onEngineStatus(EngineStatus(EngineState.FAILED))

        assertNull(vm.state.value.sessionID)
        assertEquals(2, vm.state.value.messages.size)
        assertEquals("hello", vm.state.value.messages[0].text)
    }

    @Test
    fun `a stopped engine clears the session id but keeps the transcript`() {
        val vm = openSessionWithMessages()
        // The death branch only runs on a state *change*: the view model
        // starts STOPPED, so a bare STOPPED is a no-op. Drive it through
        // RUNNING first — the realistic sequence (the engine was up).
        vm.onEngineStatus(EngineStatus(EngineState.RUNNING))

        vm.onEngineStatus(EngineStatus(EngineState.STOPPED))

        assertNull(vm.state.value.sessionID)
        assertEquals(2, vm.state.value.messages.size)
    }

    @Test
    fun `a running engine leaves the transcript alone`() {
        val vm = openSessionWithMessages()

        vm.onEngineStatus(EngineStatus(EngineState.RUNNING))

        assertEquals("ses-1", vm.state.value.sessionID)
        assertEquals(2, vm.state.value.messages.size)
    }
}
