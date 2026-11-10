package dev.bidzz.opencode.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.HairlineSeparator

/**
 * Message input styled as an AI IDE omnibar (like Cursor, Claude, Antigravity).
 *
 * Features:
 * - Sleek floating capsule with active glow and hairline focus effect.
 * - Multi-line auto-expand behavior with comfortable line-height.
 * - Quick action shortcuts: `/` command prefix, file attachment indicator, clear icon.
 * - Modern action button (Send / Stop / Voice / Abort) with dynamic colors and haptic-like press feel.
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onVoice: (() -> Unit)? = null,
    onAttachFile: (() -> Unit)? = null,
    onKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    controls: @Composable () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    val motion = OpenCodeTheme.tokens.motion

    var isFocused by remember { mutableStateOf(false) }

    val canSend = enabled && value.isNotBlank() && !busy
    // Named token, not a hardcoded 22.dp: the composer capsule is a deliberate
    // exception to the 16dp surface radius cap (see Tokens.kt).
    val shape = RoundedCornerShape(radius.composer)

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accent.copy(alpha = 0.70f) else colors.hairline,
        animationSpec = tween(durationMillis = motion.quick),
        label = "composer_border_color",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 1.dp else 0.5.dp,
        animationSpec = tween(durationMillis = motion.quick),
        label = "composer_border_width",
    )
    val glowElevation by animateDpAsState(
        targetValue = if (isFocused) 8.dp else 1.5.dp,
        animationSpec = tween(durationMillis = motion.standard),
        label = "composer_glow_elevation",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.35f else 0.05f,
        animationSpec = tween(durationMillis = motion.standard),
        label = "composer_glow_alpha",
    )

    val containerBrush = if (isFocused) {
        Brush.verticalGradient(
            colors = listOf(
                colors.surfaceRaised,
                colors.surface,
            ),
        )
    } else {
        SolidColor(colors.surface)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm)
            .shadow(
                elevation = glowElevation,
                shape = shape,
                ambientColor = if (isFocused) colors.accent.copy(alpha = glowAlpha * 0.4f) else Color.Black.copy(alpha = 0.06f),
                spotColor = if (isFocused) colors.accent.copy(alpha = glowAlpha) else Color.Black.copy(alpha = 0.10f),
            )
            .clip(shape)
            .background(containerBrush, shape)
            .border(borderWidth, borderColor, shape)
            .padding(top = spacing.md, bottom = spacing.sm),
    ) {
        // Multi-line text field with comfortable line height
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = OpenCodeTypography.input.copy(
                color = if (enabled) colors.onSurface else colors.onSurfaceMuted,
                lineHeight = 22.sp,
            ),
            cursorBrush = SolidColor(colors.accent),
            enabled = enabled,
            // The soft keyboard's action key reads "Send" and sends; a plain
            // Enter on a physical keyboard still inserts a newline (sending
            // from hardware keys is Ctrl/Cmd+Enter, handled in ChatScreen).
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .heightIn(min = 28.dp, max = dims.composerMaxHeight)
                .onFocusChanged { isFocused = it.isFocused }
                .then(if (onKeyEvent != null) Modifier.onPreviewKeyEvent(onKeyEvent) else Modifier),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(
                                when {
                                    !enabled -> R.string.composer_not_ready
                                    busy -> R.string.composer_busy
                                    else -> R.string.composer_hint
                                },
                            ),
                            style = OpenCodeTypography.input.copy(lineHeight = 22.sp),
                            color = colors.onSurfaceMuted,
                        )
                    }
                    inner()
                }
            },
        )

        Spacer(modifier = Modifier.height(spacing.xs))
        HairlineSeparator(modifier = Modifier.padding(horizontal = spacing.lg))
        Spacer(modifier = Modifier.height(spacing.xs))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.md, end = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Omnibar shortcuts: the / and @ pills are pinned outside the
            // scrolling row, so the agent/model selectors can scroll on a
            // narrow phone without ever pushing the primary command trigger
            // off-screen. Only the mode/model controls scroll.
            QuickActionChip(
                label = "/",
                glyph = null,
                isActive = value.startsWith("/"),
                enabled = enabled && !busy,
                contentDescription = stringResource(R.string.composer_commands),
                onClick = {
                    if (value.startsWith("/")) {
                        onValueChange(value.removePrefix("/").trimStart())
                    } else {
                        onValueChange("/$value")
                    }
                },
            )

            // The @ chip only exists when a real attach action is wired in.
            // Without onAttachFile it only inserted a bare "@" into the text
            // while wearing a file icon and an "Attach file" label — a fake
            // attach button. No file picker is built here; the chip simply
            // stays hidden until a caller passes onAttachFile.
            if (onAttachFile != null) {
                QuickActionChip(
                    label = "@",
                    glyph = Glyph.File,
                    isActive = false,
                    enabled = enabled && !busy,
                    contentDescription = stringResource(R.string.composer_attach),
                    onClick = { onAttachFile() },
                )
            }

            Spacer(modifier = Modifier.width(spacing.xs))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                controls()
            }

            Spacer(modifier = Modifier.width(spacing.xs))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                AnimatedVisibility(
                    visible = value.isNotEmpty() && enabled && !busy,
                    enter = fadeIn(tween(motion.quick)) + scaleIn(tween(motion.quick), initialScale = 0.8f),
                    exit = fadeOut(tween(motion.quick)) + scaleOut(tween(motion.quick), targetScale = 0.8f),
                ) {
                    ClearButton(
                        onClick = { onValueChange("") },
                    )
                }

                ActionButton(
                    busy = busy,
                    canSend = canSend,
                    hasVoice = onVoice != null && value.isBlank() && !busy,
                    onClick = {
                        when {
                            busy -> onStop()
                            canSend -> onSend()
                            onVoice != null && value.isBlank() -> onVoice()
                        }
                    },
                )
            }
        }
    }
}

/**
 * Omnibar quick action pill button (`/`, `@`).
 */
