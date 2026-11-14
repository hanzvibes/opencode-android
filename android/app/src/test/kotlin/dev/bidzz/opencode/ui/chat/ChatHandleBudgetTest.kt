package dev.bidzz.opencode.ui.chat

import androidx.lifecycle.SavedStateHandle
import dev.bidzz.opencode.api.EngineApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 B5 (M-026): the queue + attachments in the saved-state
 * handle share a byte budget — over budget the keys are left out (never
 * a stale snapshot, which could re-send follow-ups that already went)
 * and the parked store on disk stays the source of truth. Prompt history
 * in the handle is capped at the recent entries.
 */
class ChatHandleBudgetTest {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    private fun attachment(id: String, content: String) = AttachedFile(id = id, name = "f.txt", content = content)

    @Test
    fun `a small composer fits the budget and is written`() {
        val vm = viewModel()
        val handle = SavedStateHandle()
        val queue = listOf(QueuedPrompt(fullText = "hello", typedText = "hello"))
        val attachments = listOf(attachment("a1", "small"))

        vm.putBoundedComposerLists(handle, queue, attachments)

        assertEquals(queue, handle.get<ArrayList<QueuedPrompt>>(ChatViewModel.QUEUE_KEY)?.toList())
        assertEquals(attachments, handle.get<ArrayList<AttachedFile>>(ChatViewModel.ATTACHMENTS_KEY)?.toList())
    }

    @Test
    fun `an over-budget composer leaves both keys out`() {
        val vm = viewModel()
        val handle = SavedStateHandle()
        // One attachment at the 64 KB inline cap, baked into the queue
        // text as well: the same content counted twice is what makes
        // the payload blow the 256 KB budget.
        val big = "x".repeat(65_536)
        val queue = listOf(
            QueuedPrompt(
                fullText = "prompt $big",
                typedText = "prompt",
                attachments = listOf(attachment("a1", big)),
            ),
            QueuedPrompt(
                fullText = "prompt $big",
                typedText = "prompt",
                attachments = listOf(attachment("a2", big)),
            ),
        )
        val attachments = listOf(attachment("a1", big), attachment("a2", big))

        assertTrue(vm.estimatedHandleBytes(queue, attachments) > ChatViewModel.HANDLE_PAYLOAD_BUDGET_BYTES)
        vm.putBoundedComposerLists(handle, queue, attachments)

        assertFalse(handle.contains(ChatViewModel.QUEUE_KEY))
        assertFalse(handle.contains(ChatViewModel.ATTACHMENTS_KEY))
    }

    @Test
    fun `an empty composer removes both keys`() {
        val vm = viewModel()
        val handle = SavedStateHandle()
        handle[ChatViewModel.QUEUE_KEY] = arrayListOf(QueuedPrompt(fullText = "x", typedText = "x"))
        handle[ChatViewModel.ATTACHMENTS_KEY] = arrayListOf(attachment("a1", "x"))

        vm.putBoundedComposerLists(handle, emptyList(), emptyList())

        assertFalse(handle.contains(ChatViewModel.QUEUE_KEY))
        assertFalse(handle.contains(ChatViewModel.ATTACHMENTS_KEY))
    }

    @Test
    fun `mergeParkedGaps fills what the handle dropped and leaves the draft alone`() {
        val store = InMemoryParkedComposerStore()
        val vm = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" }, parkedStore = store)
        store.put(
            "s1",
            ParkedComposer(
                draft = "",
                attachments = listOf(attachment("a1", "file text")),
                queued = listOf(QueuedPrompt(fullText = "later", typedText = "later")),
            ),
        )
        // The live composer holds only a draft, as after a restore whose
        // handle dropped the over-budget queue/attachments.
        vm.onDraftChange("typed draft")

        val merged = vm.mergeParkedGaps("s1")

        assertNotNull(merged)
        assertEquals("typed draft", vm.state.value.draft)
        assertEquals(listOf("a1"), vm.state.value.pendingAttachments.map { it.id })
        assertEquals(listOf("later"), vm.state.value.queuedPrompts.map { it.typedText })
        // The parked entry stays: it is the durable backup until the next
        // park overwrites it with the merged state.
        assertNotNull(store.get("s1"))
    }

    @Test
    fun `mergeParkedGaps is idempotent — a second call finds nothing to fill`() {
        val store = InMemoryParkedComposerStore()
        val vm = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" }, parkedStore = store)
        store.put(
            "s1",
            ParkedComposer(
                draft = "",
                attachments = listOf(attachment("a1", "file text")),
                queued = emptyList(),
            ),
        )
        vm.onDraftChange("typed draft")

        assertNotNull(vm.mergeParkedGaps("s1"))
        // The live composer now holds the parked attachment, so a second
        // merge finds nothing missing and returns null.
        assertNull(vm.mergeParkedGaps("s1"))
    }

    @Test
    fun `the handle keeps only the recent prompt history`() {
        val vm = viewModel()
        val handle = SavedStateHandle()
        // The collector caps what it writes; assert the constant the
        // cap is expressed in, so a silent widening fails here first.
        assertEquals(20, ChatViewModel.HANDLE_SENT_HISTORY)
        val history = (1..50).map { "prompt $it" }
        handle[ChatViewModel.SENT_PROMPTS_KEY] = ArrayList(history.takeLast(ChatViewModel.HANDLE_SENT_HISTORY))

        val kept = handle.get<ArrayList<String>>(ChatViewModel.SENT_PROMPTS_KEY)
        assertEquals(20, kept?.size)
        assertEquals("prompt 50", kept?.last())
    }
}
