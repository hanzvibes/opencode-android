package dev.bidzz.opencode.core.design

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Semantic colours based on the cross-platform-llm-client design system.
 *
 * Phase 1 ("The quiet terminal") rules:
 * - ONE BLUE, ONE MEANING: [activity] is the single live/activity colour
 *   and is always the accent. [statusColor] maps WORKING to [activity],
 *   never to warning — orange is reserved for genuine warnings only.
 *   Header dot, streaming, tools and composer focus all read "working"
 *   through the same blue.
 * - OPAQUE SURFACES, no alpha: dark has four measured steps, light its
 *   White B ladder (see Palette). Glass and halo fields were deleted,
 *   not deprecated.
 * - USER BUBBLE IS NEUTRAL: [userBubble] is the raised surface in light
 *   and one step above the assistant bubble in dark; [onUserBubble] is
 *   [onSurface]. No blue bubble anywhere.
 * - LIGHT TEXT IS AA: in light, success / warning / danger / neutral are
 *   the dark text variants (≥ 4.5:1); the bright Apple colours live on
 *   only as [successBright] / [warningBright] / [dangerBright] for
 *   dots and fills. [accentText] is the text-safe accent in light.
 * - NEVER detect the theme by comparing colour values (the old
 *   `colors.background == Palette.darkBackground` in MarkdownText.kt):
 *   read [isDark] instead.
 */
