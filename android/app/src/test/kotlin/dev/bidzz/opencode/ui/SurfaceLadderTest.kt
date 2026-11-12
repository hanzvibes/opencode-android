package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.bidzz.opencode.core.design.OpenCodeTokens
import dev.bidzz.opencode.core.design.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The surface ladder, measured. Light steps are 1.02-1.05:1 by design
 * (White B tells surfaces apart with the 1dp border + shadow, not with
 * colour) — the test locks the ordering so a future alias cannot silently
 * collapse two steps into one. Dark steps are 1.09-1.13:1 each, and the
 * variant is its own step since Opsi B: when it aliased raised, chips
 * and inline code were invisible on dark cards. Pure JVM, no Compose.
 */
class SurfaceLadderTest {

    @Test
    fun `light ladder is ordered and no two steps are aliases`() {
        val background = Palette.lightBackground
        val raised = Palette.lightSurfaceRaised
        val sunken = Palette.lightSurfaceSunken
        val variant = Palette.lightSurfaceVariant
        // Raised (white) is the lightest, then background, sunken, variant.
        assertTrue(luminance(raised) > luminance(background))
        assertTrue(luminance(background) > luminance(sunken))
        assertTrue(luminance(sunken) > luminance(variant))
        // Each step is small by design, but it must exist.
        assertTrue(contrastRatio(raised, background) >= 1.01)
        assertTrue(contrastRatio(background, sunken) >= 1.01)
        assertTrue(contrastRatio(sunken, variant) >= 1.01)
        assertTrue(variant.toArgb() != raised.toArgb())
        // Pressed is its own step darker than variant, not the variant.
        val pressed = Palette.lightSurfacePressed
        assertTrue(luminance(pressed) < luminance(variant))
        assertTrue(contrastRatio(variant, pressed) >= 1.05)
    }

    @Test
    fun `dark ladder is four steps of at least 1_08 each`() {
        val background = Palette.darkBackground
        val sunken = Palette.darkSurfaceSunken
        val raised = Palette.darkSurfaceRaised
        val variant = Palette.darkSurfaceVariant
        assertTrue(luminance(background) < luminance(sunken))
        assertTrue(luminance(sunken) < luminance(raised))
        assertTrue(luminance(raised) < luminance(variant))
        assertTrue(contrastRatio(sunken, background) >= 1.08)
        assertTrue(contrastRatio(raised, sunken) >= 1.08)
        assertTrue(contrastRatio(variant, raised) >= 1.08)
        assertTrue(variant.toArgb() != raised.toArgb())
    }

    @Test
    fun `dark bubbles tell the sender apart`() {
        // User bubble one step above the assistant bubble (1.22:1); when
        // both were the raised surface, only position/avatar/tail told
        // the sender apart in dark.
        val user = Palette.darkUserBubble
        val assistant = Palette.darkAssistantBubble
        assertTrue(user.toArgb() != assistant.toArgb())
        assertTrue(contrastRatio(user, assistant) >= 1.15)
        assertEquals(Palette.darkSurfaceRaised.toArgb(), assistant.toArgb())
        // Light keeps the raised surface for both bubbles; there the
        // assistant bubble is the grey #EFEFF1.
        assertEquals(
            Palette.lightSurfaceRaised.toArgb(),
            Palette.lightUserBubble.toArgb(),
        )
        assertTrue(
            Palette.lightUserBubble.toArgb() != Palette.lightAssistantBubble.toArgb(),
        )
    }

    @Test
    fun `dark muted is brighter than faint`() {
        // The old pair was inverted (faint #9A9AA0 lighter than muted
        // #8E8E93); muted #AEAEB2 restores the order while staying AA.
        assertTrue(
            luminance(Palette.darkOnSurfaceMuted) >
                luminance(Palette.darkOnSurfaceFaint),
        )
    }

    @Test
    fun `alpha tokens are the only alpha values for tints and borders`() {
        val alpha = OpenCodeTokens().alpha
        assertEquals(0.12f, alpha.subtle)
        assertEquals(0.25f, alpha.border)
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
