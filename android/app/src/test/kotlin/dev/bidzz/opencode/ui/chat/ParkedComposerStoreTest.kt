package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 1a (57-F1, 13-F6): parked composers survive process death on disk.
 * The encoding is pure JVM (`java.util.Base64` + Java serialisation, the
 * same mechanism the saved-state handle already uses for the live queue),
 * so the round-trip is tested here rather than through SharedPreferences
 * (which needs Robolectric) or a view model.
 */
class ParkedComposerStoreTest {

    private fun composer() = ParkedComposer(
        draft = "cek juga X",
        attachments = listOf(AttachedFile("a1", "notes.txt", "isi file")),
        queued = listOf(
            QueuedPrompt("follow-up satu", "follow-up satu"),
            QueuedPrompt(
                "follow-up dua",
                "follow-up dua",
                listOf(AttachedFile("a2", "lain.txt", "lain")),
            ),
        ),
    )

    @Test
    fun `encode then decode returns the same composer`() {
        // Audit-200 A5 (DL-001): entries carry random ids now, so the
        // expected value must be the same instance, not a fresh build.
        val original = composer()
        val decoded = decodeParkedComposer(encodeParkedComposer(original))

        assertEquals(original, decoded)
    }

    @Test
    fun `decode of garbage returns null instead of crashing`() {
        assertNull(decodeParkedComposer("bukan-base64!!!"))
        assertNull(decodeParkedComposer("aGVsbG8"))
    }

    @Test
    fun `in-memory store puts gets and removes`() {
        val store = InMemoryParkedComposerStore()
        val original = composer()

        store.put("s1", original)

        assertEquals(original, store.get("s1"))
        store.remove("s1")
        assertNull(store.get("s1"))
    }

    @Test
    fun `removeQueued drops only the matching entry`() {
        val store = InMemoryParkedComposerStore()
        val first = QueuedPrompt("satu", "satu")
        val second = QueuedPrompt("dua", "dua")
        store.put("s1", ParkedComposer("", emptyList(), listOf(first, second)))

        store.removeQueued("s1", first)

        assertEquals(listOf(second), store.get("s1")?.queued)
    }

    @Test
    fun `removeQueued removes the entry entirely when it becomes empty`() {
        val store = InMemoryParkedComposerStore()
        val only = QueuedPrompt("satu", "satu")
        store.put("s1", ParkedComposer("", emptyList(), listOf(only)))

        store.removeQueued("s1", only)

        assertNull(store.get("s1"))
    }

    @Test
    fun `removeQueued is a no-op when the entry is absent`() {
        val store = InMemoryParkedComposerStore()
        val original = composer()
        store.put("s1", original)

        store.removeQueued("s1", QueuedPrompt("tidak ada", "tidak ada"))
        store.removeQueued("tidak ada", QueuedPrompt("satu", "satu"))

        assertEquals(original, store.get("s1"))
        assertTrue(store.get("s1")?.queued?.size == 2)
    }

    @Test
    fun `DL-001 removeQueued drops only one entry when two share the same text`() {
        val store = InMemoryParkedComposerStore()
        val first = QueuedPrompt("sama", "sama")
        val second = QueuedPrompt("sama", "sama")
        store.put("s1", ParkedComposer("", emptyList(), listOf(first, second)))

        store.removeQueued("s1", first)

        assertEquals(listOf(second), store.get("s1")?.queued)
    }

    /**
     * Audit-200 A3 (C-007, PROVEN): a composer parked before Fase 1a
     * has no `queued` field in its stored bytes; Java deserialization
     * fills the missing field with null instead of the Kotlin default.
     * The bytes of a pre-1a build cannot be produced from this class
     * anymore, so the test reproduces that null field directly with
     * reflection and proves the read boundary normalizes it and the
     * flush path survives it.
     */
    private fun pre1aComposer(): ParkedComposer {
        val composer = ParkedComposer("draft lama", emptyList())
        val field = ParkedComposer::class.java.getDeclaredField("queued")
        field.isAccessible = true
        field.set(composer, null)
        return composer
    }

    @Test
    fun `decode of a pre-1a composer treats a null queue as empty`() {
        val decoded = decodeParkedComposer(encodeParkedComposer(pre1aComposer()))

        assertEquals("draft lama", decoded?.draft)
        assertEquals(emptyList<QueuedPrompt>(), decoded?.queued)
    }

    @Test
    fun `removeQueued on a pre-1a composer does not crash and keeps the draft`() {
        val store = InMemoryParkedComposerStore()
        store.put("s1", pre1aComposer())

        store.removeQueued("s1", QueuedPrompt("follow-up", "follow-up"))

        assertEquals("draft lama", store.get("s1")?.draft)
    }
}
