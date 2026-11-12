package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.bidzz.opencode.core.design.OpenCodeColors
import dev.bidzz.opencode.core.design.Palette
import dev.bidzz.opencode.core.design.materialColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Material3 [androidx.compose.material3.ColorScheme] mapping, in full.
 *
 * Before Opsi B the mapping set 10 fields and let the rest fall through
 * to the M3 baseline defaults — dark leaked the baseline purple #D0BCFF
 * through `surfaceContainerHigh`, and `surfaceVariant` was mapped to the
 * raised surface instead of the variant step. This test builds both
 * schemes from [OpenCodeColors] mirroring the real DarkColors/LightColors
 * assignments and asserts every field reads its own token.
 *
 * The test asserts the MAPPING (which token each field reads), not the
 * ladder values: the dark container top step only reads above pressed
 * once PR #39's variant (#26262A) lands, so no ordering is asserted
 * between High and Highest in dark here — SurfaceLadderTest owns the
 * ladder values. Pure JVM: [materialColorScheme] is a pure function.
 */
class MaterialSchemeTest {

    private fun darkColors() = OpenCodeColors(
        isDark = true,
        background = Palette.darkBackground,
        surface = Palette.darkSurface,
        surfaceRaised = Palette.darkSurfaceRaised,
        surfaceSunken = Palette.darkSurfaceSunken,
        surfaceVariant = Palette.darkSurfaceVariant,
        onSurface = Palette.darkOnSurface,
        onSurfaceMuted = Palette.darkOnSurfaceMuted,
        onSurfaceFaint = Palette.darkOnSurfaceFaint,
        outline = Palette.darkOutline,
        hairline = Palette.darkHairline,
        fill = Palette.darkFill,
        surfacePressed = Palette.darkSurfacePressed,
        accent = Palette.darkAccent,
        onAccent = Palette.darkOnAccent,
        userBubble = Palette.darkUserBubble,
        onUserBubble = Palette.darkOnUserBubble,
        assistantBubble = Palette.darkAssistantBubble,
        onAssistantBubble = Palette.darkOnAssistantBubble,
        success = Palette.success,
        warning = Palette.warning,
        danger = Palette.danger,
        neutral = Palette.neutral,
        accentFill = Palette.darkAccentFill,
        syntaxKeyword = Palette.darkSyntaxKeyword,
        syntaxString = Palette.darkSyntaxString,
        syntaxNumber = Palette.darkSyntaxNumber,
        syntaxComment = Palette.darkSyntaxComment,
    )

    private fun lightColors() = OpenCodeColors(
        isDark = false,
        background = Palette.lightBackground,
        surface = Palette.lightSurface,
        surfaceRaised = Palette.lightSurfaceRaised,
        surfaceSunken = Palette.lightSurfaceSunken,
        surfaceVariant = Palette.lightSurfaceVariant,
        onSurface = Palette.lightOnSurface,
        onSurfaceMuted = Palette.lightOnSurfaceMuted,
        onSurfaceFaint = Palette.lightOnSurfaceFaint,
        outline = Palette.lightOutline,
        hairline = Palette.lightHairline,
        fill = Palette.lightFill,
        surfacePressed = Palette.lightSurfacePressed,
        accent = Palette.lightAccent,
        onAccent = Palette.lightOnAccent,
        userBubble = Palette.lightUserBubble,
        onUserBubble = Palette.lightOnUserBubble,
        assistantBubble = Palette.lightAssistantBubble,
        onAssistantBubble = Palette.lightOnAssistantBubble,
        success = Palette.lightSuccessText,
        warning = Palette.lightWarningText,
        danger = Palette.lightDangerText,
        neutral = Palette.lightAccentText,
        accentFill = Palette.lightAccentFill,
        syntaxKeyword = Palette.lightSyntaxKeyword,
        syntaxString = Palette.lightSyntaxString,
        syntaxNumber = Palette.lightSyntaxNumber,
        syntaxComment = Palette.lightSyntaxComment,
    )

    @Test
    fun `primary secondary and tertiary are the one blue in both themes`() {
        for ((colors, dark) in listOf(darkColors() to true, lightColors() to false)) {
            val scheme = materialColorScheme(colors, dark)
            assertEquals(colors.accentFill.toArgb(), scheme.primary.toArgb())
            assertEquals(colors.onAccent.toArgb(), scheme.onPrimary.toArgb())
            assertEquals(colors.accentFill.toArgb(), scheme.secondary.toArgb())
            assertEquals(colors.onAccent.toArgb(), scheme.onSecondary.toArgb())
            assertEquals(colors.accentFill.toArgb(), scheme.tertiary.toArgb())
            assertEquals(colors.onAccent.toArgb(), scheme.onTertiary.toArgb())
        }
    }

    @Test
    fun `surfaceVariant is the variant step, not raised`() {
        // The pre-Opsi B bug: surfaceVariant was mapped to surfaceRaised.
        for ((colors, dark) in listOf(darkColors() to true, lightColors() to false)) {
            val scheme = materialColorScheme(colors, dark)
            assertEquals(colors.surfaceVariant.toArgb(), scheme.surfaceVariant.toArgb())
            assertEquals(colors.onSurfaceMuted.toArgb(), scheme.onSurfaceVariant.toArgb())
        }
    }

