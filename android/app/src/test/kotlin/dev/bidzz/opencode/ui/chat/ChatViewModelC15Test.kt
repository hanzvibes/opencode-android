package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.ChatState
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.engine.EngineState
import dev.bidzz.opencode.core.engine.EngineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit-200 Fase C15: preview walk, queue flush/cancel/caps, engine-down
 * restart, deep-link deferral, restore reconciliation, onResume retry.
 *
 * The view model runs without a repository unless a test says otherwise,
 * following the ChatViewModelC10Test / ChatErrorTurnTest pattern: the
 * state the banner reads is pinned down through onEngineStatus and
 * onRepoState directly.
 */
class ChatViewModelC15Test {

    private fun viewModel(store: ParkedComposerStore = InMemoryParkedComposerStore()) =
        ChatViewModel(EngineApi { "/tmp" }, { "/tmp" }, parkedStore = store)

    private fun user(id: String, text: String) =
        SessionMessage(id = id, type = "user", text = text)

    @Test
    fun `M-219 a full queue refuses the next follow-up and says so instead of dropping it silently`() {
        val store = InMemoryParkedComposerStore()
        store.put(
            "s1",
            ParkedComposer(
                draft = "",
                attachments = emptyList(),
                queued = (1..10).map { QueuedPrompt("prompt $it", "prompt $it") },
            ),
        )
        val vm = viewModel(store)
        vm.restoreParkedComposer("s1")
        vm.onRepoState(ChatState(sessionID = "s1", busy = true))
        vm.onDraftChange("eleven")

        vm.send()

        assertEquals(10, vm.state.value.queuedPrompts.size)
        assertEquals("eleven", vm.state.value.draft)
        assertEquals(R.string.queue_full, vm.state.value.queueNoticeKey)
        assertEquals("10", vm.state.value.queueNoticeArg)
    }

    @Test
    fun `M-219 the queue cap is ten and the attachment caps are eight files or 256 KB`() {
        assertEquals(false, isQueueFull((1..9).map { QueuedPrompt("p$it", "p$it") }))
        assertEquals(true, isQueueFull((1..10).map { QueuedPrompt("p$it", "p$it") }))
        assertEquals(true, canAddAttachment(emptyList(), AttachedFile(id = "f1", name = "a", content = "x")))
        assertEquals(
            false,
            canAddAttachment(
                (1..8).map { AttachedFile(id = "f$it", name = "a$it", content = "x") },
                AttachedFile(id = "f9", name = "a9", content = "x"),
            ),
        )
        assertEquals(
            false,
            canAddAttachment(
                listOf(AttachedFile(id = "f1", name = "big", content = "x".repeat(200_000))),
                AttachedFile(id = "f2", name = "big2", content = "x".repeat(100_000)),
            ),
        )
    }

    @Test
    fun `M-219 an attachment past the caps is refused with the matching notice`() {
        val vm = viewModel()
        (1..8).forEach { vm.addAttachment(AttachedFile(id = "f$it", name = "a$it", content = "x")) }
        assertEquals(8, vm.state.value.pendingAttachments.size)

        vm.addAttachment(AttachedFile(id = "f9", name = "a9", content = "x"))

        assertEquals(8, vm.state.value.pendingAttachments.size)
        assertEquals(R.string.attach_too_many, vm.state.value.attachNoticeKey)

        val vm2 = viewModel()
        vm2.addAttachment(AttachedFile(id = "g1", name = "big", content = "x".repeat(200_000)))
        vm2.addAttachment(AttachedFile(id = "g2", name = "big2", content = "x".repeat(100_000)))

        assertEquals(1, vm2.state.value.pendingAttachments.size)
        assertEquals(R.string.attach_total_too_large, vm2.state.value.attachNoticeKey)
    }

