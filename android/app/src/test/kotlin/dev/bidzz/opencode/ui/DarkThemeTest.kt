package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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

    @Test
    fun `badge and pill text tokens pass AA on their dark tints`() {
        // Fase 6j (47-F3/47-F4): the badge label sits on a 14% tint of
        // itself over the assistant bubble, and the status pills sit on
        // a 12% tint of the status colour over the same bubble. The plain
        // tokens fail there (badge 3.95:1, running pill 4.09:1, failed
        // pill 4.40:1); the text variants below must stay AA.
        val bubble = Palette.darkAssistantBubble
        val badgeTint = Palette.darkBadgeAccentText.copy(alpha = 0.14f)
            .compositeOver(bubble)
        assertTrue(
            "dark badgeAccentText on its tint is " +
                "${contrastRatio(Palette.darkBadgeAccentText, badgeTint)}, below AA 4.5:1",
            contrastRatio(Palette.darkBadgeAccentText, badgeTint) >= 4.5,
        )
        val runningTint = Palette.darkAccent.copy(alpha = 0.12f)
            .compositeOver(bubble)
        assertTrue(
            "dark toolRunningText on the running pill tint is " +
                "${contrastRatio(Palette.darkToolRunningText, runningTint)}, below AA 4.5:1",
            contrastRatio(Palette.darkToolRunningText, runningTint) >= 4.5,
        )
        val failedTint = Palette.danger.copy(alpha = 0.12f)
            .compositeOver(bubble)
        assertTrue(
            "dark toolErrorText on the failed pill tint is " +
                "${contrastRatio(Palette.darkToolErrorText, failedTint)}, below AA 4.5:1",
            contrastRatio(Palette.darkToolErrorText, failedTint) >= 4.5,
        )
    }

    @Test
    fun `warning badge text passes AA on its dark tint`() {
        // Audit-200 D5 (m-855): the WRITE/EDIT badge label is 9sp bold mono
        // on a 14% tint of itself over the assistant bubble. The plain
        // warning already passed in dark (6.39:1), but the badge needs its
        // own text token like the blue kinds have.
        val bubble = Palette.darkAssistantBubble
        val badgeTint = Palette.darkBadgeWarningText.copy(alpha = 0.14f)
            .compositeOver(bubble)
        assertTrue(
            "dark badgeWarningText on its tint is " +
                "${contrastRatio(Palette.darkBadgeWarningText, badgeTint)}, below AA 4.5:1",
            contrastRatio(Palette.darkBadgeWarningText, badgeTint) >= 4.5,
        )
    }

    @Test
    fun `the diff deletions chip passes AA on the screen background, not on raised`() {
        // Audit-200 D5 (m-927): the audit assumed the chip sat on raised
        // #1C1C1E (4.27:1, failing) — it actually sits directly on the
        // screen background, where it passes. Pin the real background, so
        // a future move onto a card is caught by the companion assertion.
        val background = Color(0xFF000000)
        val tint = Palette.danger.copy(alpha = 0.14f).compositeOver(background)
        assertTrue(
            "dark danger chip text on its tint over the background is " +
                "${contrastRatio(Palette.danger, tint)}, below AA 4.5:1",
            contrastRatio(Palette.danger, tint) >= 4.5,
        )
        val raised = Color(0xFF1C1C1E)
        val raisedTint = Palette.danger.copy(alpha = 0.14f).compositeOver(raised)
        assertTrue(
            "the same chip over raised must stay below AA (that is why the background matters)",
            contrastRatio(Palette.danger, raisedTint) < 4.5,
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
