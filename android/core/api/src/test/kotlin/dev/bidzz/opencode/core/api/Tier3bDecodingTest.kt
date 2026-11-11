package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.InboxResponse
import dev.bidzz.opencode.core.api.dto.MoveSessionRequest
import dev.bidzz.opencode.core.api.dto.SessionStatsResponse
import dev.bidzz.opencode.core.api.dto.SessionTransferResponse
import dev.bidzz.opencode.core.api.dto.UsageLimitsResponse
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `move request sends only the directory`() {
        // Verified against the fork source: the payload is
        // Location.PublicRef ({directory}) plus optional delivery. The
        // inbox MovePayload shape ({projectID, subpath, location}) is NOT
        // the request shape, and must never be sent here.
        val encoded = ApiJson.encodeToString(
            MoveSessionRequest(directory = "/storage/emulated/0/Projects/other"),
        )
        assertEquals(
            """{"directory":"/storage/emulated/0/Projects/other"}""",
            encoded,
        )
    }

    @Test
    fun `move request keeps an explicit delivery when set`() {
        val encoded = ApiJson.encodeToString(
            MoveSessionRequest(directory = "/storage/emulated/0/Projects/other", delivery = "queue"),
        )
        assertEquals(
            """{"directory":"/storage/emulated/0/Projects/other","delivery":"queue"}""",
            encoded,
        )
    }

    @Test
    fun `decodes session stats with detail tools`() {
        val raw = """
            {"data":{"range":{"from":1793447927000,"to":1794052727000},
            "sessions":3,"subagents":1,"prompts":12,"steps":40,
            "tokens":{"input":4032,"output":9,"reasoning":83,"cache":{"read":2393,"write":0}},
            "cost":0.42,
            "tools":{"mode":"detail","totals":{"calls":20,"succeeded":18,"failed":1,"unfinished":1},
            "usage":[{"name":"read","calls":12,"succeeded":12,"failed":0,"unfinished":0,"durationP50":120.5}]},
            "activeDays":5,"streak":2,"activity":[{"date":"2026-11-11","steps":10}],
            "models":[{"model":{"providerID":"opencode","modelID":"big-pickle"},"steps":30,
            "tokens":{"input":3000,"output":5},"cost":0.3}]}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SessionStatsResponse>(raw).data
        assertEquals(3L, parsed.sessions)
        assertEquals(12L, parsed.prompts)
        assertEquals(0.42, parsed.cost, 0.001)
        assertEquals(4032L, parsed.tokens?.input)
        assertEquals("detail", parsed.tools.mode)
        assertEquals(20L, parsed.tools.totals?.calls)
        assertEquals(1, parsed.tools.usage.size)
        assertEquals("read", parsed.tools.usage[0].name)
        assertEquals(120.5, parsed.tools.usage[0].durationP50!!, 0.001)
        assertEquals(1, parsed.models.size)
        assertEquals(30L, parsed.models[0].steps)
        assertEquals(5, parsed.activeDays)
        assertEquals(2, parsed.streak)
    }

    @Test
    fun `stats decode tolerates the none tools mode`() {
        val raw = """{"data":{"sessions":0,"subagents":0,"prompts":0,"steps":0,"cost":0,
            "tools":{"mode":"none"},"activeDays":0,"streak":0,"activity":[],"models":[]}}"""
        val parsed = ApiJson.decodeFromString<SessionStatsResponse>(raw).data
        assertEquals("none", parsed.tools.mode)
        assertNull(parsed.tools.totals)
        assertTrue(parsed.tools.usage.isEmpty())
        assertTrue(parsed.models.isEmpty())
    }

    @Test
    fun `decodes a session export`() {
        val raw = """
            {"data":{"info":{"id":"ses_1","title":"Demo","projectID":"81d8a9e2","cost":0,
            "tokens":{"input":1,"output":0},"time":{"created":1,"updated":2},
            "location":{"directory":"/storage/emulated/0/Projects/demo"}},
            "messages":[{"id":"msg_1","type":"user","text":"Hi"}]}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SessionTransferResponse>(raw).data
        assertEquals("ses_1", (parsed.info["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content)
        assertEquals("Demo", (parsed.info["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content)
        assertEquals(1, parsed.messages.size)
    }

    @Test
    fun `decodes usage limits with Go meters and budgets`() {
        val raw = """
            {"data":{"workspace":{"id":"ws_1","name":"Acme","usageURL":"https://console.example/usage"},
            "go":{"rolling":{"percent":42.5,"limited":false,"resetsAt":1794052727000},
            "weekly":{"percent":10.0,"limited":false,"resetsAt":1794052727000},
            "monthly":{"percent":99.0,"limited":true,"resetsAt":1794052727000}},
            "budgets":[{"id":"bud_1","limit":50.0,"spent":12.5,"scope":"each","per":"set",
            "coverage":"all","models":[],"vendors":[],"picked":false,"shared":false,
            "resetsAt":1794052727000}]},
            "location":{"directory":"/storage/emulated/0/Projects/demo"}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<UsageLimitsResponse>(raw)
        val info = parsed.data!!
        assertEquals("ws_1", info.workspace?.id)
        assertEquals("Acme", info.workspace?.name)
        assertEquals(42.5, info.go?.rolling?.percent!!, 0.001)
        assertEquals(false, info.go?.rolling?.limited)
        assertEquals(true, info.go?.monthly?.limited)
        assertEquals(1, info.budgets.size)
        assertEquals("bud_1", info.budgets[0].id)
        assertEquals(12.5, info.budgets[0].spent, 0.001)
        assertEquals("each", info.budgets[0].scope)
        assertEquals("/storage/emulated/0/Projects/demo", parsed.location?.directory)
    }

    @Test
    fun `usage limits decode tolerates a null data answer`() {
        // The engine answers data: null when the location is not signed
        // in to an OpenCode Console workspace. That is a normal answer.
        val parsed = ApiJson.decodeFromString<UsageLimitsResponse>(
            """{"data":null,"location":{"directory":"/storage/emulated/0/Projects/demo"}}""",
        )
        assertNull(parsed.data)
    }

    @Test
    fun `usage limits decode tolerates a missing Go allowance`() {
        val parsed = ApiJson.decodeFromString<UsageLimitsResponse>(
            """{"data":{"workspace":{"id":"ws_1"},"budgets":[]}}""",
        )
        assertNull(parsed.data?.go)
        assertTrue(parsed.data?.budgets?.isEmpty() == true)
    }
}