@androidx.compose.runtime.Immutable
data class OpenCodeColors(
    /**
     * True in the dark theme, false in light. The ONLY sanctioned way for
     * a call site to branch on theme — never compare colour values.
     */
    val isDark: Boolean = false,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    /** Recedes instead of raising: code blocks, inline code. */
    val surfaceSunken: Color,
    /** Neutral fill for chips, pills, badges: small neutral surfaces. */
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val onSurfaceFaint: Color,
    val outline: Color,
    val hairline: Color,
    /**
     * White-card edge colour (White B): 1dp #EBEBEB in light, the hairline
     * in dark (dark cards stay flat — no shadow, no card border). Read by
     * `Modifier.whiteCard` (ui/Cards.kt) together with `OpenCodeTokens.shadow`.
     */
    val cardBorder: Color = hairline,
    val fill: Color,
    val surfacePressed: Color,
    val accent: Color,
    val onAccent: Color,
    /**
     * THE single live/activity colour — always [accent]. Anything that
     * means "alive / working / streaming / focused" reads this, never
     * warning orange and never a second blue.
     */
    val activity: Color = accent,
    /**
     * Neutral raised surface — user messages are NOT blue (Phase 1 §7).
     * The bubble is distinguished by position and shape, not colour.
     */
    val userBubble: Color,
    /** Text on [userBubble]. Always [onSurface]. */
    val onUserBubble: Color,
    /**
     * Grey bubble behind an assistant turn (Redesign C): light #EFEFF1,
     * dark the raised surface. Text on it is [onAssistantBubble]
     * ([onSurface] in both themes). The bubble is flat — no shadow, just
     * a hairline edge — so it reads as a message bubble, not a floating
     * card; the white user bubble keeps the card shadow instead.
     */
    val assistantBubble: Color,
    /** Text on [assistantBubble]. Always [onSurface]. */
    val onAssistantBubble: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color,
    /**
     * Text-safe accent. In dark this is [accent] itself (5.76:1 on black);
     * in light it is the darkened #005FCC (5.98:1 on white, 5.69:1 on
     * background #F9F9FA, 5.59:1 on sunken #F7F7F8) because [accent]
     * #007AFF is only 4.02:1 on white and must never be used as text.
     * Dots, bars, cursors and tints keep [accent]; solid fills that carry
     * [onAccent] text or icons use [accentFill] instead.
     */
    val accentText: Color = accent,
    /**
     * Solid fill for buttons, badges and chips that carry [onAccent]
     * (white) text or icons. Light #005FCC (white on it 5.98:1), dark
     * #0066CC (white on it 5.57:1) — both AA. The bright [accent]
     * (#007AFF / #0A84FF) is only 4.02:1 / 3.65:1 with white text, so no
     * text-bearing fill may use it.
     */
    val accentFill: Color = accent,
    /** Vivid success for dots/fills only. In dark == [success]; in light
     *  == bright #34C759 (2.22:1 on white — never as text). */
    val successBright: Color = success,
    /** Vivid warning for dots/fills only. In dark == [warning]; in light
     *  == bright #FF9500 (2.20:1 on white — never as text). */
    val warningBright: Color = warning,
    /** Vivid danger for dots/fills only. In dark == [danger]; in light
     *  == bright #FF3B30 (3.55:1 on white — never as text). */
    val dangerBright: Color = danger,
    /** Code block tokens. Separate from the status roles, which mean something else. */
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxNumber: Color,
    val syntaxComment: Color,
    /** Function / call names in code. Single source of truth with the
     *  other syntax roles — call sites must not invent local palettes. */
    val syntaxFunction: Color = Palette.darkSyntaxFunction,
    /** Type / class names in code. */
    val syntaxType: Color = Palette.darkSyntaxType,

    // ---- Tool statuses ----
    val toolRunning: Color = accent,
    val toolRunningBg: Color = accent.copy(alpha = 0.14f),
    val toolSuccess: Color = success,
    val toolSuccessBg: Color = success.copy(alpha = 0.14f),
    val toolError: Color = danger,
    val toolErrorBg: Color = danger.copy(alpha = 0.14f),
    val toolWarning: Color = warning,
    val toolWarningBg: Color = warning.copy(alpha = 0.14f),
    val toolPending: Color = onSurfaceMuted,
    val toolPendingBg: Color = fill,
    /**
     * Text-safe blue for the tool-kind badge (47-F3): the kind label is
     * 9sp bold mono on a 14% tint of itself over the assistant bubble.
     * Light #004A99 is 6.14:1 on the tint #CEDBEC (accentText #005FCC
     * is only 4.27:1 there); dark #5AC8FA is 6.77:1 on #25343D
     * (accent #0A84FF is only 3.95:1 there).
     */
    val badgeAccentText: Color = accentText,
    /**
     * Text-safe variants of [toolRunning] / [toolError] for the status
     * pills (47-F4): the pill text sits on a 12% tint of the same colour
     * over the assistant bubble. In light the plain tokens already pass
     * (running 4.50:1, failed 4.71:1); in dark they fail (running
     * 4.09:1, failed 4.40:1), so dark uses the lightened variants
     * (running 7.88:1 on #1A2839, failed 6.56:1 on #372121).
     */
    val toolRunningText: Color = toolRunning,
    val toolErrorText: Color = toolError,

    // ---- Unified Diff semantic colors (+/-) ----
    val diffAdded: Color = success,
    val diffAddedBg: Color = success.copy(alpha = 0.14f),
    val diffAddedGutter: Color = success.copy(alpha = 0.24f),
    val diffRemoved: Color = danger,
    val diffRemovedBg: Color = danger.copy(alpha = 0.14f),
    val diffRemovedGutter: Color = danger.copy(alpha = 0.24f),
    val diffModified: Color = warning,
    val diffModifiedBg: Color = warning.copy(alpha = 0.14f),
    val diffHunkHeader: Color = syntaxKeyword,
    val diffHunkHeaderBg: Color = surfaceSunken,

    // ---- Model Providers ----
    val providerAnthropic: Color = Color(0xFFD97757),
    val providerOpenAI: Color = Color(0xFF10A37F),
    val providerGoogle: Color = Color(0xFF4285F4),
    val providerDeepSeek: Color = Color(0xFF4D88FF),
    val providerMistral: Color = Color(0xFFFF7000),
    val providerOllama: Color = onSurface,
    val providerMeta: Color = Color(0xFF0081FB),
    val providerGroq: Color = Color(0xFFF55036),
    val providerCohere: Color = Color(0xFF388E6D),
    val providerQwen: Color = Color(0xFF8B5CF6),
    /** NVIDIA / Nemotron (Phase 2): brand green #76B900. */
    val providerNvidia: Color = Color(0xFF76B900),
) {
    /**
     * Text colour for a status role.
     *
     * ONE BLUE, ONE MEANING (Phase 1 §1): WORKING is [activity] (= accent).
     * It used to be warning orange, which made "working" and "warning"
     * indistinguishable — a busy header dot read as an alert, and the
     * streaming/tool/composer blues disagreed with it. Orange is now
     * reserved for genuine warnings only. READY / ERROR / IDLE are
     * unchanged: success / danger / neutral.
     */
    fun statusColor(status: Status): Color = when (status) {
        Status.READY -> success
        Status.WORKING -> activity
        Status.ERROR -> danger
        Status.IDLE -> neutral
    }

    /** Resolves a tool status string ("running", "completed", "error", etc.) to semantic text/icon color. */
    fun toolStatusColor(status: String?): Color = when (status?.lowercase()) {
        "running", "streaming", "in_progress" -> toolRunning
        "completed", "success", "done" -> toolSuccess
        "error", "failed" -> toolError
        "warning", "halted" -> toolWarning
        "pending", "queued" -> toolPending
        else -> onSurfaceMuted
    }

    /** Resolves a tool status string to semantic background tint. */
    fun toolStatusBg(status: String?): Color = when (status?.lowercase()) {
        "running", "streaming", "in_progress" -> toolRunningBg
        "completed", "success", "done" -> toolSuccessBg
        "error", "failed" -> toolErrorBg
        "warning", "halted" -> toolWarningBg
        "pending", "queued" -> toolPendingBg
        else -> surfaceSunken
    }

    /** Resolves a diff marker or line type (+, -, ~, @@) to semantic text color. */
    fun diffMarkerColor(marker: String): Color = when {
        marker.startsWith("+") -> diffAdded
        marker.startsWith("-") || marker.startsWith("−") -> diffRemoved
        marker.startsWith("~") -> diffModified
        marker.startsWith("@") -> diffHunkHeader
        else -> onSurfaceFaint
    }

    /** Resolves a diff line background tint. */
    fun diffLineBg(kind: String): Color = when (kind.lowercase()) {
        "added", "+" -> diffAddedBg
        "removed", "-" -> diffRemovedBg
        "modified", "~" -> diffModifiedBg
        "hunk_header", "header", "@@" -> diffHunkHeaderBg
        else -> Color.Transparent
    }

    /** Resolves a model provider name to its signature brand color. */
    fun providerColor(provider: String?): Color {
        if (provider.isNullOrBlank()) return accent
        val p = provider.trim().lowercase()
        return when {
            p.contains("anthropic") || p.contains("claude") -> providerAnthropic
            p.contains("openai") || p.contains("chatgpt") || p.contains("gpt") -> providerOpenAI
            p.contains("google") || p.contains("gemini") -> providerGoogle
            p.contains("deepseek") -> providerDeepSeek
            p.contains("mistral") || p.contains("codestral") -> providerMistral
            p.contains("ollama") -> providerOllama
            p.contains("meta") || p.contains("llama") -> providerMeta
            p.contains("groq") -> providerGroq
            p.contains("cohere") || p.contains("command") -> providerCohere
            p.contains("qwen") || p.contains("alibaba") -> providerQwen
            p.contains("nvidia") || p.contains("nemotron") -> providerNvidia
            else -> accent
        }
    }
}

