package dev.bidzz.opencode.ui.providers

import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.PluginFeatures
import dev.bidzz.opencode.core.api.dto.PluginInfo
import dev.bidzz.opencode.core.api.dto.PluginState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Antigravity plugin detection (audit-200 C14, M-205): the engine may
 * report the plugin by its id, by its package target, or — for a local
 * install — by a path containing the package name. Any of the three
 * counts; anything else does not.
 */
class AntigravityPluginTest {

    private fun plugin(
        id: String? = null,
        source: JsonObject = buildJsonObject { put("type", "builtin") },
    ) = PluginInfo(id = id, source = source, features = PluginFeatures(), state = PluginState(status = "active"))

    private fun packagePlugin(target: String) = plugin(
        source = buildJsonObject {
            put("type", "package")
            put("target", target)
        },
    )

    private fun localPlugin(path: String) = plugin(
        source = buildJsonObject {
            put("type", "local")
            put("path", path)
        },
    )

    @Test
    fun `package plugin matches by target`() {
        assertTrue(isAntigravityPluginRegistered(listOf(packagePlugin("opencode-antigravity-auth"))))
    }

    @Test
    fun `other package plugins do not match`() {
        assertFalse(isAntigravityPluginRegistered(listOf(packagePlugin("opencode-anthropic-auth"))))
    }

    @Test
    fun `local plugin matches by path`() {
        assertTrue(
            isAntigravityPluginRegistered(
                listOf(localPlugin("/home/user/.local/share/opencode-antigravity-auth")),
            ),
        )
    }

    @Test
    fun `plugin id matches when the engine gives one`() {
        assertTrue(isAntigravityPluginRegistered(listOf(plugin(id = "opencode-antigravity-auth"))))
    }

    @Test
    fun `empty list is not registered`() {
        assertFalse(isAntigravityPluginRegistered(emptyList()))
    }

    @Test
    fun `a failed list is unknown, not missing`() {
        val failure = ApiResult.Failure(code = 500, tag = "EngineDown")
        assertNull(antigravityPluginStatus(failure))
        assertEquals(
            true,
            antigravityPluginStatus(ApiResult.Success(listOf(packagePlugin("opencode-antigravity-auth")))),
        )
        assertEquals(
            false,
            antigravityPluginStatus(ApiResult.Success(listOf(packagePlugin("other")))),
        )
    }
}
