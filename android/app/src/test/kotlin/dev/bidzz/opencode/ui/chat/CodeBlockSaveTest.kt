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

    // Audit-200 D4 (m-076): the highlighter knows these tags, so Save must
    // not fall back to snippet.txt for them anymore.
    @Test
    fun `languages the highlighter knows save with their own extension`() {
        assertEquals("snippet.cpp", codeBlockFileName("c++"))
        assertEquals("snippet.cs", codeBlockFileName("c#"))
        assertEquals("snippet.dart", codeBlockFileName("dart"))
        assertEquals("snippet.scala", codeBlockFileName("scala"))
        assertEquals("snippet.kt", codeBlockFileName("kts"))
        assertEquals("snippet.js", codeBlockFileName("mjs"))
        assertEquals("snippet.h", codeBlockFileName("h"))
        assertEquals("snippet.hpp", codeBlockFileName("hpp"))
        assertEquals("snippet.sh", codeBlockFileName("console"))
        assertEquals("snippet.sh", codeBlockFileName("terminal"))
    }

    @Test
    fun `docker saves as Dockerfile`() {
        assertEquals("Dockerfile", codeBlockFileName("docker"))
    }

    @Test
    fun `console and terminal stay Save-only`() {
        assertEquals(false, isRunnableCodeBlock("console"))
        assertEquals(false, isRunnableCodeBlock("terminal"))
    }

    @Test
    fun `only shell fences are runnable`() {
        assertEquals(true, isRunnableCodeBlock("sh"))
        assertEquals(true, isRunnableCodeBlock("bash"))
        assertEquals(true, isRunnableCodeBlock("zsh"))
        assertEquals(true, isRunnableCodeBlock("shell"))
        assertEquals(true, isRunnableCodeBlock("Bash"))
        assertEquals(true, isRunnableCodeBlock("bash:/tmp/run.sh"))
        assertEquals(false, isRunnableCodeBlock("python"))
        assertEquals(false, isRunnableCodeBlock("javascript"))
        assertEquals(false, isRunnableCodeBlock("kotlin"))
        assertEquals(false, isRunnableCodeBlock(null))
        assertEquals(false, isRunnableCodeBlock(""))
    }
}
