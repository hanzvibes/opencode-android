package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.ToolState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C3: the queue duplicate rule (M-071), the form-reply
 * shape (M-064), skipping a question (M-066), picking a skill (M-067)
 * and the stuck-queue actions (M-069).
 *
 * The view model is driven through its public surface with no engine,
 * following the ChatDraftParkingTest / ChatErrorTurnTest pattern.
 */
class ChatViewModelC3Test {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    private fun repoState(busy: Boolean = false) = ChatState(busy = busy)

    /** One follow-up queued while the agent works. */
    private fun queueOne(vm: ChatViewModel) {
        vm.onRepoState(repoState(busy = true))
        vm.onDraftChange("cek juga X")
        vm.send()
        assertEquals(1, vm.state.value.queuedPrompts.size)
    }

    @Test
    fun `M-071 a prompt identical to the last sent one is a duplicate`() {
        val vm = viewModel()
        assertTrue(vm.isQueueDuplicate("hello", "hello", null, allowResend = false))
        assertFalse(vm.isQueueDuplicate("hello", "other", null, allowResend = false))
        assertFalse(vm.isQueueDuplicate("hello", null, null, allowResend = false))
    }

    @Test
    fun `M-071 a prompt identical to the queued tail is a duplicate`() {
        val vm = viewModel()
        assertTrue(vm.isQueueDuplicate("hello", "other", "hello", allowResend = false))
        assertFalse(vm.isQueueDuplicate("hello", "other", "different", allowResend = false))
    }

    @Test
    fun `M-071 a resend bypasses the last-sent check but not the tail check`() {
        val vm = viewModel()
        // The resend is allowed even though the text was just sent…
        assertFalse(vm.isQueueDuplicate("hello", "hello", null, allowResend = true))
        // …but a true duplicate already sitting in the queue needs no
        // second copy — a resend must never double-queue.
        assertTrue(vm.isQueueDuplicate("hello", "hello", "hello", allowResend = true))
    }

    @Test
    fun `M-064 a single-choice answer replies one string field`() {
        val vm = viewModel()
        val fields = vm.questionReplyFields(
            QuestionAnswer(
                callId = "tool-1",
                questionIndex = 0,
                labels = listOf("Yes"),
                multiple = false,
                displayText = "Continue?: Yes",
            ),
        )
        assertEquals(setOf("q0"), fields.keys)
        assertEquals("Yes", fields.getValue("q0").jsonPrimitive.content)
    }

    @Test
    fun `M-064 a multi-choice answer replies an array field`() {
        val vm = viewModel()
        val fields = vm.questionReplyFields(
            QuestionAnswer(
                callId = "tool-1",
                questionIndex = 1,
                labels = listOf("a", "b"),
                multiple = true,
                displayText = "Pick: a, b",
            ),
        )
        assertEquals(setOf("q1"), fields.keys)
        assertEquals(
            listOf("a", "b"),
            fields.getValue("q1").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    private fun questionMessage() = SessionMessage(
        id = "m1",
        type = "assistant",
        content = listOf(
            MessageContent(
                type = "tool",
                id = "q1",
                name = "question",
                state = ToolState(
                    status = "running",
                    input = Json.parseToJsonElement(
                        """{"questions":[{"question":"A?","options":["1","2"]},{"question":"B?","options":["3","4"]}]}""",
                    ),
                ),
            ),
        ),
    )

    private fun questionPart(vm: ChatViewModel): ChatTurnPart.Question {
        // A fresh message list each time forces the rebuild: the cached
        // path would keep the answers stamped at the first build.
        vm.onRepoState(ChatState(messages = listOf(questionMessage())))
        return vm.state.value.messages.single().parts
            .filterIsInstance<ChatTurnPart.Question>().single()
    }

    @Test
    fun `M-066 skipping a question marks every unanswered section skipped`() {
        val vm = viewModel()
        assertEquals(emptyList<String>(), questionPart(vm).answered)

        vm.recordSkippedQuestionAnswers("q1", 0, 2)

        assertEquals(
            listOf(SKIPPED_QUESTION_MARKER, SKIPPED_QUESTION_MARKER),
            questionPart(vm).answered,
        )
    }

    @Test
    fun `M-066 a second skip keeps the skipped markers`() {
        val vm = viewModel()
        vm.recordSkippedQuestionAnswers("q1", 0, 2)
        vm.recordSkippedQuestionAnswers("q1", 1, 2)

        assertEquals(
            listOf(SKIPPED_QUESTION_MARKER, SKIPPED_QUESTION_MARKER),
            questionPart(vm).answered,
        )
    }

    @Test
    fun `M-066 skipQuestion with no session returns false`() = runBlocking {
        val vm = viewModel()
        assertFalse(vm.skipQuestion("tool-1", 0, 1))
    }

    @Test
    fun `M-067 picking a skill puts its name in the draft`() {
        val vm = viewModel()
        vm.pickSkill(
            SlashCommand(
                name = "commit",
                description = "Write a commit message",
                isSkill = true,
                skillId = "sk_1",
            ),
        )
        assertEquals("commit ", vm.state.value.draft)
    }

    @Test
    fun `M-067 picking a row without a skill id changes nothing`() {
        val vm = viewModel()
        vm.onDraftChange("hello")
        vm.pickSkill(SlashCommand(name = "help", description = "Show help"))
        assertEquals("hello", vm.state.value.draft)
    }

    @Test
    fun `M-069 flushQueueNow refuses an empty queue`() {
        assertFalse(viewModel().flushQueueNow())
    }

    @Test
    fun `M-069 flushQueueNow refuses while the agent is busy`() {
        val vm = viewModel()
        queueOne(vm)
        assertFalse(vm.flushQueueNow())
        assertEquals(1, vm.state.value.queuedPrompts.size)
    }

    @Test
    fun `M-069 flushQueueNow refuses without an engine and keeps the queue`() {
        val vm = viewModel()
        queueOne(vm)
        // The busy→idle edge decides nothing without a repository
        // (resolveRunEndAction: hasRepository = false → NONE), so the
        // queue is still there — and the manual flush must refuse too,
        // without touching it.
        vm.onRepoState(repoState(busy = false))
        assertEquals(1, vm.state.value.queuedPrompts.size)
        assertFalse(vm.flushQueueNow())
        assertEquals(1, vm.state.value.queuedPrompts.size)
    }

    @Test
    fun `M-069 cancelQueued returns the queue to the composer`() {
        val vm = viewModel()
        queueOne(vm)
        assertTrue(vm.cancelQueued())
        assertEquals(emptyList<QueuedPrompt>(), vm.state.value.queuedPrompts)
        assertEquals("cek juga X", vm.state.value.draft)
    }
}
