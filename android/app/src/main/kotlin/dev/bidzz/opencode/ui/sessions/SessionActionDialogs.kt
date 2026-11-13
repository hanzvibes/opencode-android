package dev.bidzz.opencode.ui.sessions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.HatchDeleteConfirmationDialog
import dev.bidzz.opencode.ui.HatchRenameDialog

@Composable
fun SessionActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isPinned: Boolean,
    onPinToggle: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    /** Null hides the entry: only callers that can open the screen pass one. */
    onInboxClick: (() -> Unit)? = null,
    onMoveClick: (() -> Unit)? = null,
    onExportClick: (() -> Unit)? = null,
    onInstructionsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    val elevation = OpenCodeTheme.tokens.elevation

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(0.dp, spacing.xs),
        shape = RoundedCornerShape(radius.md),
        containerColor = colors.surfaceRaised,
        tonalElevation = 0.dp,
        shadowElevation = elevation.menu,
        border = BorderStroke(0.5.dp, colors.hairline),
        modifier = modifier.widthIn(min = 170.dp),
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(if (isPinned) R.string.session_action_unpin else R.string.session_action_pin),
                    style = OpenCodeTypography.body,
                    color = colors.onSurface,
                )
            },
            leadingIcon = {
                GlyphIcon(
                    glyph = Glyph.Pin,
                    tint = if (isPinned) colors.accent else colors.onSurfaceMuted,
                    size = dims.iconSmall,
                )
            },
            onClick = {
                onDismissRequest()
                onPinToggle()
            },
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.session_action_rename),
                    style = OpenCodeTypography.body,
                    color = colors.onSurface,
                )
            },
            leadingIcon = {
                GlyphIcon(
                    glyph = Glyph.Pencil,
                    tint = colors.onSurfaceMuted,
                    size = dims.iconSmall,
                )
            },
            onClick = {
                onDismissRequest()
                onRenameClick()
            },
        )

        if (onInboxClick != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.session_action_inbox),
                        style = OpenCodeTypography.body,
                        color = colors.onSurface,
                    )
                },
                leadingIcon = {
                    GlyphIcon(
                        glyph = Glyph.Mail,
                        tint = colors.onSurfaceMuted,
                        size = dims.iconSmall,
                    )
                },
                onClick = {
                    onDismissRequest()
                    onInboxClick()
                },
            )
        }

        if (onMoveClick != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.session_action_move),
                        style = OpenCodeTypography.body,
                        color = colors.onSurface,
                    )
                },
                leadingIcon = {
                    GlyphIcon(
                        glyph = Glyph.Folder,
                        tint = colors.onSurfaceMuted,
                        size = dims.iconSmall,
                    )
                },
                onClick = {
                    onDismissRequest()
                    onMoveClick()
                },
            )
        }

        if (onExportClick != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.session_action_export),
                        style = OpenCodeTypography.body,
                        color = colors.onSurface,
                    )
                },
                leadingIcon = {
                    GlyphIcon(
                        glyph = Glyph.Copy,
                        tint = colors.onSurfaceMuted,
                        size = dims.iconSmall,
                    )
                },
                onClick = {
                    onDismissRequest()
                    onExportClick()
                },
            )
        }

        if (onInstructionsClick != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.session_action_instructions),
                        style = OpenCodeTypography.body,
                        color = colors.onSurface,
                    )
                },
                leadingIcon = {
                    GlyphIcon(
                        glyph = Glyph.Pencil,
                        tint = colors.onSurfaceMuted,
                        size = dims.iconSmall,
                    )
                },
                onClick = {
                    onDismissRequest()
                    onInstructionsClick()
                },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.xxs)
                .height(0.5.dp)
                .background(colors.hairline),
        )

        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.session_action_delete),
                    style = OpenCodeTypography.body,
                    color = colors.danger,
                )
            },
            leadingIcon = {
                GlyphIcon(
                    glyph = Glyph.Trash,
                    tint = colors.danger,
                    size = dims.iconSmall,
                )
            },
            onClick = {
                onDismissRequest()
                onDeleteClick()
            },
        )
    }
}

@Composable
fun RenameSessionDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // Batch 1 (Aura full, Opsi B): the Hatch rename sheet. Same strings,
    // same behaviour — focus on open, Done confirms, the field survives
    // a recreate (69-F7) — only the presentation moved to HatchDialogs.
    HatchRenameDialog(
        initialTitle = initialTitle,
        title = stringResource(R.string.session_rename_title),
        hint = stringResource(R.string.session_rename_hint),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
fun DeleteSessionDialog(
    sessionTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    // Batch 1 (Aura full, Opsi B): the Hatch delete sheet. Same strings
    // and behaviour — the title is shown, not just passed in, because
    // deleting the wrong session is one tap away when every dialog
    // reads the same.
    HatchDeleteConfirmationDialog(
        title = stringResource(R.string.session_delete_title),
        message = stringResource(R.string.session_delete_message, sessionTitle),
        confirmLabel = stringResource(R.string.session_action_delete),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
