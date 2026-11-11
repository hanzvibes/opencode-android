package dev.bidzz.opencode.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTokens
import dev.bidzz.opencode.core.design.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * White B light theme: the exact palette values, the shadow tokens, and
 * the WCAG contrast audit of every light text token on the five light
 * backgrounds (#FFFFFF card / #F9F9FA page / #F7F7F8 sunken /
 * #F5F5F7 chips / #EFEFF1 assistant bubble). Pure JVM: [Color.toArgb]
 * plus local luminance math, no Android framework, no Robolectric.
 *
 * The dark palette is asserted untouched alongside — this file is the
 * guard against a light-theme edit leaking into the OLED theme.
 */
class WhiteThemeTest {

    // ---- Light palette (White B) ----

    @Test
    fun `light background is F9F9FA and cards are pure white`() {
        assertEquals(0xFFF9F9FA.toInt(), Palette.lightBackground.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), Palette.lightSurface.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), Palette.lightSurfaceRaised.toArgb())
    }

    @Test
    fun `light sunken is F7F7F8 for code blocks and variant is F5F5F7 for chips only`() {
        assertEquals(0xFFF7F7F8.toInt(), Palette.lightSurfaceSunken.toArgb())
        assertEquals(0xFFF5F5F7.toInt(), Palette.lightSurfaceVariant.toArgb())
        // Variant is its own neutral now, NOT an alias of the raised surface:
        // a chip filled with raised (#FFFFFF) would be invisible on a white card.
        assertTrue(Palette.lightSurfaceVariant.toArgb() != Palette.lightSurfaceRaised.toArgb())
    }

    @Test
    fun `light hairline and outline are opaque greys and card border is EBEBEB`() {
        assertEquals(0xFFE5E5EA.toInt(), Palette.lightHairline.toArgb())
        assertEquals(0xFFE0E0E0.toInt(), Palette.lightOutline.toArgb())
        assertEquals(0xFFEBEBEB.toInt(), Palette.lightCardBorder.toArgb())
    }

    @Test
    fun `light accent stays system blue and the user bubble stays neutral`() {
        assertEquals(0xFF007AFF.toInt(), Palette.lightAccent.toArgb())
        // The bubble was never blue: Phase 1 made it the raised surface on
        // purpose, and White B keeps that — it is NOT reverted to #0066CC.
        assertEquals(
            Palette.lightSurfaceRaised.toArgb(),
            Palette.lightUserBubble.toArgb(),
        )
        assertEquals(Palette.lightOnSurface.toArgb(), Palette.lightOnUserBubble.toArgb())
    }

    @Test
    fun `assistant bubble is EFEFF1 in light and raised in dark`() {
        // Redesign C: the assistant turn sits in a grey bubble, one step
        // below the chips neutral so it reads as a bubble on the page and
        // stays distinct from the white user bubble. Dark is the raised
        // surface — the same neutral the user bubble uses there.
        assertEquals(0xFFEFEFF1.toInt(), Palette.lightAssistantBubble.toArgb())
        assertEquals(Palette.lightOnSurface.toArgb(), Palette.lightOnAssistantBubble.toArgb())
        assertEquals(Palette.darkSurfaceRaised.toArgb(), Palette.darkAssistantBubble.toArgb())
        assertEquals(Palette.darkOnSurface.toArgb(), Palette.darkOnAssistantBubble.toArgb())
    }

    @Test
    fun `assistant bubble reads as a bubble on the page and against the user bubble`() {
        // Distinct from the page (#F9F9FA) so the bubble has an edge, and
        // from the white user bubble so the two sides never merge. Both
        // differences are small (grey on near-white), which is why the
        // bubble also carries a hairline border and the avatar marks it.
        assertTrue(Palette.lightAssistantBubble.toArgb() != Palette.lightBackground.toArgb())
        assertTrue(Palette.lightAssistantBubble.toArgb() != Palette.lightUserBubble.toArgb())
        assertTrue(contrastRatio(Palette.lightAssistantBubble, Palette.lightBackground) > 1.0)
        assertTrue(contrastRatio(Palette.lightUserBubble, Palette.lightAssistantBubble) > 1.0)
    }

    // ---- Dark palette untouched ----

    @Test
    fun `dark palette is untouched OLED black`() {
        assertEquals(0xFF000000.toInt(), Palette.darkBackground.toArgb())
        assertEquals(0xFF1C1C1E.toInt(), Palette.darkSurfaceRaised.toArgb())
        assertEquals(0xFF0E0E10.toInt(), Palette.darkSurfaceSunken.toArgb())
        assertEquals(0xFF1C1C1E.toInt(), Palette.darkSurface.toArgb())
        assertEquals(0xFF1C1C1E.toInt(), Palette.darkSurfaceVariant.toArgb())
        assertEquals(0xFF0A84FF.toInt(), Palette.darkAccent.toArgb())
    }

    // ---- Shadow tokens ----

    @Test
    fun `card shadow matches the White B spec`() {
        val shadow = OpenCodeTokens().shadow
        // 0 8dp 24dp rgba(0,0,0,0.05): the dominant CSS layer sets the elevation.
        assertEquals(8.dp, shadow.card.elevation)
        // 0 1dp 2dp rgba(0,0,0,0.06) is the ambient colour; 5% black the spot.
        assertEquals(0x0F000000.toInt(), shadow.card.ambientColor.toArgb())
        assertEquals(0x0D000000.toInt(), shadow.card.spotColor.toArgb())
        assertEquals(1.dp, shadow.cardBorderWidth)
    }

    @Test
    fun `raised shadow is one step above the card shadow`() {
        val shadow = OpenCodeTokens().shadow
        assertEquals(16.dp, shadow.raised.elevation)
        assertTrue(shadow.raised.elevation > shadow.card.elevation)
        assertEquals(0x0D000000.toInt(), shadow.raised.spotColor.toArgb())
    }

    @Test
    fun `white card radius is the 16dp surface radius`() {
        assertEquals(16.dp, OpenCodeTokens().radius.lg)
    }

    // ---- Contrast audit (WCAG AA, small text needs >= 4.5:1) ----

    private val lightBackgrounds = listOf(
        "card #FFFFFF" to Color(0xFFFFFFFF),
        "page #F9F9FA" to Color(0xFFF9F9FA),
        "sunken #F7F7F8" to Color(0xFFF7F7F8),
        "variant #F5F5F7" to Color(0xFFF5F5F7),
        // Redesign C: the assistant bubble grey. Every light text token
        // must stay AA here too — worst are faint (5.11:1) and accentText
        // (5.21:1) on #EFEFF1, so the audit below covers it as a fifth
        // background instead of trusting the grey by eye.
        "assistant bubble #EFEFF1" to Palette.lightAssistantBubble,
    )

    private val lightTextTokens = listOf(
        "onSurface" to Palette.lightOnSurface,
        "onSurfaceMuted" to Palette.lightOnSurfaceMuted,
        "onSurfaceFaint" to Palette.lightOnSurfaceFaint,
        "successText" to Palette.lightSuccessText,
        "warningText" to Palette.lightWarningText,
        "dangerText" to Palette.lightDangerText,
        "accentText" to Palette.lightAccentText,
        "syntaxKeyword" to Palette.lightSyntaxKeyword,
        "syntaxString" to Palette.lightSyntaxString,
        "syntaxNumber" to Palette.lightSyntaxNumber,
        "syntaxComment" to Palette.lightSyntaxComment,
        "syntaxFunction" to Palette.lightSyntaxFunction,
        "syntaxType" to Palette.lightSyntaxType,
        "toolPending" to Palette.lightToolPending,
    )

    @Test
    fun `every light text token passes AA on every new light background`() {
        for ((textName, text) in lightTextTokens) {
            for ((bgName, bg) in lightBackgrounds) {
                val ratio = contrastRatio(text, bg)
                assertTrue(
                    "$textName on $bgName is $ratio, below AA 4.5:1",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `muted and faint text improved over the old grey raised surface`() {
        // White B lightened every background, so the same tokens must read
        // better on a white card than they did on the old raised #E5E5EA.
        val oldRaised = Color(0xFFE5E5EA)
        val white = Color(0xFFFFFFFF)
        assertTrue(
            contrastRatio(Palette.lightOnSurfaceMuted, white) >
                contrastRatio(Palette.lightOnSurfaceMuted, oldRaised),
        )
        assertTrue(
            contrastRatio(Palette.lightOnSurfaceFaint, white) >
                contrastRatio(Palette.lightOnSurfaceFaint, oldRaised),
        )
    }

    @Test
    fun `bright accent is still dots-and-fills only, never text`() {
        // #007AFF is 4.02:1 on white — below AA — so it must never be used
        // as text; the text-safe variant is what the audit above covers.
        assertTrue(contrastRatio(Palette.lightAccent, Color(0xFFFFFFFF)) < 4.5)
        assertTrue(
            contrastRatio(Palette.lightAccentText, Color(0xFFFFFFFF)) >= 4.5,
        )
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
