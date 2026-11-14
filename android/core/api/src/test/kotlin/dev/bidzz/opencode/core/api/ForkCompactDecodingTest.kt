package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.CompactRequest
import dev.bidzz.opencode.core.api.dto.ForkRequest
import dev.bidzz.opencode.core.api.dto.InboxItem
import dev.bidzz.opencode.core.api.dto.RevertFileImpact
import dev.bidzz.opencode.core.api.dto.RevertStageRequest
import dev.bidzz.opencode.core.api.dto.SessionInfo
import dev.bidzz.opencode.core.api.dto.SessionRevert
import dev.bidzz.opencode.core.api.dto.SingleResponse
import dev.bidzz.opencode.core.api.dto.fileImpact
import dev.bidzz.opencode.core.api.dto.revertFilePaths
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding / encoding tests for session fork, compact and revert.
 *
 * Fixtures follow the shapes verified against the engine fork source at
 * pin 92b2ca0dd727942e00aba3523ed141d53e78ea28 (see the DTO comments):
 * they are written to the verified schema, not to the app's guess, so a
 * mismatch here means the client disagrees with the engine.
 */
class ForkCompactDecodingTest {

    @Test
    fun `decodes a forked session with parent and boundary`() {
        val raw = """
            {"data":{"id":"ses_01JAAAAAAAAAAAAAAAAAAAAAAAAA",
              "parentID":"ses_01JBBBBBBBBBBBBBBBBBBBBBBBBB",
              "fork":{"sessionID":"ses_01JBBBBBBBBBBBBBBBBBBBBBBBBB",
                "boundary":{"type":"before","messageID":"msg_01JCCCCCCCCCCCCCCCCCCCCCCCCC"}},
              "projectID":"81d8a9e29a4c267059be6b14ec914cff729bd300",
              "title":"Fix the scroll bug",
              "time":{"created":1793447929584,"updated":1793447929584}}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SingleResponse<SessionInfo>>(raw).data
        assertEquals("ses_01JAAAAAAAAAAAAAAAAAAAAAAAAA", parsed.id)
        assertEquals("ses_01JBBBBBBBBBBBBBBBBBBBBBBBBB", parsed.parentID)
        assertEquals("ses_01JBBBBBBBBBBBBBBBBBBBBBBBBB", parsed.fork?.sessionID)
        assertEquals("before", parsed.fork?.boundary?.type)
        assertEquals("msg_01JCCCCCCCCCCCCCCCCCCCCCCCCC", parsed.fork?.boundary?.messageID)
        assertNull(parsed.revert)
    }

    @Test
    fun `decodes a session with a staged revert`() {
        val raw = """
            {"data":{"id":"ses_01JAAAAAAAAAAAAAAAAAAAAAAAAA",
              "title":"Fix the scroll bug",
              "revert":{"messageID":"msg_01JCCCCCCCCCCCCCCCCCCCCCCCCC",
                "snapshot":"snap_01JDDDDDDDDDDDDDDDDDDDDDDDDD",
                "files":[{"path":"app/src/main/kotlin/Main.kt","status":"modified",
                  "additions":3,"deletions":1,"patch":"@@ -1,3 +1,3 @@"}]}}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SingleResponse<SessionInfo>>(raw).data
        val revert: SessionRevert? = parsed.revert
        assertEquals("msg_01JCCCCCCCCCCCCCCCCCCCCCCCCC", revert?.messageID)
        assertEquals("snap_01JDDDDDDDDDDDDDDDDDDDDDDDDD", revert?.snapshot)
        assertEquals(listOf("app/src/main/kotlin/Main.kt"), revert?.revertFilePaths())
    }

    @Test
    fun `DL-013 decodes a staged revert whose files use the file key`() {
        // The engine's own diff shape (FileDiff.Info) names the file
        // under "file", not "path" — a revert staged from that shape
        // must still count its files.
        val raw = """
            {"data":{"id":"ses_01JAAAAAAAAAAAAAAAAAAAAAAAAA",
              "title":"Fix the scroll bug",
              "revert":{"messageID":"msg_01JCCCCCCCCCCCCCCCCCCCCCCCCC",
                "files":[{"file":"app/src/main/kotlin/Main.kt","status":"modified"},
                  {"file":"README.md","status":"added"}]}}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SingleResponse<SessionInfo>>(raw).data
        val revert: SessionRevert? = parsed.revert
        assertEquals(
            listOf("app/src/main/kotlin/Main.kt", "README.md"),
            revert?.revertFilePaths(),
        )
        assertEquals(RevertFileImpact.KnownFiles, revert?.fileImpact())
    }

    @Test
    fun `DL-013 decodes a staged revert with mixed path and file keys`() {
        val raw = """
            {"data":{"id":"ses_01JAAAAAAAAAAAAAAAAAAAAAAAAA",
              "revert":{"messageID":"msg_01JCCCCCCCCCCCCCCCCCCCCCCCCC",
                "files":[{"path":"src/A.kt","status":"modified"},
                  {"file":"src/B.kt","status":"modified"}]}}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SingleResponse<SessionInfo>>(raw).data
        assertEquals(
            listOf("src/A.kt", "src/B.kt"),
            parsed.revert?.revertFilePaths(),
        )
    }

    @Test
    fun `DL-013 decodes a staged revert with a nested file object`() {
        val raw = """
            {"data":{"id":"ses_01JAAAAAAAAAAAAAAAAAAAAAAAAA",
              "revert":{"messageID":"msg_01JCCCCCCCCCCCCCCCCCCCCCCCCC",
                "files":[{"file":{"path":"src/Nested.kt"},"status":"modified"}]}}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SingleResponse<SessionInfo>>(raw).data
        assertEquals(listOf("src/Nested.kt"), parsed.revert?.revertFilePaths())
    }

    @Test
    fun `DL-013 a staged revert with unreadable file entries reports unknown files`() {
        val revert = SessionRevert(
            files = listOf(
                buildJsonObject { put("status", JsonPrimitive("modified")) },
                buildJsonObject { put("additions", JsonPrimitive(3)) },
            ),
        )
        assertTrue(revert.revertFilePaths().isEmpty())
        assertEquals(RevertFileImpact.UnknownFiles, revert.fileImpact())
    }

    @Test
    fun `DL-013 a staged revert with no files is messages only`() {
        assertEquals(RevertFileImpact.MessagesOnly, SessionRevert().fileImpact())
    }

    @Test
    fun `encodes a fork before a message and a whole-session fork`() {
        assertEquals(
            """{"before":"msg_01JCCCCCCCCCCCCCCCCCCCCCCCCC"}""",
            ApiJson.encodeToString(ForkRequest(before = "msg_01JCCCCCCCCCCCCCCCCCCCCCCCCC")),
        )
        // No `before`: the field is omitted, so the engine forks through
        // the latest message instead of receiving a null it must interpret.
        assertEquals("{}", ApiJson.encodeToString(ForkRequest()))
    }

    @Test
    fun `encodes a compact request with id and delivery`() {
        val body = ApiJson.encodeToString(
            CompactRequest(id = "msg_01JEEEEEEEEEEEEEEEEEEEEEEEEE", delivery = "queue"),
        )
        assertEquals(
            """{"id":"msg_01JEEEEEEEEEEEEEEEEEEEEEEEEE","delivery":"queue"}""",
            body,
        )
    }

    @Test
    fun `encodes a revert stage request with files`() {
        assertEquals(
            """{"messageID":"msg_01JCCCCCCCCCCCCCCCCCCCCCCCCC","files":true}""",
            ApiJson.encodeToString(
                RevertStageRequest(messageID = "msg_01JCCCCCCCCCCCCCCCCCCCCCCCCC", files = true),
            ),
        )
    }

    @Test
    fun `decodes a compact answer as a compaction inbox item`() {
        val raw = """
            {"data":{"id":"msg_01JEEEEEEEEEEEEEEEEEEEEEEEEE","sessionID":"ses_01JAAAAAAAAAAAAAAAAAAAAAAAAA",
              "time":{"created":1793447932000},"type":"compaction","delivery":"queue","payload":{}}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SingleResponse<InboxItem>>(raw).data
        assertEquals("compaction", parsed.type)
        assertEquals("queue", parsed.delivery)
        assertTrue(parsed.payload.isEmpty())
    }

    @Test
    fun `new message ids match the engine format`() {
        val first = newMessageId()
        val second = newMessageId()
        for (id in listOf(first, second)) {
            assertTrue(id.startsWith("msg_"))
            // "msg_" plus the engine's 26-character identifier.
            assertEquals(30, id.length)
            assertTrue(id.drop(4).all { it.isLetterOrDigit() })
        }
        assertTrue(first != second)
    }
}
