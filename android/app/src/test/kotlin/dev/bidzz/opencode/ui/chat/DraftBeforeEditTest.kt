package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fase 6 batch 6c (draftBeforeEdit): switching sessions must not leave
 * the pre-edit draft behind — a later Cancel would restore the old
 * session's draft into the new session's composer.
 */
class DraftBeforeEditTest {

    @Test
    fun `draftBeforeEdit a new session clears the pre-edit backup`() {
        val vm = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })
        vm.onDraftChange("hello")
        vm.startEdit("m1", "edited")

        vm.newSession()
        // The edited text is parked as the new session's draft; the
        // pre-edit backup must be gone, so a stray Cancel cannot bring
        // "hello" back into this session.
        assertEquals("edited", vm.state.value.draft)
        assertNull(vm.state.value.editingMessageId)

        vm.cancelEdit()
        assertEquals("edited", vm.state.value.draft)
    }
}