/** Engine status as the UI presents it. */
enum class Status { READY, WORKING, ERROR, IDLE }

/**
 * Which colour theme the app shows.
 *
 * [System] follows the phone; [Dark] / [Light] pin the app to one palette
 * whatever the phone is set to. The light palette already existed — it was
 * just unreachable before, because the theme was always system-driven.
 */
enum class ThemeMode(val code: String) {
    System("system"),
    Dark("dark"),
    Light("light");

    companion object {
        fun fromCode(code: String?): ThemeMode = entries.firstOrNull { it.code == code } ?: System
    }
}

/**
 * Remembers the chosen [ThemeMode].
 *
 * Same pattern as `LanguageStore`: one string in the shared `app_settings`
 * preferences, read once at startup and applied over the whole tree by
 * [OpenCodeApp], so picking a theme in Settings redraws every screen in
 * place instead of restarting the activity. No new storage mechanism.
 */
object ThemeStore {
    private const val PREFS = "app_settings"
    private const val KEY = "theme_mode"

    fun get(context: Context): ThemeMode =
        ThemeMode.fromCode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.code).apply()
    }
}

private val DarkColors = OpenCodeColors(
    isDark = true,
    background = Palette.darkBackground,
    surface = Palette.darkSurface,
    surfaceRaised = Palette.darkSurfaceRaised,
    surfaceSunken = Palette.darkSurfaceSunken,
    surfaceVariant = Palette.darkSurfaceVariant,
    onSurface = Palette.darkOnSurface,
    onSurfaceMuted = Palette.darkOnSurfaceMuted,
    onSurfaceFaint = Palette.darkOnSurfaceFaint,
    outline = Palette.darkOutline,
    hairline = Palette.darkHairline,
    cardBorder = Palette.darkHairline,
    fill = Palette.darkFill,
    surfacePressed = Palette.darkSurfacePressed,
    accent = Palette.darkAccent,
    onAccent = Palette.darkOnAccent,
    activity = Palette.darkAccent,
    userBubble = Palette.darkUserBubble,
    onUserBubble = Palette.darkOnUserBubble,
    assistantBubble = Palette.darkAssistantBubble,
    onAssistantBubble = Palette.darkOnAssistantBubble,
    success = Palette.success,
    warning = Palette.warning,
    danger = Palette.danger,
    neutral = Palette.neutral,
    accentText = Palette.darkAccent,
    accentFill = Palette.darkAccentFill,
    successBright = Palette.success,
    warningBright = Palette.warning,
    dangerBright = Palette.danger,
    syntaxKeyword = Palette.darkSyntaxKeyword,
    syntaxString = Palette.darkSyntaxString,
    syntaxNumber = Palette.darkSyntaxNumber,
    syntaxComment = Palette.darkSyntaxComment,
    syntaxFunction = Palette.darkSyntaxFunction,
    syntaxType = Palette.darkSyntaxType,
    toolRunning = Palette.darkToolRunning,
    toolRunningBg = Palette.darkToolRunningBg,
    toolSuccess = Palette.darkToolSuccess,
    toolSuccessBg = Palette.darkToolSuccessBg,
    toolError = Palette.darkToolError,
    toolErrorBg = Palette.darkToolErrorBg,
    toolWarning = Palette.darkToolWarning,
    toolWarningBg = Palette.darkToolWarningBg,
    toolPending = Palette.darkToolPending,
    toolPendingBg = Palette.darkToolPendingBg,
    badgeAccentText = Palette.darkBadgeAccentText,
    toolRunningText = Palette.darkToolRunningText,
    toolErrorText = Palette.darkToolErrorText,
    diffAdded = Palette.darkDiffAdded,
    diffAddedBg = Palette.darkDiffAddedBg,
    diffAddedGutter = Palette.darkDiffAddedGutter,
    diffRemoved = Palette.darkDiffRemoved,
    diffRemovedBg = Palette.darkDiffRemovedBg,
    diffRemovedGutter = Palette.darkDiffRemovedGutter,
    diffModified = Palette.darkDiffModified,
    diffModifiedBg = Palette.darkDiffModifiedBg,
    diffHunkHeader = Palette.darkDiffHunkHeader,
    diffHunkHeaderBg = Palette.darkDiffHunkHeaderBg,
    providerAnthropic = Palette.darkProviderAnthropic,
    providerOpenAI = Palette.darkProviderOpenAI,
    providerGoogle = Palette.darkProviderGoogle,
    providerDeepSeek = Palette.darkProviderDeepSeek,
    providerMistral = Palette.darkProviderMistral,
    providerOllama = Palette.darkProviderOllama,
    providerMeta = Palette.darkProviderMeta,
    providerGroq = Palette.darkProviderGroq,
    providerCohere = Palette.darkProviderCohere,
    providerQwen = Palette.darkProviderQwen,
    providerNvidia = Palette.darkProviderNvidia,
)