    @Test
    fun `M-217 removing one queued entry returns its text to the draft and keeps the rest`() {
        val store = InMemoryParkedComposerStore()
        val queued = listOf(QueuedPrompt("one", "one"), QueuedPrompt("two", "two"))
        store.put("s1", ParkedComposer(draft = "", attachments = emptyList(), queued = queued))
        val vm = viewModel(store)
        vm.restoreParkedComposer("s1")
        vm.onRepoState(ChatState(sessionID = "s1"))
        // The parked copy still holds the queue (as after a park).
        store.put("s1", ParkedComposer(draft = "", attachments = emptyList(), queued = vm.state.value.queuedPrompts))
        val firstId = vm.state.value.queuedPrompts.first().id

        assertEquals(true, vm.removeQueuedEntry(firstId))

        assertEquals(listOf("two"), vm.state.value.queuedPrompts.map { it.typedText })
        assertEquals("one", vm.state.value.draft)
        assertEquals(listOf("two"), store.get("s1")?.queued?.map { it.typedText })
    }

    @Test
    fun `M-217 removing an unknown id does nothing`() {
        val vm = viewModel()
        assertEquals(false, vm.removeQueuedEntry("nope"))
        assertEquals("", vm.state.value.draft)
    }

    @Test
    fun `M-217 a removed entry appends after existing draft text`() {
        val store = InMemoryParkedComposerStore()
        store.put(
            "s1",
            ParkedComposer(
                draft = "current",
                attachments = emptyList(),
                queued = listOf(QueuedPrompt("one", "one")),
            ),
        )
        val vm = viewModel(store)
        vm.restoreParkedComposer("s1")
        val id = vm.state.value.queuedPrompts.first().id

        vm.removeQueuedEntry(id)

        assertEquals("current\none", vm.state.value.draft)
        assertEquals(true, vm.state.value.queuedPrompts.isEmpty())
    }

    @Test
    fun `M-216 a failed flush plans exactly one delayed retry`() {
        val store = InMemoryParkedComposerStore()
        store.put(
            "s1",
            ParkedComposer(
                draft = "",
                attachments = emptyList(),
                queued = listOf(QueuedPrompt("one", "one"), QueuedPrompt("two", "two")),
            ),
        )
        val vm = viewModel(store)
        vm.restoreParkedComposer("s1")
        vm.onRepoState(ChatState(sessionID = "s1"))

        val retry = vm.planFlushRetry("s1")

        assertEquals(vm.state.value.queuedPrompts.first().id, retry?.entryId)
        assertEquals("s1", retry?.sessionID)
        // One pending is enough — a second failure while it waits must
        // not stack another retry behind it.
        assertNull(vm.planFlushRetry("s1"))
    }

    @Test
    fun `M-216 a retry fires only in the same idle session with the same head`() {
        val store = InMemoryParkedComposerStore()
        store.put(
            "s1",
            ParkedComposer(
                draft = "",
                attachments = emptyList(),
                queued = listOf(QueuedPrompt("one", "one")),
            ),
        )
        val vm = viewModel(store)
        vm.restoreParkedComposer("s1")
        vm.onRepoState(ChatState(sessionID = "s1"))
        val headId = vm.state.value.queuedPrompts.first().id

        assertEquals(false, vm.consumeFlushRetry(ChatViewModel.FlushRetry("s2", headId)))
        vm.onRepoState(ChatState(sessionID = "s1", busy = true))
        assertEquals(false, vm.consumeFlushRetry(ChatViewModel.FlushRetry("s1", headId)))
        vm.onRepoState(ChatState(sessionID = "s1", busy = false))
        assertEquals(false, vm.consumeFlushRetry(ChatViewModel.FlushRetry("s1", "stale-id")))
        assertEquals(true, vm.consumeFlushRetry(ChatViewModel.FlushRetry("s1", headId)))
    }

