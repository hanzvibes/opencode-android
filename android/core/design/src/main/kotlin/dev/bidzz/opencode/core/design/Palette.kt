package dev.bidzz.opencode.core.design

import androidx.compose.ui.graphics.Color

/**
 * Colour palette: the single place a raw colour value is allowed to appear.
 *
 * The brand is three colours sampled from the logo: Graphite, Ivory, and Electric Lime.
 * Everything else is a neutral ramp built from them. Screens read semantic roles from
 * `OpenCodeTheme.colors`, never from here.
 *
 * Dark is the primary brand experience. Light is designed separately, not inverted: it
 * puts Ivory under white cards, and swaps the lime for a deeper green wherever the accent
 * is used as text or an icon, because lime on a light surface is unreadable.
 *
 * Electric Lime is used selectively (primary actions, selection, the active step) and never
 * as a large fill or a glow. Status colours are muted: a developer tool should not look like
 * a dashboard with traffic lights.
 */
object Palette {

    // ---- Brand ----

    val graphite = Color(0xFF08090D)
    val ivory = Color(0xFFF6F2EE)
    val electricLime = Color(0xFFB3F834)

    // ---- Dark: the primary experience. ----

    /** App background, furthest back. The logo's own field. */
    val darkBackground = graphite

    /** Default surface: cards, top bar. */
    val darkSurface = Color(0xFF111318)

    /** Elevated surface: sheets, menus, the composer. One step above [darkSurface]. */
    val darkSurfaceRaised = Color(0xFF1B1E24)

    /** Inset surface: code blocks, fields. Sits between the background and [darkSurface]. */
    val darkSurfaceSunken = Color(0xFF0D0F13)

    /** Chips and the user bubble: a neutral step above [darkSurfaceRaised]. */
    val darkSurfaceVariant = Color(0xFF252932)

    /** Primary text. */
    val darkOnSurface = ivory

    /** Secondary text. */
    val darkOnSurfaceMuted = Color(0xFFB8B8B5)

    /** Placeholders and small labels. Nudged from the brand's #777D86 to clear 4.5:1 (WCAG AA) on the surface. */
    val darkOnSurfaceFaint = Color(0xFF7A808A)

    /** Hairline separators. */
    val darkOutline = Color(0xFF30343D)

    /** The accent: the only saturated colour in the UI. */
    val darkAccent = electricLime

    /** Text and icons on top of [darkAccent]. */
    val darkOnAccent = graphite

    /** User message bubble: neutral, so lime stays reserved for actions. */
    val darkUserBubble = darkSurfaceVariant
    val darkOnUserBubble = ivory

    // ---- Light ----

    val lightBackground = ivory
    val lightSurface = Color(0xFFFFFFFF)
    val lightSurfaceRaised = Color(0xFFFFFFFF)
    val lightSurfaceSunken = Color(0xFFECE8E2)
    val lightSurfaceVariant = Color(0xFFE6E2DB)

    val lightOnSurface = graphite
    val lightOnSurfaceMuted = Color(0xFF55585E)
    /** 4.6:1 on Ivory, 5.4:1 on white. */
    val lightOnSurfaceFaint = Color(0xFF6A6E75)

    val lightOutline = Color(0xFFDAD5CD)

    /** Electric Lime darkened until it reads as text and icons on Ivory and white (5:1 on white). */
    val lightAccent = Color(0xFF4F7A00)
    val lightOnAccent = Color(0xFFFFFFFF)

    val lightUserBubble = lightSurfaceVariant
    val lightOnUserBubble = graphite

    // ---- Status. Muted, same hue logic in both themes. ----

    /** Engine healthy. */
    val success = Color(0xFF3FB950)
    val successLight = Color(0xFF1A7F37)

    /** Starting, reconnecting, working. */
    val warning = Color(0xFFD29922)
    val warningLight = Color(0xFF9A6700)

    /** Failed, unreachable. */
    val danger = Color(0xFFF85149)
    val dangerLight = Color(0xFFCF222E)

    /** Neutral status: stopped. */
    val neutral = Color(0xFF6E7681)

    // ---- Syntax roles for code blocks. ----
    //
    // Kept separate from the status colours on purpose: a green `success` dot and green
    // string literal would read as one signal. These follow the token colours of the
    // editors this audience already uses, so keyword/string/number never collide.

    val darkSyntaxKeyword = Color(0xFF569CD6)
    val darkSyntaxString = Color(0xFFCE9178)
    val darkSyntaxNumber = Color(0xFFB5CEA8)
    val darkSyntaxComment = Color(0xFF6A9955)

    val lightSyntaxKeyword = Color(0xFF0000FF)
    val lightSyntaxString = Color(0xFFA31515)
    val lightSyntaxNumber = Color(0xFF098658)
    val lightSyntaxComment = Color(0xFF008000)
}
