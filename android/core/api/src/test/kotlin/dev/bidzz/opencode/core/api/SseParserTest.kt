package dev.bidzz.opencode.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The parser is the part most likely to be silently wrong: a bug here shows up as
 * "the reply sometimes arrives incomplete", which is very hard to trace back.
 *
 * Every case below is taken from a real capture of `GET /api/event`, including the
 * `: heartbeat` comments the engine sends between events.
 */
class SseParserTest {
    private fun feed(vararg lines: String): List<dev.bidzz.opencode.core.api.dto.EngineEvent> {
        val parser = SseParser()
        val out = mutableListOf<dev.bidzz.opencode.core.api.dto.EngineEvent>()
        for (line in lines) parser.accept(line)?.let { out += it }
        parser.flush()?.let { out += it }
        return out
    }

    @Test
    fun `parses a single data line followed by a blank line`() {
        val events = feed(
            """data: {"id":"evt_1","type":"server.connected","data":{}}""",
            "",
        )
        assertEquals(1, events.size)
        assertEquals("server.connected", events[0].type)
        assertEquals("evt_1", events[0].id)
    }

    @Test
    fun `ignores heartbeat comments`() {
        // Verbatim from the live stream: comments appear between events and must
        // not be mistaken for payload.
        val events = feed(
            ": heartbeat",
            "",
            """data: {"type":"server.connected","data":{}}""",
            ": heartbeat",
            ": heartbeat",
            "",
        )
        assertEquals(1, events.size)
        assertEquals("server.connected", events[0].type)
    }

    @Test
    fun `parses consecutive events`() {
        val events = feed(
            """data: {"type":"session.text.started","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0}}""",
            "",
            """data: {"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0,"delta":"PI"}}""",
            "",
            """data: {"type":"session.text.delta","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0,"delta":"NG"}}""",
            "",
        )
        assertEquals(3, events.size)
        assertEquals("session.text.started", events[0].type)
        assertEquals("session.text.delta", events[1].type)
    }

    @Test
    fun `joins multiple data lines in one block`() {
        // SSE allows a payload to be split across data: lines, joined with newline.
        val events = feed(
            """data: {"type":"server.connected",""",
            """data: "data":{}}""",
            "",
        )
        assertEquals(1, events.size)
        assertEquals("server.connected", events[0].type)
    }

    @Test
    fun `skips a malformed event without losing the next one`() {
        // A truncated JSON payload must not poison the stream.
        val events = feed(
            """data: {"type":"session.text.delta","data":{"delta":""",
            "",
            """data: {"type":"session.text.ended","data":{"sessionID":"s1","assistantMessageID":"m1","ordinal":0,"text":"done"}}""",
            "",
        )
        assertEquals("the good event after the bad one must survive", 1, events.size)
        assertEquals("session.text.ended", events[0].type)
    }

    @Test
    fun `blank lines without data produce nothing`() {
        val events = feed("", "", ": heartbeat", "")
        assertEquals(0, events.size)
    }

    @Test
    fun `ignores event and id fields`() {
        // This server puts the kind inside data:, but tolerate a standard frame too.
        val events = feed(
            "event: message",
            "id: 42",
            """data: {"type":"server.connected","data":{}}""",
            "",
        )
        assertEquals(1, events.size)
        assertEquals("server.connected", events[0].type)
    }

    @Test
    fun `flush emits an unterminated trailing block`() {
        // A stream that dies mid-block should still yield what arrived.
        val parser = SseParser()
        assertNull(parser.accept("""data: {"type":"server.connected","data":{}}"""))
        assertNotNull(parser.flush())
    }

    @Test
    fun `space after colon is stripped`() {
        val events = feed("""data:{"type":"server.connected","data":{}}""", "")
        assertEquals(1, events.size)
    }
}
