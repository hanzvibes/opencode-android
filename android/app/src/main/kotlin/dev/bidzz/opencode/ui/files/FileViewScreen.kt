package dev.bidzz.opencode.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconAction
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.LocalizedDraggableBottomSheet
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.SheetStop
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
    /**
     * True when [content] is only the first [LargeFileGuard.PreviewLines] lines of
     * a file over [LargeFileGuard.MaxChars]. Find searches this preview only and
     * editing is blocked until the full file is loaded.
     */
    val truncated: Boolean = false,
    /** The file's full line count while [truncated]; 0 otherwise. */
    val totalLines: Int = 0,
    /**
     * A file the user asked to open while the current draft is unsaved
     * (item 10). The switch is held — the viewer keeps showing the current
     * file — until the user saves, discards, or cancels from the dialog.
     */
    val confirmSwitchTo: String? = null,
) {
    val dirty: Boolean get() = editing && draft != content
}

private val CodeLineHeight = 20.sp

/**
 * Code viewer and editor with:
 * - Line number gutter synced to scroll
 * - Syntax highlighting for keywords, strings, numbers, and comments
 * - Find-in-file with match count, prev/next, and highlighted matches
 * - Large-file guard: files over ~1 MB show a 2,000-line preview until the
 *   user explicitly loads the whole file
 * - Unsaved changes indicator and discard safeguards
 * - Breadcrumb path display
 *
 * [embedded] renders the same body without the scaffold top bar, for the
 * expanded-width list-detail pane hosted by [ExplorerScreen]: a compact header
 * row carries the filename and the same Find/Edit actions.
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
    onLoadFull: () -> Unit = {},
    /** Discards the unsaved draft and opens the file held in [FileViewState.confirmSwitchTo]. */
    onConfirmSwitchDiscard: () -> Unit = {},
    /** Keeps the current file and drops the held switch request. */
    onCancelSwitch: () -> Unit = {},
    embedded: Boolean = false,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    var showDiscardDialog by remember { mutableStateOf(false) }
    val shownText = if (state.editing) state.draft else state.content
    val lineCount = remember(shownText) { if (shownText.isEmpty()) 1 else shownText.lines().size }
    val language = remember(state.path) { FileTypes.detectLanguage(state.path) }
    val fileInfo = remember(state.path) { FileTypes.resolve(state.path) }

    // File preview (Batch 7): images and PDFs render as a preview instead
    // of the text viewer. The view model still loads the file as text in
    // the background and fails it as non-UTF-8; every text-only branch
    // below is guarded by !isPreview so that failure never shows.
    val previewKind = remember(state.path) { previewKindForPath(state.path) }
    val isPreview = previewKind != FilePreviewKind.NONE

    // Find-in-file: plain-text, case-insensitive, over the text actually loaded.
    // Editing disables it — the draft is a moving target and the viewer (with its
    // highlights and scroll-to-match) is what find is built on.
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var selectedMatch by remember { mutableStateOf(0) }
    val findMatches = remember(shownText, findQuery, state.editing) {
        if (state.editing || findQuery.isEmpty()) emptyList() else findMatches(shownText, findQuery)
    }
    LaunchedEffect(findMatches) { selectedMatch = 0 }
    val findCountText = when {
        findQuery.isEmpty() -> null
        findMatches.isEmpty() -> stringResource(R.string.fileview_find_none)
        findMatches.size >= 1_000 -> stringResource(R.string.fileview_find_count, selectedMatch + 1, 1_000) + "+"
        else -> stringResource(R.string.fileview_find_count, selectedMatch + 1, findMatches.size)
    }
    fun stepFind(delta: Int) {
        if (findMatches.isEmpty()) return
        val size = findMatches.size
        selectedMatch = ((selectedMatch + delta) % size + size) % size
    }

    val handleBack = {
        if (state.dirty) {
            showDiscardDialog = true
        } else if (state.editing) {
            onCancelEdit()
        } else {
            onBack()
        }
    }

    // System Back goes through the same guard: without this the global
    // handler navigated away and the unsaved draft was lost silently
    // (item 10). This handler is composed after it, so it wins.
    BackHandler { handleBack() }

    // Shared top actions: Find toggle in view mode, Edit/Save/Cancel otherwise.
    // Editing a truncated preview is blocked (a save would write the preview over
    // the real file), so the Edit action is hidden until the full file loads.
    val topActions: @Composable RowScope.() -> Unit = {
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
        } else if (!isPreview && !state.loading && state.failure == null && state.failureKey == null) {
            TextAction(
                label = stringResource(R.string.fileview_find),
                onClick = { findOpen = !findOpen },
            )
            if (!state.truncated) {
                TextAction(label = stringResource(R.string.fileview_edit), onClick = onStartEdit)
            }
        }
    }

    // The body is shared by the scaffold and embedded layouts.
    val bodyContent: @Composable ColumnScope.() -> Unit = {
        // Path breadcrumb bar
        if (state.path.isNotEmpty()) {
            FilePathBreadcrumbBar(path = state.path, fileInfo = fileInfo)
        }

        // Error notifications (not while previewing: the text load of a
        // binary file always fails, and the preview is the content)
        if (!isPreview) state.failure?.let {
            Text(
                text = it.message(),
                style = OpenCodeTypography.bodySmall,
                color = colors.danger,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
            )
        }
        if (!isPreview) state.failureKey?.let {
            Text(
                text = stringResource(it),
                style = OpenCodeTypography.bodySmall,
                color = colors.danger,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
            )
        }

        // Large-file warning + explicit load-full action
        if (!isPreview && state.truncated && !state.editing) {
            LargeFileBanner(
                shownLines = lineCount,
                totalLines = state.totalLines,
                onLoadFull = onLoadFull,
            )
        }

        // Find bar (view mode only)
        if (!isPreview && findOpen && !state.editing) {
            FindBar(
                query = findQuery,
                onQueryChange = { findQuery = it },
                countText = findCountText,
                truncated = state.truncated,
                onPrevious = { stepFind(-1) },
                onNext = { stepFind(1) },
                onClose = { findOpen = false; findQuery = "" },
            )
        }

        // Editor / Viewer Body
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isPreview -> FilePreview(path = state.path, kind = previewKind)
                state.loading -> LoadingState(stringResource(R.string.fileview_reading), onRetry = onReloadFromDisk)
                state.failure != null -> EmptyState(state.failure.message(), isError = true)
                state.failureKey != null -> EmptyState(stringResource(state.failureKey), isError = true)
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
                    findMatches = findMatches,
                    selectedMatch = if (findMatches.isEmpty()) -1 else selectedMatch,
                )
            }
        }

        // Bottom Status / Metadata Bar (not while previewing: the preview
        // carries its own footer — image size, or the PDF pager)
        if (!isPreview) FileViewStatusBar(
            state = state,
            fileInfo = fileInfo,
            lineCount = lineCount,
            colors = colors,
        )
    }

    if (embedded) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(
                    glyph = Glyph.ArrowLeft,
                    description = stringResource(R.string.common_back),
                    onClick = handleBack,
                )
                Text(
                    text = state.path.substringAfterLast('/'),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing.xs, end = spacing.sm),
                )
                topActions()
            }
            bodyContent()
        }
    } else {
        ScreenScaffold(
            title = state.path.substringAfterLast('/'),
            onBack = handleBack,
            subtitle = when {
                state.dirty -> stringResource(if (lineCount == 1) R.string.fileview_lines_unsaved_one else R.string.fileview_lines_unsaved, lineCount)
                state.truncated -> stringResource(if (lineCount == 1) R.string.fileview_lines_truncated_one else R.string.fileview_lines_truncated, lineCount, state.totalLines)
                shownText.isNotEmpty() -> stringResource(if (lineCount == 1) R.string.fileview_lines_one else R.string.fileview_lines, lineCount)
                else -> null
            },
            subtitleColor = if (state.dirty) colors.warning else null,
            actions = topActions,
            content = bodyContent,
        )
    }

    // Unsaved Changes Discard Confirmation Dialog
    if (showDiscardDialog) {
        LocalizedDraggableBottomSheet(
            onDismiss = { showDiscardDialog = false },
            initialStop = SheetStop.PEEK,
            title = {
                Text(
                    text = stringResource(R.string.fileview_discard_title),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                )
            },
            content = {
                Text(
                    text = stringResource(R.string.fileview_discard_body),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            },
            actions = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.fileview_discard_dismiss), color = colors.accentText)
                }
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
        )
    }

    // A file switch held for the user's decision (item 10): the draft is
    // unsaved, so the new file is not loaded until they choose. Save writes
    // the draft first and the view model opens the held file on success;
    // Discard drops the draft and opens it now; Cancel keeps this file.
    val pendingSwitch = state.confirmSwitchTo
    if (pendingSwitch != null) {
        LocalizedDraggableBottomSheet(
            onDismiss = onCancelSwitch,
            initialStop = SheetStop.PEEK,
            title = {
                Text(
                    text = stringResource(R.string.fileview_switch_title),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                )
            },
            content = {
                Text(
                    text = stringResource(R.string.fileview_switch_body, pendingSwitch),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            },
            actions = {
                Row {
                    TextButton(onClick = onConfirmSwitchDiscard) {
                        Text(stringResource(R.string.fileview_discard_confirm), color = colors.danger)
                    }
                    TextButton(onClick = onCancelSwitch) {
                        Text(stringResource(R.string.common_cancel), color = colors.accentText)
                    }
                }
                TextButton(onClick = onSave) {
                    Text(stringResource(R.string.common_save), color = colors.accentText)
                }
            },
        )
    }

    // Conflict Dialog
    if (state.conflict && state.editing) {
        LocalizedDraggableBottomSheet(
            onDismiss = onDismissConflict,
            initialStop = SheetStop.PEEK,
            title = {
                Text(
                    stringResource(R.string.conflict_title),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                )
            },
            content = {
                Text(
                    text = stringResource(R.string.conflict_body),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TextButton(onClick = onReloadFromDisk) {
                        Text(stringResource(R.string.conflict_reload), color = colors.onSurfaceMuted)
                    }
                    TextButton(onClick = onDismissConflict) {
                        Text(stringResource(R.string.conflict_keep), color = colors.accentText)
                    }
                }
                TextButton(onClick = onOverwrite) { Text(stringResource(R.string.conflict_overwrite), color = colors.danger) }
            },
        )
    }
}

