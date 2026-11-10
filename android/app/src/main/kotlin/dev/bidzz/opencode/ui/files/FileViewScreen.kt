package dev.bidzz.opencode.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.design.OpenCodeColors
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.LocalizedAlertDialog
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/** State of one open file. */
data class FileViewState(
    val loading: Boolean = true,
    val path: String = "",
    val content: String = "",
    val failure: ApiResult.Failure? = null,
    val failureKey: Int? = null,
    val editing: Boolean = false,
    val draft: String = "",
    val saving: Boolean = false,
    val conflict: Boolean = false,
    val saved: Boolean = false,
) {
    val dirty: Boolean get() = editing && draft != content
}

private val CodeLineHeight = 20.sp

/**
 * Code viewer and editor with:
 * - Line number gutter synced to scroll
 * - Syntax highlighting for keywords, strings, numbers, and comments
 * - Unsaved changes indicator and discard safeguards
 * - Breadcrumb path display
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

    var showDiscardDialog by remember { mutableStateOf(false) }
    val shownText = if (state.editing) state.draft else state.content
    val lineCount = remember(shownText) { if (shownText.isEmpty()) 1 else shownText.lines().size }
    val language = remember(state.path) { FileTypes.detectLanguage(state.path) }
    val fileInfo = remember(state.path) { FileTypes.resolve(state.path) }

    val handleBack = {
        if (state.dirty) {
            showDiscardDialog = true
        } else if (state.editing) {
            onCancelEdit()
        } else {
            onBack()
        }
    }

    ScreenScaffold(
        title = state.path.substringAfterLast('/'),
        onBack = handleBack,
        subtitle = when {
            state.dirty -> stringResource(R.string.fileview_lines_unsaved, lineCount)
            shownText.isNotEmpty() -> stringResource(R.string.fileview_lines, lineCount)
            else -> null
        },
        subtitleColor = if (state.dirty) colors.warning else null,
        actions = {
            if (state.editing) {
                if (state.dirty) {
                    UnsavedPillBadge()
                }
                TextAction(
                    label = stringResource(if (state.saving) R.string.fileview_saving else R.string.common_save),
                    onClick = onSave,
                    enabled = state.dirty && !state.saving,
                )
                TextAction(
                    label = stringResource(R.string.common_cancel),
                    onClick = {
                        if (state.dirty) showDiscardDialog = true else onCancelEdit()
                    },
                    enabled = !state.saving,
                )
            } else if (!state.loading && state.failure == null && state.failureKey == null) {
                TextAction(label = stringResource(R.string.fileview_edit), onClick = onStartEdit)
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // Path breadcrumb bar
            if (state.path.isNotEmpty()) {
                FilePathBreadcrumbBar(path = state.path, fileInfo = fileInfo)
            }

            // Error notifications
            state.failure?.let {
                Text(
                    text = it.message(),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                )
            }
            state.failureKey?.let {
                Text(
                    text = stringResource(it),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                )
            }

            // Editor / Viewer Body
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> Centered(stringResource(R.string.fileview_reading))
                    state.failure != null -> Centered(state.failure.message())
                    state.failureKey != null -> Centered(stringResource(state.failureKey))
                    state.editing -> CodeEditorPane(
                        draft = state.draft,
                        language = language,
                        lineCount = lineCount,
                        onDraftChange = onDraftChange,
                        colors = colors,
                    )
                    else -> CodeViewerPane(
                        content = state.content,
                        language = language,
                        lineCount = lineCount,
                        colors = colors,
                    )
                }
            }

            // Bottom Status / Metadata Bar
            FileViewStatusBar(
                state = state,
                fileInfo = fileInfo,
                lineCount = lineCount,
                colors = colors,
            )
        }
    }

    // Unsaved Changes Discard Confirmation Dialog
    if (showDiscardDialog) {
        LocalizedAlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.fileview_discard_title),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.fileview_discard_body),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onCancelEdit()
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.fileview_discard_confirm), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.fileview_discard_dismiss), color = colors.accent)
                }
            },
        )
    }

    // Conflict Dialog
    if (state.conflict && state.editing) {
        LocalizedAlertDialog(
            onDismissRequest = onDismissConflict,
            title = {
                Text(
                    stringResource(R.string.conflict_title),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.conflict_body),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = onOverwrite) { Text(stringResource(R.string.conflict_overwrite), color = colors.danger) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TextButton(onClick = onReloadFromDisk) {
                        Text(stringResource(R.string.conflict_reload), color = colors.onSurfaceMuted)
                    }
                    TextButton(onClick = onDismissConflict) {
                        Text(stringResource(R.string.conflict_keep), color = colors.accent)
                    }
                }
            },
        )
    }
}

/** Breadcrumb bar displaying the full directory path and file icon. */
@Composable
private fun FilePathBreadcrumbBar(path: String, fileInfo: FileTypeInfo) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val parts = remember(path) { path.split('/').filter { it.isNotEmpty() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken.copy(alpha = 0.5f))
            .border(0.5.dp, colors.hairline)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeBadge(info = fileInfo, size = 18.dp)
        parts.forEachIndexed { index, part ->
            Text(
                text = if (index == 0) " " + part else " / $part",
                style = OpenCodeTypography.caption.copy(
                    fontFamily = OpenCodeTypography.mono,
                    fontWeight = if (index == parts.lastIndex) FontWeight.Bold else FontWeight.Normal,
                ),
                color = if (index == parts.lastIndex) colors.onSurface else colors.onSurfaceMuted,
                maxLines = 1,
            )
        }
    }
}

