package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.bidzz.opencode.core.design.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dark (OLED) theme: the accent-fill pair only. Focused mirror of
 * [WhiteThemeTest] for the fill role — the full dark contrast audit the
 * visual report recommends (every dark token on every dark background)
 * is a later item, not this one. Pure JVM: [Color.toArgb] plus local
 * luminance math, no Android framework, no Robolectric.
 */
class DarkThemeTest {

    @Test
    fun `dark accent fill is 0066CC and white text on it passes AA`() {
        // Every solid button/badge/chip fill that carries white onAccent
        // text uses accentFill, not the bright accent: white on #0066CC
        // is 5.57:1, white on #0A84FF is only 3.65:1.
        assertEquals(0xFF0066CC.toInt(), Palette.darkAccentFill.toArgb())
        assertTrue(
            contrastRatio(Color(0xFFFFFFFF), Palette.darkAccentFill) >= 4.5,
        )
        // The fill is its own token, not an alias of the bright accent:
        // aliasing it back to #0A84FF would silently reintroduce 3.65:1.
        assertTrue(Palette.darkAccentFill.toArgb() != Palette.darkAccent.toArgb())
    }

    @Test
    fun `bright dark accent is still dots-and-fills only, never a text fill`() {
        // #0A84FF is 3.65:1 with white text — below AA — so no button,
        // badge or chip fill may use it under onAccent text.
        assertTrue(contrastRatio(Color(0xFFFFFFFF), Palette.darkAccent) < 4.5)
        assertEquals(0xFF0A84FF.toInt(), Palette.darkAccent.toArgb())
    }

    @Test
    fun `dark muted and faint both pass AA on every dark surface`() {
        // Opsi B restored the order (muted #AEAEB2 brighter than faint
        // #9A9AA0); both must stay AA on black, raised, sunken and the
        // new variant step #26262A.
        val surfaces = listOf(
            Palette.darkBackground,
            Palette.darkSurfaceRaised,
            Palette.darkSurfaceSunken,
            Palette.darkSurfaceVariant,
        )
        for (surface in surfaces) {
            assertTrue(
                "muted on $surface is ${contrastRatio(Palette.darkOnSurfaceMuted, surface)}",
                contrastRatio(Palette.darkOnSurfaceMuted, surface) >= 4.5,
            )
            assertTrue(
                "faint on $surface is ${contrastRatio(Palette.darkOnSurfaceFaint, surface)}",
                contrastRatio(Palette.darkOnSurfaceFaint, surface) >= 4.5,
            )
        }
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