    @Test
    fun `containers are ladder steps in both themes`() {
        val light = materialColorScheme(lightColors(), false)
        assertEquals(Palette.lightSurfaceRaised.toArgb(), light.surfaceContainerLowest.toArgb())
        assertEquals(Palette.lightBackground.toArgb(), light.surfaceContainerLow.toArgb())
        assertEquals(Palette.lightSurfaceSunken.toArgb(), light.surfaceContainer.toArgb())
        assertEquals(Palette.lightSurfaceVariant.toArgb(), light.surfaceContainerHigh.toArgb())
        assertEquals(Palette.lightSurfacePressed.toArgb(), light.surfaceContainerHighest.toArgb())
        assertTrue(luminance(light.surfaceContainerLowest) > luminance(light.surfaceContainerLow))
        assertTrue(luminance(light.surfaceContainerLow) > luminance(light.surfaceContainer))
        assertTrue(luminance(light.surfaceContainer) > luminance(light.surfaceContainerHigh))

        val dark = materialColorScheme(darkColors(), true)
        assertEquals(Palette.darkBackground.toArgb(), dark.surfaceContainerLowest.toArgb())
        assertEquals(Palette.darkSurfaceSunken.toArgb(), dark.surfaceContainerLow.toArgb())
        assertEquals(Palette.darkSurface.toArgb(), dark.surfaceContainer.toArgb())
        assertEquals(Palette.darkSurfacePressed.toArgb(), dark.surfaceContainerHigh.toArgb())
        assertEquals(Palette.darkSurfaceVariant.toArgb(), dark.surfaceContainerHighest.toArgb())
        assertTrue(luminance(dark.surfaceContainerLowest) < luminance(dark.surfaceContainerLow))
        assertTrue(luminance(dark.surfaceContainerLow) < luminance(dark.surfaceContainer))
        assertTrue(luminance(dark.surfaceContainer) < luminance(dark.surfaceContainerHigh))
    }

    @Test
    fun `no field is an M3 baseline default`() {
        // The baseline leak: dark surfaceContainerHigh was #D0BCFF purple.
        val baselineDefaults = listOf(
            0xFFD0BCFF.toInt(), 0xFFE6E0E9.toInt(), 0xFFF7F2FA.toInt(),
            0xFFFEF7FF.toInt(), 0xFFECE6F0.toInt(), 0xFF0F0D13.toInt(),
            0xFF1D1B20.toInt(), 0xFF141218.toInt(), 0xFF2B2930.toInt(),
            0xFF36343B.toInt(),
        )
        for ((colors, dark) in listOf(darkColors() to true, lightColors() to false)) {
            val scheme = materialColorScheme(colors, dark)
            val fields = listOf(
                scheme.primary, scheme.primaryContainer, scheme.secondary,
                scheme.secondaryContainer, scheme.tertiary, scheme.tertiaryContainer,
                scheme.error, scheme.errorContainer, scheme.background, scheme.surface,
                scheme.surfaceVariant, scheme.surfaceDim, scheme.surfaceBright,
                scheme.surfaceContainerLowest, scheme.surfaceContainerLow,
                scheme.surfaceContainer, scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest, scheme.outline, scheme.outlineVariant,
                scheme.inverseSurface, scheme.inversePrimary,
            )
            for (field in fields) {
                assertTrue(
                    "M3 baseline default leaked: ${field.toArgb().toUInt().toString(16)}",
                    field.toArgb() !in baselineDefaults,
                )
            }
        }
    }

    @Test
    fun `error pair is AA in both themes`() {
        // onError is the background, not white: white on the dark danger
        // #FF453A is only 3.41:1, black-on-red is 6.17:1.
        for ((colors, dark) in listOf(darkColors() to true, lightColors() to false)) {
            val scheme = materialColorScheme(colors, dark)
            assertEquals(colors.danger.toArgb(), scheme.error.toArgb())
            assertEquals(colors.background.toArgb(), scheme.onError.toArgb())
            assertTrue(contrastRatio(scheme.onError, scheme.error) >= 4.5)
            assertEquals(colors.surfaceVariant.toArgb(), scheme.errorContainer.toArgb())
            assertEquals(colors.onSurface.toArgb(), scheme.onErrorContainer.toArgb())
        }
    }

    @Test
    fun `outline scrim inverse and tint read own tokens`() {
        for ((colors, dark) in listOf(darkColors() to true, lightColors() to false)) {
            val scheme = materialColorScheme(colors, dark)
            assertEquals(colors.outline.toArgb(), scheme.outline.toArgb())
            assertEquals(colors.hairline.toArgb(), scheme.outlineVariant.toArgb())
            assertEquals(0x52000000.toInt(), scheme.scrim.toArgb())
            assertEquals(colors.surface.toArgb(), scheme.surfaceTint.toArgb())
            assertEquals(colors.surface.toArgb(), scheme.surface.toArgb())
            assertEquals(colors.onSurface.toArgb(), scheme.onSurface.toArgb())
            assertEquals(colors.background.toArgb(), scheme.background.toArgb())
            assertEquals(colors.onSurface.toArgb(), scheme.onBackground.toArgb())
        }
        val light = materialColorScheme(lightColors(), false)
        assertEquals(Palette.darkSurfaceRaised.toArgb(), light.inverseSurface.toArgb())
        assertEquals(Palette.darkOnSurface.toArgb(), light.inverseOnSurface.toArgb())
        assertEquals(Palette.darkAccentFill.toArgb(), light.inversePrimary.toArgb())
        val dark = materialColorScheme(darkColors(), true)
        assertEquals(Palette.lightSurfaceRaised.toArgb(), dark.inverseSurface.toArgb())
        assertEquals(Palette.lightOnSurface.toArgb(), dark.inverseOnSurface.toArgb())
        assertEquals(Palette.lightAccentFill.toArgb(), dark.inversePrimary.toArgb())
    }

    // ---- WCAG relative luminance, straight from the spec (mirrors WhiteThemeTest) ----

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        fun linear(c: Double) = if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = luminance(foreground)
        val l2 = luminance(background)
        val (lighter, darker) = if (l1 >= l2) l1 to l2 else l2 to l1
        return (lighter + 0.05) / (darker + 0.05)
    }
}
