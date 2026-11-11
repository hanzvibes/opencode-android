package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Question-card answers go to the engine as a form reply (audit-70 10-F1,
 * 10-F3, 44-F1) — never as a prompt, never through the draft.
 *
 * The engine is down in these tests (no repository), so the reply cannot
 * be delivered. The answer must then come back into the composer instead
 * of vanishing: appended after what the user was typing, never
 * overwriting it, with the pending attachments untouched and nothing
 * queued or sent. A blank answer sends nothing and changes nothing.
 */
class AnswerQuestionTest {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    private fun answer(
        labels: List<String> = listOf("option A"),
        displayText: String = "Pembaruan: option A",
    ) = QuestionAnswer(
        callId = "tool-1",
        questionIndex = 0,
        labels = labels,
        multiple = false,
        displayText = displayText,
    )

    @Test
    fun `an undeliverable answer returns false and goes back into the draft`() = runBlocking {
        val vm = viewModel()
        vm.onDraftChange("half-typed message")
        vm.addAttachment(
            AttachedFile(id = "1", name = "notes.txt", content = "do not send me with the answer"),
        )

        assertFalse(vm.answerQuestion(answer()))

        assertEquals("half-typed message\nPembaruan: option A", vm.state.value.draft)
        assertEquals(
            listOf(AttachedFile(id = "1", name = "notes.txt", content = "do not send me with the answer")),
            vm.state.value.pendingAttachments,
        )
        assertEquals(emptyList<QueuedPrompt>(), vm.state.value.queuedPrompts)
        assertNull(vm.state.value.lastSentText)
    }

    @Test
    fun `a blank answer sends nothing and changes nothing`() = runBlocking {
        val vm = viewModel()
        vm.onDraftChange("keep me")

        assertFalse(vm.answerQuestion(answer(labels = emptyList(), displayText = "   ")))

        assertEquals("keep me", vm.state.value.draft)
        assertEquals(emptyList<QueuedPrompt>(), vm.state.value.queuedPrompts)
        assertNull(vm.state.value.lastSentText)
    }
}
