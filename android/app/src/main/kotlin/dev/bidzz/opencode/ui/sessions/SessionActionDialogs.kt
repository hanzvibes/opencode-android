package dev.bidzz.opencode.ui.sessions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.LocalizedAlertDialog

@Composable
fun SessionActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isPinned: Boolean,
    onPinToggle: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(0.dp, spacing.xs),
        shape = RoundedCornerShape(radius.md),
        containerColor = colors.surfaceRaised,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
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
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    var tfv by remember(initialTitle) {
        mutableStateOf(
            TextFieldValue(
                text = initialTitle,
                selection = TextRange(0, initialTitle.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.session_rename_title),
                style = OpenCodeTypography.title,
                color = colors.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = tfv,
                    onValueChange = { tfv = it },
                    singleLine = true,
                    textStyle = OpenCodeTypography.body.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (tfv.text.isNotBlank()) {
                                onConfirm(tfv.text.trim())
                            }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm)
                        .clip(RoundedCornerShape(radius.md))
                        .background(colors.surfaceSunken)
                        .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.md))
                        .focusRequester(focusRequester)
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    decorationBox = { inner ->
                        if (tfv.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.session_rename_hint),
                                style = OpenCodeTypography.body,
                                color = colors.onSurfaceFaint,
                            )
                        }
                        inner()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(tfv.text.trim()) },
                enabled = tfv.text.isNotBlank(),
            ) {
                Text(
                    text = stringResource(R.string.session_action_save),
                    style = OpenCodeTypography.bodyStrong,
                    color = if (tfv.text.isNotBlank()) colors.accent else colors.onSurfaceFaint,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.session_action_cancel),
                    style = OpenCodeTypography.body,
                    color = colors.onSurfaceMuted,
                )
            }
        },
    )
}

@Composable
fun DeleteSessionDialog(
    sessionTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = OpenCodeTheme.colors

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.session_delete_title),
                style = OpenCodeTypography.title,
                color = colors.onSurface,
            )
        },
        text = {
            // The title is shown, not just passed in: deleting the wrong
            // session is one tap away when every dialog reads the same.
            Text(
                text = stringResource(R.string.session_delete_message, sessionTitle),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.session_action_delete),
                    style = OpenCodeTypography.bodyStrong,
                    color = colors.danger,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.session_action_cancel),
                    style = OpenCodeTypography.body,
                    color = colors.onSurfaceMuted,
                )
            }
        },
    )
}
