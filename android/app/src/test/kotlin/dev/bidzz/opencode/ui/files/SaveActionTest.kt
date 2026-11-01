package dev.bidzz.opencode.ui.files

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The decision behind the editor's save button.
 *
 * The engine's write endpoint replaces the whole file, so the only thing standing
 * between a save and a deleted agent edit is this table. Each case here is a pair of
 * versions that look similar on screen but must not do the same thing.
 */
class SaveActionTest {

    @Test
    fun `an untouched draft never writes`() {
        // The user typed something, then undid it, and meanwhile the agent rewrote the
        // file. Writing the draft would push the older text over the agent's work, so
        // "no change" has to win over everything else.
        assertEquals(SaveAction.Unchanged, saveAction("same", "same", "agent version"))
    }

    @Test
    fun `an untouched draft never writes even when told to overwrite`() {
        assertEquals(SaveAction.Unchanged, saveAction("same", "same", "agent version", overwrite = true))
    }

    @Test
    fun `a file changed underneath is a conflict`() {
        assertEquals(SaveAction.Conflict, saveAction("loaded", "typed", "loaded by someone else"))
    }

    @Test
    fun `a file still as it was loaded takes the draft`() {
        assertEquals(SaveAction.Write, saveAction("loaded", "typed", "loaded"))
    }

    @Test
    fun `overwrite is the user's answer to a conflict, never a default`() {
        assertEquals(
            SaveAction.Write,
            saveAction("loaded", "typed", "changed", overwrite = true),
        )
    }

    @Test
    fun `a file that cannot be re-read blocks the write`() {
        // Not being able to check is not permission to guess: one retry costs the user
        // nothing, guessing wrong costs them the agent's edit.
        assertEquals(SaveAction.Unverifiable, saveAction("loaded", "typed", null))
    }

    @Test
    fun `an empty file that gains content is a write, not an unchanged draft`() {
        assertEquals(SaveAction.Write, saveAction("", "new text", ""))
    }

    @Test
    fun `a whitespace-only edit is still an edit`() {
        // Control for the two tests above, and a trap in itself: comparing trimmed
        // would report this as unchanged and make trailing whitespace unsavable.
        assertEquals(SaveAction.Write, saveAction("line\n", "line\n ", "line\n"))
    }

    @Test
    fun `a conflict on an empty file is still a conflict`() {
        assertEquals(SaveAction.Conflict, saveAction("", "typed", "agent wrote this"))
    }
}
