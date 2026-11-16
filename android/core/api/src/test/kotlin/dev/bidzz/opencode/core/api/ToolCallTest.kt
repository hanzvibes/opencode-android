package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.MessageContent
import dev.bidzz.opencode.core.api.dto.parseToolOutput
import dev.bidzz.opencode.core.api.dto.toToolCall
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // Audit-200 D11 (m-103/m-1134): a tool.success whose content list does
    // not decode must be flagged, not swallowed into a bare "Success".

    private fun event(json: String) =
        ApiJson.decodeFromString(JsonObject.serializer(), json)

    @Test
    fun `m-103 content that is not a list is flagged as undecodable`() {
        val parsed = parseToolOutput(event("""{"content": "not a list"}"""))

        assertNull(parsed.text)
        assertTrue(parsed.decodeFailed)
    }

    @Test
    fun `m-103 a content block without a type is flagged as undecodable`() {
        val parsed = parseToolOutput(event("""{"content": [{"text": "hi"}]}"""))

        assertNull(parsed.text)
        assertTrue(parsed.decodeFailed)
    }

    @Test
    fun `m-103 valid content decodes and missing content is not a failure`() {
        val parsed = parseToolOutput(event("""{"content": [{"type": "text", "text": "hi"}]}"""))
        assertEquals("hi", parsed.text)
        assertFalse(parsed.decodeFailed)

        val missing = parseToolOutput(event("""{}"""))
        assertNull(missing.text)
        assertFalse(missing.decodeFailed)
    }
}
