package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconAction
import dev.bidzz.opencode.ui.files.FileRow
import dev.bidzz.opencode.ui.files.FileTypeBadge
import dev.bidzz.opencode.ui.files.FileTypes

/**
 * The `@`-mention query being typed at the end of the draft, or null when the
 * draft does not end in a mention token.
 *
 * A token is `@` plus path characters with no whitespace after it, starting
 * a word (start of text or preceded by whitespace) — so `user@host` and a
 * finished `@path ` (trailing space) never open the picker. The query is the
 * text after the `@`, lowercasing left to the ranking in [rankQuickOpen].
 */
fun extractMentionQuery(draft: String): String? {
    val at = draft.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0 && !draft[at - 1].isWhitespace()) return null
    val query = draft.substring(at + 1)
    if (query.any { it.isWhitespace() }) return null
    return query
}

/**
 * Inserts `@path` into the draft as plain text, with a trailing space so the
 * next word starts cleanly and the mention token ends (a finished mention
 * must not re-open the inline picker).
 *
 * When the draft ends in a mention token being typed (`see @READ`), the
 * token is replaced; otherwise the mention is appended, separated from
 * existing text by one space.
 */
fun insertMention(draft: String, path: String): String {
    val mention = "@$path "
    if (draft.isEmpty()) return mention
    val at = draft.lastIndexOf('@')
    if (at >= 0 && extractMentionQuery(draft) != null) {
        return draft.substring(0, at) + mention
    }
    return if (draft.endsWith(' ') || draft.endsWith('\n')) draft + mention else "$draft $mention"
}

/**
 * Drops a trailing `@query` token being typed, leaving the text before it.
 * No-op (returns the draft unchanged) when the draft does not end in a
 * mention token — the dismiss path for the inline picker.
 */
fun removeMentionToken(draft: String): String {
    if (extractMentionQuery(draft) == null) return draft
    return draft.substring(0, draft.lastIndexOf('@'))
}

/**
 * Arrow / Enter / Escape navigation for the attach picker, shared by its own
 * search field (dialog mode) and the composer's key handler (typing-`@`
 * mode). Mirrors [CommandPaletteState.handleKeyEvent]: navigation keys are
 * swallowed on key-up too, so the text field never sees them as input.
 */
fun handleAttachPickerKeyEvent(
    event: KeyEvent,
    results: List<FileRow>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onPickSelected: () -> Unit,
    onClose: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return when (event.key) {
            Key.DirectionDown, Key.DirectionUp,
            Key.Enter, Key.NumPadEnter, Key.Escape -> true
            else -> false
        }
    }
    return when (event.key) {
        Key.DirectionDown -> {
            if (results.isNotEmpty()) {
                onSelectedIndexChange((selectedIndex + 1) % results.size)
            }
            true
        }
        Key.DirectionUp -> {
            if (results.isNotEmpty()) {
                onSelectedIndexChange(
                    if (selectedIndex <= 0) results.lastIndex else selectedIndex - 1,
                )
            }
            true
        }
        Key.Enter, Key.NumPadEnter -> {
            onPickSelected()
            true
        }
        Key.Escape -> {
            onClose()
            true
        }
        else -> false
    }
}

/**
 * Workspace file picker for the composer's attach affordance — one picker,
 * two entry points: the `@` chip (dialog mode, with its own search field)
 * and typing `@` in the draft (inline mode, the composer is the search
 * field). Picking inserts an `@relative/path` mention as plain text; the
 * mention itself is never parsed or stored anywhere else.
 *
 * Look follows the command palette and the files quick-open palette: a
 * raised surfaceRaised panel, radius.lg, the menu elevation token, a header
 * with the match count, ranked rows with a file-type badge and the relative
 * path in mono. Ranking is done by the caller with the shared quick-open
 * ranking over the workspace index, so this panel stays purely presentational.
 */
