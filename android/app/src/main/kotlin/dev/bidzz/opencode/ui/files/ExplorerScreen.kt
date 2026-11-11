package dev.bidzz.opencode.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeColors
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconAction
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/** Expanded width at which the explorer becomes a list-detail split. */
private val ListDetailBreakpoint = 840.dp

/** Width of the file-tree pane in the list-detail split. */
private val ListDetailListWidth = 360.dp

/**
 * Project file explorer with:
 * - Directory tree indentation and guideline connectors
 * - Rich file type badges and icons
 * - Interactive breadcrumb navigation
 * - Git change markers and diff integration
 * - Quick-open: a palette-style filename search over the whole workspace
 * - List-detail at >= 840dp: the tree stays on the left while the selected
 *   file is shown beside it, once the host wires the detail parameters below
 *
 * The detail parameters all default to "not wired": with them absent the screen
 * behaves exactly as before (a tap navigates to the full-screen file view).
 * The host ([OpenCodeApp]) wires them by passing the shared [FileViewState],
 * opening files into the detail pane instead of navigating, and clearing the
 * selection on close. Nothing here navigates on its own.
 */
@Composable
fun ExplorerScreen(
    state: ExplorerUiState,
    onBack: () -> Unit,
    onOpenDirectory: (FileRow) -> Unit,
    onOpenFile: (FileRow) -> Unit,
    onBreadcrumb: (String) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onDiff: () -> Unit,
    /** Path of the file shown in the detail pane, for the selected-row highlight. */
    selectedFilePath: String? = null,
    /** File-viewer state for the detail pane; null until a file is opened into it. */
    fileState: FileViewState? = null,
    /** Opens a file into the detail pane instead of navigating; null means unwired. */
    onOpenFileDetail: ((FileRow) -> Unit)? = null,
    /** Clears the detail selection (detail back arrow). */
    onCloseFileDetail: () -> Unit = {},
    onFileStartEdit: () -> Unit = {},
    onFileDraftChange: (String) -> Unit = {},
    onFileSave: () -> Unit = {},
    onFileCancelEdit: () -> Unit = {},
    onFileOverwrite: () -> Unit = {},
    onFileReloadFromDisk: () -> Unit = {},
    onFileDismissConflict: () -> Unit = {},
    onFileLoadFull: () -> Unit = {},
    /** Discards the unsaved draft and opens the file held by the switch dialog. */
    onFileConfirmSwitchDiscard: () -> Unit = {},
    /** Keeps the current file and drops the held switch request. */
    onFileCancelSwitch: () -> Unit = {},
    /** Starts building the workspace-wide quick-open index (lazy, on first open). */
    onEnsureQuickOpenIndex: () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    val atRoot = state.currentPath.isEmpty()
    var searchFilter by remember { mutableStateOf("") }
    var quickOpenOpen by remember { mutableStateOf(false) }

    val filteredRows = remember(state.rows, searchFilter) {
        val q = searchFilter.trim().lowercase()
        if (q.isEmpty()) state.rows else state.rows.filter { it.name.lowercase().contains(q) }
    }

    // One tap target for opening a row, wherever it comes from (list or palette):
    // directories always navigate, files go to the detail pane when it is wired.
    val detailOpener = onOpenFileDetail
    val openRow: (FileRow) -> Unit = { row ->
        if (row.isDirectory) onOpenDirectory(row) else if (detailOpener != null) detailOpener(row) else onOpenFile(row)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val detailActive = maxWidth >= ListDetailBreakpoint && onOpenFileDetail != null

        ScreenScaffold(
            title = stringResource(R.string.drawer_files),
            onBack = { if (atRoot) onBack() else onUp() },
            backLabel = if (atRoot) null else stringResource(R.string.files_up),
            actions = {
                TextAction(
                    label = stringResource(R.string.files_quick_open),
                    onClick = {
                        onEnsureQuickOpenIndex()
                        quickOpenOpen = true
                    },
                )
                if (state.changed.isNotEmpty()) {
                    TextAction(stringResource(R.string.files_diff, state.changed.size), onDiff)
                }
                TextAction(stringResource(R.string.common_refresh), onRefresh)
            },
        ) {
            when {
                quickOpenOpen -> QuickOpenPalette(
                    index = state.quickOpenIndex,
                    loading = state.quickOpenLoading,
                    onPick = { row ->
                        quickOpenOpen = false
                        openRow(row)
                    },
                    onClose = { quickOpenOpen = false },
                )

                detailActive -> Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(ListDetailListWidth)
                            .fillMaxHeight(),
                    ) {
                        ExplorerListContent(
                            state = state,
                            filteredRows = filteredRows,
                            searchFilter = searchFilter,
                            onSearchFilterChange = { searchFilter = it },
                            selectedFilePath = selectedFilePath,
                            onOpenRow = openRow,
                            onBreadcrumb = onBreadcrumb,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .fillMaxHeight()
                            .background(colors.hairline),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        if (fileState != null) {
                            FileViewScreen(
                                state = fileState,
                                onBack = onCloseFileDetail,
                                onStartEdit = onFileStartEdit,
                                onDraftChange = onFileDraftChange,
                                onSave = onFileSave,
                                onCancelEdit = onFileCancelEdit,
                                onOverwrite = onFileOverwrite,
                                onReloadFromDisk = onFileReloadFromDisk,
                                onDismissConflict = onFileDismissConflict,
                                onLoadFull = onFileLoadFull,
                                onConfirmSwitchDiscard = onFileConfirmSwitchDiscard,
                                onCancelSwitch = onFileCancelSwitch,
                                embedded = true,
                            )
                        } else {
                            Centered(stringResource(R.string.files_detail_empty))
                        }
                    }
                }

                else -> ExplorerListContent(
                    state = state,
                    filteredRows = filteredRows,
                    searchFilter = searchFilter,
                    onSearchFilterChange = { searchFilter = it },
                    selectedFilePath = selectedFilePath,
                    onOpenRow = openRow,
                    onBreadcrumb = onBreadcrumb,
                )
            }
        }
    }
}

