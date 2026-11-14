package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.LiveTool
import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.api.dto.ToolState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The optimistic send bubble (#6a): a send answers at once, and the
 * engine's echo hands over to it — never duplicating it.
 *
 * The view model is driven directly through its internal [ChatViewModel.onRepoState]
 * with synthetic [ChatState]s: the repository itself needs a live engine,
 * but the dedup rule lives entirely in the fold, and that is what these
 * tests pin down. No engine is attached here, so no send is ever issued —
 * [ChatViewModel.stageOptimistic] stages the bubble a send would stage.
 */
class ChatViewModelTest {

    private fun viewModel() = ChatViewModel(EngineApi { "/tmp" }, { "/tmp" })

    private fun user(id: String, text: String) = SessionMessage(id = id, type = "user", text = text)

    private fun repoState(messages: List<SessionMessage>, busy: Boolean = false) =
        ChatState(messages = messages, busy = busy)

    @Test
    fun `staging an optimistic bubble appends it and sets sending`() {
        val vm = viewModel()

        vm.stageOptimistic("halo")

        val messages = vm.state.value.messages
        assertEquals(1, messages.size)
        assertEquals(Role.USER, messages.single().role)
        assertEquals("halo", messages.single().text)
        assertTrue(messages.single().id.startsWith("local:"))
        assertTrue(vm.state.value.sending)
    }

    @Test
    fun `the echo replaces the optimistic bubble without duplicating`() {
        val vm = viewModel()
        vm.stageOptimistic("halo")

        vm.onRepoState(repoState(listOf(user("m1", "halo")), busy = true))

        val messages = vm.state.value.messages
        assertEquals(1, messages.size)
        assertEquals("m1", messages.single().id)
        assertEquals("halo", messages.single().text)
        assertFalse(vm.state.value.sending)
    }

    @Test
    fun `an older message with the same text is not the echo`() {
        val vm = viewModel()
        // The transcript already holds an earlier "halo".
        vm.onRepoState(repoState(listOf(user("old", "halo"))))
        // The user sends "halo" again.
        vm.stageOptimistic("halo")

        // The repo still shows only the old message: the bubble stays.
        vm.onRepoState(repoState(listOf(user("old", "halo")), busy = true))
        val pending = vm.state.value.messages
        assertEquals(2, pending.size)
        assertEquals("old", pending[0].id)
        assertTrue(pending[1].id.startsWith("local:"))
        assertTrue(vm.state.value.sending)

        // The real echo arrives under a fresh id: one old, one new, no local.
        vm.onRepoState(repoState(listOf(user("old", "halo"), user("m2", "halo")), busy = true))
        val echoed = vm.state.value.messages
        assertEquals(2, echoed.size)
        assertEquals(listOf("old", "m2"), echoed.map { it.id })
        assertFalse(vm.state.value.sending)
    }

    @Test
    fun `the bubble drops when the run ends without an echo`() {
        val vm = viewModel()
        vm.stageOptimistic("halo")

        vm.onRepoState(repoState(emptyList(), busy = true))
        assertEquals(1, vm.state.value.messages.size)
        assertTrue(vm.state.value.sending)

        vm.onRepoState(repoState(emptyList(), busy = false))
        assertEquals(emptyList<ChatMessage>(), vm.state.value.messages)
        assertFalse(vm.state.value.sending)
    }

    @Test
    fun `a second send replaces a lingering optimistic bubble`() {
        val vm = viewModel()
        vm.stageOptimistic("satu")
        vm.stageOptimistic("dua")

        val messages = vm.state.value.messages
        assertEquals(1, messages.size)
        assertEquals("dua", messages.single().text)
        assertTrue(vm.state.value.sending)
    }

    @Test
    fun `a bubble staged for another session never lands in this one`() {
        val vm = viewModel()
        // The bubble was staged while no session was open (null session);
        // the repo then reports a session the bubble was not sent to —
        // but the staged session is null (still being created), so the
        // bubble is kept: the new session IS the send's session.
        vm.stageOptimistic("halo")
        vm.onRepoState(ChatState(sessionID = "s1", messages = emptyList(), busy = true))
        assertEquals(1, vm.state.value.messages.size)
        assertTrue(vm.state.value.sending)
    }

    // Audit-200 Fase C2, M-049: a tool first seen already finished must
    // not be stamped start == end == now ("0ms") — it gets no duration.
    @Test
    fun `M-049 a tool first seen already finished gets no duration`() {
        val vm = viewModel()
        val live = mapOf(
            "t1" to LiveTool("m1", ToolCall("t1", "read", "completed")),
        )
        vm.onRepoState(ChatState(messages = emptyList(), liveTools = live, busy = true))

        val shown = vm.state.value.messages.single()
        assertEquals("t1", shown.tools.single().id)
        assertFalse("a first-seen-done tool must have no duration", shown.toolDurations.containsKey("t1"))
    }

    @Test
    fun `M-049 a tool seen running first keeps its duration when it finishes`() {
        val vm = viewModel()
        val running = mapOf(
            "t1" to LiveTool("m1", ToolCall("t1", "read", "running")),
        )
        val done = mapOf(
            "t1" to LiveTool("m1", ToolCall("t1", "read", "completed")),
        )
        vm.onRepoState(ChatState(messages = emptyList(), liveTools = running, busy = true))
        vm.onRepoState(ChatState(messages = emptyList(), liveTools = done, busy = true))

        val shown = vm.state.value.messages.single()
        assertTrue("a live-seen tool must have a duration", shown.toolDurations.containsKey("t1"))
    }

    // Audit-200 Fase C2, M-050: when no run is in flight, a history tool
    // still marked running is dead — shown as interrupted, never ticking.
    @Test
    fun `M-050 a running history tool reads interrupted once the run is over`() {
        val vm = viewModel()
        val tool = MessageContent(
            type = "tool",
            id = "t1",
            name = "read",
            state = ToolState(status = "running"),
        )
        val msg = SessionMessage(id = "m1", type = "assistant", content = listOf(tool))

        vm.onRepoState(ChatState(messages = listOf(msg), busy = false))

        val shown = vm.state.value.messages.single()
        assertEquals("interrupted", shown.tools.single().status)
        assertFalse(shown.streaming)
    }

    @Test
    fun `M-050 a running history tool stays running while the run is live`() {
        val vm = viewModel()
        val tool = MessageContent(
            type = "tool",
            id = "t1",
            name = "read",
            state = ToolState(status = "running"),
        )
        val msg = SessionMessage(id = "m1", type = "assistant", content = listOf(tool))

        vm.onRepoState(ChatState(messages = listOf(msg), busy = true))

        val shown = vm.state.value.messages.single()
        assertEquals("running", shown.tools.single().status)
    }

    // Audit-200 Fase C2, M-052/M-053: Reject only counts when the prompt
    // was actually accepted, and the decision lives in the view model.
    @Test
    fun `M-052 a reject with no engine attached records no decision`() {
        val vm = viewModel()

        assertFalse(vm.requestDiffRevert("c1", "src/Foo.kt", null))
        assertTrue(vm.state.value.diffDecisions.isEmpty())
    }

    @Test
    fun `M-052 a queued reject records the decision, a duplicate does not resend`() {
        val vm = viewModel()
        vm.onRepoState(repoState(emptyList(), busy = true))

        assertTrue(vm.requestDiffRevert("c1", "src/Foo.kt", null))
        assertEquals(DiffDecision.REJECTED, vm.state.value.diffDecisions["c1"])
        // The same prompt queued again is a duplicate: the second call
        // is refused, so the UI keeps the first request — it never
        // claims a second one.
        assertFalse(vm.requestDiffRevert("c1", "src/Foo.kt", null))
        assertEquals(DiffDecision.REJECTED, vm.state.value.diffDecisions["c1"])
        assertEquals(1, vm.state.value.queuedPrompts.size)
    }

    @Test
    fun `M-053 an accept decision is recorded in the view model`() {
        val vm = viewModel()
        vm.recordDiffDecision("c9", DiffDecision.ACCEPTED)
        assertEquals(DiffDecision.ACCEPTED, vm.state.value.diffDecisions["c9"])
    }

    @Test
    fun `M-052 a revert returned to the draft resets its decision`() {
        val queued = listOf(QueuedPrompt("Please revert src/Foo.kt to undo the change described below. If newer edits exist on this file, keep them and only undo this one.", "Please revert src/Foo.kt to undo the change described below. If newer edits exist on this file, keep them and only undo this one."))
        val calls = mapOf(
            "Please revert src/Foo.kt to undo the change described below. If newer edits exist on this file, keep them and only undo this one." to "c1",
        )
        assertEquals(setOf("c1"), revertDecisionsForReturnedQueue(queued, calls))
        assertEquals(
            emptySet<String>(),
            revertDecisionsForReturnedQueue(
                listOf(QueuedPrompt("some other prompt", "some other prompt")),
                calls,
            ),
        )
    }
}
