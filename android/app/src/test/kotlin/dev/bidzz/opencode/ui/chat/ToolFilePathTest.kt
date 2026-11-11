package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.ToolCall
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolFilePathTest {

    private fun call(name: String, inputJson: String?, status: String = "completed") =
        ToolCall(
            id = "1",
            name = name,
            status = status,
            input = inputJson?.let { Json.parseToJsonElement(it) },
        )

    @Test
    fun `read tool path comes from filePath in the input`() {
        val path = toolFilePath(
            call("read", """{"filePath": "src/chat/ChatScreen.kt"}"""),
        )

        assertEquals("src/chat/ChatScreen.kt", path)
    }

    @Test
    fun `path and file keys are accepted too`() {
        assertEquals(
            "a/b.txt",
            toolFilePath(call("edit", """{"path": "a/b.txt", "oldString": "x", "newString": "y"}""")),
        )
        assertEquals(
            "c/d.txt",
            toolFilePath(call("write", """{"file": "c/d.txt", "content": "hi"}""")),
        )
    }

    @Test
    fun `non-file tools have no file path`() {
        assertNull(toolFilePath(call("bash", """{"command": "npm test"}""")))
        assertNull(toolFilePath(call("grep", """{"pattern": "foo", "path": "src"}""")))
        assertNull(toolFilePath(call("glob", """{"pattern": "**/*.kt"}""")))
    }

    @Test
    fun `summary is the fallback when the input uses an unknown key`() {
        assertEquals(
            "src/x.kt",
            toolFilePath(call("read", """{"uri": "src/x.kt"}""")),
        )
    }

    @Test
    fun `input-only path never falls back to the summary`() {
        // The summary is free text, not a verified path: the revert target
        // must come from the input alone (audit-70 70-F03).
        assertNull(toolInputFilePath(call("read", """{"uri": "src/x.kt"}""")))
        assertNull(toolInputFilePath(call("edit", """{"oldString": "x", "newString": "y"}""")))
        assertEquals(
            "src/chat/ChatScreen.kt",
            toolInputFilePath(call("edit", """{"filePath": "src/chat/ChatScreen.kt", "oldString": "x", "newString": "y"}""")),
        )
    }

    @Test
    fun `absolute path inside the workspace relativizes`() {
        assertEquals(
            "src/chat/ChatScreen.kt",
            relativizeToWorkspace(
                "/storage/emulated/0/proj/src/chat/ChatScreen.kt",
                "/storage/emulated/0/proj",
            ),
        )
    }

    @Test
    fun `absolute path outside the workspace is refused`() {
        assertNull(
            relativizeToWorkspace("/etc/passwd", "/storage/emulated/0/proj"),
        )
        assertNull(
            relativizeToWorkspace("/storage/emulated/0/other/x.kt", "/storage/emulated/0/proj"),
        )
    }

    @Test
    fun `relative paths pass through and parent climbs are refused`() {
        assertEquals("src/x.kt", relativizeToWorkspace("src/x.kt", "/ws"))
        assertEquals("src/x.kt", relativizeToWorkspace("./src/x.kt", "/ws"))
        assertNull(relativizeToWorkspace("../secret.txt", "/ws"))
        assertNull(relativizeToWorkspace("", "/ws"))
    }

    @Test
    fun `an absolute path with no workspace to check against is refused`() {
        assertNull(relativizeToWorkspace("/storage/emulated/0/proj/x.kt", null))
        assertEquals("src/x.kt", relativizeToWorkspace("src/x.kt", null))
    }
}
