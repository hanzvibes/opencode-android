package dev.bidzz.opencode.ui.config

import dev.bidzz.opencode.core.api.dto.ConfigEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Decoding of the `GET /api/config` response. */
class ConfigEntryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes a document entry with its key settings`() {
        val entries = json.decodeFromString<List<ConfigEntry>>(
            """[{
            |"type":"document",
            |"path":"/home/user/.config/opencode/opencode.json",
            |"info":{
            |  "model":"anthropic/claude-sonnet-4",
            |  "default_agent":"build",
            |  "shell":"/bin/bash",
            |  "timezone":"Asia/Jakarta",
            |  "username":"rehan",
            |  "providers":{}
            |}}]""".trimMargin(),
        )
        val entry = entries.single()
        assertEquals("document", entry.type)
        assertEquals("/home/user/.config/opencode/opencode.json", entry.path)
        assertEquals("anthropic/claude-sonnet-4", configModelLabel(entry.info?.model))
        assertEquals("build", entry.info?.default_agent)
        assertEquals("/bin/bash", entry.info?.shell)
        assertEquals("Asia/Jakarta", entry.info?.timezone)
        assertEquals("rehan", entry.info?.username)
    }

    @Test
    fun `decodes a directory entry with no document`() {
        val entries = json.decodeFromString<List<ConfigEntry>>(
            """[{"type":"directory","path":"/home/user/project/.opencode"}]""",
        )
        val entry = entries.single()
        assertEquals("directory", entry.type)
        assertEquals("/home/user/project/.opencode", entry.path)
        assertNull(entry.info)
    }

    @Test
    fun `model label handles a string, an object, and null`() {
        assertEquals(
            "anthropic/claude-sonnet-4",
            configModelLabel(JsonPrimitive("anthropic/claude-sonnet-4")),
        )
        val objectModel = buildJsonObject {
            put("modelID", "claude-sonnet-4")
            put("providerID", "anthropic")
        }
        assertEquals(
            """{"modelID":"claude-sonnet-4","providerID":"anthropic"}""",
            configModelLabel(objectModel),
        )
        assertNull(configModelLabel(null))
        assertNull(configModelLabel(JsonPrimitive("   ")))
    }
}
