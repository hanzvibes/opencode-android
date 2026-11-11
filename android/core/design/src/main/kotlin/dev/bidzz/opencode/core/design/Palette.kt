package dev.bidzz.opencode.core.design

import androidx.compose.ui.graphics.Color

/**
 * Colour palette based on cross-platform-llm-client (PrivateLM design system):
 * Plain OLED black / white pages, quiet neutral surfaces with hairline borders,
 * sentence-case section labels, and Apple System Blue accent.
 *
 * Phase 1 ("The quiet terminal") rules, enforced here:
 * - THREE OPAQUE SURFACES per theme, no alpha: background, raised, sunken.
 *   Dark: #000000 / #1C1C1E / #0E0E10. Light: #FFFFFF / #E5E5EA / #EBEBED.
 *   The old near-blacks #141416 and #1E1E21 (dark) and #F5F5F7 / #F0F0F2
 *   (light) were folded into the raised value — on OLED they were visually
 *   indistinguishable from it. `surface` and `surfaceVariant` are aliases
 *   of the raised value, kept so call sites need no rename.
 * - NO GLASS, NO HALOS: the glass/halo colour blocks were deleted with the
 *   Glassmorphism / ElevationHalos tokens (zero consumers). Depth comes
 *   from the three surfaces plus a hairline, never from translucency.
 * - ONE BLUE: accent is the only live/activity colour (see Theme.kt).
 * - USER BUBBLE IS NEUTRAL, not blue: it is the raised surface in both
 *   themes, with [OpenCodeColors.onUserBubble] = onSurface.
 * - LIGHT-THEME TEXT IS AA: bright semantic colours (#34C759, #FF9500,
 *   #FF3B30, #007AFF) are 2.2–4.0:1 on light backgrounds and must NEVER be
 *   used as text. Text uses the `*Text` variants below (all ≥ 4.5:1 on
 *   white, on raised #E5E5EA, and on sunken #EBEBED); bright values live
 *   on as dots/fills via the `*Bright` roles in [OpenCodeColors].
 */
object Palette {

    // ---- Apple System Colors ----
    val systemBlue = Color(0xFF007AFF)
    val systemBlueDark = Color(0xFF0A84FF)
    val systemPurple = Color(0xFF5856D6)
    val systemPurpleDark = Color(0xFF5E5CE6)

    // ---- Dark: OLED black experience ----
    // Three canonical opaque surfaces: background #000000, raised #1C1C1E,
    // sunken #0E0E10. surface / surfaceVariant alias the raised value.
    val darkBackground = Color(0xFF000000)
    val darkSurfaceRaised = Color(0xFF1C1C1E)
    val darkSurfaceSunken = Color(0xFF0E0E10)
    val darkSurface = darkSurfaceRaised
    val darkSurfaceVariant = darkSurfaceRaised

    val darkOnSurface = Color(0xFFFFFFFF)
    val darkOnSurfaceMuted = Color(0xFF8E8E93)
    // #9A9AA0 on OLED black is ~7:1, so even ≤13sp text in this token stays
    // readable. The previous #5A5A5F was ~3.0:1 — fine for a decorative dot,
    // too faint for the small labels and chevrons that actually use it.
    val darkOnSurfaceFaint = Color(0xFF9A9AA0)

    val darkOutline = Color(0x33FFFFFF)       // 20% white
    val darkHairline = Color(0x1FFFFFFF)      // 12% white (crisp hairline on OLED black)
    val darkFill = Color(0x14FFFFFF)          // 8% white
    // Pressed state, not a surface in the ladder: one step lighter than
    // raised so a press reads, then releases back to the flat surface.
    val darkSurfacePressed = Color(0xFF242428)

    val darkAccent = systemBlueDark
    val darkOnAccent = Color(0xFFFFFFFF)

    // NEUTRAL user bubble (Phase 1 §7): the raised surface, not blue.
    // The old deepened blues #0062D6 / #0066CC are gone on purpose — a blue
    // bubble fought the "quiet terminal" and duplicated the accent's job.
    // Text on it is plain onSurface (white on dark, near-black on light).
    val darkUserBubble = darkSurfaceRaised
    val darkOnUserBubble = darkOnSurface

