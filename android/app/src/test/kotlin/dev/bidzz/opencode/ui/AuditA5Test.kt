package dev.bidzz.opencode.ui

import dev.bidzz.opencode.ui.chat.AttachedFile
import dev.bidzz.opencode.ui.chat.ParkedComposer
import dev.bidzz.opencode.ui.chat.QueuedPrompt
import dev.bidzz.opencode.ui.chat.decodeParkedComposer
import dev.bidzz.opencode.ui.chat.encodeParkedComposer
import dev.bidzz.opencode.ui.chat.mergeParkedComposers
import dev.bidzz.opencode.ui.chat.withUniqueIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase A5: the ParkedComposerStore family (DL-001, DL-004,
 * DL-007, DL-008, DL-009, DL-020, DL-030).
 *
 * Pure JVM: the identity rule (entries match by id, never by text),
 * the read-boundary migration for entries written before ids existed,
 * and the merge used when a deleted session's draft is rescued. The
 * session-switch guards (DL-007/DL-030), the anchor surviving typing
 * (DL-020) and the delete-to-unsent move (DL-009) are exercised through
 * the view model in ChatDraftParkingTest; the synchronous write
 * (DL-004, commit) and the biometric-style decode-failure notice
 * (DL-008, banner via failureKey) need SharedPreferences / the
 * framework and are verified by compile + the fast-verify run.
 */
class AuditA5Test {

    /** A queued entry whose id was never written (pre-A5 bytes), via reflection. */
    private fun legacyEntry(text: String): QueuedPrompt {
        val entry = QueuedPrompt(text, text)
        val field = QueuedPrompt::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(entry, null)
        return entry
    }

    @Test
    fun `fresh entries get distinct ids`() {
        val first = QueuedPrompt("sama", "sama")
        val second = QueuedPrompt("sama", "sama")

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `withUniqueIds gives a legacy entry an id and keeps existing ones`() {
        val legacy = legacyEntry("lama")
        val current = QueuedPrompt("baru", "baru")

        val fixed = listOf(legacy, current).withUniqueIds()

        assertNotNull(fixed[0].id)
        assertEquals(current.id, fixed[1].id)
        assertNotEquals(fixed[0].id, fixed[1].id)
    }

    @Test
    fun `withUniqueIds gives two legacy entries different ids`() {
        val fixed = listOf(legacyEntry("sama"), legacyEntry("sama")).withUniqueIds()

        assertNotNull(fixed[0].id)
        assertNotNull(fixed[1].id)
        assertNotEquals(fixed[0].id, fixed[1].id)
    }

    // DL-036: entries written before attachments existed carry a null
    // list at runtime, the same deserialization bypass as the id --------

    /** A queued entry whose attachments were never written, via reflection. */
    private fun legacyEntryWithoutAttachments(text: String): QueuedPrompt {
        val entry = QueuedPrompt(text, text)
        val field = QueuedPrompt::class.java.getDeclaredField("attachments")
        field.isAccessible = true
        field.set(entry, null)
        return entry
    }

    @Test
    fun `withUniqueIds gives a legacy entry without attachments an empty list`() {
        val fixed = listOf(legacyEntryWithoutAttachments("lama")).withUniqueIds()

        assertEquals(emptyList<AttachedFile>(), fixed[0].attachments)
        assertNotNull(fixed[0].id)
    }

    @Test
    fun `withUniqueIds fixes an entry with both a null id and null attachments`() {
        val entry = legacyEntry("lama")
        val field = QueuedPrompt::class.java.getDeclaredField("attachments")
        field.isAccessible = true
        field.set(entry, null)

        val fixed = listOf(entry).withUniqueIds()

        assertNotNull(fixed[0].id)
        assertEquals(emptyList<AttachedFile>(), fixed[0].attachments)
    }

    @Test
    fun `decode of a pre-id composer gives its entries fresh distinct ids`() {
        val composer = ParkedComposer(
            draft = "draft lama",
            attachments = emptyList(),
            queued = listOf(legacyEntry("satu"), legacyEntry("satu")),
        )
        val decoded = decodeParkedComposer(encodeParkedComposer(composer))

        assertEquals("draft lama", decoded?.draft)
        val ids = decoded?.queued?.map { it.id } ?: emptyList()
        assertEquals(2, ids.size)
        assertTrue(ids.all { it.isNotBlank() })
        assertNotEquals(ids[0], ids[1])
    }

    @Test
    fun `merge joins drafts unions attachments and concatenates queues`() {
        val first = ParkedComposer(
            draft = "draft satu",
            attachments = listOf(AttachedFile("a1", "satu.txt", "satu")),
            queued = listOf(QueuedPrompt("antre satu", "antre satu")),
        )
        val second = ParkedComposer(
            draft = "draft dua",
            attachments = listOf(
                AttachedFile("a1", "satu.txt", "satu"),
                AttachedFile("a2", "dua.txt", "dua"),
            ),
            queued = listOf(QueuedPrompt("antre dua", "antre dua")),
        )

        val merged = mergeParkedComposers(first, second)

        assertEquals("draft satu\ndraft dua", merged.draft)
        assertEquals(listOf("a1", "a2"), merged.attachments.map { it.id })
        assertEquals(2, merged.queued.size)
        assertEquals("antre satu", merged.queued[0].typedText)
        assertEquals("antre dua", merged.queued[1].typedText)
    }

    @Test
    fun `merge keeps the non-blank draft when one side is empty`() {
        val merged = mergeParkedComposers(
            ParkedComposer("", emptyList(), emptyList()),
            ParkedComposer("hanya ini", emptyList(), emptyList()),
        )

        assertEquals("hanya ini", merged.draft)
        assertTrue(merged.attachments.isEmpty())
        assertTrue(merged.queued.isEmpty())
    }

    @Test
    fun `merge of two empty composers is empty`() {
        val merged = mergeParkedComposers(
            ParkedComposer("", emptyList(), emptyList()),
            ParkedComposer("", emptyList(), emptyList()),
        )

        assertEquals("", merged.draft)
        assertTrue(merged.attachments.isEmpty())
        assertTrue(merged.queued.isEmpty())
    }
}