private val LightColors = OpenCodeColors(
    isDark = false,
    background = Palette.lightBackground,
    surface = Palette.lightSurface,
    surfaceRaised = Palette.lightSurfaceRaised,
    surfaceSunken = Palette.lightSurfaceSunken,
    surfaceVariant = Palette.lightSurfaceVariant,
    onSurface = Palette.lightOnSurface,
    onSurfaceMuted = Palette.lightOnSurfaceMuted,
    onSurfaceFaint = Palette.lightOnSurfaceFaint,
    outline = Palette.lightOutline,
    hairline = Palette.lightHairline,
    cardBorder = Palette.lightCardBorder,
    fill = Palette.lightFill,
    surfacePressed = Palette.lightSurfacePressed,
    accent = Palette.lightAccent,
    onAccent = Palette.lightOnAccent,
    activity = Palette.lightAccent,
    userBubble = Palette.lightUserBubble,
    onUserBubble = Palette.lightOnUserBubble,
    assistantBubble = Palette.lightAssistantBubble,
    onAssistantBubble = Palette.lightOnAssistantBubble,
    // Light semantic TEXT roles are the dark AA variants (see Palette):
    // bright #34C759 / #FF9500 / #FF3B30 / #007AFF fail AA as text.
    success = Palette.lightSuccessText,
    warning = Palette.lightWarningText,
    danger = Palette.lightDangerText,
    neutral = Palette.lightAccentText,
    accentText = Palette.lightAccentText,
    accentFill = Palette.lightAccentFill,
    successBright = Palette.successLight,
    warningBright = Palette.warningLight,
    dangerBright = Palette.dangerLight,
    syntaxKeyword = Palette.lightSyntaxKeyword,
    syntaxString = Palette.lightSyntaxString,
    syntaxNumber = Palette.lightSyntaxNumber,
    syntaxComment = Palette.lightSyntaxComment,
    syntaxFunction = Palette.lightSyntaxFunction,
    syntaxType = Palette.lightSyntaxType,
    toolRunning = Palette.lightToolRunning,
    toolRunningBg = Palette.lightToolRunningBg,
    toolSuccess = Palette.lightToolSuccess,
    toolSuccessBg = Palette.lightToolSuccessBg,
    toolError = Palette.lightToolError,
    toolErrorBg = Palette.lightToolErrorBg,
    toolWarning = Palette.lightToolWarning,
    toolWarningBg = Palette.lightToolWarningBg,
    toolPending = Palette.lightToolPending,
    toolPendingBg = Palette.lightToolPendingBg,
    badgeAccentText = Palette.lightBadgeAccentText,
    toolRunningText = Palette.lightToolRunning,
    toolErrorText = Palette.lightToolError,
    diffAdded = Palette.lightDiffAdded,
    diffAddedBg = Palette.lightDiffAddedBg,
    diffAddedGutter = Palette.lightDiffAddedGutter,
    diffRemoved = Palette.lightDiffRemoved,
    diffRemovedBg = Palette.lightDiffRemovedBg,
    diffRemovedGutter = Palette.lightDiffRemovedGutter,
    diffModified = Palette.lightDiffModified,
    diffModifiedBg = Palette.lightDiffModifiedBg,
    diffHunkHeader = Palette.lightDiffHunkHeader,
    diffHunkHeaderBg = Palette.lightDiffHunkHeaderBg,
    providerAnthropic = Palette.lightProviderAnthropic,
    providerOpenAI = Palette.lightProviderOpenAI,
    providerGoogle = Palette.lightProviderGoogle,
    providerDeepSeek = Palette.lightProviderDeepSeek,
    providerMistral = Palette.lightProviderMistral,
    providerOllama = Palette.lightProviderOllama,
    providerMeta = Palette.lightProviderMeta,
    providerGroq = Palette.lightProviderGroq,
    providerCohere = Palette.lightProviderCohere,
    providerQwen = Palette.lightProviderQwen,
    providerNvidia = Palette.lightProviderNvidia,
)

