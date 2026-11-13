package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.AuraPalette
import dev.bidzz.opencode.core.design.OpenCodeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aura palette + two-layer shadow tokens (Opsi B).
 *
 * The exact hex values extracted from the Meta Muse app
 * (`com.facebook.aura` 16.0.0.2.159, `resources.arsc`), and the WCAG
 * contrast audit of every Aura text role on the surfaces it may sit on
 * (#FFFFFF card / #F6F7F8 Aura page). Pure JVM: [Color.toArgb] plus
 * local luminance math, no Android framework, no Robolectric — the same
 * pattern as [WhiteThemeTest].
 *
 * The guard is the point: Aura blue #5890FF and faint #9197A3 look like
 * text colours but fail AA outright, so the tests lock them to
 * dots/fills/decoration. [AuraPalette.auraBlueFill] and [AuraPalette.auraDanger]
 * pass on white and fail on the Aura page, so the tests lock the
 * white-only rule instead of letting a call site assume AA everywhere.
 */
class AuraThemeTest {

    // ---- Extracted hex (resources.arsc) ----

    @Test
    fun `aura blue scale is the extracted Facebook blue`() {
        assertEquals(0xFF5890FF.toInt(), AuraPalette.auraBlue.toArgb())
        assertEquals(0xFF4673CC.toInt(), AuraPalette.auraBlueFill.toArgb())
        assertEquals(0xFF355089.toInt(), AuraPalette.auraBlueText.toArgb())
        assertEquals(0xFF9BBCFF.toInt(), AuraPalette.auraBlueLight.toArgb())
        assertEquals(0x1A5890FF.toInt(), AuraPalette.auraBlueAlpha10.toArgb())
        assertEquals(0x335890FF.toInt(), AuraPalette.auraBlueAlpha20.toArgb())
        assertEquals(0x665890FF.toInt(), AuraPalette.auraBlueAlpha40.toArgb())
        assertEquals(0x995890FF.toInt(), AuraPalette.auraBlueAlpha60.toArgb())
        assertEquals(0xCC5890FF.toInt(), AuraPalette.auraBlueAlpha80.toArgb())
    }

    @Test
    fun `aura purple is the extracted M3 baseline`() {
        assertEquals(0xFF6750A4.toInt(), AuraPalette.auraPurple.toArgb())
        assertEquals(0xFFEADDFF.toInt(), AuraPalette.auraPurpleContainer.toArgb())
        assertEquals(0xFFD0BCFF.toInt(), AuraPalette.auraPurpleLight.toArgb())
    }

    @Test
    fun `aura neutrals and navy are the extracted values`() {
        assertEquals(0xFFF6F7F8.toInt(), AuraPalette.auraSurface.toArgb())
        assertEquals(0xFFE9EAED.toInt(), AuraPalette.auraBorder.toArgb())
        assertEquals(0xFF6A7180.toInt(), AuraPalette.auraMuted.toArgb())
        assertEquals(0xFF9197A3.toInt(), AuraPalette.auraFaint.toArgb())
        assertEquals(0xFF1C1B1F.toInt(), AuraPalette.auraOnSurface.toArgb())
        assertEquals(0xFF141823.toInt(), AuraPalette.auraNavy.toArgb())
        assertEquals(0xFF080A10.toInt(), AuraPalette.auraNavyDeep.toArgb())
        assertEquals(0xFF232937.toInt(), AuraPalette.auraSlate.toArgb())
        assertEquals(0xFF373E4D.toInt(), AuraPalette.auraSlateLight.toArgb())
        assertEquals(0xFF3A465C.toInt(), AuraPalette.auraSlateBorder.toArgb())
    }

    @Test
    fun `aura semantics are the extracted green and red`() {
        assertEquals(0xFF1D873B.toInt(), AuraPalette.auraSuccess.toArgb())
        assertEquals(0xFF14632B.toInt(), AuraPalette.auraSuccessText.toArgb())
        assertEquals(0xFFD93025.toInt(), AuraPalette.auraDanger.toArgb())
        // The text variant is a darkening of the brand green, not an alias:
        // aliasing it back to #1D873B would silently drop AA on #F6F7F8.
        assertTrue(AuraPalette.auraSuccessText.toArgb() != AuraPalette.auraSuccess.toArgb())
    }

    // ---- WCAG AA text roles (4.5:1) ----

    @Test
    fun `aura blue text muted and purple are AA on white and on the aura page`() {
        val white = Color(0xFFFFFFFF)
        val page = AuraPalette.auraSurface
        assertTrue(contrastRatio(AuraPalette.auraBlueText, white) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraBlueText, page) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraMuted, white) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraMuted, page) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraPurple, white) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraSuccessText, white) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraSuccessText, page) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraOnSurface, page) >= 4.5)
    }

    @Test
    fun `aura blue fill and danger are AA on white only, not on the aura page`() {
        val white = Color(0xFFFFFFFF)
        val page = AuraPalette.auraSurface
        // White text on the fill, and the colour as text on white: both AA.
        assertTrue(contrastRatio(white, AuraPalette.auraBlueFill) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraDanger, white) >= 4.5)
        // …but both fall under AA on the Aura page, so a call site must
        // not use them as text there (use auraBlueText / lightDangerText).
        assertTrue(contrastRatio(AuraPalette.auraBlueFill, page) < 4.5)
        assertTrue(contrastRatio(AuraPalette.auraDanger, page) < 4.5)
    }

    @Test
    fun `aura blue and faint are never text`() {
        val white = Color(0xFFFFFFFF)
        assertTrue(contrastRatio(AuraPalette.auraBlue, white) < 4.5)
        assertTrue(contrastRatio(AuraPalette.auraFaint, white) < 4.5)
    }

    @Test
    fun `aura purple container pair and navy pair are AA`() {
        assertTrue(
            contrastRatio(AuraPalette.auraPurple, AuraPalette.auraPurpleContainer) >= 4.5,
        )
        assertTrue(contrastRatio(Color.White, AuraPalette.auraNavy) >= 4.5)
        assertTrue(contrastRatio(AuraPalette.auraBlueLight, AuraPalette.auraNavy) >= 4.5)
    }

    // ---- Two-layer shadow tokens ----

    @Test
    fun `aura card shadow is two layers, ambient 2dp 6pct plus key 8dp 5pct`() {
        val card = OpenCodeTokens().auraShadow.card
        assertEquals(2.dp, card.ambient.elevation)
        assertEquals(0x0F000000.toInt(), card.ambient.ambientColor.toArgb())
        assertEquals(0x0F000000.toInt(), card.ambient.spotColor.toArgb())
        assertEquals(8.dp, card.key.elevation)
        assertEquals(0x0D000000.toInt(), card.key.ambientColor.toArgb())
        assertEquals(0x0D000000.toInt(), card.key.spotColor.toArgb())
    }

    @Test
    fun `aura raised shadow is one step above the card`() {
        val raised = OpenCodeTokens().auraShadow.raised
        assertEquals(4.dp, raised.ambient.elevation)
        assertEquals(16.dp, raised.key.elevation)
        assertTrue(raised.key.elevation > OpenCodeTokens().auraShadow.card.key.elevation)
    }

    // ---- WCAG relative luminance, straight from the spec ----

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