    // ---- Light ----
    // Three canonical opaque surfaces: background #FFFFFF, raised #E5E5EA,
    // sunken #EBEBED. surface / surfaceVariant alias the raised value.
    // (The old light surface #F5F5F7 folded into raised #E5E5EA, mirroring
    // the dark collapse; one constant to tune if light cards feel heavy.)
    val lightBackground = Color(0xFFFFFFFF)
    val lightSurfaceRaised = Color(0xFFE5E5EA)
    val lightSurfaceSunken = Color(0xFFEBEBED)
    val lightSurface = lightSurfaceRaised
    val lightSurfaceVariant = lightSurfaceRaised

    val lightOnSurface = Color(0xFF0B0B0C)
    // Darkened in Phase 1: the old #6E6E73 was 5.07:1 on white but only
    // 4.04:1 on the (new) raised surface #E5E5EA, where muted text
    // actually sits (drawer, cards, chips). #5F5F66 is 6.33 / 5.05 / 5.32
    // on white / raised / sunken.
    val lightOnSurfaceMuted = Color(0xFF5F5F66)
    // Same story: old #6B6B70 was 5.30:1 on white but 4.22:1 on raised.
    // #64646C is 5.86 / 4.67 / 4.93 — still lighter than muted, still AA.
    val lightOnSurfaceFaint = Color(0xFF64646C)

    val lightOutline = Color(0x26000000)      // 15% black
    val lightHairline = Color(0x1F000000)     // 12% black (subtle hairline separator)
    val lightFill = Color(0x0D000000)         // 5% black
    val lightSurfacePressed = lightSurfaceRaised

    val lightAccent = systemBlue
    val lightOnAccent = Color(0xFFFFFFFF)

    // NEUTRAL user bubble (Phase 1 §7): the raised surface, not blue.
    val lightUserBubble = lightSurfaceRaised
    val lightOnUserBubble = lightOnSurface

    // ---- Status (Apple System semantic) ----
    // Base values are the DARK theme's: bright Apple colours, which pass AA
    // on black (success 10.39:1, warning 10.22:1, danger 6.16:1, accent
    // 5.76:1 on #000000) and are used as text AND dots/fills in dark.
    val success = Color(0xFF30D158)
    val successLight = Color(0xFF34C759)

    val warning = Color(0xFFFF9F0A)           // Soft warning (Apple Dark System Orange)
    val warningLight = Color(0xFFFF9500)      // Apple Light System Orange

    val danger = Color(0xFFFF453A)            // Soft danger (Apple Dark System Red)
    val dangerLight = Color(0xFFFF3B30)       // Apple Light System Red

    val neutral = Color(0xFF5AC8FA)
    val neutralLight = Color(0xFF007AFF)      // Vivid neutral, dots/fills only (3.20:1 on raised — never as text)

    // ---- Light-theme text-safe semantic colours (WCAG AA ≥ 4.5:1) ----
    // Ratios verified by computation on the three light backgrounds
    // (white / raised #E5E5EA / sunken #EBEBED). The brief's first guesses
    // (#1F7A33 = 4.30:1, #9A6200 = 4.06:1 on raised) FAILED on the raised
    // surface, so both were darkened one step in the same hue family.
    // Bright values above stay ONLY for dots/fills (see *Bright roles).
    // success text: 6.58 / 5.25 / 5.53
    val lightSuccessText = Color(0xFF1B6B2E)
    // warning text: 5.93 / 4.72 / 4.98
    val lightWarningText = Color(0xFF8A5A00)
    // danger text (Material error red): 6.54 / 5.21 / 5.49.
    // The old #FF3B30 was 3.55:1 on white — same failure as success/warning.
    val lightDangerText = Color(0xFFB3261E)
    // accent-as-text: 5.98 / 4.77 / 5.03. The accent itself (#007AFF,
    // 4.02:1 on white) stays for dots/fills/backgrounds only.
    val lightAccentText = Color(0xFF005FCC)