    @Test
    fun `M-216 a consumed retry spends the budget until a new entry refills it`() {
        val store = InMemoryParkedComposerStore()
        store.put(
            "s1",
            ParkedComposer(
                draft = "",
                attachments = emptyList(),
                queued = listOf(QueuedPrompt("one", "one")),
            ),
        )
        val vm = viewModel(store)
        vm.restoreParkedComposer("s1")
        vm.onRepoState(ChatState(sessionID = "s1"))
        val retry = vm.planFlushRetry("s1")!!
        assertEquals(true, vm.consumeFlushRetry(retry))

        // Budget spent: no second automatic retry.
        assertNull(vm.planFlushRetry("s1"))

        // A newly queued entry refills it.
        vm.onRepoState(ChatState(sessionID = "s1", busy = true))
        vm.onDraftChange("three")
        vm.send()
        assertEquals(2, vm.state.value.queuedPrompts.size)
        assertEquals(true, vm.planFlushRetry("s1") != null)
    }

    @Test
    fun `M-229 a prompt that landed clears the draft holding it instead of duplicating the turn`() {
        val store = InMemoryParkedComposerStore()
        // Process death mid-send: the draft still holds the prompt and
        // the parked retry target names the same text.
        store.put(
            "s1",
            ParkedComposer(
                draft = "fix the bug",
                attachments = emptyList(),
                lastSentText = "fix the bug",
            ),
        )
        val vm = viewModel(store)
        vm.restoreParkedComposer("s1")
        assertEquals("fix the bug", vm.state.value.draft)
        assertEquals("fix the bug", vm.state.value.lastSentText)

        // The first transcript shows the prompt did land.
        vm.onRepoState(ChatState(sessionID = "s1", messages = listOf(user("m1", "fix the bug"))))

        assertEquals("", vm.state.value.draft)
        assertNull(vm.state.value.lastSentText)
    }

    @Test
    fun `M-229 a prompt that did not land keeps the draft as the resend offer`() {
        val store = InMemoryParkedComposerStore()
        store.put(
            "s1",
            ParkedComposer(
                draft = "fix the bug",
                attachments = emptyList(),
                lastSentText = "fix the bug",
            ),
        )
        val vm = viewModel(store)
        vm.restoreParkedComposer("s1")

        // The transcript's last user message is an older prompt — the
        // mid-send prompt never reached the engine.
        vm.onRepoState(ChatState(sessionID = "s1", messages = listOf(user("m0", "something else"))))

        assertEquals("fix the bug", vm.state.value.draft)
        assertEquals("fix the bug", vm.state.value.lastSentText)
    }

    @Test
    fun `M-229 newer typing survives the reconciliation even when the prompt landed`() {
        val store = InMemoryParkedComposerStore()
        store.put(
            "s1",
            ParkedComposer(
                draft = "fix the bug and add tests",
                attachments = emptyList(),
                lastSentText = "fix the bug",
            ),
        )
        val vm = viewModel(store)
        vm.restoreParkedComposer("s1")

        vm.onRepoState(ChatState(sessionID = "s1", messages = listOf(user("m1", "fix the bug"))))

        assertEquals("fix the bug and add tests", vm.state.value.draft)
        assertNull(vm.state.value.lastSentText)
    }

    @Test
    fun `M-229 the reconciliation only trusts the session the prompt belongs to`() {
        val store = InMemoryParkedComposerStore()
        store.put(
            "s1",
            ParkedComposer(
                draft = "fix the bug",
                attachments = emptyList(),
                lastSentText = "fix the bug",
            ),
        )
        val vm = viewModel(store)
        vm.restoreParkedComposer("s1")

        // A different session's transcript — even one that happens to
        // contain the same text — must not clear this session's draft.
        vm.onRepoState(ChatState(sessionID = "s2", messages = listOf(user("m1", "fix the bug"))))

        assertEquals("fix the bug", vm.state.value.draft)
    }