@Composable
fun AttachFilePicker(
    results: List<FileRow>,
    totalFiles: Int,
    loading: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onPick: (FileRow) -> Unit,
    onClose: () -> Unit,
    showSearchField: Boolean,
    /**
     * Opens the system file picker for a file outside the workspace (SAF).
     * Null hides the row. Dialog mode only: in inline (typing-@) mode the
     * user is mid-mention, and a device file is not a mention — the row
     * would leave a dangling `@query` token behind.
     */
    onBrowseDeviceFiles: (() -> Unit)? = null,
    /** True when the index walk hit a failure: the index is partial. */
    failed: Boolean = false,
    /** Re-runs the walk; only used when the index failed. */
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // A new result set always restarts at the top; the selected row is
    // scrolled into view during keyboard navigation.
    LaunchedEffect(results) { onSelectedIndexChange(0) }
    LaunchedEffect(selectedIndex) {
        if (results.isNotEmpty() && selectedIndex in results.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    // Dialog mode owns the keyboard: focus its search field on open.
    // Inline mode leaves focus in the composer, which is the search field.
    LaunchedEffect(showSearchField) {
        if (showSearchField) focusRequester.requestFocus()
    }

    val pickSelected: () -> Unit = {
        results.getOrNull(selectedIndex)?.let { onPick(it) }
    }

    val shape = RoundedCornerShape(radius.lg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.xs)
            // Menu elevation comes from the token (Tokens.Elevation.menu),
            // same 8dp as the command palette — no hand-tuned shadow.
            .shadow(elevation = OpenCodeTheme.tokens.elevation.menu, shape = shape)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(OpenCodeTheme.tokens.dimensions.hairline, colors.outline.copy(alpha = 0.30f), shape),
    ) {
        if (showSearchField) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
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
                        .padding(start = spacing.sm)
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            handleAttachPickerKeyEvent(
                                event = event,
                                results = results,
                                selectedIndex = selectedIndex,
                                onSelectedIndexChange = onSelectedIndexChange,
                                onPickSelected = pickSelected,
                                onClose = onClose,
                            )
                        },
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.attach_picker_hint),
                                style = OpenCodeTypography.bodySmall,
                                color = colors.onSurfaceFaint,
                            )
                        }
                        inner()
                    },
                )
                IconAction(
                    glyph = Glyph.Close,
                    description = stringResource(R.string.files_quick_open_close),
                    onClick = onClose,
                )
            }
        }

        // Header: @ badge, title, and the showing-N-of-M count.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.xs))
                        .background(colors.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "@",
                        style = OpenCodeTypography.code,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                    )
                }
                Text(
                    text = stringResource(R.string.attach_picker_title),
                    style = OpenCodeTypography.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceMuted,
                    letterSpacing = 0.5.sp,
                )
            }
            Text(
                text = if (loading) {
                    stringResource(R.string.files_quick_open_loading)
                } else {
                    stringResource(R.string.attach_picker_results, results.size, totalFiles)
                },
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceFaint,
            )
        }

        // A partial index says so and offers a retry, instead of silently
        // searching only the folders that happened to load.
        if (failed && !loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.files_quick_open_failed),
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.chat_retry),
                    style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onRetry)
                        .padding(start = spacing.sm),
                )
            }
        }

        when {
            loading && totalFiles == 0 -> {
                Text(
                    text = stringResource(R.string.files_quick_open_loading),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                )
            }
            results.isEmpty() -> {
                Text(
                    text = if (failed) {
                        stringResource(R.string.files_quick_open_failed)
                    } else if (query.isEmpty()) {
                        stringResource(R.string.attach_picker_no_files)
                    } else {
                        stringResource(R.string.attach_picker_empty, query)
                    },
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
            ) {
                itemsIndexed(results, key = { _, row -> row.path }) { rowIndex, row ->
                    val fileInfo = remember(row.name) { FileTypes.resolve(row.name) }
                    val isSelected = rowIndex == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable(role = Role.Button, onClick = { onPick(row) })
                            .padding(horizontal = spacing.md, vertical = spacing.sm)
                            .animateItem(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FileTypeBadge(info = fileInfo, size = 28.dp)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = spacing.md),
                        ) {
                            Text(
                                text = row.name,
                                style = OpenCodeTypography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = row.path,
                                style = OpenCodeTypography.caption.copy(fontFamily = OpenCodeTypography.mono),
                                color = colors.onSurfaceMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isSelected) {
                            Text(
                                text = "↵",
                                style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Bold),
                                color = colors.accent,
                            )
                        } else {
                            GlyphIcon(
                                glyph = Glyph.ChevronRight,
                                tint = colors.onSurfaceFaint,
                                size = 12.dp,
                            )
                        }
                    }
                }
            }
        }

        // Device files (SAF, outside the workspace): a footer row under the
        // workspace results, separated by a hairline. The engine cannot read
        // a path outside the workspace, so the file picked here is read in
        // as text and inlined into the message instead of mentioned — the
        // row says "browse", not "@", and the chip it produces is not a
        // mention. Dialog mode only (see onBrowseDeviceFiles).
        if (showSearchField && onBrowseDeviceFiles != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.hairline),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onBrowseDeviceFiles)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                GlyphIcon(
                    glyph = Glyph.External,
                    tint = colors.onSurfaceMuted,
                    size = 14.dp,
                )
                Text(
                    text = stringResource(R.string.attach_browse_files),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                GlyphIcon(
                    glyph = Glyph.ChevronRight,
                    tint = colors.onSurfaceFaint,
                    size = 12.dp,
                )
            }
        }
    }
}