/** The explorer list: breadcrumbs, current-directory filter, VCS note, rows. */
@Composable
private fun ExplorerListContent(
    state: ExplorerUiState,
    filteredRows: List<FileRow>,
    searchFilter: String,
    onSearchFilterChange: (String) -> Unit,
    selectedFilePath: String?,
    onOpenRow: (FileRow) -> Unit,
    onBreadcrumb: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    // Breadcrumb Navigation
    Breadcrumbs(state.breadcrumbs, onBreadcrumb)

    // Search bar when there are multiple items
    if (state.rows.size > 5) {
        ExplorerSearchBar(
            query = searchFilter,
            onQueryChange = onSearchFilterChange,
            colors = colors,
        )
    }

    state.vcsNote?.let { note ->
        Text(
            text = stringResource(
                when (note) {
                    VcsNote.NOT_A_REPOSITORY -> R.string.files_note_not_repo
                    VcsNote.NO_CHANGES -> R.string.files_note_clean
                },
            ),
            style = OpenCodeTypography.label,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(
                start = spacing.lg,
                end = spacing.lg,
                top = if (state.breadcrumbs.isEmpty()) spacing.lg else spacing.xs,
                bottom = spacing.sm,
            ),
        )
    }

    when {
        state.loading -> Centered(stringResource(R.string.common_loading))
        state.failure != null -> Centered(state.failure.message(), isError = true)
        state.failureKey != null -> Centered(stringResource(state.failureKey), isError = true)
        state.rows.isEmpty() -> EmptyFolder()
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            itemsIndexed(filteredRows, key = { _, row -> row.path }) { index, row ->
                val rel = if (state.currentPath.isEmpty()) {
                    row.path
                } else {
                    row.path.removePrefix(state.currentPath).trimStart('/')
                }
                val depth = rel.count { it == '/' }

                FileRowItem(
                    row = row,
                    depth = depth,
                    isFirst = index == 0,
                    isLast = index == filteredRows.lastIndex,
                    selected = row.path == selectedFilePath,
                    onClick = { onOpenRow(row) },
                )
            }
        }
    }
}

