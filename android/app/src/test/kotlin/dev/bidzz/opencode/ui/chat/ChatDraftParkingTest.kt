package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1a: drafts and queues survive session switches, engine death and
 * (through the store) process death.
 *
 * The view model is driven through its public surface with an explicit
 * in-memory store, so the tests assert on the store exactly what the
 * production SharedPreferences store would hold. The repo-backed paths
 * (reopening a session by id) need a live engine and are covered by the
 * store assertions plus the unsent restore, which exercises the same
 * take-and-restore shape.
 */
class ChatDraftParkingTest {

    private fun viewModel(store: ParkedComposerStore = InMemoryParkedComposerStore()) =
        ChatViewModel(EngineApi { "/tmp" }, { "/tmp" }, parkedStore = store)

    private fun repoState(busy: Boolean = false, sessionID: String? = null) =
        ChatState(busy = busy, sessionID = sessionID)

    @Test
    fun `95-F6 draft typed in a fresh chat survives newSession`() {
        val vm = viewModel()
        vm.onDraftChange("teks belum terkirim")

        vm.newSession()

        assertEquals("teks belum terkirim", vm.state.value.draft)
    }

    @Test
    fun `95-F6 unsent draft survives repeated newSession taps`() {
        val vm = viewModel()
        vm.onDraftChange("teks belum terkirim")

        vm.newSession()
        vm.newSession()

        assertEquals("teks belum terkirim", vm.state.value.draft)
    }

    @Test
    fun `13-F6 queued follow-up is parked on newSession and restored`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        vm.onRepoState(repoState(busy = true))
        vm.onDraftChange("cek juga X")
        vm.send()

        assertEquals(1, vm.state.value.queuedPrompts.size)
        assertEquals("", vm.state.value.draft)

        vm.newSession()

        assertEquals(1, vm.state.value.queuedPrompts.size)
        assertEquals("cek juga X", vm.state.value.queuedPrompts.single().typedText)
        assertNull(store.get(UNSENT_SESSION_KEY))
    }

    @Test
    fun `13-F1 a flush that cannot start leaves the entry queued`() {
        val vm = viewModel()
        vm.onRepoState(repoState(busy = true))
        vm.onDraftChange("follow-up")
        vm.send()
        assertEquals(1, vm.state.value.queuedPrompts.size)

        // The run ends, but there is no repository: the flush cannot start.
        vm.onRepoState(repoState(busy = false))

        assertEquals(1, vm.state.value.queuedPrompts.size)
        assertEquals("follow-up", vm.state.value.queuedPrompts.single().typedText)
    }

    @Test
    fun `13-F1 resolveFlushDrop drops only the matching head`() {
        val first = QueuedPrompt("satu", "satu")
        val second = QueuedPrompt("dua", "dua")
        val queue = listOf(first, second)

        assertEquals(listOf(second), resolveFlushDrop(queue, first))
        assertEquals(queue, resolveFlushDrop(queue, second))
        assertEquals(queue, resolveFlushDrop(queue, null))
        assertEquals(emptyList<QueuedPrompt>(), resolveFlushDrop(listOf(first), first))
    }

    @Test
    fun `13-F3 starting a second edit restores the original draft on cancel`() {
        val vm = viewModel()
        vm.onDraftChange("A")

        vm.startEdit("m1", "teks X")
        assertEquals("teks X", vm.state.value.draft)
        vm.startEdit("m2", "teks Y")
        assertEquals("teks Y", vm.state.value.draft)

        vm.cancelEdit()

        assertEquals("A", vm.state.value.draft)
        assertNull(vm.state.value.editingMessageId)
    }

    @Test
    fun `80-F5 engine death parks the draft under its session`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        vm.onRepoState(repoState(sessionID = "s1"))
        vm.onDraftChange("teks panjang")

        vm.onEngineStatus(EngineStatus(state = EngineState.FAILED))

        assertEquals("teks panjang", store.get("s1")?.draft)
        assertNull(vm.state.value.sessionID)
        assertEquals("teks panjang", vm.state.value.draft)

        // Switching away after the death must not lose the parked copy:
        // the fresh session starts empty and s1 keeps its draft.
        vm.newSession()

        assertEquals("", vm.state.value.draft)
        assertEquals("teks panjang", store.get("s1")?.draft)
        assertTrue(vm.state.value.queuedPrompts.isEmpty())
    }
}