private val LocalColors = staticCompositionLocalOf { DarkColors }
private val LocalTokens = staticCompositionLocalOf { OpenCodeTokens() }

/**
 * Animates every semantic colour from its current value to this palette's
 * value, so switching theme (system change, or the Appearance picker in
 * Settings) fades instead of cutting. On first composition each
 * [animateColorAsState] starts at its target, so launch shows no fade —
 * the animation only runs when the target actually changes.
 *
 * Every stored colour field is animated (one state per field, all sharing
 * [animationSpec]); [isDark] is a Boolean and copies across unchanged.
 */
@Composable
private fun OpenCodeColors.animated(animationSpec: AnimationSpec<Color>): OpenCodeColors = copy(
    background = animateColorAsState(background, animationSpec, label = "background").value,
    surface = animateColorAsState(surface, animationSpec, label = "surface").value,
    surfaceRaised = animateColorAsState(surfaceRaised, animationSpec, label = "surfaceRaised").value,
    surfaceSunken = animateColorAsState(surfaceSunken, animationSpec, label = "surfaceSunken").value,
    surfaceVariant = animateColorAsState(surfaceVariant, animationSpec, label = "surfaceVariant").value,
    onSurface = animateColorAsState(onSurface, animationSpec, label = "onSurface").value,
    onSurfaceMuted = animateColorAsState(onSurfaceMuted, animationSpec, label = "onSurfaceMuted").value,
    onSurfaceFaint = animateColorAsState(onSurfaceFaint, animationSpec, label = "onSurfaceFaint").value,
    outline = animateColorAsState(outline, animationSpec, label = "outline").value,
    hairline = animateColorAsState(hairline, animationSpec, label = "hairline").value,
    cardBorder = animateColorAsState(cardBorder, animationSpec, label = "cardBorder").value,
    fill = animateColorAsState(fill, animationSpec, label = "fill").value,
    surfacePressed = animateColorAsState(surfacePressed, animationSpec, label = "surfacePressed").value,
    accent = animateColorAsState(accent, animationSpec, label = "accent").value,
    onAccent = animateColorAsState(onAccent, animationSpec, label = "onAccent").value,
    activity = animateColorAsState(activity, animationSpec, label = "activity").value,
    userBubble = animateColorAsState(userBubble, animationSpec, label = "userBubble").value,
    onUserBubble = animateColorAsState(onUserBubble, animationSpec, label = "onUserBubble").value,
    assistantBubble = animateColorAsState(assistantBubble, animationSpec, label = "assistantBubble").value,
    onAssistantBubble = animateColorAsState(onAssistantBubble, animationSpec, label = "onAssistantBubble").value,
    success = animateColorAsState(success, animationSpec, label = "success").value,
    warning = animateColorAsState(warning, animationSpec, label = "warning").value,
    danger = animateColorAsState(danger, animationSpec, label = "danger").value,
    neutral = animateColorAsState(neutral, animationSpec, label = "neutral").value,
    accentText = animateColorAsState(accentText, animationSpec, label = "accentText").value,
    accentFill = animateColorAsState(accentFill, animationSpec, label = "accentFill").value,
    successBright = animateColorAsState(successBright, animationSpec, label = "successBright").value,
    warningBright = animateColorAsState(warningBright, animationSpec, label = "warningBright").value,
    dangerBright = animateColorAsState(dangerBright, animationSpec, label = "dangerBright").value,
    syntaxKeyword = animateColorAsState(syntaxKeyword, animationSpec, label = "syntaxKeyword").value,
    syntaxString = animateColorAsState(syntaxString, animationSpec, label = "syntaxString").value,
    syntaxNumber = animateColorAsState(syntaxNumber, animationSpec, label = "syntaxNumber").value,
    syntaxComment = animateColorAsState(syntaxComment, animationSpec, label = "syntaxComment").value,
    syntaxFunction = animateColorAsState(syntaxFunction, animationSpec, label = "syntaxFunction").value,
    syntaxType = animateColorAsState(syntaxType, animationSpec, label = "syntaxType").value,
    toolRunning = animateColorAsState(toolRunning, animationSpec, label = "toolRunning").value,
    toolRunningBg = animateColorAsState(toolRunningBg, animationSpec, label = "toolRunningBg").value,
    toolSuccess = animateColorAsState(toolSuccess, animationSpec, label = "toolSuccess").value,
    toolSuccessBg = animateColorAsState(toolSuccessBg, animationSpec, label = "toolSuccessBg").value,
    toolError = animateColorAsState(toolError, animationSpec, label = "toolError").value,
    toolErrorBg = animateColorAsState(toolErrorBg, animationSpec, label = "toolErrorBg").value,
    toolWarning = animateColorAsState(toolWarning, animationSpec, label = "toolWarning").value,
    toolWarningBg = animateColorAsState(toolWarningBg, animationSpec, label = "toolWarningBg").value,
    toolPending = animateColorAsState(toolPending, animationSpec, label = "toolPending").value,
    toolPendingBg = animateColorAsState(toolPendingBg, animationSpec, label = "toolPendingBg").value,
    badgeAccentText = animateColorAsState(badgeAccentText, animationSpec, label = "badgeAccentText").value,
    toolRunningText = animateColorAsState(toolRunningText, animationSpec, label = "toolRunningText").value,
    toolErrorText = animateColorAsState(toolErrorText, animationSpec, label = "toolErrorText").value,
    diffAdded = animateColorAsState(diffAdded, animationSpec, label = "diffAdded").value,
    diffAddedBg = animateColorAsState(diffAddedBg, animationSpec, label = "diffAddedBg").value,
    diffAddedGutter = animateColorAsState(diffAddedGutter, animationSpec, label = "diffAddedGutter").value,
    diffRemoved = animateColorAsState(diffRemoved, animationSpec, label = "diffRemoved").value,
    diffRemovedBg = animateColorAsState(diffRemovedBg, animationSpec, label = "diffRemovedBg").value,
    diffRemovedGutter = animateColorAsState(diffRemovedGutter, animationSpec, label = "diffRemovedGutter").value,
    diffModified = animateColorAsState(diffModified, animationSpec, label = "diffModified").value,
    diffModifiedBg = animateColorAsState(diffModifiedBg, animationSpec, label = "diffModifiedBg").value,
    diffHunkHeader = animateColorAsState(diffHunkHeader, animationSpec, label = "diffHunkHeader").value,
    diffHunkHeaderBg = animateColorAsState(diffHunkHeaderBg, animationSpec, label = "diffHunkHeaderBg").value,
    providerAnthropic = animateColorAsState(providerAnthropic, animationSpec, label = "providerAnthropic").value,
    providerOpenAI = animateColorAsState(providerOpenAI, animationSpec, label = "providerOpenAI").value,
    providerGoogle = animateColorAsState(providerGoogle, animationSpec, label = "providerGoogle").value,
    providerDeepSeek = animateColorAsState(providerDeepSeek, animationSpec, label = "providerDeepSeek").value,
    providerMistral = animateColorAsState(providerMistral, animationSpec, label = "providerMistral").value,
    providerOllama = animateColorAsState(providerOllama, animationSpec, label = "providerOllama").value,
    providerMeta = animateColorAsState(providerMeta, animationSpec, label = "providerMeta").value,
    providerGroq = animateColorAsState(providerGroq, animationSpec, label = "providerGroq").value,
    providerCohere = animateColorAsState(providerCohere, animationSpec, label = "providerCohere").value,
    providerQwen = animateColorAsState(providerQwen, animationSpec, label = "providerQwen").value,
    providerNvidia = animateColorAsState(providerNvidia, animationSpec, label = "providerNvidia").value,
)

