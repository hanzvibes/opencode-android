package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6f (15-F4): the SAF result handler the view model applies
 * when a picked file finishes reading. The collection itself lives in
 * [ChatViewModel.observeSafPicks] (viewModelScope, so it cannot be driven
 * in a JVM unit test); the handler is pure state work and is tested here.
 */
class SafReadResultTest {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    @Test
    fun `a successful read attaches the file`() {
        val vm = viewModel()
        vm.handleSafReadResult(
            SafReadResult.Success(AttachedFile(id = "1", name = "notes.md", content = "hi")),
        )
        val attachments = vm.state.value.pendingAttachments
        assertEquals(1, attachments.size)
        assertEquals("notes.md", attachments.single().name)
        assertNull(vm.state.value.attachNoticeKey)
    }

    @Test
    fun `a too-large file shows the too-large refusal`() {
        val vm = viewModel()
        vm.handleSafReadResult(SafReadResult.TooLarge("big.log"))
        assertTrue(vm.state.value.pendingAttachments.isEmpty())
        assertEquals(R.string.attach_file_too_large, vm.state.value.attachNoticeKey)
        assertEquals("big.log", vm.state.value.attachNoticeArg)
    }

    @Test
    fun `a binary file shows the not-text refusal`() {
        val vm = viewModel()
        vm.handleSafReadResult(SafReadResult.NotText("photo.png"))
        assertEquals(R.string.attach_not_text_file, vm.state.value.attachNoticeKey)
        assertEquals("photo.png", vm.state.value.attachNoticeArg)
    }

    @Test
    fun `an unreadable file shows the unreadable refusal`() {
        val vm = viewModel()
        vm.handleSafReadResult(SafReadResult.Unreadable("gone.txt"))
        assertEquals(R.string.attach_unreadable_file, vm.state.value.attachNoticeKey)
        assertEquals("gone.txt", vm.state.value.attachNoticeArg)
    }

    @Test
    fun `a successful read clears a previous refusal`() {
        val vm = viewModel()
        vm.handleSafReadResult(SafReadResult.TooLarge("big.log"))
        vm.handleSafReadResult(
            SafReadResult.Success(AttachedFile(id = "2", name = "ok.md", content = "x")),
        )
        assertNull(vm.state.value.attachNoticeKey)
        assertNull(vm.state.value.attachNoticeArg)
    }
}
