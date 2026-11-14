package dev.bidzz.opencode.ui.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6f: quick-open indexing (35-F7/F9) and the explorer hidden
 * filter (89-F6) — pure predicates, no engine needed.
 */
class QuickOpenFilterTest {

    @Test
    fun `35-F7 dot-directories are indexed unless explicitly skipped`() {
        assertTrue(shouldIndexDirectory(".github"))
        assertTrue(shouldIndexDirectory(".vscode"))
        assertTrue(shouldIndexDirectory(".config"))
        assertTrue(shouldIndexDirectory("src"))
    }

    @Test
    fun `35-F7 generated and VCS directories stay skipped`() {
        assertFalse(shouldIndexDirectory(".git"))
        assertFalse(shouldIndexDirectory(".gradle"))
        assertFalse(shouldIndexDirectory(".idea"))
        assertFalse(shouldIndexDirectory("node_modules"))
        assertFalse(shouldIndexDirectory("build"))
    }

    @Test
    fun `35-F9 a walk that ends because the tree ended is not truncated`() {
        assertFalse(quickOpenWalkTruncated(queueRemaining = 0, foundSize = 2000, dirsVisited = 200))
        assertFalse(quickOpenWalkTruncated(queueRemaining = 0, foundSize = 12, dirsVisited = 3))
    }

    @Test
    fun `35-F9 a walk that stops on a size limit with dirs queued is truncated`() {
        assertTrue(quickOpenWalkTruncated(queueRemaining = 5, foundSize = 2000, dirsVisited = 50))
        assertTrue(quickOpenWalkTruncated(queueRemaining = 3, foundSize = 100, dirsVisited = 200))
    }

    @Test
    fun `35-F9 queued dirs under both limits are not truncation`() {
        // The loop only exits early on a limit; queued dirs with room left
        // cannot happen — but if they did, the predicate must not claim a
        // truncation it cannot prove.
        assertFalse(quickOpenWalkTruncated(queueRemaining = 3, foundSize = 100, dirsVisited = 50))
    }

    @Test
    fun `M-139 a flat directory that drops files at the cap is truncated even with an empty queue`() {
        // A workspace with 3,000 files in one directory fills `found` while
        // the queue is already empty: without the dropped-entries flag the
        // walk looked complete and the partial index was served as whole.
        assertTrue(quickOpenWalkTruncated(queueRemaining = 0, foundSize = 2000, dirsVisited = 1, droppedEntries = true))
        assertTrue(quickOpenWalkTruncated(queueRemaining = 0, foundSize = 2000, dirsVisited = 200, droppedEntries = true))
    }

    @Test
    fun `M-139 no dropped entries means an empty queue is still a complete walk`() {
        assertFalse(quickOpenWalkTruncated(queueRemaining = 0, foundSize = 2000, dirsVisited = 1, droppedEntries = false))
        assertFalse(quickOpenWalkTruncated(queueRemaining = 0, foundSize = 12, dirsVisited = 3, droppedEntries = false))
    }

    @Test
    fun `89-F6 dot-directories are hidden except github and vscode`() {
        assertTrue(isHiddenDirectory(".git", isDirectory = true))
        assertTrue(isHiddenDirectory(".idea", isDirectory = true))
        assertTrue(isHiddenDirectory(".gradle", isDirectory = true))
        assertFalse(isHiddenDirectory(".github", isDirectory = true))
        assertFalse(isHiddenDirectory(".vscode", isDirectory = true))
    }

    @Test
    fun `89-F6 dot-files and plain directories stay visible`() {
        assertFalse(isHiddenDirectory(".gitignore", isDirectory = false))
        assertFalse(isHiddenDirectory(".env", isDirectory = false))
        assertFalse(isHiddenDirectory("src", isDirectory = true))
    }

    @Test
    fun `89-F4 the switch dialog hides while the conflict dialog shows`() {
        assertTrue(shouldShowSwitchDialog("b.kt", conflict = false, editing = true))
        assertFalse(shouldShowSwitchDialog("b.kt", conflict = true, editing = true))
        // A conflict flag without an editor behind it shows no dialog anyway.
        assertTrue(shouldShowSwitchDialog("b.kt", conflict = true, editing = false))
    }
}
