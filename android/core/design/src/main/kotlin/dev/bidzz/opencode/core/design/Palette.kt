package dev.bidzz.opencode.core.design

import androidx.compose.ui.graphics.Color

/**
 * Colour palette based on cross-platform-llm-client (PrivateLM design system):
 * Plain OLED black / white pages, quiet neutral surfaces with hairline borders,
 * sentence-case section labels, and Apple System Blue accent.
 *
 * Phase 1 ("The quiet terminal") rules, enforced here:
 * - OPAQUE SURFACES, no alpha: dark has four measured steps — background
 *   #000000, sunken #0E0E10, raised #1C1C1E, variant #26262A (each step
 *   1.09-1.13:1); light (White B) has #F9F9FA / #FFFFFF / #F7F7F8
 *   (code blocks) / variant #F5F5F7 (chips only). `surface` aliases the
 *   raised value in both themes. Never `surface*.copy(alpha = …)`: on
 *   dark an alpha surface renders darker than its container and the
 *   ladder collapses — tints use [dev.bidzz.opencode.core.design.AlphaTokens].
 *   The old near-blacks #141416 and #1E1E21 (dark) and #F5F5F7 / #F0F0F2
 *   (light) were folded into the raised value in Phase 1 — on OLED they were
 *   visually indistinguishable from it. White B un-folds the light side:
 *   the page is #F9F9FA, cards are pure #FFFFFF, so the ladder reads again.
 * - NO GLASS, NO HALOS: the glass/halo colour blocks were deleted with the
 *   Glassmorphism / ElevationHalos tokens (zero consumers). Depth comes
 *   from the surfaces plus a hairline in dark; in light, from the
 *   white-card shadow tokens (`ShadowTokens` in Tokens.kt) plus a 1dp
 *   #EBEBEB border — never from translucency.
 * - ONE BLUE: accent is the only live/activity colour (see Theme.kt).
 * - USER BUBBLE IS NEUTRAL, not blue. In light it is the raised surface
 *   (a white bubble on the #F9F9FA page — distinguished by position and
 *   shape only); in dark it is one step above the assistant bubble
 *   (#2C2C30 vs #1C1C1E), so the sender reads without relying on the
 *   avatar alone. [OpenCodeColors.onUserBubble] = onSurface in both.
 * - LIGHT-THEME TEXT IS AA: bright semantic colours (#34C759, #FF9500,
 *   #FF3B30, #007AFF) are 2.2–4.0:1 on light backgrounds and must NEVER be
 *   used as text. Text uses the `*Text` variants below (all ≥ 4.5:1 on
 *   white, on background #F9F9FA, on sunken #F7F7F8, and on variant
 *   #F5F5F7 — White B lightened every light background, so every ratio
 *   improved over Phase 1); bright values live on as dots/fills via the
 *   `*Bright` roles in [OpenCodeColors].
 */
object Palette {

    // ---- Apple System Colors ----
    val systemBlue = Color(0xFF007AFF)
    val systemBlueDark = Color(0xFF0A84FF)
    val systemPurple = Color(0xFF5856D6)
    val systemPurpleDark = Color(0xFF5E5CE6)

    // ---- Dark: OLED black experience ----
    // FOUR opaque surfaces, one step apart (measured): background #000000,
    // sunken #0E0E10 (1.09:1), raised #1C1C1E (1.13:1), variant #26262A
    // (1.13:1). `surface` aliases the raised value; `surfaceVariant` is
    // its own step since Opsi B — when it aliased raised, chips and inline
    // code were invisible on dark cards. Light keeps its White B ladder
    // (background #F9F9FA, raised #FFFFFF, sunken #F7F7F8, variant
    // #F5F5F7): its steps are 1.02-1.05:1 by design and surfaces there are
    // told apart by the 1dp border + shadow, not by colour.
    val darkBackground = Color(0xFF000000)
    val darkSurfaceRaised = Color(0xFF1C1C1E)
    val darkSurfaceSunken = Color(0xFF0E0E10)
    val darkSurface = darkSurfaceRaised
    val darkSurfaceVariant = Color(0xFF26262A)

    val darkOnSurface = Color(0xFFFFFFFF)
    // #AEAEB2, not the old #8E8E93: muted must be BRIGHTER than faint
    // (the old pair was inverted — faint #9A9AA0 was lighter than muted).
    // #AEAEB2 is 9.50 / 7.69 / 8.72 on black / raised / sunken, so it
    // stays AA everywhere faint is used too.
    val darkOnSurfaceMuted = Color(0xFFAEAEB2)
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
    // Accent FILL for buttons/badges/chips that carry onAccent (white)
    // text or icons. White on #0066CC is 5.57:1 (AA); white on the bright
    // #0A84FF is only 3.65:1, so dark fills never use darkAccent itself.
    val darkAccentFill = Color(0xFF0066CC)