@Composable
private fun QuickActionChip(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    isActive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val motion = OpenCodeTheme.tokens.motion
    val haptic = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "chip_press_scale",
    )

    val chipShape = RoundedCornerShape(radius.full)

    val targetBg = when {
        isActive -> colors.accent.copy(alpha = 0.14f)
        else -> colors.fill
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = motion.quick),
        label = "chip_bg",
    )

    val targetBorder = when {
        isActive -> colors.accent.copy(alpha = 0.60f)
        else -> colors.hairline
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(durationMillis = motion.quick),
        label = "chip_border",
    )

    val contentColor = when {
        !enabled -> colors.onSurfaceFaint
        isActive -> colors.accent
        else -> colors.onSurfaceMuted
    }

    // Hit area is 40dp; the pill itself stays 28dp, centred inside it, so
    // the chip is easier to tap without the composer controls growing taller.
    Box(
        modifier = modifier
            .height(dims.composerChipTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(28.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(chipShape)
                .background(backgroundColor)
                .border(0.5.dp, borderColor, chipShape)
                .padding(horizontal = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            if (glyph != null) {
                GlyphIcon(
                    glyph = glyph,
                    tint = contentColor,
                    size = 13.dp,
                )
            }
            Text(
                text = label,
                style = OpenCodeTypography.caption.copy(fontFamily = OpenCodeTypography.mono),
                color = contentColor,
            )
        }
    }
}

/**
 * Clear button (dismiss cross) shown when text is present.
 */
@Composable
private fun ClearButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions
    val haptic = LocalHapticFeedback.current
    val label = stringResource(R.string.composer_clear)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "clear_button_scale",
    )

    // Hit area is 40dp; the 28dp circle stays the visual, centred inside it.
    Box(
        modifier = modifier
            .size(dims.composerChipTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = Glyph.Close,
                tint = colors.onSurfaceMuted,
                size = 12.dp,
            )
        }
    }
}

/**
 * Modern Action Button (Send / Stop / Voice / Abort) with dynamic colors,
 * breathing pulse when busy, and spring tactile press feel.
 */
@Composable
private fun ActionButton(
    busy: Boolean,
    canSend: Boolean,
    hasVoice: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions
    val motion = OpenCodeTheme.tokens.motion
    val haptic = LocalHapticFeedback.current

    val active = busy || canSend || hasVoice
    val label = stringResource(
        when {
            busy -> R.string.composer_stop
            canSend -> R.string.composer_send
            hasVoice -> R.string.composer_voice
            else -> R.string.composer_send
        },
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile press feel: snappy spring bounce compression
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "composer_action_press_scale",
    )

    // Breathing pulse during agent execution (Abort / Stop mode)
    val infiniteTransition = rememberInfiniteTransition(label = "busy_pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "busy_pulse_scale",
    )
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "busy_pulse_glow",
    )

    val targetBg = when {
        busy -> colors.danger
        canSend -> colors.accent
        hasVoice -> colors.accent.copy(alpha = 0.15f)
        else -> colors.fill
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = motion.quick),
        label = "composer_action_bg",
    )

    val targetTint = when {
        busy || canSend -> Color.White
        hasVoice -> colors.accent
        // Clearly grey and dim when there is nothing to send, so the accent
        // state above reads as "ready" instead of "slightly different grey".
        else -> colors.onSurfaceFaint
    }
    val iconTint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = tween(durationMillis = motion.quick),
        label = "composer_action_tint",
    )

    val shadowElevation = when {
        busy -> 4.dp
        canSend -> 3.dp
        else -> 0.dp
    }
    val shadowColor = when {
        busy -> colors.danger.copy(alpha = pulseGlow)
        canSend -> colors.accent.copy(alpha = 0.40f)
        else -> Color.Transparent
    }

    val finalScale = pressScale * (if (busy) pulseScale else 1.0f)
    // Disabled state gets a hairline ring so it still reads as a button,
    // just an unavailable one, instead of a flat grey dot.
    val isDisabled = !busy && !canSend && !hasVoice

    // Hit area is 44dp; the 36dp circle stays the visual, centred inside it.
    Box(
        modifier = modifier
            .size(dims.composerActionTouchTarget)
            .semantics { contentDescription = label }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = active,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    scaleX = finalScale
                    scaleY = finalScale
                }
                .shadow(
                    elevation = shadowElevation,
                    shape = CircleShape,
                    ambientColor = shadowColor.copy(alpha = shadowColor.alpha * 0.5f),
                    spotColor = shadowColor,
                )
                .clip(CircleShape)
                .background(backgroundColor)
                .then(
                    if (isDisabled) Modifier.border(0.5.dp, colors.hairline, CircleShape)
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
        Crossfade(
            targetState = when {
                busy -> ActionState.BUSY
                hasVoice -> ActionState.VOICE
                else -> ActionState.SEND
            },
            animationSpec = tween(durationMillis = motion.quick),
            label = "composer_action_icon",
        ) { state ->
            when (state) {
                ActionState.BUSY -> {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .background(iconTint, RoundedCornerShape(2.5.dp)),
                    )
                }
                ActionState.VOICE -> {
                    GlyphIcon(
                        glyph = Glyph.Mic,
                        tint = iconTint,
                        size = 18.dp,
                    )
                }
                ActionState.SEND -> {
                    GlyphIcon(
                        glyph = Glyph.ArrowUp,
                        tint = iconTint,
                        size = dims.iconMedium,
                    )
                }
            }
        }
        }
    }
}

private enum class ActionState {
    BUSY, SEND, VOICE
}