/** Access point for semantic colours: `OpenCodeTheme.colors.accent`. */
object OpenCodeTheme {
    val colors: OpenCodeColors
        @Composable @ReadOnlyComposable get() = LocalColors.current

    val tokens: OpenCodeTokens
        @Composable @ReadOnlyComposable get() = LocalTokens.current
}

/**
 * The Material3 [ColorScheme] for these [OpenCodeColors], in full.
 *
 * Every field is set from our own tokens — nothing falls through to the
 * M3 baseline defaults (dark used to leak the baseline purple #D0BCFF
 * through `surfaceContainerHigh` and friends, and `surfaceVariant` was
 * mapped to the raised surface instead of the variant step). A pure
 * function, not a @Composable, so MaterialSchemeTest can assert it on
 * the JVM.
 *
 * The design system has ONE BLUE and neutral containers: primary,
 * secondary and tertiary are all [OpenCodeColors.accentFill] (white on
 * it is AA in both themes); every `*Container` is a ladder step, never
 * a tinted M3 container, and `on*Container` is plain
 * [OpenCodeColors.onSurface]. Error keeps [OpenCodeColors.danger] with
 * [OpenCodeColors.background] on it — white on the dark danger #FF453A
 * is only 3.41:1, black-on-red is 6.17:1 (light: #F9F9FA on #B3261E is
 * 6.21:1). `surfaceTint` is the surface itself: depth comes from
 * shadow, never from a tinted elevation overlay. `scrim` is the
 * standard 32% black — a translucent overlay by definition, not a
 * surface, so the opaque-surface rule does not apply to it.
 */
