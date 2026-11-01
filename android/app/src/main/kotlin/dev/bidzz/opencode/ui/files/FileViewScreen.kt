package dev.bidzz.opencode.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/** State of one open file. */
data class FileViewState(
    val loading: Boolean = true,
    val path: String = "",
    val content: String = "",
    val error: String? = null,
    /** True while the content is in an editable field. */
    val editing: Boolean = false,
    /** What is in the field. Only meaningful while [editing]. */
    val draft: String = "",
    val saving: Boolean = false,
    /** The file changed underneath the editor, so the draft was not written. */
    val conflict: Boolean = false,
    /** Set after a write landed, cleared by the next edit. */
    val saved: Boolean = false,
) {
    val dirty: Boolean get() = editing && draft != content
}

/**
 * File view, with editing.
 *
 * Saving re-reads the file first and refuses to write if the agent changed it in the
 * meantime, because the engine's write endpoint replaces the whole file — see
 * [saveAction]. The draft is kept through a failed save for the same reason: the text
 * is the user's, and the engine gets to be retried.
 */
@Composable
fun FileViewScreen(
    state: FileViewState,
    onBack: () -> Unit,
    onStartEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onOverwrite: () -> Unit,
    onReloadFromDisk: () -> Unit,
    onDismissConflict: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    val shown = if (state.editing) state.draft else state.content

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(radius.sm))
                    .clickable(onClick = onBack)
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
            ) {
                Text("Back", style = OpenCodeTypography.label, color = colors.accent)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.md),
            ) {
                Text(
                    text = state.path.substringAfterLast('/'),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                )
                if (shown.isNotEmpty()) {
                    Text(
                        text = shown.lines().size.toString() + " lines" +
                            if (state.dirty) " · unsaved" else "",
                        style = OpenCodeTypography.label,
                        color = if (state.dirty) colors.accent else colors.onSurfaceFaint,
                    )
                }
            }
            if (state.editing) {
                HeaderAction(
                    text = if (state.saving) "Saving…" else "Save",
                    enabled = state.dirty && !state.saving,
                    onClick = onSave,
                )
                HeaderAction(text = "Cancel", enabled = !state.saving, onClick = onCancelEdit)
            } else if (!state.loading && state.error == null) {
                HeaderAction(text = "Edit", enabled = true, onClick = onStartEdit)
            }
        }

        when {
            state.loading -> Centered("Reading file…")
            state.editing -> Column(Modifier.fillMaxSize()) {
                state.error?.let {
                    Text(
                        text = it,
                        style = OpenCodeTypography.bodySmall,
                        color = colors.danger,
                        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                    )
                }
                BasicTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    textStyle = OpenCodeTypography.code.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.None,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.md),
                )
            }

            state.error != null -> Centered(state.error)
            else -> SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.md),
                ) {
                    if (state.saved) {
                        Text(
                            text = "Saved",
                            style = OpenCodeTypography.label,
                            color = colors.success,
                            modifier = Modifier.padding(bottom = spacing.sm),
                        )
                    }
                    Text(
                        text = state.content,
                        style = OpenCodeTypography.code,
                        color = colors.onSurface,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }

    if (state.conflict && state.editing) {
        AlertDialog(
            onDismissRequest = onDismissConflict,
            title = {
                Text("File changed while editing", style = OpenCodeTypography.title, color = colors.onSurface)
            },
            text = {
                Text(
                    text = "The file on disk is no longer the version this editor was opened with — " +
                        "the agent has probably written it. Nothing has been saved yet.\n\n" +
                        "Overwriting replaces the file with what is typed here. Reloading shows what is " +
                        "on disk and discards what is typed here.",
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = onOverwrite) { Text("Overwrite", color = colors.danger) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TextButton(onClick = onReloadFromDisk) { Text("Reload", color = colors.onSurfaceMuted) }
                    TextButton(onClick = onDismissConflict) { Text("Keep editing", color = colors.accent) }
                }
            },
        )
    }
}

@Composable
private fun HeaderAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.sm))
            .background(if (enabled) colors.surfaceSunken else colors.surface)
            .border(0.5.dp, colors.outline, RoundedCornerShape(radius.sm))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
    ) {
        Text(
            text = text,
            style = OpenCodeTypography.label,
            color = if (enabled) colors.accent else colors.onSurfaceFaint,
        )
    }
}

@Composable
private fun Centered(text: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = OpenCodeTypography.body,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(spacing.xxl),
        )
    }
}