/** Warning shown for a truncated file, with the explicit opt-in to load it all. */
@Composable
private fun LargeFileBanner(shownLines: Int, totalLines: Int, onLoadFull: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm)
            .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.md))
            .background(colors.warning.copy(alpha = 0.12f))
            .border(0.5.dp, colors.warning.copy(alpha = 0.4f), RoundedCornerShape(OpenCodeTheme.tokens.radius.md))
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.fileview_large_warning, shownLines, totalLines),
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurface,
        )
        TextAction(
            label = stringResource(R.string.fileview_load_full),
            onClick = onLoadFull,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}

/** Find bar: text input, match count, prev/next navigation, close. */
@Composable
private fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    countText: String?,
    truncated: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken.copy(alpha = 0.5f))
            .border(0.5.dp, colors.hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.Search, tint = colors.onSurfaceMuted, size = 15.dp)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = OpenCodeTypography.bodySmall.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.sm),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.fileview_find_hint),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceFaint,
                        )
                    }
                    inner()
                },
            )
            if (countText != null) {
                Text(
                    text = countText,
                    style = OpenCodeTypography.caption.copy(fontFamily = OpenCodeTypography.mono),
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = spacing.xs),
                )
            }
            IconAction(
                glyph = Glyph.ArrowUp,
                description = stringResource(R.string.fileview_find_prev),
                onClick = onPrevious,
            )
            IconAction(
                glyph = Glyph.ArrowDown,
                description = stringResource(R.string.fileview_find_next),
                onClick = onNext,
            )
            IconAction(
                glyph = Glyph.Close,
                description = stringResource(R.string.fileview_find_close),
                onClick = onClose,
            )
        }
        if (truncated) {
            Text(
                text = stringResource(R.string.fileview_find_partial),
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(
                    start = spacing.lg,
                    end = spacing.lg,
                    bottom = spacing.xs,
                ),
            )
        }
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

