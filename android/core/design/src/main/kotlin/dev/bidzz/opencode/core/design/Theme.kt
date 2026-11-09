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
) {
    /** Text colour for a status role. */
    fun statusColor(status: Status): Color = when (status) {
        Status.READY -> success
        Status.WORKING -> warning
        Status.ERROR -> danger
        Status.IDLE -> neutral
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
