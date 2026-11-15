package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit-200 D7 (m-602/m-1104): the anchor snippet and the expandable file
 * list of the staged-revert banner, as pure functions.
 */
class StagedRevertD7Test {

    // ---- stagedRevertTargetText (m-602) ----

    @Test
    fun `m-602 a short anchor message passes through unchanged`() {
        assertEquals("fix the login", stagedRevertTargetText("fix the login"))
    }

    @Test
    fun `m-602 a long anchor message is cut at 80 characters with an ellipsis`() {
        val long = "a".repeat(100)
        val cut = stagedRevertTargetText(long)
        assertEquals("a".repeat(80) + "…", cut)
    }

    @Test
    fun `m-602 exactly 80 characters is not cut`() {
        val exact = "b".repeat(80)
        assertEquals(exact, stagedRevertTargetText(exact))
    }

    @Test
    fun `m-602 a missing or blank anchor yields null for the honest fallback`() {
        assertNull(stagedRevertTargetText(null))
        assertNull(stagedRevertTargetText(""))
        assertNull(stagedRevertTargetText("   "))
    }

    @Test
    fun `m-602 surrounding whitespace is trimmed before cutting`() {
        assertEquals("hello", stagedRevertTargetText("  hello  "))
    }

    // ---- revertBannerFileLines (m-1104) ----

    @Test
    fun `m-1104 no files yields an empty list`() {
        val list = revertBannerFileLines(emptyList())
        assertEquals(emptyList<String>(), list.shown)
        assertEquals(0, list.remaining)
    }

    @Test
    fun `m-1104 three files all show`() {
        val list = revertBannerFileLines(listOf("a.kt", "b.kt", "c.kt"))
        assertEquals(listOf("a.kt", "b.kt", "c.kt"), list.shown)
        assertEquals(0, list.remaining)
    }

    @Test
    fun `m-1104 eight files show the first five plus three more`() {
        val paths = (1..8).map { "file$it.kt" }
        val list = revertBannerFileLines(paths)
        assertEquals(listOf("file1.kt", "file2.kt", "file3.kt", "file4.kt", "file5.kt"), list.shown)
        assertEquals(3, list.remaining)
    }

    @Test
    fun `m-1104 exactly five files show with nothing more`() {
        val paths = (1..5).map { "file$it.kt" }
        val list = revertBannerFileLines(paths)
        assertEquals(paths, list.shown)
        assertEquals(0, list.remaining)
    }
}
