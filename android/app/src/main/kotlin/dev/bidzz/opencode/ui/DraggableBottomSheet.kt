package dev.bidzz.opencode.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.bidzz.opencode.core.design.OpenCodeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Opsi C — custom draggable bottom sheet, the one component every dialog
 * in the app now opens in (see the call sites that used to call
 * [LocalizedAlertDialog]).
 *
 * Three stops — [SheetStop.PEEK], [SheetStop.HALF], [SheetStop.FULL] —
 * as fractions of the available height ([sheetStopHeights]). The drag
 * handle (and the title row) can be dragged between stops; a fast fling
 * moves one stop, a slow release snaps to the nearest stop, and dragging
 * below the Peek stop dismisses. The scrim and the back button dismiss
 * too. Dragging, the scrim and back NEVER confirm anything: a dismiss is
 * always just `onDismiss`, exactly like closing an AlertDialog.
 *
 * This is deliberately NOT `ModalBottomSheet`: the sheet is a plain
 * [Dialog] with `usePlatformDefaultWidth = false` so it can be full-width
 * on a phone and capped at 640dp, centred, on a tablet (the sidebar is
 * permanent at >= 840dp, so a full-width sheet there would be absurd).
 * The snap rules live in SheetSnap.kt as pure Kotlin and are unit-tested;
 * this file only animates a height.
 *
 * Keyboard: the sheet sits in `ime ∪ navigationBars` insets, so a text
 * field (Rename, KeyPrompt, Custom Provider) is never covered by the
 * keyboard — the sheet shrinks and rides up with it, and the content
 * scrolls.
 *
 * Language: like [LocalizedAlertDialog], the content is wrapped in
 * [Localized], because a dialog is a separate window whose compose root
 * resets LocalContext/LocalConfiguration to the phone's language.
 * Without that wrapper every sheet would render in the phone's language
 * instead of the app's chosen one.
 */

/** Same API as [LocalizedAlertDialog], but the container is a draggable sheet. */
@Composable
fun LocalizedDraggableBottomSheet(
    onDismiss: () -> Unit,
    initialStop: SheetStop = SheetStop.HALF,
    title: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
    actions: (@Composable () -> Unit)? = null,
) {
    Localized {
        DraggableBottomSheet(
            onDismiss = onDismiss,
            initialStop = initialStop,
            title = title,
            content = content,
            actions = actions,
        )
    }
}

/** The sheet itself, without the app-language wrapper. */
@Composable
fun DraggableBottomSheet(
    onDismiss: () -> Unit,
    initialStop: SheetStop = SheetStop.HALF,
    title: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
    actions: (@Composable () -> Unit)? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val motion = OpenCodeTheme.tokens.motion
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var currentStop by remember { mutableStateOf(initialStop) }
    var dismissing by remember { mutableStateOf(false) }
    // The sheet's height in pixels. Starts at 0 so the sheet slides up
    // on open; every stop change animates it (see the LaunchedEffect).
    val heightAnim = remember { Animatable(0f) }

    fun dismiss() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            heightAnim.animateTo(
                0f,
                tween(durationMillis = motion.quick, easing = motion.easingQuick),
            )
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // ime ∪ navigationBars (a union, not a sum): when the keyboard is
        // up its insets already cover the navigation bar, so adding both
        // would float the sheet too high.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val maxHeightPx = with(density) { maxHeight.toPx() }
                val stopHeights = remember(maxHeightPx) { sheetStopHeights(maxHeightPx) }

                // Entry animation and every snap: animate to the target stop
                // whenever the stop or the available height (rotation,
                // keyboard) changes. Dragging writes to heightAnim directly
                // and never changes currentStop, so this never fights a drag.
                LaunchedEffect(currentStop, maxHeightPx) {
                    if (!dismissing) {
                        heightAnim.animateTo(
                            stopHeights[currentStop.ordinal],
                            tween(
                                durationMillis = motion.standard,
                                easing = motion.easingStandard,
                            ),
                        )
                    }
                }

                val dragState = rememberDraggableState { delta ->
                    // Dragging down shrinks the sheet, dragging up grows it.
                    // snapTo cancels a running snap animation, so grabbing
                    // the handle mid-animation never fights it.
                    scope.launch {
                        heightAnim.snapTo(
                            (heightAnim.value - delta).coerceIn(0f, stopHeights.last()),
                        )
                    }
                }
                val onDragStopped: suspend CoroutineScope.(Float) -> Unit = { velocity ->
                        when (val snap = resolveSheetSnap(heightAnim.value, velocity, stopHeights)) {
                            is SheetSnap.Stop -> currentStop = SheetStop.entries[snap.index]
                            SheetSnap.Dismiss -> dismiss()
                        }
                    }
                val dragModifier = Modifier.draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    enabled = !dismissing,
                    onDragStopped = onDragStopped,
                )

                // Scrim: tap to dismiss (never to confirm).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { dismiss() },
                        ),
                )

                // The sheet. A no-op clickable so taps on empty sheet area
                // don't fall through to the scrim and dismiss under the user.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .height(with(density) { heightAnim.value.toDp() })
                        .clip(
                            RoundedCornerShape(
                                topStart = radius.md,
                                topEnd = radius.md,
                            ),
                        )
                        .background(colors.surfaceRaised)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(colors.hairline),
                        )
                        // Drag handle: the grabbable strip. Tapping it does
                        // nothing; dragging it moves between stops.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(dragModifier)
                                .padding(vertical = spacing.sm),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(radius.full))
                                    .background(colors.onSurfaceFaint),
                            )
                        }
                        if (title != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(dragModifier)
                                    .padding(horizontal = spacing.lg),
                            ) {
                                title()
                            }
                        }
                        // Content scrolls when it is taller than the stop;
                        // long forms (Custom Provider, Manage Credentials)
                        // are also one drag away from Full.
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = spacing.lg, vertical = spacing.sm),
                        ) {
                            content()
                        }
                        if (actions != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = spacing.md,
                                        end = spacing.md,
                                        top = spacing.xs,
                                        bottom = spacing.sm,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(
                                    spacing.sm,
                                    Alignment.End,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                actions()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Union of two insets (the max of each side), written out by hand:
 * the Compose version pinned by this project (BOM 2024.12.01) has
 * neither `WindowInsets.union` nor the `+` operator — both fail CI
 * with "Unresolved reference" — so the union is built from the four
 * `WindowInsets` getters directly. A union, not a sum: when the
 * keyboard is up its insets already cover the navigation bar, so
 * adding both would float the sheet too high.
 */
private fun WindowInsets.union(other: WindowInsets): WindowInsets {
    val first = this
    return object : WindowInsets {
        override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int =
            maxOf(first.getLeft(density, layoutDirection), other.getLeft(density, layoutDirection))

        override fun getTop(density: Density): Int =
            maxOf(first.getTop(density), other.getTop(density))

        override fun getRight(density: Density, layoutDirection: LayoutDirection): Int =
            maxOf(first.getRight(density, layoutDirection), other.getRight(density, layoutDirection))

        override fun getBottom(density: Density): Int =
            maxOf(first.getBottom(density), other.getBottom(density))
    }
}
