package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.ShellInfo
import dev.bidzz.opencode.core.api.dto.ShellOutput
import dev.bidzz.opencode.core.api.dto.SingleResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shell endpoint shapes (audit-200 C14, M-195/M-196), verified against the
 * engine fork source at pin fedc6f20 (`packages/schema/src/shell.ts`).
 * The fixtures mirror the schema, including the location envelope
 * (`{location, data}`) the create/get/output endpoints answer with.
 */
class ShellDecodingTest {

    @Test
    fun `decodes a created shell from the location envelope`() {
        val raw = """
            {"location":{"directory":"/storage/emulated/0/Projects/demo","project":"demo"},
            "data":{"id":"sh_01K8","status":"running","command":"./gradlew test","cwd":"/storage/emulated/0/Projects/demo","shell":"/bin/bash","file":"/tmp/sh_01K8.out","pid":1234,"metadata":{},"time":{"started":1793447927442}}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<SingleResponse<ShellInfo>>(raw)
        val info = parsed.data
        assertEquals("sh_01K8", info.id)
        assertEquals("running", info.status)
        assertEquals("./gradlew test", info.command)
        assertEquals("/tmp/sh_01K8.out", info.file)
        assertEquals(1234, info.pid)
        assertNull(info.exit)
        assertNull(info.signal)
    }

    @Test
    fun `decodes an exited shell with its exit code`() {
        val raw = """
            {"location":{"directory":"/tmp","project":"demo"},
            "data":{"id":"sh_01K8","status":"exited","command":"true","cwd":"/tmp","shell":"/bin/sh","file":"/tmp/sh_01K8.out","exit":0,"metadata":{},"time":{"started":1,"completed":2}}}
        """.trimIndent()

        val info = ApiJson.decodeFromString<SingleResponse<ShellInfo>>(raw).data
        assertEquals("exited", info.status)
        assertEquals(0, info.exit)
        assertTrue(info.isTerminal)
    }

    @Test
    fun `decodes an output page`() {
        val raw = """
            {"location":{"directory":"/tmp","project":"demo"},
            "data":{"output":"line1\nline2\n","cursor":12,"size":12,"truncated":false}}
        """.trimIndent()

        val page = ApiJson.decodeFromString<SingleResponse<ShellOutput>>(raw).data
        assertEquals("line1\nline2\n", page.output)
        assertEquals(12L, page.cursor)
        assertEquals(12L, page.size)
        assertEquals(false, page.truncated)
    }
}
