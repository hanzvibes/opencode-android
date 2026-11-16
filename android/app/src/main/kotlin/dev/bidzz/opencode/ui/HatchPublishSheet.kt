package dev.bidzz.opencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.AuraPalette
import dev.bidzz.opencode.core.design.Palette
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Hatch publish sheets — clean-room re-implementation of the Meta Muse
 * app's (`com.facebook.aura` 16.0.0.2.159) `HatchPublish*BottomSheet`
 * family (Publish / Success / Failed / UpToDate / Unpublished), from
 * their dex class names in `aura-unpack/ANALYSIS.md`. No Aura code,
 * font, or asset is copied: the sheet is our own
 * [LocalizedDraggableBottomSheet] with an Aura status band and dot.
 *
 * Honest scope note: the app has no publish action yet — artifacts are
 * "published" by the engine, never by a tap in the app — so Batch 1
 * ships the sheet as a component with its state mapping unit-tested,
 * and no forced call site. The first real caller will be whichever
 * screen gains a publish action.
 */

/** The five publish states, in Aura's order. */
enum class HatchPublishState {
    PUBLISH,
    SUCCESS,
    FAILED,
    UP_TO_DATE,
    UNPUBLISHED,
}

/**
 * The status dot for a state. Dots are decoration, never text, so the
 * bar is the 3:1 graphics ratio, not 4.5:1 — but the light fills used
 * raw in dark were all 3.47–3.71:1 on the dark sheet and, worse, the
 * muted/faint order inverted there (faint 5.80 brighter than muted
 * 3.47). Audit-200 D5 (m-675): the dark fills are brighter — blue
 * #5890FF (5.55), green #30D158 (8.42), danger #FF453A (4.99),
 * muted #9A9AA0 (6.08), faint #9197A3 (5.80) — so the order survives
 * and every dot clears 3:1 on the dark sheet #1C1C1E. Pure so it is
 * unit-testable.
 */
fun HatchPublishState.dotColor(isDark: Boolean): Color = when (this) {
    HatchPublishState.PUBLISH -> if (isDark) AuraPalette.auraBlue else AuraPalette.auraBlueFill
    HatchPublishState.SUCCESS -> if (isDark) Palette.success else AuraPalette.auraSuccess
    HatchPublishState.FAILED -> if (isDark) Palette.danger else AuraPalette.auraDanger
    HatchPublishState.UP_TO_DATE -> if (isDark) Palette.darkOnSurfaceFaint else AuraPalette.auraMuted
    HatchPublishState.UNPUBLISHED -> AuraPalette.auraFaint
}

/** The user-visible label for a state. Pure so it is unit-testable. */
fun hatchPublishStateLabelRes(state: HatchPublishState): Int = when (state) {
    HatchPublishState.PUBLISH -> R.string.publish_state_publish
    HatchPublishState.SUCCESS -> R.string.publish_state_success
    HatchPublishState.FAILED -> R.string.publish_state_failed
    HatchPublishState.UP_TO_DATE -> R.string.publish_state_up_to_date
    HatchPublishState.UNPUBLISHED -> R.string.publish_state_unpublished
}

/**
 * The default title for a state (Audit-200 D1, m-696): a sheet called
 * with no title used to render an empty title line. Pure so it is
 * unit-testable, like [hatchPublishStateLabelRes].
 */
fun hatchPublishTitleRes(state: HatchPublishState): Int = when (state) {
    HatchPublishState.PUBLISH -> R.string.publish_title_publish
    HatchPublishState.SUCCESS -> R.string.publish_title_success
    HatchPublishState.FAILED -> R.string.publish_title_failed
    HatchPublishState.UP_TO_DATE -> R.string.publish_title_up_to_date
    HatchPublishState.UNPUBLISHED -> R.string.publish_title_unpublished
}

/** The default message for a state (Audit-200 D1, m-696). */
fun hatchPublishMessageRes(state: HatchPublishState): Int = when (state) {
    HatchPublishState.PUBLISH -> R.string.publish_message_publish
    HatchPublishState.SUCCESS -> R.string.publish_message_success
    HatchPublishState.FAILED -> R.string.publish_message_failed
    HatchPublishState.UP_TO_DATE -> R.string.publish_message_up_to_date
    HatchPublishState.UNPUBLISHED -> R.string.publish_message_unpublished
}

