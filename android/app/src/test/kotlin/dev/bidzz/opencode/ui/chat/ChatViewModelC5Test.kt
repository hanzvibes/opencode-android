package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.dto.PendingPermission
import dev.bidzz.opencode.core.api.dto.SessionInfo
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C5: chips (M-087/M-088), send/switch session
 * (M-092–M-096), and find in chat (M-100).
 *
 * Same no-engine harness as C3/C4: the view model runs without a
 * repository, so no send is ever issued — the tests pin down the
 * pure decisions and the composer-side behaviour. The live sends
 * (the interrupt POST, the queue drain, the find scroll) need an
 * engine and are covered by inspection + the repo-level test
 * (ChatRepositoryC5Test, M-091).
 */
class ChatViewModelC5Test {

    private fun viewModel(store: ParkedComposerStore = InMemoryParkedComposerStore()) =
        ChatViewModel(EngineApi { "/tmp" }, { "/tmp" }, parkedStore = store)

    private fun user(id: String, text: String) = SessionMessage(id = id, type = "user", text = text)

    // M-092: shouldInterruptOnSwitch.

    @Test
    fun `M-092 leaving a busy session interrupts it`() {
        assertTrue(shouldInterruptOnSwitch(busy = true, leavingSessionID = "s1", targetSessionID = "s2"))
    }

    @Test
    fun `M-092 an idle session is left alone`() {
        assertFalse(shouldInterruptOnSwitch(busy = false, leavingSessionID = "s1", targetSessionID = "s2"))
    }

    @Test
    fun `M-092 no session to leave interrupts nothing`() {
        assertFalse(shouldInterruptOnSwitch(busy = true, leavingSessionID = null, targetSessionID = "s2"))
    }

    @Test
    fun `M-092 a switch to the same session interrupts nothing`() {
        assertFalse(shouldInterruptOnSwitch(busy = true, leavingSessionID = "s1", targetSessionID = "s1"))
    }

    // M-094: shouldQueueForSend.

    @Test
    fun `M-094 a busy session queues`() {
        assertTrue(
            shouldQueueForSend(
                busy = true, sendInFlight = false,
                sendInFlightSessionID = null, currentSessionID = "s1",
            ),
        )
    }

    @Test
    fun `M-094 an in-flight send for the same session queues`() {
        assertTrue(
            shouldQueueForSend(
                busy = false, sendInFlight = true,
                sendInFlightSessionID = "s1", currentSessionID = "s1",
            ),
        )
    }

    @Test
    fun `M-094 an in-flight send for another session does not queue`() {
        assertFalse(
            shouldQueueForSend(
                busy = false, sendInFlight = true,
                sendInFlightSessionID = "s1", currentSessionID = "s2",
            ),
        )
    }

    @Test
    fun `M-094 an in-flight send while creating a session queues`() {
        assertTrue(
            shouldQueueForSend(
                busy = false, sendInFlight = true,
                sendInFlightSessionID = null, currentSessionID = null,
            ),
        )
    }

    @Test
    fun `M-094 an idle session with nothing in flight sends at once`() {
        assertFalse(
            shouldQueueForSend(
                busy = false, sendInFlight = false,
                sendInFlightSessionID = null, currentSessionID = "s1",
            ),
        )
    }

    // M-087/M-088: sendFollowUp guards.

    @Test
    fun `M-087 a chip tap with no engine reports it and sends nothing`() {
        val vm = viewModel()
        vm.onDraftChange("my own text")
        assertFalse(vm.sendFollowUp("tell me more"))
        assertEquals(R.string.error_engine_not_running, vm.state.value.failureKey)
        assertEquals("my own text", vm.state.value.draft)
    }

    @Test
    fun `M-088 a chip tap while busy is dropped, not queued`() {
        val vm = viewModel()
        vm.onRepoState(ChatState(busy = true))
        assertFalse(vm.sendFollowUp("tell me more"))
        assertTrue(vm.state.value.queuedPrompts.isEmpty())
    }

