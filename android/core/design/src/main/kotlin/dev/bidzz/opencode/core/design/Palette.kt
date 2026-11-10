package dev.bidzz.opencode.core.design

import androidx.compose.ui.graphics.Color

/**
 * Colour palette based on cross-platform-llm-client (PrivateLM design system):
 * Plain OLED black / white pages, quiet neutral surfaces with hairline borders,
 * sentence-case section labels, and Apple System Blue accent.
 */
object Palette {

    // ---- Apple System Colors ----
    val systemBlue = Color(0xFF007AFF)
    val systemBlueDark = Color(0xFF0A84FF)
    val systemPurple = Color(0xFF5856D6)
    val systemPurpleDark = Color(0xFF5E5CE6)

    // ---- Dark: OLED black experience ----
    val darkBackground = Color(0xFF000000)
    val darkSurface = Color(0xFF141416)
    val darkSurfaceRaised = Color(0xFF1C1C1E)
    val darkSurfaceSunken = Color(0xFF0E0E10)
    val darkSurfaceVariant = Color(0xFF1E1E21)

    val darkOnSurface = Color(0xFFFFFFFF)
    val darkOnSurfaceMuted = Color(0xFF8E8E93)
    val darkOnSurfaceFaint = Color(0xFF5A5A5F)

    val darkOutline = Color(0x33FFFFFF)       // 20% white
    val darkHairline = Color(0x1FFFFFFF)      // 12% white (crisp hairline on OLED black)
    val darkFill = Color(0x14FFFFFF)          // 8% white
    val darkSurfacePressed = Color(0xFF242428)

    val darkAccent = systemBlueDark
    val darkOnAccent = Color(0xFFFFFFFF)

    val darkUserBubble = systemBlueDark
    val darkOnUserBubble = Color(0xFFFFFFFF)

    // ---- Light ----
    val lightBackground = Color(0xFFFFFFFF)
    val lightSurface = Color(0xFFF5F5F7)
    val lightSurfaceRaised = Color(0xFFE5E5EA)
    val lightSurfaceSunken = Color(0xFFEBEBED)
    val lightSurfaceVariant = Color(0xFFF0F0F2)

    val lightOnSurface = Color(0xFF0B0B0C)
    val lightOnSurfaceMuted = Color(0xFF6E6E73)
    val lightOnSurfaceFaint = Color(0xFFAEAEB2)

    val lightOutline = Color(0x26000000)      // 15% black
    val lightHairline = Color(0x1F000000)     // 12% black (subtle hairline separator)
    val lightFill = Color(0x0D000000)         // 5% black
    val lightSurfacePressed = Color(0xFFE5E5EA)

    val lightAccent = systemBlue
    val lightOnAccent = Color(0xFFFFFFFF)

    val lightUserBubble = systemBlue
    val lightOnUserBubble = Color(0xFFFFFFFF)

    // ---- Status (Apple System semantic) ----
    val success = Color(0xFF30D158)
    val successLight = Color(0xFF34C759)

    val warning = Color(0xFFFF9F0A)           // Soft warning (Apple Dark System Orange)
    val warningLight = Color(0xFFFF9500)      // Apple Light System Orange

    val danger = Color(0xFFFF453A)            // Soft danger (Apple Dark System Red)
    val dangerLight = Color(0xFFFF3B30)       // Apple Light System Red

    val neutral = Color(0xFF5AC8FA)
    val neutralLight = Color(0xFF007AFF)

    // ---- Syntax roles for code blocks ----
    val darkSyntaxKeyword = Color(0xFF5AC8FA)
    val darkSyntaxString = Color(0xFF30D158)
    val darkSyntaxNumber = Color(0xFFFF9F0A)
    val darkSyntaxComment = Color(0xFF8E8E93)

    val lightSyntaxKeyword = Color(0xFF007AFF)
    val lightSyntaxString = Color(0xFF34C759)
    val lightSyntaxNumber = Color(0xFFFF9500)
    val lightSyntaxComment = Color(0xFF8E8E93)
}
