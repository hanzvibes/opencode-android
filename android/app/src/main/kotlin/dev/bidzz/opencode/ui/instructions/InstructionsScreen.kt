package dev.bidzz.opencode.ui.instructions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.InstructionEntry
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.message

/**
 * Per-session instruction entries: list, add, edit, delete.
 *
 * The editor sends the value as a JSON string; a saved entry is
 * announced to the model at the next step boundary. Tapping a row
 * loads it into the editor, where saving replaces it. A key that does
 * not match the engine's pattern and a value over the 256 KB maximum
 * are both reported in words, not as engine error bodies.
 */
@Composable
fun InstructionsScreen(
    sessionID: String,
    state: InstructionsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onEdit: (InstructionEntry) -> Unit,
    onDelete: (InstructionEntry) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    LaunchedEffect(sessionID) { onRefresh() }

    ScreenScaffold(
        title = stringResource(R.string.instructions_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.instructions_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            // Editor: key on one line, value below it, Save on the right.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg),
            ) {
                EditorField(
                    value = state.key,
                    onValueChange = onKeyChange,
                    hintRes = R.string.instructions_key_hint,
                    singleLine = true,
                )
                EditorField(
                    value = state.value,
                    onValueChange = onValueChange,
                    hintRes = R.string.instructions_value_hint,
                    singleLine = false,
                    modifier = Modifier.padding(top = spacing.sm),
                )
                if (state.keyInvalid) {
                    Text(
                        text = stringResource(R.string.instructions_key_invalid),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.danger,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
                if (state.tooLarge) {
                    Text(
                        text = stringResource(R.string.instructions_too_large),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.danger,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
                // A load failure with entries already on screen stays an inline
                // note under the editor (the list is still usable); a failure
                // with an empty list is the list's own state, with retry
                // (audit-100 64-F15 — the old code showed the red note AND
                // the "no instructions" empty state at once).
                if (state.failure != null && state.entries.isNotEmpty()) {
                    Text(
                        text = state.failure.message(),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.danger,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (state.saving) stringResource(R.string.common_loading) else "",
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onSave,
                        enabled = state.key.isNotBlank() && !state.saving,
                    ) {
                        Text(
                            text = stringResource(R.string.instructions_save),
                            style = OpenCodeTypography.label,
                            color = if (state.key.isNotBlank() && !state.saving) {
                                colors.accent
                            } else {
                                colors.onSurfaceFaint
                            },
                        )
                    }
                }
            }
            when {
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.failure != null && state.entries.isEmpty() -> EmptyState(state.failure.message(), isError = true, onRetry = onRefresh)
                state.entries.isEmpty() -> EmptyState(stringResource(R.string.instructions_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(
                            state.entries,
                            key = { index, entry -> "$index:${entry.key}" },
                        ) { index, entry ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            EntryRow(
                                entry = entry,
                                onEdit = { onEdit(entry) },
                                onDelete = { onDelete(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One text field in the editor: neutral fill, hairline edge, accent cursor. */
@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    hintRes: Int,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(radius.md))
            .background(colors.fill)
            .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.md))
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            textStyle = OpenCodeTypography.body.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(hintRes),
                        style = OpenCodeTypography.body,
                        color = colors.onSurfaceFaint,
                    )
                }
                inner()
            },
        )
    }
}

/** One entry: key + value, tap to edit, trash to delete. */
@Composable
private fun EntryRow(
    entry: InstructionEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(role = Role.Button, onClick = onEdit)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = spacing.xs),
        ) {
            Text(
                text = entry.key,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val valueText = instructionValueText(entry.value)
            if (valueText.isNotBlank()) {
                Text(
                    text = valueText,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }
        }
        // Hoisted: stringResource() is @Composable and cannot be called
        // inside a .semantics {} block.
        val deleteDescription = stringResource(R.string.common_delete)
        Box(
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onDelete)
                // A11y (#8a): an icon-only button with no name announces
                // just "Button" — the entry text before it is the context.
                .semantics { contentDescription = deleteDescription },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = Glyph.Trash,
                tint = colors.danger,
                size = 16.dp,
                modifier = Modifier.padding(spacing.xs),
            )
        }
    }
}
