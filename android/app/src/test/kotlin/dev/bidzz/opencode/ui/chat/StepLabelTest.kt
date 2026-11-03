package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.ui.Glyph
import org.junit.Assert.assertEquals
import org.junit.Test

class StepLabelTest {

    @Test
    fun `a finished read names the file, not the whole path`() {
        val label = stepLabel("read", "/data/user/0/dev.bidzz.opencode/files/workspace/README.md", "completed")
        assertEquals("Read README.md", label.text)
        assertEquals(Glyph.File, label.glyph)
    }

    @Test
    fun `a running step is in the present tense`() {
        assertEquals("Reading README.md", stepLabel("read", "/a/b/README.md", "running").text)
        assertEquals("Running npm test", stepLabel("shell", "npm test", "streaming").text)
    }

    @Test
    fun `a command keeps its text and is capped`() {
        assertEquals("Ran npm test", stepLabel("bash", "npm test", "completed").text)
        val long = "x".repeat(200)
        assertEquals("Ran " + "x".repeat(56), stepLabel("bash", long, "completed").text)
    }

    @Test
    fun `a windows style path still yields the file name`() {
        assertEquals("Edited a.kt", stepLabel("edit", "C:\\src\\a.kt", "completed").text)
    }

    @Test
    fun `an unknown tool falls back to its own name`() {
        assertEquals("Todowrite", stepLabel("todowrite", "", "completed").text)
        assertEquals("Foo bar", stepLabel("foo", "bar", "completed").text)
    }

    @Test
    fun `a tool with no input reads as just the verb`() {
        assertEquals("Read", stepLabel("read", "", "completed").text)
    }
}