/** Code Editor with synchronized line numbers and real-time syntax highlighting. */
@Composable
private fun CodeEditorPane(
    draft: String,
    language: String?,
    lineCount: Int,
    onDraftChange: (String) -> Unit,
    colors: OpenCodeColors,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val verticalScroll = rememberScrollState()
    val visualTransformation = remember(language, colors) {
        SyntaxHighlightVisualTransformation(language, colors)
    }

    val codeStyle = TextStyle(
        fontFamily = OpenCodeTypography.mono,
        fontSize = 13.sp,
        lineHeight = CodeLineHeight,
        letterSpacing = 0.sp,
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(verticalScroll),
    ) {
        // Line numbers gutter
        LineNumberGutter(lineCount = lineCount, colors = colors, codeStyle = codeStyle)

        // Code Editor
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = codeStyle.copy(color = colors.onSurface),
                visualTransformation = visualTransformation,
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.None,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

/** Code Viewer with line numbers, syntax highlighting, and text selection. */
@Composable
private fun CodeViewerPane(
    content: String,
    language: String?,
    lineCount: Int,
    colors: OpenCodeColors,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val highlightedText = remember(content, language, colors) {
        buildAnnotatedCode(language, content, colors)
    }

    val codeStyle = TextStyle(
        fontFamily = OpenCodeTypography.mono,
        fontSize = 13.sp,
        lineHeight = CodeLineHeight,
        letterSpacing = 0.sp,
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(verticalScroll),
    ) {
        // Line numbers gutter
        LineNumberGutter(lineCount = lineCount, colors = colors, codeStyle = codeStyle)

        // Highlighted code view inside SelectionContainer
        SelectionContainer(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
        ) {
            Text(
                text = highlightedText,
                style = codeStyle,
                softWrap = false,
                modifier = Modifier.horizontalScroll(horizontalScroll),
            )
        }
    }
}

/** Pinned gutter rendering line numbers aligned with each line of code. */
@Composable
private fun LineNumberGutter(
    lineCount: Int,
    colors: OpenCodeColors,
    codeStyle: TextStyle,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val gutterWidth = (maxOf(3, lineCount.toString().length) * 8 + 16).dp

    Column(
        modifier = Modifier
            .width(gutterWidth)
            .background(colors.surfaceSunken.copy(alpha = 0.6f))
            .border(width = 0.5.dp, color = colors.hairline)
            .padding(vertical = spacing.xs, horizontal = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        for (i in 1..lineCount) {
            Text(
                text = i.toString(),
                style = codeStyle.copy(fontSize = 11.sp),
                color = colors.onSurfaceFaint,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

/** Status bar at the bottom showing language, line counts, unsaved indicator, and encoding. */
@Composable
private fun FileViewStatusBar(
    state: FileViewState,
    fileInfo: FileTypeInfo,
    lineCount: Int,
    colors: OpenCodeColors,
) {
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(colors.surfaceSunken)
            .border(0.5.dp, colors.hairline)
            .padding(horizontal = spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.dirty) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colors.warning),
                    )
                    Text(
                        text = " " + stringResource(R.string.fileview_dirty_badge),
                        style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Bold),
                        color = colors.warning,
                    )
                }
            } else if (state.saved) {
                Text(
                    text = "✓ " + stringResource(R.string.code_saved),
                    style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.success,
                )
            } else {
                Text(
                    text = if (state.editing) "Editing" else "Viewing",
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceMuted,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$lineCount lines",
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceMuted,
            )
            Text(
                text = fileInfo.language,
                style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Medium),
                color = fileInfo.color,
            )
            Text(
                text = "UTF-8",
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceFaint,
            )
        }
    }
}

@Composable
private fun UnsavedPillBadge() {
    val colors = OpenCodeTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.full))
            .background(colors.warning.copy(alpha = 0.16f))
            .border(0.5.dp, colors.warning.copy(alpha = 0.4f), RoundedCornerShape(OpenCodeTheme.tokens.radius.full))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.warning),
            )
            Text(
                text = " " + stringResource(R.string.fileview_dirty_badge),
                style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = colors.warning,
            )
        }
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
