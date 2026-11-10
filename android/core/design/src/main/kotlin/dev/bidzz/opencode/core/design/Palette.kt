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
    // #9A9AA0 on OLED black is ~7:1, so even ≤13sp text in this token stays
    // readable. The previous #5A5A5F was ~3.0:1 — fine for a decorative dot,
    // too faint for the small labels and chevrons that actually use it.
    val darkOnSurfaceFaint = Color(0xFF9A9AA0)

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

    // ---- Glassmorphism blur tints ----
    val darkGlassSurfaceSubtle = Color(0x66141416)
    val darkGlassSurface = Color(0x99141416)
    val darkGlassSurfaceThick = Color(0xCC1C1C1E)
    val darkGlassBorder = Color(0x26FFFFFF)
    val darkGlassHighlight = Color(0x33FFFFFF)

    val lightGlassSurfaceSubtle = Color(0x80FFFFFF)
    val lightGlassSurface = Color(0xB3FFFFFF)
    val lightGlassSurfaceThick = Color(0xCCF5F5F7)
    val lightGlassBorder = Color(0x1F000000)
    val lightGlassHighlight = Color(0x66FFFFFF)

    // ---- Subtle Elevation Halos & Glows ----
    val darkHaloAmbient = Color(0x40000000)
    val darkHaloAccent = Color(0x2E0A84FF)
    val darkHaloSuccess = Color(0x2630D158)
    val darkHaloWarning = Color(0x26FF9F0A)
    val darkHaloDanger = Color(0x26FF453A)

    val lightHaloAmbient = Color(0x14000000)
    val lightHaloAccent = Color(0x24007AFF)
    val lightHaloSuccess = Color(0x1F34C759)
    val lightHaloWarning = Color(0x1FFF9500)
    val lightHaloDanger = Color(0x1FFF3B30)

    // ---- Tool Statuses ----
    val darkToolRunning = systemBlueDark
    val darkToolRunningBg = Color(0x240A84FF)
    val darkToolSuccess = success
    val darkToolSuccessBg = Color(0x2430D158)
    val darkToolError = danger
    val darkToolErrorBg = Color(0x24FF453A)
    val darkToolWarning = warning
    val darkToolWarningBg = Color(0x24FF9F0A)
    val darkToolPending = Color(0xFF8E8E93)
    val darkToolPendingBg = Color(0x1F8E8E93)

    val lightToolRunning = systemBlue
    val lightToolRunningBg = Color(0x1F007AFF)
    val lightToolSuccess = successLight
    val lightToolSuccessBg = Color(0x1F34C759)
    val lightToolError = dangerLight
    val lightToolErrorBg = Color(0x1FFF3B30)
    val lightToolWarning = warningLight
    val lightToolWarningBg = Color(0x1FFF9500)
    val lightToolPending = Color(0xFF6E6E73)
    val lightToolPendingBg = Color(0x146E6E73)

    // ---- Unified Diff semantic colors (+/-) ----
    val darkDiffAdded = Color(0xFF30D158)
    val darkDiffAddedBg = Color(0x2430D158)
    val darkDiffAddedGutter = Color(0x3D30D158)

    val darkDiffRemoved = Color(0xFFFF453A)
    val darkDiffRemovedBg = Color(0x24FF453A)
    val darkDiffRemovedGutter = Color(0x3DFF453A)

    val darkDiffModified = Color(0xFFFF9F0A)
    val darkDiffModifiedBg = Color(0x24FF9F0A)

    val darkDiffHunkHeader = Color(0xFF5AC8FA)
    val darkDiffHunkHeaderBg = Color(0x1F5AC8FA)

    val lightDiffAdded = Color(0xFF28A745)
    val lightDiffAddedBg = Color(0x1F28A745)
    val lightDiffAddedGutter = Color(0x3328A745)

    val lightDiffRemoved = Color(0xFFD73A49)
    val lightDiffRemovedBg = Color(0x1FD73A49)
    val lightDiffRemovedGutter = Color(0x33D73A49)

    val lightDiffModified = Color(0xFFB08800)
    val lightDiffModifiedBg = Color(0x1FB08800)

    val lightDiffHunkHeader = Color(0xFF007AFF)
    val lightDiffHunkHeaderBg = Color(0x14007AFF)

    // ---- Model Providers ----
    val darkProviderAnthropic = Color(0xFFD97757)
    val lightProviderAnthropic = Color(0xFFC95738)

    val darkProviderOpenAI = Color(0xFF10A37F)
    val lightProviderOpenAI = Color(0xFF0D8C6D)

    val darkProviderGoogle = Color(0xFF4285F4)
    val lightProviderGoogle = Color(0xFF1A73E8)

    val darkProviderDeepSeek = Color(0xFF4D88FF)
    val lightProviderDeepSeek = Color(0xFF1E60D5)

    val darkProviderMistral = Color(0xFFFF7000)
    val lightProviderMistral = Color(0xFFE65100)

    val darkProviderOllama = Color(0xFFF0F0F0)
    val lightProviderOllama = Color(0xFF24292F)

    val darkProviderMeta = Color(0xFF0081FB)
    val lightProviderMeta = Color(0xFF0668E1)

    val darkProviderGroq = Color(0xFFF55036)
    val lightProviderGroq = Color(0xFFD63B22)

    val darkProviderCohere = Color(0xFF388E6D)
    val lightProviderCohere = Color(0xFF1E3A34)
}
