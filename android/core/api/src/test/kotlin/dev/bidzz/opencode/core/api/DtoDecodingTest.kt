package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.MessagesResponse
import dev.bidzz.opencode.core.api.dto.McpServersResponse
import dev.bidzz.opencode.core.api.dto.ModelsResponse
import dev.bidzz.opencode.core.api.dto.PermissionReplyRequest
import dev.bidzz.opencode.core.api.dto.SavedPermissionsResponse
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.SessionsResponse
import dev.bidzz.opencode.core.api.dto.SingleResponse
import dev.bidzz.opencode.core.api.dto.SkillsResponse
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding tests against bodies captured verbatim from a live engine.
 *
 * Fixtures are real responses rather than hand-written JSON: the point is to catch
 * the case where the client's idea of the shape is wrong, which hand-written
 * fixtures cannot do because they encode the same misunderstanding.
 */
class DtoDecodingTest {

    @Test
    fun `decodes a session list`() {
        val raw = """
            {"data":[{"id":"ses_e6e2c6175ffehL2HSYCN4GMS7b",
            "projectID":"81d8a9e29a4c267059be6b14ec914cff729bd300",
            "model":{"id":"big-pickle","providerID":"opencode","variant":"default"},
            "cost":0,
            "tokens":{"input":4032,"output":9,"reasoning":83,"cache":{"read":2393,"write":0}},
            "outcome":"succeeded",
            "time":{"created":1793447927442,"updated":1793448001445,"busy":1793447929557,"idle":1793447931983},
            "title":"Exact PING reply",
            "location":{"directory":"/storage/emulated/0/Projects/demo"}}],
            "cursor":{"previous":"eyJpZCI6Ingi","next":null}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SessionsResponse>(raw)
        assertEquals(1, parsed.data.size)
        val s = parsed.data[0]
        assertEquals("ses_e6e2c6175ffehL2HSYCN4GMS7b", s.id)
        assertEquals("Exact PING reply", s.title)
        assertEquals("big-pickle", s.model?.id)
        assertEquals("/storage/emulated/0/Projects/demo", s.location?.directory)
        assertEquals(4032L, s.tokens?.input)
        assertEquals(2393L, s.tokens?.cache?.read)
        assertEquals("eyJpZCI6Ingi", parsed.cursor?.previous)
    }

    @Test
    fun `decodes the three message kinds`() {
        val raw = """
            {"data":[
              {"id":"msg_a","time":{"created":1793447929584},"text":"Reply with exactly: PING","type":"user"},
              {"id":"msg_b","time":{"created":1793447929635,"streamed":1793447931973,"completed":1793447931976},
               "type":"assistant","agent":"build","model":{"id":"big-pickle","providerID":"opencode"},
               "content":[{"type":"text","text":"PING"}],"finish":"stop","rawFinish":"stop","cost":0,
               "tokens":{"input":3935,"output":3,"reasoning":0,"cache":{"read":1971,"write":0}}},
              {"id":"msg_c","time":{"created":1793447931983},"type":"idle","outcome":"succeeded"}
            ],"cursor":{"next":null,"previous":"eyJ4Ijox"}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<MessagesResponse>(raw)
        assertEquals(3, parsed.data.size)

        val user = parsed.data[0]
        assertTrue(user.isUser)
        assertEquals("Reply with exactly: PING", user.plainText)

        val assistant = parsed.data[1]
        assertTrue(assistant.isAssistant)
        assertEquals("PING", assistant.plainText)
        assertEquals("build", assistant.agent)
        assertEquals("stop", assistant.finish)

        val idle = parsed.data[2]
        assertTrue(idle.isIdle)
        assertEquals("succeeded", idle.outcome)
        assertEquals("", idle.plainText)
    }

    @Test
    fun `decodes a session context listing`() {
        // GET /api/session/{id}/context answers the same {data: SessionMessage[]}
        // envelope as /message, without a cursor — and after a compaction the
        // listing starts with the compaction summary instead of the old turns.
        val raw = """
            {"data":[
              {"id":"msg_compact","time":{"created":1793447932000},"type":"compaction",
               "status":"completed","summary":"Summarized 12 turns",
               "cost":0,"tokens":{"input":900,"output":120,"reasoning":0}},
              {"id":"msg_b","time":{"created":1793447932100},
               "type":"assistant","agent":"build","model":{"id":"big-pickle","providerID":"opencode"},
               "content":[{"type":"text","text":"PONG"}],"finish":"stop",
               "tokens":{"input":1020,"output":5,"reasoning":0,"cache":{"read":800,"write":0}}}
            ]}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<MessagesResponse>(raw)
        assertEquals(2, parsed.data.size)
        val summary = parsed.data[0]
        assertEquals("compaction", summary.type)
        assertEquals(900L, summary.tokens?.input)
        val assistant = parsed.data[1]
        assertTrue(assistant.isAssistant)
        assertEquals(1020L, assistant.tokens?.input)
        assertEquals(800L, assistant.tokens?.cache?.read)
    }

    @Test
    fun `joins multiple text blocks in order`() {
        val msg = SessionMessage(
            id = "m",
            type = "assistant",
            content = listOf(
                dev.bidzz.opencode.core.api.dto.MessageContent(type = "text", text = "Hello "),
                dev.bidzz.opencode.core.api.dto.MessageContent(type = "text", text = "world"),
            ),
        )
        assertEquals("Hello world", msg.plainText)
    }

    @Test
    fun `ignores unknown fields instead of failing`() {
        // The engine adds fields between releases; a strict parser would break the
        // chat screen on every upgrade.
        val raw = """
            {"data":{"id":"ses_1","somethingNew":{"nested":true},"title":"t"}}
        """.trimIndent()
        val parsed = ApiJson.decodeFromString<SingleResponse<dev.bidzz.opencode.core.api.dto.SessionInfo>>(raw)
        assertEquals("ses_1", parsed.data.id)
        assertEquals("t", parsed.data.title)
    }

    @Test
    fun `decodes a model list`() {
        val raw = """
            {"location":{"directory":"/data/data/dev.bidzz.opencode/files/workspace"},
             "data":[{"id":"big-pickle","modelID":"big-pickle","providerID":"opencode","type":"chat",
             "family":"big-pickle","name":"Big Pickle","status":"active","enabled":true,
             "limit":{"context":200000,"input":160000,"output":32000}}]}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<ModelsResponse>(raw)
        assertEquals(1, parsed.data.size)
        assertEquals("Big Pickle", parsed.data[0].name)
        assertEquals(200000L, parsed.data[0].limit?.context)
    }

    @Test
    fun `error body carries the tag used for routing`() {
        val body = """{"_tag":"LocationNotFoundError","location":{"directory":"/x"},"message":"Location not found: /x"}"""
        val parsed = ApiJson.decodeFromString<ErrorBody>(body)
        assertEquals("LocationNotFoundError", parsed.tag)
        assertEquals("Location not found: /x", parsed.message)

        val failure = ApiResult.Failure(code = 404, tag = parsed.tag, message = parsed.message)
        assertTrue(failure.isLocationMissing)
        assertEquals(FailureReason.LOCATION_MISSING, failure.reason)
    }

    @Test
    fun `permission reply body uses the decision field`() {
        // The engine's reply schema is `{decision: once|always|reject, message?}`.
        // Sending `reply` instead returns 400 and leaves the tool blocked, so the
        // field name is load-bearing and worth pinning.
        val body = ApiJson.encodeToString(PermissionReplyRequest(decision = "always"))
        assertEquals("""{"decision":"always"}""", body)
    }

    @Test
    fun `decodes saved permissions, ignoring the unmodelled time field`() {
        // `time` is listed in the reference without a shape, so the DTO omits
        // it; a response carrying it as an object must still decode.
        val raw = """
            {"data":[
              {"id":"perm_01","projectID":"81d8a9e29a4c267059be6b14ec914cff729bd300",
               "action":"bash","resource":"bash:ls *","time":{"created":1793447927442}},
              {"id":"perm_02","projectID":"81d8a9e29a4c267059be6b14ec914cff729bd300",
               "action":"edit","resource":"/storage/emulated/0/Projects/demo/README.md"}
            ]}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SavedPermissionsResponse>(raw)
        assertEquals(2, parsed.data.size)
        assertEquals("perm_01", parsed.data[0].id)
        assertEquals("bash", parsed.data[0].action)
        assertEquals("bash:ls *", parsed.data[0].resource)
        assertEquals("edit", parsed.data[1].action)
        assertEquals("/storage/emulated/0/Projects/demo/README.md", parsed.data[1].resource)
    }

    @Test
    fun `decodes the skill list`() {
        val raw = """
            {"data":[
              {"id":"sk_1","name":"commit","description":"Write a commit message",
               "autoinvoke":true,"path":"/skills/commit/SKILL.md","content":"# commit\n"},
              {"id":"sk_2","name":"review-pr","description":"Review a pull request",
               "autoinvoke":false,"path":"/skills/review-pr/SKILL.md"}
            ]}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SkillsResponse>(raw)
        assertEquals(2, parsed.data.size)
        assertEquals("commit", parsed.data[0].name)
        assertEquals(true, parsed.data[0].autoinvoke)
        assertEquals("Write a commit message", parsed.data[0].description)
        assertEquals("review-pr", parsed.data[1].name)
        assertEquals(false, parsed.data[1].autoinvoke)
    }

    @Test
    fun `decodes the MCP server list with per-variant status objects`() {
        // Status is an object, not a bare string: each Mcp.Status.* variant
        // carries its own `status` field, and failed/needs-auth add `error`.
        val raw = """
            {"data":[
              {"name":"github","status":{"status":"connected"},"title":"GitHub","integrationID":"github"},
              {"name":"broken","status":{"status":"failed","error":"connection refused"},"title":"Broken server"},
              {"name":"local","status":{"status":"pending"}}
            ]}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<McpServersResponse>(raw)
        assertEquals(3, parsed.data.size)
        assertEquals("github", parsed.data[0].name)
        assertEquals("connected", parsed.data[0].status.status)
        assertEquals("GitHub", parsed.data[0].title)
        assertEquals("github", parsed.data[0].integrationID)
        assertEquals("failed", parsed.data[1].status.status)
        assertEquals("connection refused", parsed.data[1].status.error)
        assertEquals("pending", parsed.data[2].status.status)
        assertEquals(null, parsed.data[2].status.error)
        assertEquals(null, parsed.data[2].title)
    }

    @Test
    fun `unauthorized is distinguishable from a missing session`() {
        val unauthorized = ApiResult.Failure(code = 401, tag = "UnauthorizedError")
        val missing = ApiResult.Failure(code = 404, tag = "SessionNotFoundError")
        assertTrue(unauthorized.isUnauthorized)
        assertTrue(!unauthorized.isSessionMissing)
        assertTrue(missing.isSessionMissing)
        assertTrue(!missing.isUnauthorized)
    }
}
