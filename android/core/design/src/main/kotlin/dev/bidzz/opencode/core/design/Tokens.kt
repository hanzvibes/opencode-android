package dev.bidzz.opencode.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central design tokens.
 *
 * Every component reads from here rather than inventing values, so spacing and
 * radii stay consistent as the app grows. `plan.md` §12 makes this mandatory and
 * forbids ad-hoc padding; a hardcoded `12.dp` in a screen is a bug.
 *
 * The scales are deliberately short. A small set used everywhere reads as
 * designed; a large set used inconsistently reads as generated.
 */

/** 4dp base grid. Named by role, not by size, so a change stays meaningful. */
@Immutable
data class Spacing(
    /** Hairline separation, e.g. between label and value. */
    val xxs: Dp = 2.dp,
    /** Inside a chip or a dense row. */
    val xs: Dp = 4.dp,
    /** Between related lines. */
    val sm: Dp = 8.dp,
    /** Default gap between elements in a group. */
    val md: Dp = 12.dp,
    /** Between groups. */
    val lg: Dp = 16.dp,
    /** Screen edge padding. */
    val xl: Dp = 20.dp,
    /** Between major sections. */
    val xxl: Dp = 28.dp,
    /** Above a primary action area. */
    val xxxl: Dp = 40.dp,
)

/**
 * Corner radii.
 *
 * `plan.md` §12 bans "excessive rounded cards". The scale tops out at 14dp for
 * surfaces and uses a full pill only for chips and the send button, which is where
 * a pill is conventional on Android rather than decorative.
 */
@Immutable
data class Radius(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 14.dp,
    /** Pills: chips, send button. */
    val full: Dp = 999.dp,
)

/**
 * Motion durations.
 *
 * Short and purposeful: `plan.md` §12 asks for meaningful motion only. Streaming
 * text needs no animation at all — the text itself is the feedback.
 */
@Immutable
data class Motion(
    /** State change the user caused, e.g. a chip settling. */
    val quick: Int = 120,
    /** Entering content, sheet presentation. */
    val standard: Int = 220,
    /** Screen transitions. */
    val emphasized: Int = 320,
)

/** Fixed component dimensions, so rows line up across screens. */
@Immutable
data class Dimensions(
    /** Minimum touch target. Below this, Android accessibility guidance is violated. */
    val minTouchTarget: Dp = 48.dp,
    val topBarHeight: Dp = 56.dp,
    /** Composer grows to this then scrolls. */
    val composerMaxHeight: Dp = 160.dp,
    val avatarSize: Dp = 28.dp,
    val statusDotSize: Dp = 8.dp,
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 20.dp,
    val iconLarge: Dp = 24.dp,
    /** Content column cap, so text does not stretch unreadably on tablets. */
    val contentMaxWidth: Dp = 720.dp,
    /** Assistant output uses the full column; user bubbles stay narrower. */
    val userBubbleMaxWidthFraction: Float = 0.85f,
)

/** All tokens, passed through CompositionLocal so previews can override them. */
@Immutable
data class OpenCodeTokens(
    val spacing: Spacing = Spacing(),
    val radius: Radius = Radius(),
    val motion: Motion = Motion(),
    val dimensions: Dimensions = Dimensions(),
)
