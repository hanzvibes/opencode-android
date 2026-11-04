package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeKindTest {
    @Test
    fun `file names follow the language`() {
        assertEquals("index.html", suggestedFileName("html"))
        assertEquals("code.kt", suggestedFileName("Kotlin"))
        assertEquals("code.py", suggestedFileName("python"))
        assertEquals("code.txt", suggestedFileName(null))
        assertEquals("code.txt", suggestedFileName("brainfuck"))
    }
}
