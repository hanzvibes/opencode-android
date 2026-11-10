package dev.bidzz.opencode.core.design

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
 */
@androidx.compose.runtime.Immutable
data class OpenCodeColors(
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
    val userBubble: Color,
    /** Text on [userBubble]. */
    val onUserBubble: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color,
    /** Code block tokens. Separate from the status roles, which mean something else. */
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxNumber: Color,
    val syntaxComment: Color,

    // ---- Glassmorphism blur tints ----
    val glassSurface: Color = fill,
    val glassSurfaceSubtle: Color = fill,
    val glassSurfaceThick: Color = surfaceRaised,
    val glassBorder: Color = hairline,
    val glassHighlight: Color = outline,

    // ---- Elevation halos & glows ----
    val haloAmbient: Color = outline,
    val haloAccent: Color = accent.copy(alpha = 0.18f),
    val haloSuccess: Color = success.copy(alpha = 0.15f),
    val haloWarning: Color = warning.copy(alpha = 0.15f),
    val haloDanger: Color = danger.copy(alpha = 0.15f),

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
) {
    /** Text colour for a status role. */
    fun statusColor(status: Status): Color = when (status) {
        Status.READY -> success
        Status.WORKING -> warning
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
            else -> accent
        }
    }
}

/** Engine status as the UI presents it. */
enum class Status { READY, WORKING, ERROR, IDLE }

private val DarkColors = OpenCodeColors(
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
    userBubble = Palette.darkUserBubble,
    onUserBubble = Palette.darkOnUserBubble,
    success = Palette.success,
    warning = Palette.warning,
    danger = Palette.danger,
    neutral = Palette.neutral,
    syntaxKeyword = Palette.darkSyntaxKeyword,
    syntaxString = Palette.darkSyntaxString,
    syntaxNumber = Palette.darkSyntaxNumber,
    syntaxComment = Palette.darkSyntaxComment,
    glassSurface = Palette.darkGlassSurface,
    glassSurfaceSubtle = Palette.darkGlassSurfaceSubtle,
    glassSurfaceThick = Palette.darkGlassSurfaceThick,
    glassBorder = Palette.darkGlassBorder,
    glassHighlight = Palette.darkGlassHighlight,
    haloAmbient = Palette.darkHaloAmbient,
    haloAccent = Palette.darkHaloAccent,
    haloSuccess = Palette.darkHaloSuccess,
    haloWarning = Palette.darkHaloWarning,
    haloDanger = Palette.darkHaloDanger,
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
)

private val LightColors = OpenCodeColors(
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
    userBubble = Palette.lightUserBubble,
    onUserBubble = Palette.lightOnUserBubble,
    success = Palette.successLight,
    warning = Palette.warningLight,
    danger = Palette.dangerLight,
    neutral = Palette.neutralLight,
    syntaxKeyword = Palette.lightSyntaxKeyword,
    syntaxString = Palette.lightSyntaxString,
    syntaxNumber = Palette.lightSyntaxNumber,
    syntaxComment = Palette.lightSyntaxComment,
    glassSurface = Palette.lightGlassSurface,
    glassSurfaceSubtle = Palette.lightGlassSurfaceSubtle,
    glassSurfaceThick = Palette.lightGlassSurfaceThick,
    glassBorder = Palette.lightGlassBorder,
    glassHighlight = Palette.lightGlassHighlight,
    haloAmbient = Palette.lightHaloAmbient,
    haloAccent = Palette.lightHaloAccent,
    haloSuccess = Palette.lightHaloSuccess,
    haloWarning = Palette.lightHaloWarning,
    haloDanger = Palette.lightHaloDanger,
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
 */
@Composable
fun OpenCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
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
