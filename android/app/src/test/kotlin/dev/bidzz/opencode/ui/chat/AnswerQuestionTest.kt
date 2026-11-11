package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Question-card answers must not touch the draft or the pending
 * attachments (item 16): the answer travels on its own path, and the
 * files the user picked belong to the message being typed, not to the
 * answer. The same holds for a resend, which queues and sends with an
 * empty attachment list.
 *
 * The engine is down in these tests (no repository), so the send itself
 * fails — the point is what survives it: the draft and the attachments.
 */
class AnswerQuestionTest {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    @Test
    fun `answering a question leaves the draft and pending attachments alone`() {
        val vm = viewModel()
        vm.onDraftChange("half-typed message")
        vm.addAttachment(
            AttachedFile(id = "1", name = "notes.txt", content = "do not send me with the answer"),
        )

        vm.answerQuestion("option A")

        assertEquals("half-typed message", vm.state.value.draft)
        assertEquals(
            listOf(AttachedFile(id = "1", name = "notes.txt", content = "do not send me with the answer")),
            vm.state.value.pendingAttachments,
        )
        assertEquals(emptyList<QueuedPrompt>(), vm.state.value.queuedPrompts)
    }

    @Test
    fun `a blank answer sends nothing and changes nothing`() {
        val vm = viewModel()
        vm.onDraftChange("keep me")

        vm.answerQuestion("   ")

        assertEquals("keep me", vm.state.value.draft)
        assertEquals(emptyList<QueuedPrompt>(), vm.state.value.queuedPrompts)
        assertEquals(null, vm.state.value.lastSentText)
    }
}