    // ---- Text variants for tinted badges/pills (Fase 6j, 47-F3/47-F4) ----
    // The plain status blues/red fail AA as small text on their own tint
    // over the assistant bubble (#0A84FF: badge 3.95:1, running pill
    // 4.09:1; #FF453A failed pill 4.40:1). The lightened variants below
    // are the text-safe versions for the dark theme; the light theme
    // keeps its existing AA text tokens for the pills.
    // Badge kind-label text: 6.77:1 on the badge tint #25343D (14%
    // #5AC8FA over #1C1C1E). Same value as darkSyntaxKeyword — kept
    // explicit so a syntax change cannot silently break badge contrast.
    val darkBadgeAccentText = Color(0xFF5AC8FA)
    // Running pill text: 7.88:1 on the pill tint #1A2839 (12% #0A84FF
    // over #1C1C1E).
    val darkToolRunningText = Color(0xFF5AC8FA)
    // Failed pill text: 6.56:1 on the pill tint #372121 (12% #FF453A
    // over #1C1C1E).
    val darkToolErrorText = Color(0xFFFF8A80)

    // NEUTRAL user bubble (Phase 1 §7): not blue. Since Opsi B it is one
    // step ABOVE the assistant bubble (#2C2C30 vs #1C1C1E, 1.22:1) — when
    // both were the raised surface, only position/avatar/tail told the
    // sender apart in dark. Light keeps the raised surface for both
    // bubbles; there the assistant bubble is the grey #EFEFF1.
    // Text on it is plain onSurface (white on dark, near-black on light).
    val darkUserBubble = Color(0xFF2C2C30)
    val darkOnUserBubble = darkOnSurface

    // Assistant bubble (Redesign C): the classic grey bubble for
    // assistant turns, with a small logomark avatar beside it. Dark is
    // the raised surface #1C1C1E.
    val darkAssistantBubble = darkSurfaceRaised
    val darkOnAssistantBubble = darkOnSurface

    // ---- Light (White B) ----
    // Three canonical opaque surfaces: background #F9F9FA, raised #FFFFFF,
    // sunken #F7F7F8 (code blocks). `surface` aliases the raised value;
    // `surfaceVariant` is its own chips-only neutral #F5F5F7 (see below).
    // White B replaces the Phase 1 light ladder (#FFFFFF / #E5E5EA /
    // #EBEBED): the page drops to #F9F9FA so pure-white cards lift off it
    // with the shadow tokens instead of sitting grey on white.
    val lightBackground = Color(0xFFF9F9FA)
    val lightSurfaceRaised = Color(0xFFFFFFFF)
    val lightSurfaceSunken = Color(0xFFF7F7F8)
    val lightSurface = lightSurfaceRaised
    // Chips only — deliberately NOT an alias of raised any more: on a white
    // card a chip filled with raised (#FFFFFF) would be invisible, so chips
    // keep their own neutral #F5F5F7. Call sites that want "the card
    // surface" use `surface` / `surfaceRaised`, never this.
    val lightSurfaceVariant = Color(0xFFF5F5F7)

    val lightOnSurface = Color(0xFF0B0B0C)
    // White B lightened every light background, so the Phase 1 ratios all
    // improved: #5F5F66 is now 6.33 / 6.02 / 5.92 / 5.82 on white /
    // background #F9F9FA / sunken #F7F7F8 / variant #F5F5F7 (was 6.33 /
    // 5.05 / 5.32 on white / raised #E5E5EA / sunken #EBEBED).
    val lightOnSurfaceMuted = Color(0xFF5F5F66)
    // Same story: #64646C is now 5.86 / 5.57 / 5.48 / 5.39 — still lighter
    // than muted, still AA on every light surface.
    val lightOnSurfaceFaint = Color(0xFF64646C)

