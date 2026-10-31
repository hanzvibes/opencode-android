package dev.bidzz.opencode.core.design

import androidx.compose.ui.graphics.Color

/**
 * Colour palette.
 *
 * Dark and light are designed separately, not inverted. `plan.md` §12 requires
 * this: inverting produces surfaces that sit at the wrong elevation and text that
 * passes contrast in one theme but not the other.
 *
 * The palette is restrained on purpose. One accent carries all interactive meaning;
 * everything else is a neutral ramp. Status colours are muted — a developer tool
 * should not look like a dashboard with traffic lights.
 */
object Palette {

    // ---- Dark: the default. A coding tool is used in low light. ----

    /** App background, furthest back. Not pure black: pure black makes borders invisible. */
    val darkBackground = Color(0xFF0B0D10)

    /** Default surface: message area, sheets. */
    val darkSurface = Color(0xFF12151A)

    /** Raised surface: top bar, composer, cards. One step above [darkSurface]. */
    val darkSurfaceRaised = Color(0xFF181C22)

    /** Inset surface: code blocks, inline code. Recedes rather than raises. */
    val darkSurfaceSunken = Color(0xFF0E1116)

    /** Primary text. Off-white, not #FFFFFF, to avoid halation on dark backgrounds. */
    val darkOnSurface = Color(0xFFE6E9EF)

    /** Secondary text: timestamps, hints, metadata. */
    val darkOnSurfaceMuted = Color(0xFF9AA3B0)

    /** Disabled text and icons. */
    val darkOnSurfaceFaint = Color(0xFF5D6672)

    /** Hairline separators. Low contrast by design; borders should whisper. */
    val darkOutline = Color(0xFF232830)

    /** Accent: the only saturated colour in the UI. */
    val darkAccent = Color(0xFF4C8DFF)

    /** Text/icon on top of [darkAccent]. */
    val darkOnAccent = Color(0xFF06121F)

    /** User message bubble fill. A tint of the accent, dark enough for body text. */
    val darkUserBubble = Color(0xFF1B2735)

    // ---- Light ----

    val lightBackground = Color(0xFFF7F8FA)
    val lightSurface = Color(0xFFFFFFFF)
    val lightSurfaceRaised = Color(0xFFFFFFFF)
    val lightSurfaceSunken = Color(0xFFF1F3F6)

    val lightOnSurface = Color(0xFF11151A)
    val lightOnSurfaceMuted = Color(0xFF5C6672)
    val lightOnSurfaceFaint = Color(0xFF9BA4B0)

    val lightOutline = Color(0xFFE2E6EB)

    val lightAccent = Color(0xFF1F6FEB)
    val lightOnAccent = Color(0xFFFFFFFF)

    val lightUserBubble = Color(0xFFE8F0FE)

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
}
