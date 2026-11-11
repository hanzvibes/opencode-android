package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeBlockSaveTest {

    @Test
    fun `filename follows the fence language`() {
        assertEquals("snippet.kt", codeBlockFileName("kotlin"))
        assertEquals("snippet.py", codeBlockFileName("python"))
        assertEquals("snippet.js", codeBlockFileName("javascript"))
        assertEquals("snippet.ts", codeBlockFileName("ts"))
        assertEquals("snippet.sh", codeBlockFileName("bash"))
        assertEquals("snippet.json", codeBlockFileName("json"))
        assertEquals("snippet.md", codeBlockFileName("markdown"))
        assertEquals("snippet.yaml", codeBlockFileName("yml"))
    }

    @Test
    fun `a fence suffix never leaks into the filename`() {
        assertEquals("snippet.kt", codeBlockFileName("kotlin:Main.kt"))
        assertEquals("snippet.py", codeBlockFileName("python /app/main.py"))
    }

    @Test
    fun `missing or unknown languages save as plain text`() {
        assertEquals("snippet.txt", codeBlockFileName(null))
        assertEquals("snippet.txt", codeBlockFileName(""))
        assertEquals("snippet.txt", codeBlockFileName("brainfuck"))
    }

    @Test
    fun `dockerfile keeps its conventional name`() {
        assertEquals("Dockerfile", codeBlockFileName("dockerfile"))
    }
}
