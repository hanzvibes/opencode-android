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
        val decoded = decodeParkedComposer(encodeParkedComposer(composer()))

        assertEquals(composer(), decoded)
    }

    @Test
    fun `decode of garbage returns null instead of crashing`() {
        assertNull(decodeParkedComposer("bukan-base64!!!"))
        assertNull(decodeParkedComposer("aGVsbG8"))
    }

    @Test
    fun `in-memory store puts gets and removes`() {
        val store = InMemoryParkedComposerStore()

        store.put("s1", composer())

        assertEquals(composer(), store.get("s1"))
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
        store.put("s1", composer())

        store.removeQueued("s1", QueuedPrompt("tidak ada", "tidak ada"))
        store.removeQueued("tidak ada", QueuedPrompt("satu", "satu"))

        assertEquals(composer(), store.get("s1"))
        assertTrue(store.get("s1")?.queued?.size == 2)
    }
}
