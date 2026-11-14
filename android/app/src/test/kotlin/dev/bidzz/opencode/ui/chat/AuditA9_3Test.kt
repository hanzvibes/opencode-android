package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase A9-3 (DL-010, DL-029, DL-034, DL-035): the active
 * composer's draft, queue, attachments and edit-in-progress survive
 * process death, and a parked composer comes back on resume.
 *
 * The debounced park itself lives in the view model's scope (like
 * [ChatViewModel.observeSafPicks]) and cannot be driven in a JVM unit
 * test; what is tested here is the synchronous machinery it relies on —
 * parking on clear, the busy-session report, the edit backup travelling
 * with the parked composer, and the restore itself. The auto-resume
 * call ([ChatViewModel.onResume] invoking the restore before opening
 * the session) is covered by compile + fast-verify.
 */
class AuditA9_3Test {

    private fun viewModel(
        store: ParkedComposerStore = InMemoryParkedComposerStore(),
        onBusySessionChanged: (String?) -> Unit = {},
    ) = ChatViewModel(
        EngineApi { "/tmp" },
        { "/tmp" },
        parkedStore = store,
        onBusySessionChanged = onBusySessionChanged,
    )

    private fun repoState(busy: Boolean = false, sessionID: String? = null) =
        ChatState(busy = busy, sessionID = sessionID)

    // DL-010: parking on clear -------------------------------------------

    @Test
    fun `onCleared parks the live draft`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        vm.onDraftChange("hello")

        vm.parkOnCleared()

        assertEquals("hello", store.get(UNSENT_SESSION_KEY)?.draft)
    }

    @Test
    fun `onCleared removes the entry once the composer is empty`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        vm.onDraftChange("hello")
        vm.parkOnCleared()
        assertEquals("hello", store.get(UNSENT_SESSION_KEY)?.draft)

        vm.onDraftChange("")
        vm.parkOnCleared()

        assertNull(store.get(UNSENT_SESSION_KEY))
    }

    // DL-029: busy-session report -----------------------------------------

    @Test
    fun `a run starting reports its session, a run ending clears it`() {
        val reported = mutableListOf<String?>()
        val vm = viewModel(onBusySessionChanged = { reported += it })

        vm.onRepoState(repoState(busy = true, sessionID = "A"))
        vm.onRepoState(repoState(busy = false, sessionID = "A"))

        assertEquals(listOf("A", null), reported)
    }

    @Test
    fun `repeated busy emissions report only once`() {
        val reported = mutableListOf<String?>()
        val vm = viewModel(onBusySessionChanged = { reported += it })

        vm.onRepoState(repoState(busy = true, sessionID = "A"))
        vm.onRepoState(repoState(busy = true, sessionID = "A"))
        vm.onRepoState(repoState(busy = true, sessionID = "A"))

        assertEquals(listOf("A"), reported)
    }

    @Test
    fun `an idle emission reports nothing`() {
        val reported = mutableListOf<String?>()
        val vm = viewModel(onBusySessionChanged = { reported += it })

        vm.onRepoState(repoState(busy = false, sessionID = "A"))

        assertTrue(reported.isEmpty())
    }

    // DL-034: the edit backup travels with the parked composer -----------

    @Test
    fun `switching sessions parks the edit backup with the draft`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        vm.onRepoState(repoState(sessionID = "A"))
        vm.onDraftChange("AAA")
        vm.startEdit("m1", "edited")

        vm.newSession()

        val parked = store.get("A")
        assertEquals("edited", parked?.draft)
        assertEquals("AAA", parked?.draftBeforeEdit)
        assertEquals("m1", parked?.editingMessageId)
    }

    @Test
    fun `restoring a parked edit brings Cancel back`() {
        val store = InMemoryParkedComposerStore()
        store.put(
            "A",
            ParkedComposer(
                draft = "edited",
                attachments = emptyList(),
                queued = emptyList(),
                draftBeforeEdit = "AAA",
                editingMessageId = "m1",
            ),
        )
        val vm = viewModel(store)

        val restored = vm.restoreParkedComposer("A")

        assertEquals("edited", restored?.draft)
        assertEquals("edited", vm.state.value.draft)
        assertEquals("m1", vm.state.value.editingMessageId)
        vm.cancelEdit()
        assertEquals("AAA", vm.state.value.draft)
        assertNull(vm.state.value.editingMessageId)
    }

    @Test
    fun `submitting the edit drops the backup`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        vm.onRepoState(repoState(sessionID = "A"))
        vm.onDraftChange("AAA")
        vm.startEdit("m1", "edited")
        // No repository: submitEdit falls through to send(), which needs
        // none here — the edit is finished either way, so the backup must
        // not survive it.
        vm.submitEdit()

        vm.newSession()

        val parked = store.get("A")
        assertNull(parked?.draftBeforeEdit)
        assertNull(parked?.editingMessageId)
    }

    // DL-035: restoring a parked composer ---------------------------------

    @Test
    fun `a parked composer restores draft, attachments and queue`() {
        val store = InMemoryParkedComposerStore()
        val file = AttachedFile(id = "1", name = "notes.md", content = "hi")
        val queued = QueuedPrompt("tunggu", "tunggu")
        store.put("A", ParkedComposer("draft A", listOf(file), listOf(queued)))
        val vm = viewModel(store)

        val restored = vm.restoreParkedComposer("A")

        assertEquals("draft A", restored?.draft)
        assertEquals("draft A", vm.state.value.draft)
        assertEquals(listOf(file), vm.state.value.pendingAttachments)
        assertEquals(listOf(queued), vm.state.value.queuedPrompts)
        assertNull(store.get("A"))
    }

    @Test
    fun `a restore guarded by an empty composer skips live text`() {
        val store = InMemoryParkedComposerStore()
        store.put("A", ParkedComposer("parked", emptyList(), emptyList()))
        val vm = viewModel(store)
        vm.onDraftChange("live text")

        val restored = vm.restoreParkedComposer("A", onlyIfComposerEmpty = true)

        assertNull(restored)
        assertEquals("live text", vm.state.value.draft)
        assertEquals("parked", store.get("A")?.draft)
    }

    @Test
    fun `a guarded restore applies when the live composer is empty`() {
        val store = InMemoryParkedComposerStore()
        store.put("A", ParkedComposer("parked", emptyList(), emptyList()))
        val vm = viewModel(store)

        val restored = vm.restoreParkedComposer("A", onlyIfComposerEmpty = true)

        assertEquals("parked", restored?.draft)
        assertEquals("parked", vm.state.value.draft)
        assertNull(store.get("A"))
    }
}