    @Test
    fun `M-229 the draft rule keeps only the exact prompt or its typed text`() {
        assertEquals(true, draftStillHoldsSentPrompt("fix the bug", "fix the bug"))
        assertEquals(
            true,
            draftStillHoldsSentPrompt(
                "fix the bug",
                "fix the bug\n\n```path notes.md\ncontent\n```",
            ),
        )
        assertEquals(false, draftStillHoldsSentPrompt("fix the bug and add tests", "fix the bug"))
        assertEquals(false, draftStillHoldsSentPrompt("", "fix the bug"))
        assertEquals(false, draftStillHoldsSentPrompt("something else", "fix the bug"))
    }

    @Test
    fun `M-232 onResume without a repository records the restore instead of exiting silently`() {
        val vm = viewModel()

        vm.onResume("s1")

        // The restore is not lost: it waits for the next attach instead
        // of never happening, and nothing pretends an error occurred.
        assertEquals("s1", vm.pendingRestoreForTest())
        assertNull(vm.state.value.sessionID)
        assertNull(vm.state.value.failure)
        assertNull(vm.state.value.failureKey)
    }

    @Test
    fun `M-232 a pending restore clears once a session is open`() {
        val vm = viewModel()
        vm.onResume("s1")
        assertEquals("s1", vm.pendingRestoreForTest())

        vm.onRepoState(ChatState(sessionID = "s2"))
        vm.onResume("s1")

        assertNull(vm.pendingRestoreForTest())
    }

    @Test
    fun `M-232 a fresh session drops a pending restore`() {
        val vm = viewModel()
        vm.onResume("s1")
        assertEquals("s1", vm.pendingRestoreForTest())

        vm.newSession()

        assertNull(vm.pendingRestoreForTest())
        assertNull(vm.pendingOpenForTest())
    }

    @Test
    fun `M-227 a deferred open banners instead of opening unchecked, and keeps the pending id for retry`() {
        val vm = viewModel()
        // A deep link that arrived before attach waits as a pending open.
        vm.openSession("s9")
        assertEquals("s9", vm.pendingOpenForTest())

        vm.deferSessionOpen()

        assertEquals(R.string.session_link_verify_failed, vm.state.value.failureKey)
        assertEquals(RetryKind.NONE, vm.state.value.retryKind)
        // Kept: the next attach re-verifies it instead of the link dying.
        assertEquals("s9", vm.pendingOpenForTest())
        assertNull(vm.state.value.sessionID)
    }

    @Test
    fun `M-233 a repo failure after the engine died keeps the restart action`() {
        val vm = viewModel()
        vm.onEngineStatus(EngineStatus(EngineState.RUNNING))
        vm.onEngineStatus(EngineStatus(EngineState.FAILED, lastError = "engine exited (code 1)"))
        assertEquals(RetryKind.RESTART_ENGINE, vm.state.value.retryKind)
        // The repository's stream dies after the engine does, and its
        // failure emission used to overwrite the restart action with
        // NONE — the banner then showed "engine not running" with no
        // button at all, exactly the reported symptom.
        vm.onRepoState(ChatState(lastFailure = ApiResult.Failure(code = 0, message = "Connection refused")))
        assertEquals(RetryKind.RESTART_ENGINE, vm.state.value.retryKind)
        assertEquals(R.string.error_engine_not_running, vm.state.value.failureKey)
    }

    @Test
    fun `M-233 a failure before the engine ever ran keeps its own retry kind`() {
        // The engine starts STOPPED: a failure on a view model whose
        // engine never ran is a plain failure, not a dead engine — the
        // restart action must not swallow the real retry kind.
        val vm = viewModel()
        vm.onRepoState(ChatState(lastFailure = ApiResult.Failure(code = 500, message = "boom")))
        assertEquals(RetryKind.NONE, vm.state.value.retryKind)
        vm.onRepoState(ChatState(lastFailure = ApiResult.Failure(code = 429)))
        assertEquals(RetryKind.RATE_LIMITED, vm.state.value.retryKind)
    }
}