/** Code Viewer with line numbers, syntax highlighting, text selection, and find-match highlights. */
@Composable
private fun CodeViewerPane(
    content: String,
    language: String?,
    lineCount: Int,
    colors: OpenCodeColors,
    findMatches: List<IntRange> = emptyList(),
    selectedMatch: Int = -1,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val highlightedText = remember(content, language, colors, findMatches, selectedMatch) {
        withFindHighlights(buildAnnotatedCode(language, content, colors), findMatches, selectedMatch, colors)
    }

    val codeStyle = TextStyle(
        fontFamily = OpenCodeTypography.mono,
        fontSize = 13.sp,
        lineHeight = CodeLineHeight,
        letterSpacing = 0.sp,
    )

    // Jump the viewer to the selected match's line. Lines are a uniform height,
    // so the offset is approximate by the gutter's top padding — close enough to
    // land the match on screen, and the highlight itself marks the exact spot.
    val density = LocalDensity.current
    val lineHeightPx = with(density) { CodeLineHeight.toPx() }
    LaunchedEffect(selectedMatch, findMatches) {
        val range = findMatches.getOrNull(selectedMatch) ?: return@LaunchedEffect
        val line = content.substring(0, range.first).count { it == '\n' }
        verticalScroll.scrollTo((line * lineHeightPx).toInt().coerceAtLeast(0))
    }

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
                text = if (state.truncated) {
                    stringResource(R.string.fileview_lines_truncated, lineCount, state.totalLines)
                } else {
                    "$lineCount lines"
                },
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceMuted,
            )
            Text(
                text = fileInfo.language,
                style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Medium),
                // The brand colour as text is 2.14:1 for Rust — use the
                // AA-safe text variant, same as the badge (FileTypes.kt).
                color = if (colors.isDark) fileInfo.darkTextColor else fileInfo.textColor,
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