fun materialColorScheme(colors: OpenCodeColors, darkTheme: Boolean): ColorScheme {
    // The container scale runs lightest -> darkest in light (Lowest is
    // the raised white, Highest the pressed step) and darkest -> lightest
    // in dark (Lowest is the black background, Highest the variant step).
    // NOTE: the top dark step only reads above pressed once PR #39's
    // variant (#26262A) lands; until then Highest == surface.
    val surfaceContainerLowest = if (darkTheme) colors.background else colors.surfaceRaised
    val surfaceContainerLow = if (darkTheme) colors.surfaceSunken else colors.background
    val surfaceContainer = if (darkTheme) colors.surface else colors.surfaceSunken
    val surfaceContainerHigh = if (darkTheme) colors.surfacePressed else colors.surfaceVariant
    val surfaceContainerHighest = if (darkTheme) colors.surfaceVariant else colors.surfacePressed
    val surfaceDim = if (darkTheme) colors.background else colors.surfacePressed
    val surfaceBright = if (darkTheme) colors.surfaceVariant else colors.surfaceRaised
    // Inverse roles are the opposite theme's ladder, for Snackbars.
    val inverseSurface = if (darkTheme) Palette.lightSurfaceRaised else Palette.darkSurfaceRaised
    val inverseOnSurface = if (darkTheme) Palette.lightOnSurface else Palette.darkOnSurface
    val inversePrimary = if (darkTheme) Palette.lightAccentFill else Palette.lightInversePrimary
    return if (darkTheme) {
        darkColorScheme(
            primary = colors.accentFill,
            onPrimary = colors.onAccent,
            primaryContainer = colors.surfaceVariant,
            onPrimaryContainer = colors.onSurface,
            secondary = colors.accentFill,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.surfaceVariant,
            onSecondaryContainer = colors.onSurface,
            tertiary = colors.accentFill,
            onTertiary = colors.onAccent,
            tertiaryContainer = colors.surfaceVariant,
            onTertiaryContainer = colors.onSurface,
            error = colors.danger,
            onError = colors.background,
            errorContainer = colors.surfaceVariant,
            onErrorContainer = colors.onSurface,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.onSurfaceMuted,
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = colors.outline,
            outlineVariant = colors.hairline,
            scrim = Color(0x52000000),
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            inversePrimary = inversePrimary,
            surfaceTint = colors.surface,
        )
    } else {
        lightColorScheme(
            primary = colors.accentFill,
            onPrimary = colors.onAccent,
            primaryContainer = colors.surfaceVariant,
            onPrimaryContainer = colors.onSurface,
            secondary = colors.accentFill,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.surfaceVariant,
            onSecondaryContainer = colors.onSurface,
            tertiary = colors.accentFill,
            onTertiary = colors.onAccent,
            tertiaryContainer = colors.surfaceVariant,
            onTertiaryContainer = colors.onSurface,
            error = colors.danger,
            onError = colors.background,
            errorContainer = colors.surfaceVariant,
            onErrorContainer = colors.onSurface,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.onSurfaceMuted,
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = colors.outline,
            outlineVariant = colors.hairline,
            scrim = Color(0x52000000),
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            inversePrimary = inversePrimary,
            surfaceTint = colors.surface,
        )
    }
}

