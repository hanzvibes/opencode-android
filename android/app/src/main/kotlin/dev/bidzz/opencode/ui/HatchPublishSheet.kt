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
 * The status dot for a state. Dots are decoration, never text, so
 * [AuraPalette.auraFaint] is fine for [HatchPublishState.UNPUBLISHED]
 * even though it fails AA as text.
 */
fun HatchPublishState.dotColor(): Color = when (this) {
    HatchPublishState.PUBLISH -> AuraPalette.auraBlueFill
    HatchPublishState.SUCCESS -> AuraPalette.auraSuccess
    HatchPublishState.FAILED -> AuraPalette.auraDanger
    HatchPublishState.UP_TO_DATE -> AuraPalette.auraMuted
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
 * A publish-state sheet: a 4dp status band on top (mesh gradient while
 * publishing / on success, danger while failed, hairline otherwise),
 * the state label with its dot, then title + message and an optional
 * primary action. Actions are 48dp tall (a11y).
 */
@Composable
fun HatchPublishBottomSheet(
    state: HatchPublishState,
    title: String,
    message: String,
    onDismiss: () -> Unit,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LocalizedDraggableBottomSheet(
        onDismiss = onDismiss,
        initialStop = SheetStop.PEEK,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(state.dotColor(), CircleShape),
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
                    text = title,
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = spacing.sm),
                )
                Text(
                    text = message,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        },
        actions = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minHeight = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_action_cancel),
                    style = OpenCodeTypography.body,
                    color = colors.onSurfaceMuted,
                )
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
