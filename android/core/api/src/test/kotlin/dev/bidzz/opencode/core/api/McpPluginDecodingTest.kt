package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.McpAddRequest
import dev.bidzz.opencode.core.api.dto.McpResourceCatalogResponse
import dev.bidzz.opencode.core.api.dto.McpServerConfig
import dev.bidzz.opencode.core.api.dto.PluginCheckRequest
import dev.bidzz.opencode.core.api.dto.PluginUpdateRequest
import dev.bidzz.opencode.core.api.dto.PluginsResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding / encoding tests for the MCP writes and the plugin check/update.
 *
 * Fixtures follow the shapes verified against the engine fork source at
 * pin 92b2ca0dd727942e00aba3523ed141d53e78ea28 (see the DTO comments):
 * they are written to the verified schema, not to the app's guess, so a
 * mismatch here means the client disagrees with the engine.
 */
class McpPluginDecodingTest {

    @Test
    fun `local add request encodes command as an array`() {
        val encoded = ApiJson.encodeToString(
            McpAddRequest(
                config = McpServerConfig.local(
                    command = listOf("npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp"),
                    cwd = "/home/user/project",
                ),
            ),
        )
        assertEquals(
            """{"config":{"type":"local","command":["npx","-y","@modelcontextprotocol/server-filesystem","/tmp"],"cwd":"/home/user/project"}}""",
            encoded,
        )
    }

    @Test
    fun `pool flag encodes when set and is omitted when absent`() {
        val withPool = ApiJson.encodeToString(
            McpAddRequest(
                config = McpServerConfig.local(
                    command = listOf("npx", "-y", "server"),
                ).copy(pool = true),
            ),
        )
        assertEquals(
            """{"config":{"type":"local","command":["npx","-y","server"],"pool":true}}""",
            withPool,
        )
        val withoutPool = ApiJson.encodeToString(
            McpAddRequest(config = McpServerConfig.remote("https://mcp.example.com/sse")),
        )
        assertEquals(
            """{"config":{"type":"remote","url":"https://mcp.example.com/sse"}}""",
            withoutPool,
        )
    }

    @Test
    fun `remote add request encodes only type and url`() {
        val encoded = ApiJson.encodeToString(
            McpAddRequest(config = McpServerConfig.remote("https://mcp.example.com/sse")),
        )
        assertEquals(
            """{"config":{"type":"remote","url":"https://mcp.example.com/sse"}}""",
            encoded,
        )
    }

    @Test
    fun `decodes a resource catalog with resources and templates`() {
        val raw = """
            {"data":{"resources":[
              {"server":"filesystem","name":"notes","uri":"file:///notes.md",
               "description":"Project notes","mimeType":"text/markdown"},
              {"server":"filesystem","name":"config","uri":"file:///config.json"}],
             "templates":[
              {"server":"filesystem","name":"file","uriTemplate":"file:///{path}",
               "description":"Any file"}]}}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<McpResourceCatalogResponse>(raw).data
        assertEquals(2, parsed.resources.size)
        assertEquals("filesystem", parsed.resources[0].server)
        assertEquals("notes", parsed.resources[0].name)
        assertEquals("file:///notes.md", parsed.resources[0].uri)
        assertEquals("text/markdown", parsed.resources[0].mimeType)
        assertEquals(null, parsed.resources[1].description)
        assertEquals(1, parsed.templates.size)
        assertEquals("file:///{path}", parsed.templates[0].uriTemplate)
    }

    @Test
    fun `decodes an empty resource catalog`() {
        val parsed = ApiJson.decodeFromString<McpResourceCatalogResponse>(
            """{"data":{"resources":[],"templates":[]}}""",
        ).data
        assertTrue(parsed.resources.isEmpty())
        assertTrue(parsed.templates.isEmpty())
    }

    @Test
    fun `plugin check request omits an absent target`() {
        assertEquals("{}", ApiJson.encodeToString(PluginCheckRequest(target = null)))
        assertEquals(
            """{"target":"opencode-anthropic-auth"}""",
            ApiJson.encodeToString(PluginCheckRequest(target = "opencode-anthropic-auth")),
        )
    }

    @Test
    fun `decodes a plugin check answer with outdated stamped in`() {
        val raw = """
            {"data":[
              {"source":{"type":"package","target":"opencode-anthropic-auth",
                "version":"1.2.3","outdated":true},
               "features":{},"state":{"status":"active"}},
              {"source":{"type":"builtin"},"features":{},"state":{"status":"active"}}]}
        """.trimIndent()

        val parsed = ApiJson.decodeFromString<PluginsResponse>(raw).data
        assertEquals(2, parsed.size)
        assertEquals(true, (parsed[0].source["outdated"] as? JsonPrimitive)?.booleanOrNull)
        assertEquals("package", (parsed[0].source["type"] as? JsonPrimitive)?.contentOrNull)
        assertFalse(parsed[1].source.containsKey("outdated"))
    }

    @Test
    fun `plugin update request encodes the target list`() {
        val encoded = ApiJson.encodeToString(
            PluginUpdateRequest(targets = listOf("opencode-anthropic-auth", "opencode-foo")),
        )
        assertEquals(
            """{"targets":["opencode-anthropic-auth","opencode-foo"]}""",
            encoded,
        )
    }
}
