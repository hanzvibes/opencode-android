package dev.bidzz.opencode.ui

import dev.bidzz.opencode.ui.files.conflictChanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase A6 (DL-022): the conflict dialog shows the agent's edit.
 *
 * Pure JVM: what changed on disk between the version the editor opened
 * and what the file holds now. The save-job cancel (DL-021), the draft
 * surviving a failed origin load (DL-023) and the queue ordering
 * (DL-024/DL-025/DL-026) are covered in FileViewSaveTest and
 * CodeSaveQueueTest; the photo trace (DL-027) needs the framework and is
 * verified by compile + the fast-verify run.
 */
class AuditA6Test {

    @Test
    fun `identical texts have no changes`() {
        val changes = conflictChanges("one\ntwo\n", "one\ntwo\n")

        assertEquals(0, changes.added)
        assertEquals(0, changes.removed)
        assertTrue(changes.shownLines.isEmpty())
        assertFalse(changes.tooLarge)
    }

    @Test
    fun `added lines are counted and shown with a plus`() {
        val changes = conflictChanges(
            "one\ntwo\n",
            "one\ntwo\nthree\nfour\n",
        )

        assertEquals(2, changes.added)
        assertEquals(0, changes.removed)
        assertEquals(listOf("+ three", "+ four"), changes.shownLines)
        assertEquals(0, changes.moreLines)
    }

    @Test
    fun `removed lines are counted and shown with a minus`() {
        val changes = conflictChanges(
            "one\ntwo\nthree\n",
            "one\n",
        )

        assertEquals(0, changes.added)
        assertEquals(2, changes.removed)
        assertEquals(listOf("− two", "− three"), changes.shownLines)
    }

    @Test
    fun `an insertion in the middle shows only the changed lines`() {
        val changes = conflictChanges(
            "a\nb\nc\nd\ne\n",
            "a\nb\nNEW\nc\nd\ne\n",
        )

        assertEquals(1, changes.added)
        assertEquals(0, changes.removed)
        assertEquals(listOf("+ NEW"), changes.shownLines)
    }

    @Test
    fun `shown lines are capped and the rest counted`() {
        val base = (1..3).joinToString("\n") { "line $it" } + "\n"
        val disk = base + (4..20).joinToString("\n") { "added $it" } + "\n"
        val changes = conflictChanges(base, disk, maxShown = 5)

        assertEquals(17, changes.added)
        assertEquals(5, changes.shownLines.size)
        assertEquals(12, changes.moreLines)
    }

    @Test
    fun `a file too large for an exact diff reports tooLarge`() {
        val base = (1..400).joinToString("\n") { "line $it" } + "\n"
        val disk = base + "one more\n"
        val changes = conflictChanges(base, disk)

        assertTrue(changes.tooLarge)
        assertTrue(changes.shownLines.isEmpty())
    }
}
