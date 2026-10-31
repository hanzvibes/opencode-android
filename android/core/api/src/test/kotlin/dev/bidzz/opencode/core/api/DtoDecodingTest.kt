package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.MessagesResponse
import dev.bidzz.opencode.core.api.dto.ModelsResponse
import dev.bidzz.opencode.core.api.dto.PermissionReplyRequest
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.SessionsResponse
import dev.bidzz.opencode.core.api.dto.SingleResponse
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
        assertEquals("Project folder is no longer available", failure.display)
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
    fun `unauthorized is distinguishable from a missing session`() {
        val unauthorized = ApiResult.Failure(code = 401, tag = "UnauthorizedError")
        val missing = ApiResult.Failure(code = 404, tag = "SessionNotFoundError")
        assertTrue(unauthorized.isUnauthorized)
        assertTrue(!unauthorized.isSessionMissing)
        assertTrue(missing.isSessionMissing)
        assertTrue(!missing.isUnauthorized)
    }
}
