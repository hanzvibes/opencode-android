package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.ui.Glyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the step label is made of, not how it is worded.
 *
 * The wording is a string resource per language, so asserting "Read README.md" here would
 * only prove that English is English. The parts are what this function decides.
 */
class StepLabelTest {

    @Test
    fun `a finished read names the file and its folder, not the whole path`() {
        // P2 (A-31): the target is the last two path segments, so the folder
        // disambiguates files with the same name in different folders.
        val label = stepLabel("read", "/data/user/0/dev.bidzz.opencode/files/workspace/README.md", "completed")
        assertEquals(Glyph.File, label.glyph)
        assertEquals(StepVerb.READ, label.verb)
        assertEquals("workspace/README.md", label.target)
        assertFalse(label.active)
        assertFalse(label.literal)
    }

    @Test
    fun `a running step is in the present tense`() {
        val running = stepLabel("read", "/a/b/README.md", "running")
        assertTrue(running.active)
        assertEquals(StepVerb.READ, running.verb)

        val streaming = stepLabel("shell", "npm test", "streaming")
        assertTrue(streaming.active)
        assertEquals(StepVerb.RUN, streaming.verb)
        assertEquals("npm test", streaming.target)
    }

    @Test
    fun `a command keeps its text and is capped`() {
        val done = stepLabel("bash", "npm test", "completed")
        assertEquals(StepVerb.RUN, done.verb)
        assertEquals("npm test", done.target)
        assertFalse(done.active)

        val long = "x".repeat(200)
        // P2 (A-31): truncation is marked with "…" instead of silently
        // cutting the text off, so the cap is 55 chars + "…" (56 total).
        assertEquals("x".repeat(55) + "…", stepLabel("bash", long, "completed").target)
    }

    @Test
    fun `a windows style path still yields the file and its folder`() {
        // Same last-two-segments rule as Unix paths (see A-31 above).
        assertEquals("src/a.kt", stepLabel("edit", "C:\\src\\a.kt", "completed").target)
    }

    @Test
    fun `an unknown tool falls back to its own name`() {
        val unknown = stepLabel("todowrite", "", "completed")
        assertEquals(StepVerb.OTHER, unknown.verb)
        assertTrue(unknown.literal)
        assertEquals("Todowrite", unknown.target)

        assertEquals("Foo bar", stepLabel("foo", "bar", "completed").target)
    }

    @Test
    fun `a tool with no input has an empty target`() {
        val label = stepLabel("read", "", "completed")
        assertEquals(StepVerb.READ, label.verb)
        assertEquals("", label.target)
    }
}