    @Test
    fun `M-087 a blank chip sends nothing`() {
        val vm = viewModel()
        assertFalse(vm.sendFollowUp("   "))
    }

    // M-095: engine-down guards.

    @Test
    fun `M-095 opening a session with no engine reports it`() {
        val vm = viewModel()
        vm.onRepoState(ChatState(sessionID = "s1"))
        vm.openSession(SessionInfo(id = "s2"))
        assertEquals(R.string.error_engine_not_running, vm.state.value.failureKey)
        assertEquals("s1", vm.state.value.sessionID)
    }

    @Test
    fun `M-095 a new session with no engine reports it and keeps the old one`() {
        val vm = viewModel()
        vm.onRepoState(ChatState(sessionID = "s1"))
        vm.newSession()
        assertEquals(R.string.error_engine_not_running, vm.state.value.failureKey)
        assertEquals("s1", vm.state.value.sessionID)
    }

    @Test
    fun `M-095 opening by id with no engine waits for attach, by design`() {
        val vm = viewModel()
        vm.onRepoState(ChatState(sessionID = "s1"))
        vm.openSession("s2")
        // The cold-start path (56-F1) keeps the pending open instead of
        // failing: the session may still attach. No banner.
        assertNull(vm.state.value.failureKey)
    }

    // M-093: park/restore of retry + history state.