    // ---- Syntax roles for code blocks ----
    // SINGLE source of truth (Phase 1 §5). The slate/violet palette
    // MarkdownText.kt hardcoded locally (#C084FC / #86EFAC / #FDBA74 /
    // #94A3B8 dark, #7C3AED / #15803D / #C2410C / #64748B light) is
    // superseded by these roles; the next wave deletes the local copy and
    // reads colors.syntax* (plus syntaxFunction / syntaxType) instead.
    // Dark roles on sunken #0E0E10: keyword 10.17:1, string 9.54:1,
    // number 9.38:1, comment 5.91:1, function 11.57:1, type 7.09:1.
    val darkSyntaxKeyword = Color(0xFF5AC8FA)
    val darkSyntaxString = Color(0xFF30D158)
    val darkSyntaxNumber = Color(0xFFFF9F0A)
    val darkSyntaxComment = Color(0xFF8E8E93)
    // Function: accent lightened (sky-blue tint of the accent hue — no new
    // saturated colour). Type: systemPurple lightened to pass AA on sunken
    // (systemPurpleDark itself is only 3.81:1 there).
    val darkSyntaxFunction = Color(0xFF7DD3FC)
    val darkSyntaxType = Color(0xFFA78BFA)

    // Light roles on sunken #EBEBED (code-block background): keyword 5.03:1,
    // string 5.47:1, number 5.39:1, comment 5.66:1, function 4.75:1,
    // type 4.98:1. All ≥ 4.5:1 — the old light roles (#007AFF 3.37:1,
    // #34C759 1.86:1, #FF9500 1.85:1 on sunken) failed outright.
    // Keyword reuses accent-as-text; string/number are darkened versions of
    // MarkdownText's local greens/oranges; comment is a darkened slate.
    val lightSyntaxKeyword = lightAccentText
    val lightSyntaxString = Color(0xFF146C33)
    val lightSyntaxNumber = Color(0xFFA83A0B)
    val lightSyntaxComment = Color(0xFF4E5D6E)
    // Function: systemPurple (existing hue). Type: warm amber = warningText
    // hue — distinct from number's burnt orange, same warm family.
    val lightSyntaxFunction = systemPurple
    val lightSyntaxType = lightWarningText

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

    // Light tool TEXT roles are the AA text variants (running 5.03:1,
    // success 5.53:1, warning 4.98:1, error 5.49:1 on sunken). The *Bg
    // tints stay bright-based: they are fills, never text.
    val lightToolRunning = lightAccentText
    val lightToolRunningBg = Color(0x1F007AFF)
    val lightToolSuccess = lightSuccessText
    val lightToolSuccessBg = Color(0x1F34C759)
    val lightToolError = lightDangerText
    val lightToolErrorBg = Color(0x1FFF3B30)
    val lightToolWarning = lightWarningText
    val lightToolWarningBg = Color(0x1FFF9500)
    val lightToolPending = Color(0xFF5F5F66)
    val lightToolPendingBg = Color(0x145F5F66)

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

    // Light diff TEXT roles are the AA text variants: the old values failed
    // as text (#28A745 3.13:1, #B08800 3.30:1 on white; #D73A49 3.84:1 on
    // sunken). *Bg / *Gutter tints stay bright-based (fills, never text).
    val lightDiffAdded = lightSuccessText
    val lightDiffAddedBg = Color(0x1F28A745)
    val lightDiffAddedGutter = Color(0x3328A745)

    val lightDiffRemoved = lightDangerText
    val lightDiffRemovedBg = Color(0x1FD73A49)
    val lightDiffRemovedGutter = Color(0x33D73A49)

    val lightDiffModified = lightWarningText
    val lightDiffModifiedBg = Color(0x1FB08800)

    val lightDiffHunkHeader = lightAccentText
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

    // Qwen / Alibaba (Phase 1 §5): previously hardcoded ONLY in
    // ChatControls.resolveBrand as Color(0xFF8B5CF6) — now a Palette token
    // so providerColor() is the single source. Light variant darkened to
    // #7C3AED (5.70:1 on white, 4.54:1 on raised) for text use.
    val darkProviderQwen = Color(0xFF8B5CF6)
    val lightProviderQwen = Color(0xFF7C3AED)

    // NVIDIA / Nemotron (Phase 2): brand green #76B900. Light variant
    // darkened to #5C8A00 for text use, like the other providers.
    val darkProviderNvidia = Color(0xFF76B900)
    val lightProviderNvidia = Color(0xFF5C8A00)
}
