package dev.bidzz.opencode.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import dev.bidzz.opencode.core.design.AuraPalette
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Batch 4 (Aura full, Opsi B): file actions in Aura style — the edit
 * button and the preview-unavailable card.
 *
 * [HatchEditFileButton] is [TextAction] in Aura's voice: same 48dp
 * touch target and tactile press scale, but the label is Aura's
 * text-safe blue ([AuraPalette.auraBlueText], 7.89:1 on white) instead
 * of the Muse B accent. It wires into the one place the app offers an
 * edit action, the file viewer's top bar.
 *
 * [HatchFilePreviewUnavailableCard] is the failed preview's face: the
 * same message the viewer already showed, now as an Aura card around
 * the Batch 2 error state instead of a bare error line.
 */

/**
 * Pure: whether the file viewer's Edit action is shown. Mirrors the
 * top-bar condition in [FileViewScreen]: view mode (not a preview),
 * fully loaded, no failure, and not a truncated preview — editing a
 * truncated preview is blocked because a save would write the preview
 * over the real file.
 */
fun hatchEditFileButtonVisible(
    isPreview: Boolean,
    loading: Boolean,
    hasFailure: Boolean,
    hasFailureKey: Boolean,
    truncated: Boolean,
): Boolean = !isPreview && !loading && !hasFailure && !hasFailureKey && !truncated

/**
 * An Aura edit action for a top bar or a row of links: accent-blue
 * label, at least 48dp touch target with tactile press — the same
 * contract as [TextAction], in Aura's text-safe blue.
 */
@Composable
fun HatchEditFileButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val motion = OpenCodeTheme.tokens.motion
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = motion.springBouncy(),
        label = "hatch_edit_file_button_scale",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = dims.minTouchTarget, minHeight = dims.minTouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.md))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.label,
            // Aura blue as text is only AA in its darkened form:
            // auraBlueText (7.89:1 on white). Bright auraBlue (3.07:1)
            // is dots/fills only, never text.
            color = if (enabled) AuraPalette.auraBlueText
            else OpenCodeTheme.colors.onSurfaceFaint,
        )
    }
}

/**
 * The card shown when a file preview cannot open (corrupt image,
 * empty PDF, undecodable file): an Aura card around the Batch 2
 * error state, with an optional retry. The viewer wires this into
 * both failed preview branches, replacing the bare error line.
 */
@Composable
fun HatchFilePreviewUnavailableCard(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    HatchSurface(modifier = modifier) {
        HatchErrorState(message = message, onRetry = onRetry)
    }
}
