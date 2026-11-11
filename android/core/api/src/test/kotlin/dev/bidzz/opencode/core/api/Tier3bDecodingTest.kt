package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.InboxResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding tests for Tier 3 items 6-10.
 *
 * Fixtures follow the shapes verified against the engine fork source at
 * pin 92b2ca0 (see the DTO comments): they are written to the verified
 * schema, not to the app's guess, so a mismatch here means the client
 * disagrees with the engine.
 */
class Tier3bDecodingTest {

    @Test
    fun `decodes an inbox list with all four variants`() {
        val raw = """
            {"data":[
              {"id":"msg_1","sessionID":"ses_1","time":{"created":1793447929584},
               "type":"user","delivery":"steer",
               "payload":{"text":"Fix the scroll bug","files":[{"data":"aGVsbG8=","mime":"text/plain","source":{"type":"inline"}}]}},
              {"id":"msg_2","sessionID":"ses_1","time":{"created":1793447929600},
               "type":"synthetic","delivery":"queue",
               "payload":{"text":"Reminder","description":"Check the build"}},
              {"id":"msg_3","sessionID":"ses_1","time":{"created":1793447929700},
               "type":"compaction","delivery":"queue","payload":{}},
              {"id":"msg_4","sessionID":"ses_1","time":{"created":1793447929800},
               "type":"move","delivery":"steer",
               "payload":{"projectID":"81d8a9e29a4c267059be6b14ec914cff729bd300",
               "location":{"directory":"/storage/emulated/0/Projects/other"}}}
            ],"location":{"directory":"/storage/emulated/0/Projects/demo"}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<InboxResponse>(raw)
        assertEquals(4, parsed.data.size)
        assertEquals(listOf("user", "synthetic", "compaction", "move"), parsed.data.map { it.type })
        assertEquals("msg_1", parsed.data[0].id)
        assertEquals(1793447929584L, parsed.data[0].time?.created)
        assertEquals("steer", parsed.data[0].delivery)
        assertEquals("/storage/emulated/0/Projects/demo", parsed.location?.directory)
        // The payload stays a raw object: the union variants differ.
        assertEquals(
            "Fix the scroll bug",
            (parsed.data[0].payload["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content,
        )
        assertTrue(parsed.data[2].payload.isEmpty())
    }

    @Test
    fun `inbox decode tolerates an unknown variant type`() {
        val raw = """
            {"data":[
              {"id":"msg_9","sessionID":"ses_1","time":{"created":1793447929900},
               "type":"future-kind","delivery":"queue","payload":{"text":"Hello"}}
            ]}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<InboxResponse>(raw)
        assertEquals(1, parsed.data.size)
        assertEquals("future-kind", parsed.data[0].type)
    }

    @Test
    fun `inbox decode tolerates missing optional fields`() {
        val parsed = ApiJson.decodeFromString<InboxResponse>("""{"data":[{"id":"msg_1"}]}""")
        assertEquals(1, parsed.data.size)
        assertEquals("", parsed.data[0].type)
        assertEquals(null, parsed.data[0].delivery)
        assertTrue(parsed.data[0].payload.isEmpty())
    }
}