    @Test
    fun `M-093 coming back restores the parked retry state`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        store.put(
            "s1",
            ParkedComposer(
                draft = "",
                attachments = emptyList(),
                sentPrompts = listOf("first prompt", "second prompt"),
                lastSentText = "second prompt",
                deferredPermission = PendingPermission(
                    id = "p1", sessionID = "s1", action = "read",
                    resources = setOf("a.txt"), save = emptySet(), message = "read a.txt?",
                ).toParked(),
            ),
        )
        vm.restoreParkedComposer("s1")
        assertEquals(listOf("first prompt", "second prompt"), vm.state.value.sentPrompts)
        assertEquals("second prompt", vm.state.value.lastSentText)
        assertEquals("p1", vm.state.value.deferredPermission?.id)
        assertEquals("read", vm.state.value.deferredPermission?.action)
        assertEquals(setOf("a.txt"), vm.state.value.deferredPermission?.resources)
        // The snapshot drops the metadata (not serialisable); the
        // restore rebuilds with an empty map, never a null.
        assertTrue(vm.state.value.deferredPermission?.metadata?.isEmpty() == true)
    }

    @Test
    fun `M-093 an engine death parks the retry state with the draft`() {
        val store = InMemoryParkedComposerStore()
        val vm = viewModel(store)
        store.put(
            "s1",
            ParkedComposer(
                draft = "",
                attachments = emptyList(),
                sentPrompts = listOf("first prompt"),
                lastSentText = "first prompt",
                deferredPermission = PendingPermission(
                    id = "p1", sessionID = "s1", action = "read",
                    resources = setOf("a.txt"), save = emptySet(), message = "read a.txt?",
                ).toParked(),
            ),
        )
        vm.onRepoState(ChatState(sessionID = "s1"))
        vm.restoreParkedComposer("s1")
        vm.onDraftChange("typed while dying")
        vm.onEngineStatus(EngineStatus(EngineState.FAILED))
        val parked = store.get("s1")
        assertEquals("typed while dying", parked?.draft)
        assertEquals(listOf("first prompt"), parked?.sentPrompts)
        assertEquals("first prompt", parked?.lastSentText)
        assertEquals("p1", parked?.deferredPermission?.id)
    }

    // M-100: findMessageIds (pure).

    @Test
    fun `M-100 find matches text, reasoning and tool output`() {
        val messages = listOf(
            ChatMessage(id = "m1", role = Role.USER, text = "fix the bug"),
            ChatMessage(id = "m2", role = Role.ASSISTANT, text = "done", reasoning = "the BUG was here"),
            ChatMessage(
                id = "m3", role = Role.ASSISTANT, text = "ran it",
                tools = listOf(ToolCall(id = "t1", name = "bash", status = "completed", output = "BUG reproduced")),
            ),
            ChatMessage(id = "m4", role = Role.ASSISTANT, text = "nothing"),
        )
        assertEquals(listOf("m1", "m2", "m3"), findMessageIds(messages, "bug"))
        // Case-insensitive, and a tool error counts too.
        assertEquals(listOf("m1", "m2", "m3"), findMessageIds(messages, "BUG"))
        val errorMessages = listOf(
            ChatMessage(
                id = "m5", role = Role.ASSISTANT, text = "failed",
                tools = listOf(ToolCall(id = "t2", name = "bash", status = "error", error = "needle exploded")),
            ),
        )
        assertEquals(listOf("m5"), findMessageIds(errorMessages, "needle"))
    }

    @Test
    fun `M-100 find matches nothing for a blank query or no hit`() {
        val messages = listOf(ChatMessage(id = "m1", role = Role.USER, text = "hello"))
        assertEquals(emptyList<String>(), findMessageIds(messages, "   "))
        assertEquals(emptyList<String>(), findMessageIds(messages, "zzz"))
    }

    // M-100: the view-model find state.

    @Test
    fun `M-100 setFindQuery finds matches and stepping wraps around`() {
        val vm = viewModel()
        vm.onRepoState(
            ChatState(
                sessionID = "s1",
                messages = listOf(
                    user("m1", "hello world"),
                    user("m2", "goodbye world"),
                    user("m3", "hello again"),
                ),
            ),
        )
        vm.setFindQuery("hello")
        assertEquals(listOf("m1", "m3"), vm.state.value.findMatches)
        assertEquals(0, vm.state.value.findIndex)
        vm.findNext()
        assertEquals(1, vm.state.value.findIndex)
        vm.findNext()
        assertEquals(0, vm.state.value.findIndex)
        vm.findPrevious()
        assertEquals(1, vm.state.value.findIndex)
        vm.clearFind()
        assertEquals("", vm.state.value.findQuery)
        assertTrue(vm.state.value.findMatches.isEmpty())
        assertEquals(-1, vm.state.value.findIndex)
    }

    @Test
    fun `M-100 a new emission recomputes the matches and keeps the current one`() {
        val vm = viewModel()
        vm.onRepoState(
            ChatState(
                sessionID = "s1",
                messages = listOf(user("m1", "hello world"), user("m2", "hello again")),
            ),
        )
        vm.setFindQuery("hello")
        assertEquals(listOf("m1", "m2"), vm.state.value.findMatches)
        // A fresh list instance (buildUiMessages keys on identity):
        // m1 is gone, m2 stays, m3 arrives. The current match was m1,
        // so the index clamps to the first surviving match.
        vm.onRepoState(
            ChatState(
                sessionID = "s1",
                messages = listOf(user("m2", "hello again"), user("m3", "hello there")),
            ),
        )
        assertEquals(listOf("m2", "m3"), vm.state.value.findMatches)
        assertEquals(0, vm.state.value.findIndex)
    }

    @Test
    fun `M-100 recomputeFindMatches keeps the current match when it still exists`() {
        val vm = viewModel()
        val messages = listOf(
            ChatMessage(id = "m1", role = Role.USER, text = "hello"),
            ChatMessage(id = "m2", role = Role.USER, text = "hello again"),
        )
        assertEquals(listOf("m1", "m2") to 1, vm.recomputeFindMatches(messages, "hello", "m2"))
        assertEquals(listOf("m1", "m2") to 0, vm.recomputeFindMatches(messages, "hello", "gone"))
        assertEquals(emptyList<String>() to -1, vm.recomputeFindMatches(messages, "  ", "m1"))
        assertEquals(emptyList<String>() to -1, vm.recomputeFindMatches(messages, "zzz", "m1"))
    }
}
