package dev.bidzz.opencode.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
 *
 * Phase 1 ("The quiet terminal"): flat hairline surfaces, Zed model.
 * Glassmorphism and elevation halos are dead — they had zero consumers —
 * so the Glassmorphism / ElevationHalos classes were deleted, not deprecated.
 * Do not reintroduce blur/glow tokens; depth comes from the three opaque
 * surfaces in [Palette] plus a 0.5dp hairline.
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
 * `plan.md` §12 bans "excessive rounded cards". The ladder tops out at 16dp
 * for surfaces. Only two exceptions survive: the user bubble (asymmetric,
 * 18dp with a 4dp tail corner — geometry owned by MessageBubble, not this
 * ladder) and the full pill for chips and the send button, where a pill is
 * conventional on Android rather than decorative.
 *
 * The old 22dp composer capsule token was deleted in Phase 1: the composer
 * moves to [md] (12dp) in the next wave. Do not re-add a composer radius.
 */
@Immutable
data class Radius(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    /**
     * Deliberate exception to the 16dp surface cap above: the user bubble
     * (asymmetric, 18dp with a 4dp tail corner). Named here so a hardcoded
     * 18.dp in a screen is still a bug, but the value itself is a conscious
     * choice, not an accident. Bubble geometry is out of scope for the
     * radius ladder — see Phase 1 §7.
     */
    val bubble: Dp = 18.dp,
    /** Pills: chips, send button. */
    val full: Dp = 999.dp,
)

/**
 * Motion durations, easing curves, and spring physics.
 *
 * Short and purposeful: `plan.md` §12 asks for meaningful motion only. Streaming
 * text needs no animation at all — the text itself is the feedback.
 */
@Immutable
data class Motion(
    /** State change the user caused, e.g. a chip settling (120ms). */
    val quick: Int = 120,
    /** Entering content, sheet presentation (220ms). */
    val standard: Int = 220,
    /** Screen transitions (320ms). */
    val emphasized: Int = 320,

    /**
     * THE one live pulse period (1000ms). Every pulsing live indicator —
     * status dot, thinking pill, jump-to-latest, busy send button — uses
     * this period so the app breathes at a single rate. Call sites still
     * hardcode their own durations today (e.g. LivePulseIndicator's 1200ms
     * tween); the next wave wires them to this token. Do not invent a
     * second pulse period.
     */
    val pulsePeriodMs: Int = 1000,
    /**
     * Sole exception to [pulsePeriodMs]: the terminal cursor blink (500ms).
     * A cursor is not a live indicator — it blinks at the terminal's own
     * rate. BlinkingCursor already uses a 500ms tween; the next wave wires
     * it to this token. No other animation may use 500ms as a pulse.
     */
    val cursorBlinkPeriodMs: Int = 500,

    // ---- Easing Curves ----
    /** Snappy responsive curve for user-initiated micro-interactions. */
    val easingQuick: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    /** Standard material ease-in-out for content transitions. */
    val easingStandard: Easing = FastOutSlowInEasing,
    /** Emphasized deceleration for sheets and large dialogs. */
    val easingEmphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    /** Smooth exit deceleration curve. */
    val easingDecelerate: Easing = LinearOutSlowInEasing,

    // ---- Spring Physics Parameters ----
    /** Medium bounce for playful micro-feedback (0.75f). */
    val springDampingBouncy: Float = Spring.DampingRatioMediumBouncy,
    /** Critical damping without overshoot for responsive UI (1.0f). */
    val springDampingSmooth: Float = Spring.DampingRatioNoBouncy,
    /** Soft damping with minimal settle oscillation (0.9f). */
    val springDampingGentle: Float = 0.9f,
    /** Snappy stiffness for direct manipulation / taps (1500f). */
    val springStiffnessResponsive: Float = Spring.StiffnessMedium,
    /** Smooth stiffness for expanding rows / tool steps (400f). */
    val springStiffnessSmooth: Float = Spring.StiffnessMediumLow,
    /** Gentle stiffness for sheets and drawer motions (200f). */
    val springStiffnessGentle: Float = Spring.StiffnessLow,
) {
    /** Smooth spring animation for UI elements (no overshoot, smooth settling). */
    fun <T> springSmooth(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = springDampingSmooth, stiffness = springStiffnessSmooth, visibilityThreshold = visibilityThreshold)

    /** Responsive snappy spring for taps and quick micro-interactions. */
    fun <T> springResponsive(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = springDampingSmooth, stiffness = springStiffnessResponsive, visibilityThreshold = visibilityThreshold)

    /** Bouncy spring for badges, checkmarks, and celebratory states. */
    fun <T> springBouncy(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = springDampingBouncy, stiffness = springStiffnessResponsive, visibilityThreshold = visibilityThreshold)

    /** Gentle spring for larger layout changes, accordions, and bottom sheets. */
    fun <T> springGentle(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = springDampingGentle, stiffness = springStiffnessGentle, visibilityThreshold = visibilityThreshold)

    /** Quick tween with snappy easing. */
    fun <T> tweenQuick(delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis = quick, delayMillis = delayMillis, easing = easingQuick)

    /** Standard tween for content entry and fade transitions. */
    fun <T> tweenStandard(delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis = standard, delayMillis = delayMillis, easing = easingStandard)

    /** Emphasized tween for modal navigation and sheet presentation. */
    fun <T> tweenEmphasized(delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis = emphasized, delayMillis = delayMillis, easing = easingEmphasized)
}

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
    /** Streaming indicator pulse (11dp dot, 22dp animated pulse ring). Large enough to
     *  read at a glance while the assistant is typing, unlike the 8dp status dot. */
    val streamingIndicatorSize: Dp = 11.dp,
    /** Touch target for the composer send/stop button. Visual stays 36dp; the
     *  extra padding is hit area only, so the transcript does not shrink. */
    val composerActionTouchTarget: Dp = 44.dp,
    /** Touch target for composer quick-action chips and the clear button. */
    val composerChipTouchTarget: Dp = 40.dp,
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 20.dp,
    val iconLarge: Dp = 24.dp,
    /** Standard hairline border/divider thickness (0.5dp for crisp iOS-style hairline). */
    val hairline: Dp = 0.5.dp,
    /** Content column cap, so text does not stretch unreadably on tablets. */
    val contentMaxWidth: Dp = 720.dp,
    /** Assistant output uses the full column; user bubbles stay narrower. */
    val userBubbleMaxWidthFraction: Float = 0.80f,
)

/** All tokens, passed through CompositionLocal so previews can override them. */
@Immutable
data class OpenCodeTokens(
    val spacing: Spacing = Spacing(),
    val radius: Radius = Radius(),
    val motion: Motion = Motion(),
    val dimensions: Dimensions = Dimensions(),
)
