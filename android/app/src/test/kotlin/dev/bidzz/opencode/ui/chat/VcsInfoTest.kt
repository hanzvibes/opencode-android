package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.VcsBaseResponse
import dev.bidzz.opencode.core.api.dto.VcsInfoResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Decoding of the `GET /api/vcs` and `GET /api/vcs/base` responses. */
class VcsInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes current and default branch`() {
        val decoded = json.decodeFromString<VcsInfoResponse>(
            """{"location":{"directory":"/home/user/app"},"data":{
            |"provider":"git",
            |"branch":{"current":"feature/login","default":"main"}
            |}}""".trimMargin(),
        )
        assertEquals("git", decoded.data.provider)
        assertEquals("feature/login", decoded.data.branch.current)
        assertEquals("main", decoded.data.branch.default)
    }

    @Test
    fun `missing branch names decode as null, not a crash`() {
        val decoded = json.decodeFromString<VcsInfoResponse>(
            """{"data":{"branch":{}}}""",
        )
        assertNull(decoded.data.provider)
        assertNull(decoded.data.branch.current)
        assertNull(decoded.data.branch.default)
    }

    @Test
    fun `decodes the review base with its source`() {
        val decoded = json.decodeFromString<VcsBaseResponse>(
            """{"data":{"name":"main","ref":"abc123","source":"reflog"}}""",
        )
        assertEquals("main", decoded.data?.name)
        assertEquals("abc123", decoded.data?.ref)
        assertEquals("reflog", decoded.data?.source)
    }

    @Test
    fun `a null base decodes as null data`() {
        val decoded = json.decodeFromString<VcsBaseResponse>("""{"data":null}""")
        assertNull(decoded.data)
    }
}
