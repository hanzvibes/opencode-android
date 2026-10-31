package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.MessagesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ordering rules for message history.
 *
 * Regression cover for a real bug: `GET /api/session/{id}/message` returns
 * **newest first**, and the first implementation assumed oldest first. The visible
 * symptom was a chat that never left "busy", because the last element of a
 * newest-first list is the user's own message, which is never `idle`.
 */
class MessageOrderTest {

    /** Verbatim shape from a live engine, newest first. */
    private val newestFirst = """
        {"data":[
          {"id":"msg_idle","type":"idle","outcome":"succeeded"},
          {"id":"msg_asst","type":"assistant","content":[{"type":"text","text":"APICLIENT-OK"}]},
          {"id":"msg_user","type":"user","text":"Reply with exactly: APICLIENT-OK"}
        ],"cursor":{"next":null,"previous":"eyJ4Ijox"}}
    """.trimIndent()

    @Test
    fun `engine returns newest first`() {
        val parsed = ApiJson.decodeFromString<MessagesResponse>(newestFirst)
        assertEquals("idle", parsed.data.first().type)
        assertEquals("user", parsed.data.last().type)
    }

    @Test
    fun `reversing yields a readable transcript`() {
        val parsed = ApiJson.decodeFromString<MessagesResponse>(newestFirst)
        val chronological = parsed.data.reversed()
        assertEquals(listOf("user", "assistant", "idle"), chronological.map { it.type })
        assertEquals("Reply with exactly: APICLIENT-OK", chronological[0].plainText)
        assertEquals("APICLIENT-OK", chronological[1].plainText)
    }

    @Test
    fun `busy is decided by the newest entry`() {
        val parsed = ApiJson.decodeFromString<MessagesResponse>(newestFirst)
        // Newest is idle => finished.
        val finished = parsed.data.first()
        assertTrue(finished.isIdle)
        assertFalse("an idle head means the loop is not running", !finished.isIdle)

        // The naive check — last element of the raw list — would have said busy.
        val naive = parsed.data.lastOrNull()
        assertFalse("the last element is the user message, not a terminator", naive?.isIdle == true)
    }

    @Test
    fun `an in-flight session is busy`() {
        val inFlight = """
            {"data":[
              {"id":"msg_asst","type":"assistant","content":[{"type":"text","text":"wor"}]},
              {"id":"msg_user","type":"user","text":"hi"}
            ]}
        """.trimIndent()
        val parsed = ApiJson.decodeFromString<MessagesResponse>(inFlight)
        val newest = parsed.data.first()
        assertFalse(newest.isIdle)
        assertEquals("assistant", newest.type)
    }

    @Test
    fun `empty history is not busy`() {
        val parsed = ApiJson.decodeFromString<MessagesResponse>("""{"data":[]}""")
        val newest = parsed.data.firstOrNull()
        assertTrue("no messages means nothing is running", newest == null)
    }
}