/**
 * Applies the design system.
 *
 * [themeMode] resolves to dark/light here: System follows the phone, Dark /
 * Light pin the palette. The default keeps the old system-only behaviour
 * for previews and any caller that does not pass a mode.
 *
 * Switching theme fades: every semantic colour animates to its new value
 * over [Motion.standard] (220ms, standard easing) via [OpenCodeColors.animated],
 * so the dark/light selection logic itself is unchanged — only the cut
 * between palettes is gone.
 */
@Composable
fun OpenCodeTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val tokens = OpenCodeTokens()
    val targetColors = if (darkTheme) DarkColors else LightColors
    val colors = targetColors.animated(
        tween(durationMillis = tokens.motion.standard, easing = tokens.motion.easingStandard),
    )

    val material = materialColorScheme(colors, darkTheme)

    // #12: the system bars follow the APP theme, not the system theme — a
    // user who forces Light in Settings must get dark status icons on the
    // white bars even when the system is in dark mode (and vice versa).
    // No new dependency: framework WindowInsetsController on API 30+,
    // legacy SYSTEM_UI_FLAG_* bits on API 29, touching only the light bits
    // so the edge-to-edge layout flags from enableEdgeToEdge() survive.
    // A non-Activity context (e.g. a @Preview) simply skips this.
    val context = LocalContext.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        val lightBars = !darkTheme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return@SideEffect
            val appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            controller.setSystemBarsAppearance(if (lightBars) appearance else 0, appearance)
        } else {
            @Suppress("DEPRECATION")
            val flags = window.decorView.systemUiVisibility
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (lightBars) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        }
    }

    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTokens provides tokens,
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = OpenCodeTypography.material(),
            content = content,
        )
    }
}