    // Opaque hairline/outline (White B): the old 15% / 12% black alphas
    // are replaced by fixed greys so a border reads the same on #F9F9FA
    // and on a white card. Both are decoration only (1.26:1 / 1.32:1
    // against white) — never text.
    val lightOutline = Color(0xFFE0E0E0)
    val lightHairline = Color(0xFFE5E5EA)
    val lightFill = Color(0x0D000000)         // 5% black
    // Pressed must stay visible in White B: raised is now pure white, so a
    // pressed alias would be invisible — and it must NOT be #F5F5F7 any
    // more, that is surfaceVariant (a pressed card looked identical to a
    // chip). #E9E9EC is one step darker than variant (1.11:1).
    val lightSurfacePressed = Color(0xFFE9E9EC)
    // White-card edge: 1dp #EBEBEB border on every floating white card
    // (see Tokens.ShadowTokens / Modifier.whiteCard). Dark uses hairline.
    val lightCardBorder = Color(0xFFEBEBEB)

    val lightAccent = systemBlue
    val lightOnAccent = Color(0xFFFFFFFF)

    // NEUTRAL user bubble (Phase 1 §7): the raised surface, not blue.
    val lightUserBubble = lightSurfaceRaised
    val lightOnUserBubble = lightOnSurface

    // Assistant bubble (Redesign C): grey #EFEFF1, one step below the
    // chips neutral #F5F5F7 so it reads as a bubble on the #F9F9FA page
    // and stays distinct from the white user bubble on the other side.
    // Every light text token stays AA on it — worst are faint #64646C
    // at 5.11:1 and accentText at 5.21:1 (see WhiteThemeTest). It is
    // deliberately NOT surfaceVariant: chips must keep their own neutral,
    // and a bubble the colour of a chip reads as a chip.
    val lightAssistantBubble = Color(0xFFEFEFF1)
    val lightOnAssistantBubble = lightOnSurface

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
    // Ratios verified by computation on the four White B light backgrounds
    // (white #FFFFFF / page #F9F9FA / sunken #F7F7F8 / variant #F5F5F7).
    // White B lightened every background, so every ratio improved over the
    // Phase 1 values on raised #E5E5EA / sunken #EBEBED.
    // Bright values above stay ONLY for dots/fills (see *Bright roles).
    // success text: 6.58 / 6.26 / 6.15 / 6.05 (was 6.58 / 5.25 / 5.53)
    val lightSuccessText = Color(0xFF1B6B2E)
    // warning text: 5.93 / 5.63 / 5.54 / 5.44 (was 5.93 / 4.72 / 4.98)
    val lightWarningText = Color(0xFF8A5A00)
    // danger text (Material error red): 6.54 / 6.21 / 6.11 / 6.00.
    // The old #FF3B30 was 3.55:1 on white — same failure as success/warning.
    val lightDangerText = Color(0xFFB3261E)
    // accent-as-text: 5.98 / 5.69 / 5.59 / 5.50 (was 5.98 / 4.77 / 5.03).
    // The accent itself (#007AFF, 4.02:1 on white) stays for dots, bars,
    // cursors and tints only — never as text, and never as a fill under
    // onAccent text (see accentFill below).
    val lightAccentText = Color(0xFF005FCC)
    // Accent FILL for buttons/badges/chips that carry onAccent (white)
    // text or icons. White on #005FCC is 5.98:1 (AA); white on the bright
    // #007AFF is only 4.02:1, so light fills never use lightAccent itself.
    val lightAccentFill = Color(0xFF005FCC)

    // ---- Text variants for tinted badges/pills (Fase 6j, 47-F3/92-F6) ----
    // Badge kind-label text: accentText #005FCC is only 4.27:1 on the
    // badge tint #CEDBEC (14% accentText over the assistant bubble
    // #EFEFF1), so the badge needs its own darker blue: 6.14:1 there.
    val lightBadgeAccentText = Color(0xFF004A99)
    // Inverse primary (snackbar action) for the light theme: the dark
    // accent fill #0066CC is only 3.06:1 on the inverse surface #1C1C1E,
    // so the snackbar action uses this lighter blue instead: 7.60:1.
    val lightInversePrimary = Color(0xFF66B2FF)

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

    // Light roles on sunken #F7F7F8 (code-block background): keyword 5.59:1,
    // string 6.09:1, number 5.99:1, comment 6.30:1, function 5.28:1,
    // type 5.54:1. All ≥ 4.5:1 — and all higher than on the old sunken
    // #EBEBED, since White B lightened it. The old light roles (#007AFF
    // 3.37:1, #34C759 1.86:1, #FF9500 1.85:1 on sunken) failed outright.
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

