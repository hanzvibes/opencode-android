package dev.bidzz.opencode.ui

import dev.bidzz.opencode.core.api.SessionLogEvent
import dev.bidzz.opencode.core.api.dto.FormInfo
import dev.bidzz.opencode.core.api.dto.InboxItem
import dev.bidzz.opencode.core.api.dto.ScheduleInfo
import dev.bidzz.opencode.ui.chat.TokenKind
import dev.bidzz.opencode.ui.chat.highlight
import dev.bidzz.opencode.ui.inbox.summaryText
import dev.bidzz.opencode.ui.sessionlog.sessionLogSummary
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase C17: the streaming highlight threshold (M-252), the
 * inbox move summary (M-256), generic-form detection (M-263), the
 * schedule `when` text (M-259), and the session-log summary (M-259).
 */
class AuditC17Test {

    // M-252: a streaming code block re-highlights on every publish, so
    // blocks over 1_000 chars stay plain while streaming and colour in
    // once the turn completes.

    @Test
    fun `short code colours while streaming`() {
        val code = "val x = 1"
        val tokens = highlight("kotlin", code, streaming = true)
        assertTrue(tokens.any { it.kind != TokenKind.Plain })
        assertEquals(code, tokens.joinToString("") { it.text })
    }

    @Test
    fun `long code stays plain while streaming`() {
        val code = "val x = 1\n".repeat(200) // 2_000 chars
        val tokens = highlight("kotlin", code, streaming = true)
        assertEquals(1, tokens.size)
        assertEquals(TokenKind.Plain, tokens[0].kind)
        assertEquals(code, tokens[0].text)
    }

    @Test
    fun `long code colours once streaming ends`() {
        val code = "val x = 1\n".repeat(200) // 2_000 chars
        val tokens = highlight("kotlin", code, streaming = false)
        assertTrue(tokens.any { it.kind != TokenKind.Plain })
        assertEquals(code, tokens.joinToString("") { it.text })
    }

    // M-256: a move summary names the destination directory, not the
    // opaque project id.

    private fun moveItem(payload: JsonObject) = InboxItem(id = "msg_1", type = "move", payload = payload)

    @Test
    fun `move summary shows directory and subpath`() {
        val payload = buildJsonObject {
            put("projectID", "81d8a9e2")
            put("subpath", "src/main")
            putJsonObject("location") { put("directory", "/home/user/demo") }
        }
        assertEquals("/home/user/demo/src/main", moveItem(payload).summaryText())
    }

    @Test
    fun `move summary shows the directory without a subpath`() {
        val payload = buildJsonObject {
            put("projectID", "81d8a9e2")
            putJsonObject("location") { put("directory", "/home/user/demo") }
        }
        assertEquals("/home/user/demo", moveItem(payload).summaryText())
    }

    @Test
    fun `move summary falls back to the project id without a location`() {
        val payload = buildJsonObject { put("projectID", "81d8a9e2") }
        assertEquals("81d8a9e2", moveItem(payload).summaryText())
    }

    // M-263: only question forms live in the transcript; everything
    // else is a generic form and needs its own card.

    @Test
    fun `a question form is not generic`() {
        val form = FormInfo(
            id = "frm_1",
            metadata = buildJsonObject {
                put("kind", "question")
                putJsonObject("tool") { put("id", "call-1") }
            },
        )
        assertTrue(form.isQuestion())
        assertFalse(form.isGeneric())
        assertTrue(form.isQuestionFor("call-1"))
        assertFalse(form.isQuestionFor("call-2"))
    }

    @Test
    fun `a form without question metadata is generic`() {
        assertTrue(FormInfo(id = "frm_2").isGeneric())
        assertFalse(FormInfo(id = "frm_2").isQuestion())
        val otherKind = FormInfo(
            id = "frm_3",
            metadata = buildJsonObject { put("kind", "elicitation") },
        )
        assertTrue(otherKind.isGeneric())
    }

    // M-259: the schedule `when` reads as a sentence.

    @Test
    fun `schedule when text names the kind and value`() {
        fun info(whenValue: JsonObject) = ScheduleInfo(id = "sch_1", whenValue = whenValue)
        assertEquals(
            "in 20 minutes",
            info(buildJsonObject { put("type", "in"); put("in", "20 minutes") }).whenText(),
        )
        assertEquals(
            "at 2026-10-27T09:00",
            info(buildJsonObject { put("type", "at"); put("at", "2026-10-27T09:00") }).whenText(),
        )
        assertEquals(
            "every 1 hour",
            info(buildJsonObject { put("type", "every"); put("every", "1 hour") }).whenText(),
        )
        assertEquals(
            "cron 0 9 * * 1-5",
            info(buildJsonObject { put("type", "cron"); put("cron", "0 9 * * 1-5") }).whenText(),
        )
        assertEquals("", ScheduleInfo(id = "sch_2").whenText())
    }

    // M-259: the log summary reads the most human field it can find.

    private fun logEvent(data: JsonObject) =
        SessionLogEvent(type = "session.updated", seq = 7L, createdMs = 1_792_000_000_000L, data = data)

    @Test
    fun `log summary prefers a text field`() {
        val data = buildJsonObject { put("text", "hello") }
        assertEquals("hello", sessionLogSummary(logEvent(data)))
    }

    @Test
    fun `log summary reads the first text part of a content update`() {
        val data = buildJsonObject {
            putJsonArray("content") {
                add(buildJsonObject { put("type", "text"); put("text", "Fixed the bug") })
                add(buildJsonObject { put("type", "tool") })
            }
        }
        assertEquals("Fixed the bug", sessionLogSummary(logEvent(data)))
    }

    @Test
    fun `log summary names a usage record by cost and tokens`() {
        val data = buildJsonObject {
            put("cost", 0.04)
            putJsonObject("tokens") {
                put("input", 1200)
                put("output", 300)
            }
        }
        assertEquals("Cost 0.04 · 1500 tokens", sessionLogSummary(logEvent(data)))
    }

    @Test
    fun `log summary falls back to the payload json`() {
        val data = buildJsonObject { put("sessionID", "s1") }
        assertEquals("""{"sessionID":"s1"}""", sessionLogSummary(logEvent(data)))
    }
}
