package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C4: the edit family (M-077/M-078/M-079).
 *
 * No engine is attached here, so no send is ever issued — the tests pin
 * down the composer-side behaviour: what startEdit loads, what Cancel
 * restores, and what a busy submit queues. The rewrite itself
 * (submitEditAsRewrite: stage + commit + send) needs a session handle
 * and is covered by inspection + the repo-level interrupt test, not
 * by this harness.
 */
class ChatViewModelC4Test {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    private fun file(name: String, content: String) =
        AttachedFile(id = "id-$name", name = name, content = content)

    @Test
    fun `M-079 startEdit loads the typed text, not the file dump`() {
        val vm = viewModel()
        vm.onDraftChange("pre-edit draft")
        vm.startEdit("m1", "fix this\n\n```notes.md\nFILE\n```")
        assertEquals("fix this", vm.state.value.draft)
        assertEquals(
            listOf("notes.md"),
            vm.state.value.pendingAttachments.map { it.name },
        )
    }

    @Test
    fun `M-078 startEdit snapshots the pre-edit files and cancel restores them`() {
        val vm = viewModel()
        vm.onDraftChange("pre-edit draft")
        vm.addAttachment(file("draft.txt", "DRAFT"))
        vm.startEdit("m1", "fix this\n\n```notes.md\nFILE\n```")
        // The message's file joins the chips; the draft's file is still
        // there too — it only leaves if the edit is submitted.
        assertEquals(
            listOf("draft.txt", "notes.md"),
            vm.state.value.pendingAttachments.map { it.name },
        )
        vm.cancelEdit()
        assertEquals("pre-edit draft", vm.state.value.draft)
        assertEquals(
            listOf("draft.txt"),
            vm.state.value.pendingAttachments.map { it.name },
        )
        assertNull(vm.state.value.editingMessageId)
    }

    @Test
    fun `M-078 a file picked during the edit leaves with Cancel`() {
        val vm = viewModel()
        vm.onDraftChange("pre-edit draft")
        vm.addAttachment(file("draft.txt", "DRAFT"))
        vm.startEdit("m1", "fix this")
        vm.addAttachment(file("edit.txt", "EDIT"))
        assertEquals(
            listOf("draft.txt", "edit.txt"),
            vm.state.value.pendingAttachments.map { it.name },
        )
        vm.cancelEdit()
        assertEquals(
            listOf("draft.txt"),
            vm.state.value.pendingAttachments.map { it.name },
        )
    }

    @Test
    fun `M-078 a busy submit queues only the edit's files`() {
        val vm = viewModel()
        vm.onDraftChange("pre-edit draft")
        vm.addAttachment(file("draft.txt", "DRAFT"))
        vm.startEdit("m1", "fix this\n\n```notes.md\nFILE\n```")
        vm.addAttachment(file("edit.txt", "EDIT"))
        vm.onRepoState(ChatState(busy = true))
        vm.onDraftChange("fix this for real")
        vm.submitEdit()
        val queued = vm.state.value.queuedPrompts
        assertEquals(1, queued.size)
        val full = queued.single().fullText
        assertTrue("edit text queued", "fix this for real" in full)
        assertTrue("message file queued", "FILE" in full)
        assertTrue("edit-time file queued", "EDIT" in full)
        assertTrue("pre-edit file not queued", "DRAFT" !in full)
        assertTrue("pre-edit file not left pending", vm.state.value.pendingAttachments.isEmpty())
        assertNull(vm.state.value.editingMessageId)
    }

    @Test
    fun `M-077 an idle submit without an engine falls back to a plain send`() {
        val vm = viewModel()
        vm.onDraftChange("pre-edit draft")
        vm.startEdit("m1", "fix this")
        vm.onDraftChange("fix this for real")
        vm.submitEdit()
        // No session handle, so there is nothing to rewrite: the edit
        // closes and the send fails the usual way (no engine running),
        // instead of hanging in edit mode.
        assertNull(vm.state.value.editingMessageId)
        assertEquals(R.string.error_engine_not_running, vm.state.value.failureKey)
    }

    @Test
    fun `M-077 submitEdit with an empty draft cancels the edit`() {
        val vm = viewModel()
        vm.onDraftChange("pre-edit draft")
        vm.addAttachment(file("draft.txt", "DRAFT"))
        vm.startEdit("m1", "fix this")
        vm.onDraftChange("   ")
        vm.submitEdit()
        assertNull(vm.state.value.editingMessageId)
        assertEquals("pre-edit draft", vm.state.value.draft)
        assertEquals(
            listOf("draft.txt"),
            vm.state.value.pendingAttachments.map { it.name },
        )
    }
}