    // Light tool TEXT roles are the AA text variants (running 5.59:1,
    // success 6.15:1, warning 5.54:1, error 6.11:1 on sunken #F7F7F8). The
    // *Bg tints stay bright-based: they are fills, never text.
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

/**
 * Aura palette — extracted from the Meta Muse app (`com.facebook.aura`
 * 16.0.0.2.159, `resources.arsc` + dex class names, `Hatch*` UI).
 *
 * Clean-room re-type, NOT a copy: the APK's resource names are obfuscated
 * (`(name removed)`), so these values are the raw hex from `resources.arsc`
 * given Aura names here; the Optimistic font family and every PNG/WebP
 * asset stay in the APK and are NOT bundled. Substitutes: Inter (the
 * Optimistic role) + JetBrains Mono (the Roboto Mono role), already bundled.
 *
 * AA rules, verified by computation (see `AuraThemeTest`):
 * - Text-safe on white AND on [auraSurface] #F6F7F8: [auraBlueText]
 *   (7.89 / 7.36), [auraMuted] (4.90 / 4.57), [auraPurple] (6.44),
 *   [auraSuccessText] (7.37 / 6.87).
 * - [auraBlueFill] and [auraDanger] are AA on white only (4.58 / 4.77)
 *   and fall just under AA on [auraSurface] (4.27 / 4.45): as text on
 *   the Aura page, use [lightDangerText] / [auraBlueText] instead.
 *   As a FILL under white text they are AA on any surface.
 * - [auraBlue] (3.07 on white) and [auraFaint] (2.93) are NEVER text:
 *   blue is dots / fills / gradient stops only, faint is decoration only.
 * - [auraSuccess] #1D873B is the extracted brand green (fill / dot);
 *   [auraSuccessText] is the same hue darkened for AA text, the same
 *   move Palette already makes for [lightSuccessText].
 */
object AuraPalette {
    // ---- Facebook blue (extracted scale, alpha steps from resources.arsc) ----
    /** Bright blue: dots, fills, gradient stops — never text (3.07:1). */
    val auraBlue = Color(0xFF5890FF)
    /** Fill under white text (white on it 4.58:1); as text, white only. */
    val auraBlueFill = Color(0xFF4673CC)
    /** Text-safe blue (7.89:1 on white, 7.36:1 on #F6F7F8). */
    val auraBlueText = Color(0xFF355089)
    /** Light blue: gradient stop + text on navy (9.31:1 on #141823). */
    val auraBlueLight = Color(0xFF9BBCFF)
    /** Alpha steps of [auraBlue] for tints (10 / 20 / 40 / 60 / 80%). */
    val auraBlueAlpha10 = Color(0x1A5890FF)
    val auraBlueAlpha20 = Color(0x335890FF)
    val auraBlueAlpha40 = Color(0x665890FF)
    val auraBlueAlpha60 = Color(0x995890FF)
    val auraBlueAlpha80 = Color(0xCC5890FF)

    // ---- Material 3 purple (extracted baseline) ----
    val auraPurple = Color(0xFF6750A4)
    val auraPurpleContainer = Color(0xFFEADDFF)
    val auraPurpleLight = Color(0xFFD0BCFF)

    // ---- Aura neutrals ----
    /** Aura page surface (vs Muse B #F9F9FA). */
    val auraSurface = Color(0xFFF6F7F8)
    /** Aura card border (vs Muse B #EBEBEB). */
    val auraBorder = Color(0xFFE9EAED)
    /** Muted text, AA on white and on [auraSurface]. */
    val auraMuted = Color(0xFF6A7180)
    /** Decoration only, never text (2.93:1 on white). */
    val auraFaint = Color(0xFF9197A3)
    /** Near-black text on Aura surfaces (15.97:1 on #F6F7F8). */
    val auraOnSurface = Color(0xFF1C1B1F)

    // ---- Aura dark navy (dark-theme surfaces, extracted) ----
    val auraNavy = Color(0xFF141823)
    val auraNavyDeep = Color(0xFF080A10)
    val auraSlate = Color(0xFF232937)
    val auraSlateLight = Color(0xFF373E4D)
    val auraSlateBorder = Color(0xFF3A465C)

    // ---- Aura semantics ----
    /** Brand green: fill / dot (white on it 4.58:1). */
    val auraSuccess = Color(0xFF1D873B)
    /** Darkened green for AA text (7.37:1 white, 6.87:1 #F6F7F8). */
    val auraSuccessText = Color(0xFF14632B)
    /** Google red: AA as text on white only (4.77:1; 4.45:1 on #F6F7F8). */
    val auraDanger = Color(0xFFD93025)
}
