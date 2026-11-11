package dev.bidzz.opencode.ui.plugin

import dev.bidzz.opencode.core.api.dto.PluginFeatures
import dev.bidzz.opencode.core.api.dto.PluginInfo
import dev.bidzz.opencode.core.api.dto.PluginState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginLabelTest {

    private fun plugin(
        id: String? = null,
        source: JsonObject = buildJsonObject { put("type", "builtin") },
        features: PluginFeatures = PluginFeatures(),
        state: PluginState = PluginState(status = "active"),
    ) = PluginInfo(id = id, source = source, features = features, state = state)

    @Test
    fun `builtin source reads as Built-in`() {
        assertEquals("Built-in", pluginSourceLabel(buildJsonObject { put("type", "builtin") }))
    }

    @Test
    fun `package source shows target and version`() {
        val source = buildJsonObject {
            put("type", "package")
            put("target", "opencode-anthropic")
            put("version", "1.2.3")
        }
        assertEquals("opencode-anthropic@1.2.3", pluginSourceLabel(source))
    }

    @Test
    fun `package source without version shows target only`() {
        val source = buildJsonObject {
            put("type", "package")
            put("target", "opencode-anthropic")
        }
        assertEquals("opencode-anthropic", pluginSourceLabel(source))
    }

    @Test
    fun `local source shows its path`() {
        val source = buildJsonObject {
            put("type", "local")
            put("path", "/home/user/my-plugin")
        }
        assertEquals("/home/user/my-plugin", pluginSourceLabel(source))
    }

    @Test
    fun `sdk source reads as SDK`() {
        assertEquals("SDK", pluginSourceLabel(buildJsonObject { put("type", "sdk") }))
    }

    @Test
    fun `unknown source type is shown as-is, never hidden`() {
        assertEquals("future-kind", pluginSourceLabel(buildJsonObject { put("type", "future-kind") }))
        assertEquals("?", pluginSourceLabel(JsonObject(emptyMap())))
    }

    @Test
    fun `display name prefers the id, falls back to the source`() {
        val withId = plugin(id = "my-plugin")
        assertEquals("my-plugin", withId.displayName())
        val withoutId = plugin(id = null)
        assertEquals("Built-in", withoutId.displayName())
        val blankId = plugin(id = "  ")
        assertEquals("Built-in", blankId.displayName())
    }

    @Test
    fun `state kind maps case-insensitively, unknown stays unknown`() {
        assertEquals(PluginStateKind.ACTIVE, plugin(state = PluginState(status = "Active")).stateKind())
        assertEquals(PluginStateKind.FAILED, plugin(state = PluginState(status = "failed")).stateKind())
        assertEquals(PluginStateKind.UNKNOWN, plugin(state = PluginState(status = "paused")).stateKind())
        assertEquals(PluginStateKind.UNKNOWN, plugin(state = PluginState(status = "")).stateKind())
    }

    @Test
    fun `feature words list only the flags the plugin declares`() {
        val all = plugin(features = PluginFeatures(server = true, tui = true, rpc = true, gui = true))
        assertEquals(listOf("server", "tui", "rpc", "gui"), all.featureWords())
        val none = plugin(features = PluginFeatures())
        assertEquals(emptyList<String>(), none.featureWords())
    }
}
