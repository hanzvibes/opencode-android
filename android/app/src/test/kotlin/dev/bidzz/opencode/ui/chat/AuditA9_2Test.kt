package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase A9-2 (DL-017, DL-018): a file or workspace path dropped
 * into the composer belongs to the session it was dropped into, even
 * when the user leaves the screen or switches sessions before the read
 * finishes.
 *
 * The [viewModelScope] read itself ([ChatViewModel.handleDroppedFiles])
 * cannot be driven in a JVM unit test — same note as
 * [ChatViewModel.observeSafPicks] (15-F4) — so the landing is tested
 * directly through [ChatViewModel.handleDroppedFileResult] and
 * [ChatViewModel.handleDroppedPaths], which are pure state work.
 */
class AuditA9_2Test {

    private fun viewModel(store: ParkedComposerStore = InMemoryParkedComposerStore()) =
        ChatViewModel(EngineApi { "/tmp" }, { "/tmp" }, parkedStore = store)

    private fun repoState(sessionID: String?) = ChatState(sessionID = sessionID)

    private fun file(id: String = "1", name: String = "notes.md", content: String = "hi") =
        AttachedFile(id = id, name = name, content = content)

    // Dropped files -------------------------------------------------------

    @Test
    fun `a dropped file in the live session attaches`() {
        val vm = viewModel()
        vm.onRepoState(repoState("A"))

        vm.handleDroppedFileResult(SafReadResult.Success(file()), "A")

        assertEquals(listOf(file()), vm.state.value.pendingAttachments)
        assertNull(vm.state.value.attachNoticeKey)
    }

    @Test
    fun `a dropped file parks under its session after a switch`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        vm.onRepoState(repoState("A"))
        vm.onRepoState(repoState("B"))

        vm.handleDroppedFileResult(SafReadResult.Success(file()), "A")

        assertTrue(vm.state.value.pendingAttachments.isEmpty())
        assertEquals(listOf(file()), store.get("A")?.attachments)
        assertEquals("", store.get("A")?.draft)
    }

    @Test
    fun `a parked drop merges with what the session already had parked`() {
        val store = InMemoryParkedComposerStore()
        val queued = QueuedPrompt("tunggu", "tunggu")
        store.put("A", ParkedComposer("draft A", listOf(file("0", "old.md", "old")), listOf(queued)))
        val vm = viewModel(store)
        vm.onRepoState(repoState("B"))

        vm.handleDroppedFileResult(SafReadResult.Success(file()), "A")

        val parked = store.get("A")
        assertEquals("draft A", parked?.draft)
        assertEquals(listOf("old.md", "notes.md"), parked?.attachments?.map { it.name })
        assertEquals(listOf(queued), parked?.queued)
    }

    @Test
    fun `a duplicate dropped file is not parked twice`() {
        val store = InMemoryParkedComposerStore()
        store.put("A", ParkedComposer("", listOf(file()), emptyList()))
        val vm = viewModel(store)
        vm.onRepoState(repoState("B"))

        vm.handleDroppedFileResult(SafReadResult.Success(file()), "A")

        assertEquals(1, store.get("A")?.attachments?.size)
    }

    @Test
    fun `a refusal after a switch is shown where the user is now`() {
        val vm = viewModel()
        vm.onRepoState(repoState("A"))
        vm.onRepoState(repoState("B"))

        vm.handleDroppedFileResult(SafReadResult.TooLarge("big.log"), "A")

        assertEquals(R.string.attach_file_too_large, vm.state.value.attachNoticeKey)
        assertEquals("big.log", vm.state.value.attachNoticeArg)
    }

    @Test
    fun `a dropped file in a fresh chat attaches live`() {
        val vm = viewModel()

        vm.handleDroppedFileResult(SafReadResult.Success(file()), UNSENT_SESSION_KEY)

        assertEquals(listOf(file()), vm.state.value.pendingAttachments)
    }

    // Dropped workspace paths ---------------------------------------------

    @Test
    fun `a dropped path mentions into the live draft`() {
        val vm = viewModel()
        vm.onRepoState(repoState("A"))
        vm.onDraftChange("lihat ")

        vm.handleDroppedPaths(listOf("src/a.md"), "A")

        assertEquals("lihat @src/a.md ", vm.state.value.draft)
    }

    @Test
    fun `a dropped path parks into the dropped session's draft after a switch`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        vm.onRepoState(repoState("A"))
        vm.onDraftChange("live text")
        vm.onRepoState(repoState("B"))

        vm.handleDroppedPaths(listOf("src/a.md"), "A")

        assertEquals("live text", vm.state.value.draft)
        assertEquals("@src/a.md ", store.get("A")?.draft)
    }

    @Test
    fun `a parked path keeps the session's attachments and queue`() {
        val store = InMemoryParkedComposerStore()
        val queued = QueuedPrompt("tunggu", "tunggu")
        store.put("A", ParkedComposer("draft A", listOf(file()), listOf(queued)))
        val vm = viewModel(store)
        vm.onRepoState(repoState("B"))

        vm.handleDroppedPaths(listOf("src/a.md", "src/b.md"), "A")

        val parked = store.get("A")
        assertEquals("draft A @src/a.md @src/b.md ", parked?.draft)
        assertEquals(listOf(file()), parked?.attachments)
        assertEquals(listOf(queued), parked?.queued)
    }

    @Test
    fun `a dropped path in a fresh chat mentions into the live draft`() {
        val vm = viewModel()

        vm.handleDroppedPaths(listOf("src/a.md"), null)

        assertEquals("@src/a.md ", vm.state.value.draft)
    }
}
