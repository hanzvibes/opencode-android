package dev.bidzz.opencode.core.design

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours based on the cross-platform-llm-client design system.
 *
 * Phase 1 ("The quiet terminal") rules:
 * - ONE BLUE, ONE MEANING: [activity] is the single live/activity colour
 *   and is always the accent. [statusColor] maps WORKING to [activity],
 *   never to warning — orange is reserved for genuine warnings only.
 *   Header dot, streaming, tools and composer focus all read "working"
 *   through the same blue.
 * - THREE OPAQUE SURFACES, NO ALPHA: background / raised / sunken only
 *   (see Palette). Glass and halo fields were deleted, not deprecated.
 * - USER BUBBLE IS NEUTRAL: [userBubble] is the raised surface in both
 *   themes; [onUserBubble] is [onSurface]. No blue bubble anywhere.
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
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color,
    /**
     * Text-safe accent. In dark this is [accent] itself (5.76:1 on black);
     * in light it is the darkened #005FCC (5.98:1 on white, 4.77:1 on
     * raised) because [accent] #007AFF is only 4.02:1 on white and must
     * never be used as text. Dots, fills and backgrounds keep [accent].
     */
    val accentText: Color = accent,
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
    fill = Palette.darkFill,
    surfacePressed = Palette.darkSurfacePressed,
    accent = Palette.darkAccent,
    onAccent = Palette.darkOnAccent,
    activity = Palette.darkAccent,
    userBubble = Palette.darkUserBubble,
    onUserBubble = Palette.darkOnUserBubble,
    success = Palette.success,
    warning = Palette.warning,
    danger = Palette.danger,
    neutral = Palette.neutral,
    accentText = Palette.darkAccent,
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
    fill = Palette.lightFill,
    surfacePressed = Palette.lightSurfacePressed,
    accent = Palette.lightAccent,
    onAccent = Palette.lightOnAccent,
    activity = Palette.lightAccent,
    userBubble = Palette.lightUserBubble,
    onUserBubble = Palette.lightOnUserBubble,
    // Light semantic TEXT roles are the dark AA variants (see Palette):
    // bright #34C759 / #FF9500 / #FF3B30 / #007AFF fail AA as text.
    success = Palette.lightSuccessText,
    warning = Palette.lightWarningText,
    danger = Palette.lightDangerText,
    neutral = Palette.lightAccentText,
    accentText = Palette.lightAccentText,
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

/** Access point for semantic colours: `OpenCodeTheme.colors.accent`. */
object OpenCodeTheme {
    val colors: OpenCodeColors
        @Composable @ReadOnlyComposable get() = LocalColors.current

    val tokens: OpenCodeTokens
        @Composable @ReadOnlyComposable get() = LocalTokens.current
}

/**
 * Applies the design system.
 *
 * [themeMode] resolves to dark/light here: System follows the phone, Dark /
 * Light pin the palette. The default keeps the old system-only behaviour
 * for previews and any caller that does not pass a mode.
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
    val colors = if (darkTheme) DarkColors else LightColors
    val tokens = OpenCodeTokens()

    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.onSurfaceMuted,
            outline = colors.hairline,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.onSurfaceMuted,
            outline = colors.hairline,
            error = colors.danger,
        )
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
