package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6j (47-F5): [IconBadge] picks its default foreground from the
 * background's luminance, because white on a light badge background is
 * unreadable (2.2:1 on #34C759 / #FF9500 / #30B0C7). Pure JVM: [Color]
 * luminance math, no Android framework, no Robolectric.
 */
class IconBadgeForegroundTest {

    @Test
    fun `white on the light Diagnostics badge colours fails AA`() {
        // Guards the finding itself: if these ever passed with white, the
        // luminance branch below would be dead weight.
        for (bg in listOf(0xFF34C759, 0xFFFF9500, 0xFF30B0C7)) {
            assertTrue(contrastRatio(Color.White, Color(bg)) < 4.5)
        }
    }

    @Test
    fun `light badge backgrounds get a near-black foreground`() {
        // The three Diagnostics literals, each AA with the dark glyph
        // (8.86 / 8.95 / 7.65:1) and failing with white.
        for (bg in listOf(0xFF34C759, 0xFFFF9500, 0xFF30B0C7)) {
            assertEquals(
                Color(0xFF0B0B0C).toArgb(),
                foregroundForBackground(Color(bg)).toArgb(),
            )
            assertTrue(contrastRatio(foregroundForBackground(Color(bg)), Color(bg)) >= 4.5)
        }
    }

    @Test
    fun `dark fills keep a white foreground`() {
        // The accent fills that carry white text elsewhere: white stays
        // AA on them (5.98:1 on #005FCC, 5.57:1 on #0066CC).
        for (bg in listOf(0xFF005FCC, 0xFF0066CC, 0xFF000000)) {
            assertEquals(
                Color.White.toArgb(),
                foregroundForBackground(Color(bg)).toArgb(),
            )
            assertTrue(contrastRatio(foregroundForBackground(Color(bg)), Color(bg)) >= 4.5)
        }
    }

    @Test
    fun `the bright dark accent gets a dark foreground, not white`() {
        // #0A84FF is bright enough (luminance 0.238, above the 0.179
        // threshold) that white on it is only 3.65:1 — the palette
        // itself forbids white text on it. The dark glyph is 5.39:1.
        assertEquals(
            Color(0xFF0B0B0C).toArgb(),
            foregroundForBackground(Color(0xFF0A84FF)).toArgb(),
        )
        assertTrue(contrastRatio(Color.White, Color(0xFF0A84FF)) < 4.5)
        assertTrue(
            contrastRatio(foregroundForBackground(Color(0xFF0A84FF)), Color(0xFF0A84FF)) >= 4.5,
        )
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