/**
 * Quick-open palette: filename search over the workspace index, filtered as you
 * type. Same pattern as the chat command palette — a raised panel with a header
 * count, ranked rows, and arrow/Enter/Escape keyboard navigation — but over
 * files, with a type badge and the relative path on each row.
 */
@Composable
private fun QuickOpenPalette(
    index: List<FileRow>,
    loading: Boolean,
    onPick: (FileRow) -> Unit,
    onClose: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val results = remember(index, query) { rankQuickOpen(index, query) }
    LaunchedEffect(results) { selectedIndex = 0 }
    LaunchedEffect(selectedIndex) {
        if (results.isNotEmpty() && selectedIndex in results.indices) {
            listState.scrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val pickSelected: () -> Unit = {
        results.getOrNull(selectedIndex)?.let { onPick(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radius.lg))
                .background(colors.surfaceRaised)
                .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.lg)),
        ) {
            // Search field with keyboard navigation (arrows / Enter / Escape)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Search, tint = colors.onSurfaceMuted, size = 15.dp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = OpenCodeTypography.bodySmall.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing.sm)
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> {
                                    if (results.isNotEmpty()) {
                                        selectedIndex = (selectedIndex + 1) % results.size
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (results.isNotEmpty()) {
                                        selectedIndex = if (selectedIndex <= 0) results.lastIndex else selectedIndex - 1
                                    }
                                    true
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    pickSelected()
                                    true
                                }
                                Key.Escape -> {
                                    onClose()
                                    true
                                }
                                else -> false
                            }
                        },
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.files_quick_open_hint),
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

            // Header count, mirroring the command palette header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.files_quick_open),
                    style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurfaceMuted,
                )
                Text(
                    text = if (loading) {
                        stringResource(R.string.files_quick_open_loading)
                    } else {
                        stringResource(R.string.files_quick_open_results, results.size, index.size)
                    },
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceFaint,
                )
            }

            when {
                loading && index.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.files_quick_open_loading),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                    )
                }

                results.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.files_quick_open_empty, query),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                ) {
                    itemsIndexed(results, key = { _, row -> row.path }) { rowIndex, row ->
                        val fileInfo = remember(row.name) { FileTypes.resolve(row.name) }
                        val isSelected = rowIndex == selectedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable(role = Role.Button, onClick = { onPick(row) })
                                .padding(horizontal = spacing.md, vertical = spacing.sm),
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
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Breadcrumbs row: Root / dir / subdir chips with horizontal auto-scroll. */
@Composable
private fun Breadcrumbs(crumbs: List<ExplorerUiState.Crumb>, onBreadcrumb: (String) -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val scroll = rememberScrollState()

    LaunchedEffect(crumbs) {
        withFrameNanos { }
        scroll.scrollTo(scroll.maxValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crumb(
            label = stringResource(R.string.files_root),
            current = crumbs.isEmpty(),
            glyph = Glyph.Folder,
        ) { onBreadcrumb("") }

        crumbs.forEachIndexed { index, crumb ->
            GlyphIcon(Glyph.ChevronRight, tint = colors.onSurfaceFaint, size = 14.dp)
            Crumb(label = crumb.label, current = index == crumbs.lastIndex) { onBreadcrumb(crumb.path) }
        }
    }
}

@Composable
private fun Crumb(label: String, current: Boolean, glyph: Glyph? = null, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.full))
                .background(if (current) colors.accent.copy(alpha = 0.16f) else colors.fill)
                .border(0.5.dp, if (current) colors.accent else colors.hairline, RoundedCornerShape(OpenCodeTheme.tokens.radius.full))
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                GlyphIcon(glyph, tint = if (current) colors.accent else colors.onSurfaceMuted, size = 14.dp)
            }
            Text(
                text = label,
                style = OpenCodeTypography.caption.copy(fontWeight = if (current) FontWeight.Bold else FontWeight.Medium),
                color = if (current) colors.accent else colors.onSurfaceMuted,
                maxLines = 1,
                modifier = Modifier.padding(start = if (glyph != null) spacing.xs else 0.dp),
            )
        }
    }
}