/**
 * The dismiss button's label for a state (Audit-200 D9, m-693/m-913):
 * "Cancel" is only honest while something is in flight. On success or
 * when already up to date the sheet just closes a finished job
 * ("Done"); on failure or when nothing was published it only closes
 * ("Close" — Retry, if any, is the primary action). Pure so it is
 * unit-testable, like [hatchPublishStateLabelRes].
 */
fun hatchPublishDismissLabelRes(state: HatchPublishState): Int = when (state) {
    HatchPublishState.PUBLISH -> R.string.session_action_cancel
    HatchPublishState.SUCCESS, HatchPublishState.UP_TO_DATE -> R.string.common_done
    HatchPublishState.FAILED, HatchPublishState.UNPUBLISHED -> R.string.common_close
}

/**
 * A publish-state sheet: a 4dp status band on top (mesh gradient while
 * publishing / on success, danger while failed, hairline otherwise),
 * the state label with its dot, then title + message and an optional
 * primary action. Actions are 48dp tall (a11y).
 *
 * [title] and [message] default to the per-state text (D1 m-696): a
 * caller that passes nothing — or an empty string — gets a sheet that
 * still says what happened, never a bare dot and band.
 *
 * Audit-200 D15 (m-692): [dismissable] defaults to false while a
 * publish is in flight (`state == PUBLISH`) — closing the sheet then
 * does NOT cancel the publish, so the sheet simply stays: the scrim,
 * the back button and a dismiss drag are ignored, and the Cancel
 * button is hidden. A dismiss is never a cancel; the first real caller
 * must wire its own cancellation, not rely on the sheet closing.
 */
@Composable
fun HatchPublishBottomSheet(
    state: HatchPublishState,
    title: String? = null,
    message: String? = null,
    onDismiss: () -> Unit,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    dismissable: Boolean = state != HatchPublishState.PUBLISH,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LocalizedDraggableBottomSheet(
        onDismiss = onDismiss,
        dismissable = dismissable,
        initialStop = SheetStop.PEEK,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(state.dotColor(colors.isDark), CircleShape),
                )
                Text(
                    text = stringResource(hatchPublishStateLabelRes(state)),
                    style = OpenCodeTypography.labelSmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Status band: mesh while publishing / on success, danger
                // while failed, a hairline for the quiet states.
                when (state) {
                    HatchPublishState.PUBLISH, HatchPublishState.SUCCESS ->
                        AuraMeshGradient(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                        )
                    HatchPublishState.FAILED ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(AuraPalette.auraDanger),
                        )
                    HatchPublishState.UP_TO_DATE, HatchPublishState.UNPUBLISHED ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.hairline),
                        )
                }
                Text(
                    text = title?.takeIf { it.isNotBlank() } ?: stringResource(hatchPublishTitleRes(state)),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = spacing.sm),
                )
                Text(
                    text = message?.takeIf { it.isNotBlank() } ?: stringResource(hatchPublishMessageRes(state)),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        },
        actions = {
            // Audit-200 D15 (m-692): no dismiss button while the sheet is
            // locked — a "Cancel" button that does nothing would be worse
            // than none, and dismissing is not cancelling the publish.
            if (dismissable) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) {
                    Text(
                        text = stringResource(hatchPublishDismissLabelRes(state)),
                        style = OpenCodeTypography.body,
                        color = colors.onSurfaceMuted,
                    )
                }
            }
            if (primaryLabel != null && onPrimary != null) {
                TextButton(
                    onClick = onPrimary,
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) {
                    Text(
                        text = primaryLabel,
                        style = OpenCodeTypography.bodyStrong,
                        // Aura blue text is AA on white / the Aura page, but
                    // dark-on-dark in the dark theme — there the app's
                    // own accent text stays.
                    color = if (state == HatchPublishState.FAILED) {
                            colors.danger
                        } else if (colors.isDark) {
                            colors.accentText
                        } else {
                            AuraPalette.auraBlueText
                        },
                    )
                }
            }
        },
    )
}
