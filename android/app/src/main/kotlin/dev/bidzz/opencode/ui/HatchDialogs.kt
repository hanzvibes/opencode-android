package dev.bidzz.opencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.AuraPalette
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Hatch confirmation dialogs — clean-room re-implementation of the Meta
 * Muse app's (`com.facebook.aura` 16.0.0.2.159) `DeleteConfirmationDialog`,
 * `RenameDialog` and `TaskDeleteConfirmationDialog`, from their dex class
 * names in `aura-unpack/ANALYSIS.md`. No Aura code, font, or asset is
 * copied: the sheets are our own [LocalizedDraggableBottomSheet] with a
 * Hatch danger dot, Aura text colours and 48dp action buttons.
 *
 * Behaviour is deliberately unchanged from the dialogs these replace:
 * dismissing never confirms, a rename only confirms a non-blank trimmed
 * title, and the field survives a recreate (fold, rotation).
 */

/** A rename only confirms a non-blank title. Pure so it is unit-testable. */
internal fun isValidRenameTitle(text: String): Boolean = text.isNotBlank()

/**
 * Generic Hatch delete confirmation: danger dot + title in the sheet
 * header, the item named in the message (deleting the wrong item is one
 * tap away when every dialog reads the same), Cancel + Delete actions.
 * The confirm button is always 48dp tall (a11y).
 */
@Composable
fun HatchDeleteConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
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
                // Danger dot: Aura danger as a fill, never as text
                // (#D93025 is AA on white only, 4.45:1 on the Aura page).
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(AuraPalette.auraDanger, CircleShape),
                )
                Text(
                    text = title,
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
        },
        content = {
            Text(
                text = message,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
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
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.sizeIn(minHeight = 48.dp),
            ) {
                Text(
                    text = confirmLabel,
                    style = OpenCodeTypography.bodyStrong,
                    color = colors.danger,
                )
            }
        },
    )
}

/**
 * Generic Hatch rename sheet: the same field behaviour as the old
 * session rename (focus on open, Done confirms, survives recreate),
 * with the Hatch title style and a 48dp Save.
 */
@Composable
fun HatchRenameDialog(
    initialTitle: String,
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    // Audit-200 A2 (C-006, PROVEN): TextFieldValue needs its Saver —
    // without it, backgrounding the app with this dialog open crashes
    // in onSaveInstanceState (IllegalStateException from performSave).
    var tfv by rememberSaveable(initialTitle, stateSaver = TextFieldValue.Saver) {
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

    LocalizedDraggableBottomSheet(
        onDismiss = onDismiss,
        initialStop = SheetStop.HALF,
        title = {
            Text(
                text = title,
                style = OpenCodeTypography.title,
                color = colors.onSurface,
            )
        },
        content = {
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
                            if (isValidRenameTitle(tfv.text)) {
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
                                text = hint,
                                style = OpenCodeTypography.body,
                                color = colors.onSurfaceFaint,
                            )
                        }
                        inner()
                    },
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
            TextButton(
                onClick = { onConfirm(tfv.text.trim()) },
                enabled = isValidRenameTitle(tfv.text),
                modifier = Modifier.sizeIn(minHeight = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_action_save),
                    style = OpenCodeTypography.bodyStrong,
                    // The enabled Save label is text — accent is only
                    // 4.02:1 on white in light.
                    color = if (isValidRenameTitle(tfv.text)) colors.accentText else colors.onSurfaceFaint,
                )
            }
        },
    )
}

/**
 * Hatch delete confirmation for a background task / job
 * ([TaskDeleteConfirmationDialog] in Aura). There is no delete-task
 * action in the app yet — Jobs has no delete API — so this has no call
 * site in Batch 1; it is ready for one, with its own strings.
 */
@Composable
fun HatchTaskDeleteConfirmationDialog(
    taskTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    HatchDeleteConfirmationDialog(
        title = stringResource(R.string.task_delete_title),
        message = stringResource(R.string.task_delete_message, taskTitle),
        confirmLabel = stringResource(R.string.common_delete),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
