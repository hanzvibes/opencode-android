package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.toToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolCallTest {
    private fun decode(json: String) = ApiJson.decodeFromString(MessageContent.serializer(), json)

    @Test
    fun `completed shell block maps name, summary and output`() {
        val call = decode(
            """{"type":"tool","id":"c1","name":"shell","executed":true,
            "state":{"status":"completed","input":{"command":"ls -la\nmore"},
            "content":[{"type":"text","text":"ok"}]}}""",
        ).toToolCall()!!
        assertEquals("shell", call.name)
        assertEquals("ls -la", call.summary)
        assertEquals("ok", call.output)
        assertEquals(true, call.isDone)
    }

    @Test
    fun `streaming block with string input does not crash and has no summary`() {
        val call = decode(
            """{"type":"tool","id":"c2","name":"read","state":{"status":"streaming","input":"{\"pa"}}""",
        ).toToolCall()!!
        assertEquals("", call.summary)
        assertEquals(false, call.isDone)
    }

    @Test
    fun `aborted error surfaces its message and text blocks are not tools`() {
        val call = decode(
            """{"type":"tool","id":"c3","name":"edit","state":{"status":"error","input":{"path":"a.kt"},
            "error":{"type":"aborted","message":"The user declined this tool call"}}}""",
        ).toToolCall()!!
        assertEquals("The user declined this tool call", call.error)
        assertEquals("a.kt", call.summary)
        assertNull(decode("""{"type":"text","text":"hi"}""").toToolCall())
    }
}
