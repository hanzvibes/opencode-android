package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.bidzz.opencode.core.design.OpenCodeColors
import dev.bidzz.opencode.core.design.Palette
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Audit-200 D2 (m-1175): the Providers screen hardcoded brand colours in
 * two places and skipped the light/dark variants of `providerColor()` —
 * DeepSeek and Ollama did not match either theme. Colours now come from
 * the theme only; this pins the OpenCode branch, the dark/light split,
 * and the guard that no hardcoded brand hex survives in ProvidersScreen.
 */
class ProviderColorTest {

    // providerColor() only reads the provider* fields and the accent
    // fallback, so the other 24 required fields are Unspecified here.
    private fun colors(
        accent: Color = Color.Red,
        providerOpenCode: Color = Palette.darkProviderOpenCode,
        providerOllama: Color = Palette.darkProviderOllama,
        providerDeepSeek: Color = Palette.darkProviderDeepSeek,
    ) = OpenCodeColors(
        background = Color.Unspecified,
        surface = Color.Unspecified,
        surfaceRaised = Color.Unspecified,
        surfaceSunken = Color.Unspecified,
        surfaceVariant = Color.Unspecified,
        onSurface = Color.Unspecified,
        onSurfaceMuted = Color.Unspecified,
        onSurfaceFaint = Color.Unspecified,
        outline = Color.Unspecified,
        hairline = Color.Unspecified,
        fill = Color.Unspecified,
        surfacePressed = Color.Unspecified,
        accent = accent,
        onAccent = Color.Unspecified,
        userBubble = Color.Unspecified,
        onUserBubble = Color.Unspecified,
        assistantBubble = Color.Unspecified,
        onAssistantBubble = Color.Unspecified,
        success = Color.Unspecified,
        warning = Color.Unspecified,
        danger = Color.Unspecified,
        neutral = Color.Unspecified,
        syntaxKeyword = Color.Unspecified,
        syntaxString = Color.Unspecified,
        syntaxNumber = Color.Unspecified,
        syntaxComment = Color.Unspecified,
        providerOpenCode = providerOpenCode,
        providerOllama = providerOllama,
        providerDeepSeek = providerDeepSeek,
    )

    @Test
    fun `opencode resolves to its own brand indigo, not the accent fallback`() {
        assertEquals(
            Palette.darkProviderOpenCode.toArgb(),
            colors().providerColor("opencode").toArgb(),
        )
        assertEquals(0xFF6366F1.toInt(), Palette.darkProviderOpenCode.toArgb())
        assertNotEquals(
            Color.Red.toArgb(),
            colors().providerColor("opencode").toArgb(),
        )
        assertEquals(
            Palette.darkProviderOpenCode.toArgb(),
            colors().providerColor("zen").toArgb(),
        )
        // And the fallback still works for an unknown provider.
        assertEquals(Color.Red.toArgb(), colors().providerColor("some-unknown").toArgb())
    }

    @Test
    fun `provider colours differ between the dark and light palettes`() {
        assertNotEquals(
            Palette.darkProviderOpenCode.toArgb(),
            Palette.lightProviderOpenCode.toArgb(),
        )
        assertNotEquals(
            Palette.darkProviderOllama.toArgb(),
            Palette.lightProviderOllama.toArgb(),
        )
        assertNotEquals(
            Palette.darkProviderDeepSeek.toArgb(),
            Palette.lightProviderDeepSeek.toArgb(),
        )
        // The fields are actually consulted: a colours object built with
        // the light tokens resolves the light colour.
        val light = colors(
            providerOllama = Palette.lightProviderOllama,
            providerDeepSeek = Palette.lightProviderDeepSeek,
            providerOpenCode = Palette.lightProviderOpenCode,
        )
        assertEquals(Palette.lightProviderOllama.toArgb(), light.providerColor("ollama").toArgb())
        assertEquals(Palette.lightProviderDeepSeek.toArgb(), light.providerColor("deepseek").toArgb())
        assertEquals(Palette.lightProviderOpenCode.toArgb(), light.providerColor("opencode").toArgb())
    }

    @Test
    fun `no hardcoded brand hex survives in ProvidersScreen`() {
        val source = File("src/main/kotlin/dev/bidzz/opencode/ui/providers/ProvidersScreen.kt").readText()
        assertFalse(
            "ProvidersScreen still hardcodes a brand colour — use OpenCodeTheme.colors.providerColor()",
            Regex("""Color\(0xFF[0-9A-Fa-f]{6}\)""").containsMatchIn(source),
        )
    }
}
