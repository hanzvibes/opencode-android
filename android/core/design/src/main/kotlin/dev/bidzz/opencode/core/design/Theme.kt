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
 * Semantic colours the app reads.
 *
 * Separate from Material3's `ColorScheme` because the app needs roles Material
 * does not name: a sunken code surface, a user-bubble fill, and status colours.
 * Material's scheme is still provided underneath so stock components look right.
 */
@androidx.compose.runtime.Immutable
data class OpenCodeColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    /** Recedes instead of raising: code blocks, inline code. */
    val surfaceSunken: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val onSurfaceFaint: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val userBubble: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color,
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
    onSurface = Palette.darkOnSurface,
    onSurfaceMuted = Palette.darkOnSurfaceMuted,
    onSurfaceFaint = Palette.darkOnSurfaceFaint,
    outline = Palette.darkOutline,
    accent = Palette.darkAccent,
    onAccent = Palette.darkOnAccent,
    userBubble = Palette.darkUserBubble,
    success = Palette.success,
    warning = Palette.warning,
    danger = Palette.danger,
    neutral = Palette.neutral,
)

private val LightColors = OpenCodeColors(
    background = Palette.lightBackground,
    surface = Palette.lightSurface,
    surfaceRaised = Palette.lightSurfaceRaised,
    surfaceSunken = Palette.lightSurfaceSunken,
    onSurface = Palette.lightOnSurface,
    onSurfaceMuted = Palette.lightOnSurfaceMuted,
    onSurfaceFaint = Palette.lightOnSurfaceFaint,
    outline = Palette.lightOutline,
    accent = Palette.lightAccent,
    onAccent = Palette.lightOnAccent,
    userBubble = Palette.lightUserBubble,
    success = Palette.successLight,
    warning = Palette.warningLight,
    danger = Palette.dangerLight,
    neutral = Palette.neutral,
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
 * Dark is the default because the app is a coding tool used in low light; light is
 * a deliberate alternative, not the starting point.
 */
@Composable
fun OpenCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dark is the default when the system has no opinion, so a fresh install looks
    // like the intended product rather than a washed-out light screen.
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
            outline = colors.outline,
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
            outline = colors.outline,
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