@Composable
private fun ExplorerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    colors: OpenCodeColors,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius.full

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xxs)
            .clip(RoundedCornerShape(radius))
            .background(colors.fill)
            .border(0.5.dp, colors.hairline, RoundedCornerShape(radius))
            .padding(horizontal = spacing.md, vertical = spacing.xs + 1.dp),
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
                        text = "Search files…",
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceFaint,
                    )
                }
                inner()
            },
        )
    }
}

/** One row with tree indentation, file type badge, and git tracking. */
@Composable
private fun FileRowItem(
    row: FileRow,
    depth: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val r = OpenCodeTheme.tokens.radius.md

    val shape = RoundedCornerShape(
        topStart = if (isFirst) r else 0.dp,
        topEnd = if (isFirst) r else 0.dp,
        bottomStart = if (isLast) r else 0.dp,
        bottomEnd = if (isLast) r else 0.dp,
    )

    val fileInfo = remember(row.name, row.isDirectory) {
        FileTypes.resolve(row.name, row.isDirectory)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.surface),
    ) {
        if (!isFirst) RowDivider(inset = spacing.md + (depth * 16).dp + 32.dp + spacing.md)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Directory tree indentation guides
            if (depth > 0) {
                TreeIndentationGuide(depth = depth, colors = colors)
            }

            // File type badge
            FileTypeBadge(info = fileInfo, size = 32.dp)

            // Name
            Text(
                text = row.name,
                style = if (row.isDirectory) OpenCodeTypography.bodyStrong else OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.md),
            )

            // Git stats and badge
            if (row.gitStatus != null) {
                if (row.additions > 0 || row.deletions > 0) {
                    Text(
                        text = "+${row.additions} −${row.deletions}",
                        style = OpenCodeTypography.caption.copy(fontFamily = OpenCodeTypography.mono, fontSize = 11.sp),
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(start = spacing.xs),
                    )
                }
                GitBadge(row.gitStatus, Modifier.padding(start = spacing.sm))
            }

            if (row.isDirectory) {
                GlyphIcon(
                    Glyph.ChevronRight,
                    tint = colors.onSurfaceFaint,
                    size = 16.dp,
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
        }
    }
}

/** Renders vertical directory tree guide lines and spacing. */
@Composable
private fun TreeIndentationGuide(depth: Int, colors: OpenCodeColors) {
    Row(
        modifier = Modifier.padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (d in 0 until depth) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .heightIn(min = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(colors.hairline),
                )
            }
        }
    }
}

@Composable
private fun GitBadge(status: String, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val tint: Color = when (status) {
        "added" -> colors.success
        "deleted" -> colors.danger
        else -> colors.warning
    }
    val spoken = stringResource(
        when (status) {
            "added" -> R.string.files_status_added
            "deleted" -> R.string.files_status_deleted
            else -> R.string.files_status_modified
        },
    )
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.xs))
            .background(tint.copy(alpha = 0.16f))
            .border(0.5.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(OpenCodeTheme.tokens.radius.xs))
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // An empty status from the engine used to throw here
            // (NoSuchElementException, inside composition) and crash the
            // Files screen on open (item 3).
            text = status.firstOrNull()?.uppercase() ?: "?",
            style = OpenCodeTypography.caption.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = OpenCodeTypography.mono,
            ),
            color = tint,
        )
    }
}

@Composable
private fun EmptyFolder() {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlyphIcon(Glyph.Folder, tint = colors.onSurfaceFaint, size = 40.dp)
        Text(
            text = stringResource(R.string.files_empty),
            style = OpenCodeTypography.body,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.md),
        )
    }
}

@Composable
private fun Centered(text: String, isError: Boolean = false) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = OpenCodeTypography.body,
            color = if (isError) colors.danger else colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(spacing.xxl),
        )
    }
}
