package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.PendingPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C5, M-093: the parked composer now carries the retry
 * and history state (sent prompts, last sent text, the deferred
 * permission), so switching away and coming back restores it — and
 * the delete-path merge deliberately does NOT carry it.
 */
class ParkedComposerC5Test {

    private fun parkedPermission() = PendingPermission(
        id = "p1", sessionID = "s1", action = "read",
        resources = setOf("a.txt"), save = emptySet(), message = "read a.txt?",
    ).toParked()

    @Test
    fun `M-093 the parked round-trip keeps the retry state and the deferred permission`() {
        val store = InMemoryParkedComposerStore()
        val entry = ParkedComposer(
            draft = "half typed",
            attachments = emptyList(),
            sentPrompts = listOf("first prompt", "second prompt"),
            lastSentText = "second prompt",
            deferredPermission = parkedPermission(),
        )
        store.put("s1", entry)
        val restored = store.get("s1")
        assertEquals("half typed", restored?.draft)
        assertEquals(listOf("first prompt", "second prompt"), restored?.sentPrompts)
        assertEquals("second prompt", restored?.lastSentText)
        assertEquals("p1", restored?.deferredPermission?.id)
        assertEquals("read", restored?.deferredPermission?.action)
        assertEquals(setOf("a.txt"), restored?.deferredPermission?.resources)
        // The snapshot drops the metadata (not serialisable); the
        // restore rebuilds with an empty map, never a null.
        assertEquals(emptyMap<String, kotlinx.serialization.json.JsonElement>(), restored?.deferredPermission?.toPending()?.metadata)
    }

    @Test
    fun `M-093 the delete-path merge drops the retry state on purpose`() {
        // Moving unsent work to __unsent__ after a delete must not hand
        // a fresh session the dead session's history or its permission.
        val dead = ParkedComposer(
            draft = "unsent",
            attachments = emptyList(),
            sentPrompts = listOf("old prompt"),
            lastSentText = "old prompt",
            deferredPermission = parkedPermission(),
        )
        val merged = mergeParkedComposers(ParkedComposer(draft = "", attachments = emptyList()), dead)
        assertEquals("unsent", merged?.draft)
        assertTrue(merged?.sentPrompts.isNullOrEmpty())
        assertNull(merged?.lastSentText)
        assertNull(merged?.deferredPermission)
    }

    @Test
    fun `M-093 a drained queue keeps the entry while retry state remains`() {
        val store = InMemoryParkedComposerStore()
        store.put(
            "s1",
            ParkedComposer(
                draft = "",
                attachments = emptyList(),
                sentPrompts = listOf("retry me"),
                lastSentText = "retry me",
            ),
        )
        // The queue drains (the entry was sent) — but the retry state
        // is still live, so the entry must not vanish.
        val entry = store.get("s1")!!
        store.removeQueued("s1", QueuedPrompt(fullText = "retry me", typedText = "retry me", id = "q1"))
        assertEquals(listOf("retry me"), store.get("s1")?.sentPrompts)
        assertEquals("retry me", store.get("s1")?.lastSentText)
        assertEquals(entry.draft, store.get("s1")?.draft)
    }

    @Test
    fun `M-093 a truly empty entry is removed once its queue drains`() {
        val store = InMemoryParkedComposerStore()
        store.put(
            "s1",
            ParkedComposer(
                draft = "",
                attachments = emptyList(),
                queued = listOf(QueuedPrompt(fullText = "only prompt", typedText = "only prompt", id = "q1")),
            ),
        )
        store.removeQueued("s1", QueuedPrompt(fullText = "only prompt", typedText = "only prompt", id = "q1"))
        assertNull(store.get("s1"))
    }
}
