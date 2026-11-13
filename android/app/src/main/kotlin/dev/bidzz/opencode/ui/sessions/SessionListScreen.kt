package dev.bidzz.opencode.ui.sessions

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.formatCompactTokens
import dev.bidzz.opencode.ui.NoticeKind
import dev.bidzz.opencode.ui.NoticeRow
import dev.bidzz.opencode.ui.sessionTitleSharedKey
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.SearchField
import dev.bidzz.opencode.ui.groupedCardShape
import dev.bidzz.opencode.ui.pressScale
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/**
 * Conversation history matching Cursor / ChatGPT experience.
 *
 * Grouped cleanly by date (Pinned, Today, Yesterday, Previous 7 days, Older)
 * with a quick action menu on every session for Rename, Delete, and Pin.
 *
 * @param details per-session preview/model/status by session id (see
 * [SessionDetail]). The engine's list endpoint returns none of these, so
 * they are supplied separately; when absent a row shows title + age/cost only
 * and search falls back to matching titles.
 */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun SessionListScreen(
    state: SessionListUiState,
    onBack: () -> Unit,
    onOpen: (SessionRow) -> Unit,
    onToggleScope: () -> Unit,
    onNewSession: () -> Unit,
    onRename: (SessionRow, String) -> Unit = { _, _ -> },
    onDelete: (SessionRow) -> Unit = {},
    onTogglePin: (SessionRow) -> Unit = {},
    onInbox: (SessionRow) -> Unit = {},
    onMove: (SessionRow) -> Unit = {},
    onExport: (SessionRow) -> Unit = {},
    onInstructions: (SessionRow) -> Unit = {},
    details: Map<String, SessionDetail> = emptyMap(),
    /** Reloads the list; used as the retry for a full-screen load error. */
    onRefresh: () -> Unit = {},
    /** Dismisses the failed-delete banner. */
    onDismissDeleteFailure: () -> Unit = {},
    /** Dismisses the failed-rename banner. */
    onDismissRenameFailure: () -> Unit = {},
    /** Dismisses a load-failure banner shown over a non-empty list. */
    onDismissLoadFailure: () -> Unit = {},
    /** Loads the next page of sessions (19-F3). */
    onLoadMore: () -> Unit = {},
    /**
     * Animation C shared-element pilot: the scopes for the session-title
     * flight to the chat header. Null disables the flight (the expanded
     * list-detail pane passes null — see OpenCodeApp).
     */
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    ScreenScaffold(
        title = stringResource(R.string.history_title),
        onBack = onBack,
        // The body cap lives here now (see ScreenScaffold): the inner rows
        // keep their own 720dp caps as well, so the layout is the same
        // centred column on tablets as before.
        contentMaxWidth = 720.dp,
        actions = { TextAction(stringResource(R.string.history_new), onNewSession) },
    ) {
        SessionListPane(
            state = state,
            onOpen = onOpen,
            onToggleScope = onToggleScope,
            onRename = onRename,
            onDelete = onDelete,
            onTogglePin = onTogglePin,
            onInbox = onInbox,
            onMove = onMove,
            onExport = onExport,
            onInstructions = onInstructions,
            details = details,
            onRefresh = onRefresh,
            onDismissDeleteFailure = onDismissDeleteFailure,
            onDismissRenameFailure = onDismissRenameFailure,
            onDismissLoadFailure = onDismissLoadFailure,
            onLoadMore = onLoadMore,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.fillMaxSize(),
        )
    }
}


/**
 * The session list without its scaffold: search, scope toggle and the
 * grouped history. Used by [SessionListScreen] and, at expanded widths,
 * as the list pane of the sessions list-detail layout (see OpenCodeApp),
 * where selecting a row opens it in the chat pane beside it instead of
 * navigating away. [selectedSessionId] highlights the open session;
 * null (the full screen) highlights nothing.
 */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun SessionListPane(
    state: SessionListUiState,
    onOpen: (SessionRow) -> Unit,
    onToggleScope: () -> Unit,
    onRename: (SessionRow, String) -> Unit = { _, _ -> },
    onDelete: (SessionRow) -> Unit = {},
    onTogglePin: (SessionRow) -> Unit = {},
    onInbox: (SessionRow) -> Unit = {},
    onMove: (SessionRow) -> Unit = {},
    onExport: (SessionRow) -> Unit = {},
    onInstructions: (SessionRow) -> Unit = {},
    details: Map<String, SessionDetail> = emptyMap(),
    selectedSessionId: String? = null,
    /** Reloads the list; used as the retry for a load error. */
    onRefresh: () -> Unit = {},
    /** Dismisses the failed-delete banner. */
    onDismissDeleteFailure: () -> Unit = {},
    /** Dismisses the failed-rename banner. */
    onDismissRenameFailure: () -> Unit = {},
    /** Dismisses a load-failure banner shown over a non-empty list. */
    onDismissLoadFailure: () -> Unit = {},
    /** Loads the next page of sessions (19-F3). */
    onLoadMore: () -> Unit = {},
    /**
     * Animation C shared-element pilot: the scopes for the session-title
     * flight to the chat header. Null disables the flight (the expanded
     * list-detail pane passes null — see OpenCodeApp).
     */
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val colors = OpenCodeTheme.colors

    var sessionToRename by remember { mutableStateOf<SessionRow?>(null) }
    var sessionToDelete by remember { mutableStateOf<SessionRow?>(null) }

    sessionToRename?.let { target ->
        RenameSessionDialog(
            initialTitle = target.title,
            onDismiss = { sessionToRename = null },
            onConfirm = { newTitle ->
                sessionToRename = null
                onRename(target, newTitle)
            },
        )
    }

    sessionToDelete?.let { target ->
        DeleteSessionDialog(
            sessionTitle = target.title,
            onDismiss = { sessionToDelete = null },
            onConfirm = {
                sessionToDelete = null
                onDelete(target)
            },
        )
    }

    // Search matches titles and, when supplied, transcript previews,
    // model names and outcomes — the drawer only shows the latest 20, so
    // anything older was unreachable without scrolling the whole list.
    var query by rememberSaveable { mutableStateOf("") }
    val filteredRows = remember(state.rows, query, details) {
        filterSessionRows(state.rows, query, details)
    }
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Search field
        SearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.history_search_hint),
            imeAction = ImeAction.Search,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )

        // Scope toggle: "this project" and "everything".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            ScopeChip(
                label = stringResource(R.string.history_this_project),
                selected = state.filterDirectory != null,
                onClick = onToggleScope,
            )
            ScopeChip(
                label = stringResource(R.string.history_all_projects),
                selected = state.filterDirectory == null,
                onClick = onToggleScope,
            )
        }

        // A failed delete is a banner over the list that stays visible
        // (19-F7/65-F1/80-F2): the row the delete just restored must stay
        // on screen, with a retry for that same session. A load failure
        // with rows already loaded gets the same treatment — only an
        // empty list earns the full-screen error below. A failed rename
        // (65-F2/80-F3) is a banner of the same shape: the row is reverted
        // and the retry re-sends the same title. A delete or rename that
        // never ran because the engine is down (65-F3) has no failure
        // value, only a message key — the banner shows either.
        val deleteFailure = state.deleteFailure
        val deleteFailureKey = state.deleteFailureKey
        if (deleteFailure != null || deleteFailureKey != null) {
            val failedRow = state.rows.find { it.id == state.deleteFailedSessionID }
            NoticeRow(
                message = deleteFailure?.message()
                    ?: deleteFailureKey?.let { stringResource(it) }.orEmpty(),
                kind = NoticeKind.Error,
                onRetry = failedRow?.let { row -> { onDelete(row) } },
                onDismiss = onDismissDeleteFailure,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
        } else if (state.renameFailure != null || state.renameFailureKey != null) {
            val failedRow = state.rows.find { it.id == state.renameFailedSessionID }
            val failedTitle = state.renameFailedTitle
            NoticeRow(
                message = state.renameFailure?.message()
                    ?: state.renameFailureKey?.let { stringResource(it) }.orEmpty(),
                kind = NoticeKind.Error,
                onRetry = if (failedRow != null && failedTitle != null) {
                    { onRename(failedRow, failedTitle) }
                } else {
                    null
                },
                onDismiss = onDismissRenameFailure,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
        } else if (state.rows.isNotEmpty()) {
            val loadFailure = state.failure
            val loadFailureKey = state.failureKey
            if (loadFailure != null || loadFailureKey != null) {
                NoticeRow(
                    message = loadFailure?.message()
                        ?: loadFailureKey?.let { stringResource(it) }.orEmpty(),
                    kind = NoticeKind.Error,
                    onRetry = onRefresh,
                    onDismiss = onDismissLoadFailure,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = spacing.lg, vertical = spacing.xs),
                )
            }
        }

        when {
            state.loading -> LoadingState(stringResource(R.string.history_loading))
            state.failure != null && state.rows.isEmpty() -> EmptyState(
                state.failure.message(),
                isError = true,
                onRetry = onRefresh,
            )
            state.failureKey != null && state.rows.isEmpty() -> EmptyState(
                stringResource(state.failureKey),
                isError = true,
                onRetry = onRefresh,
            )
            filteredRows.isEmpty() -> EmptyState(
                if (query.isBlank()) stringResource(R.string.history_empty)
                else stringResource(R.string.history_search_no_results, query),
            )
            else -> {
                val grouped = remember(filteredRows) { groupSessions(filteredRows) }

                // The list is paged at 50 (19-F3): while a next page
                // exists, the count names what was loaded, not the whole
                // history — "Showing 3 of 50" used to read as a total.
                Text(
                    text = if (state.hasMoreSessions) {
                        stringResource(R.string.history_showing_more, filteredRows.size, state.rows.size)
                    } else {
                        stringResource(R.string.history_showing, filteredRows.size, state.rows.size)
                    },
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceFaint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = spacing.lg, vertical = spacing.xs),
                )

                // Search matches previews only once they land (80-F8), and
                // the previews arrive one session at a time — without a
                // word about it, a query reads "no match" and the rows show
                // up seconds later. The note shows only while searching
                // and only while some listed session still has no detail.
                if (query.isNotBlank() && state.rows.any { details[it.id] == null }) {
                    Text(
                        text = stringResource(R.string.history_previews_loading),
                        style = OpenCodeTypography.caption,
                        color = colors.onSurfaceFaint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = spacing.lg, vertical = spacing.xs),
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                        .align(Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(
                        start = spacing.lg,
                        end = spacing.lg,
                        top = spacing.xs,
                        bottom = spacing.xxl,
                    ),
                ) {
                    grouped.forEach { (group, rows) ->
                        item(key = "header_${group.name}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .padding(
                                        top = spacing.md,
                                        bottom = spacing.xs,
                                        start = spacing.xs,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (group == SessionDateGroup.PINNED) {
                                    GlyphIcon(
                                        glyph = Glyph.Pin,
                                        tint = colors.accent,
                                        size = 14.dp,
                                        modifier = Modifier.padding(end = spacing.xs),
                                    )
                                }
                                Text(
                                    text = stringResource(group.titleRes()).uppercase(),
                                    style = OpenCodeTypography.label,
                                    color = if (group == SessionDateGroup.PINNED) colors.accentText else colors.onSurfaceFaint,
                                )
                            }
                        }

                        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                            SessionRowItem(
                                row = row,
                                modifier = Modifier.animateItem(),
                                selected = row.id == selectedSessionId,
                                detail = details[row.id],
                                showDirectory = state.filterDirectory == null,
                                isFirst = index == 0,
                                isLast = index == rows.lastIndex,
                                onClick = { onOpen(row) },
                                onPinToggle = { onTogglePin(row) },
                                onRenameClick = { sessionToRename = row },
                                onDeleteClick = { sessionToDelete = row },
                                onInboxClick = { onInbox(row) },
                                onMoveClick = { onMove(row) },
                                onExportClick = { onExport(row) },
                                onInstructionsClick = { onInstructions(row) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    }
                    // The next page (19-F3): a trailing item, so it scrolls
                    // with the list instead of floating over it. The search
                    // query stays applied — a loaded page is filtered the
                    // same way as the first one.
                    if (state.hasMoreSessions) {
                        item(key = "load_more") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .padding(vertical = spacing.md),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                if (state.loadingMore) {
                                    Text(
                                        text = stringResource(R.string.common_loading),
                                        style = OpenCodeTypography.body,
                                        color = colors.onSurfaceFaint,
                                    )
                                } else {
                                    TextButton(onClick = onLoadMore) {
                                        Text(stringResource(R.string.history_load_more))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "5m ago" / "5 mnt lalu", worded in the app's current language. */
@Composable
fun SessionAge.label(): String = when (unit) {
    AgeUnit.NOW -> stringResource(R.string.age_now)
    AgeUnit.MINUTES -> stringResource(R.string.age_minutes, value)
    AgeUnit.HOURS -> stringResource(R.string.age_hours, value)
    AgeUnit.DAYS -> stringResource(R.string.age_days, value)
    AgeUnit.MONTHS -> stringResource(R.string.age_months, value)
}

/** One session. First and last round the card's corners; the rest are square. */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun SessionRowItem(
    row: SessionRow,
    showDirectory: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onPinToggle: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    /** Null hides the menu entry: only callers that can open it pass one. */
    onInboxClick: (() -> Unit)? = null,
    onMoveClick: (() -> Unit)? = null,
    onExportClick: (() -> Unit)? = null,
    onInstructionsClick: (() -> Unit)? = null,
    detail: SessionDetail? = null,
    /** Accent background for the session open in the detail pane beside the list. */
    selected: Boolean = false,
    /** Carries LazyColumn placement animation (animateItem) from the list. */
    modifier: Modifier = Modifier,
    /**
     * Animation C shared-element pilot: the scopes for the session-title
     * flight to the chat header. Null disables the flight (the expanded
     * list-detail pane passes null — see OpenCodeApp).
     */
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val shape = groupedCardShape(isFirst, isLast, OpenCodeTheme.tokens.radius.lg)

    var menuExpanded by remember { mutableStateOf(false) }

    val age = if (row.age != null) row.age.label() else null
    val directory = row.directory
        ?.takeIf { showDirectory }
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotEmpty() }
    val tokenLabel = row.tokenTotal?.let { stringResource(R.string.turn_footer_tokens, formatCompactTokens(it)) }
    val meta = listOfNotNull(detail?.model, detail?.status, age, directory, row.cost, tokenLabel).joinToString(" · ")
    val preview = detail?.preview?.trim()?.takeIf { it.isNotEmpty() }

    // Animation C shared-element pilot: this title is the origin side of
    // the flight to the chat header. sharedBounds, not sharedElement —
    // the two sides differ in size and style, so only the bounds fly.
    // Both scopes must be present: the expanded list-detail pane passes
    // null, because the detail chat beside it shows the same title at
    // the same time and a shared key may only exist once per transition.
    val sharedTitleModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = sessionTitleSharedKey(row.id)),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

    // Animation C: shared press feedback on the whole row card; the
    // interaction source is shared with the row's clickable below.
    val rowPressSource = remember { MutableInteractionSource() }
    // White B: rows in a group form one floating card — the shadow is
    // drawn per row with the grouped shape, but siblings paint their
    // opaque white background over each neighbour's shadow, so only the
    // group's outer edges show it. Light only; dark stays flat.
    // (Literal whiteCard() would force a symmetric 16dp radius and
    // break the grouped first/last corner shape, so the equivalent
    // is applied here with the row's own shape.)
    val rowCardModifier = if (!colors.isDark) {
        val shadow = OpenCodeTheme.tokens.shadow.card
        Modifier
            .shadow(
                elevation = shadow.elevation,
                shape = shape,
                ambientColor = shadow.ambientColor,
                spotColor = shadow.spotColor,
            )
            .border(
                OpenCodeTheme.tokens.shadow.cardBorderWidth,
                colors.cardBorder,
                shape,
            )
    } else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(motion, rowPressSource)
            .then(rowCardModifier)
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.surface)
            .then(
                if (selected) {
                    Modifier.border(0.5.dp, colors.accent.copy(alpha = 0.35f), shape)
                } else Modifier
            ),
    ) {
        if (!isFirst) RowDivider(inset = spacing.md)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(
                    interactionSource = rowPressSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (row.isPinned) {
                        GlyphIcon(
                            glyph = Glyph.Pin,
                            tint = colors.accent,
                            size = 14.dp,
                            modifier = Modifier.padding(end = spacing.xs),
                        )
                    }
                    Text(
                        text = row.title.ifBlank { stringResource(R.string.drawer_untitled) },
                        style = if (selected) OpenCodeTypography.bodyStrong else OpenCodeTypography.body,
                        color = if (selected) colors.accentText else colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).then(sharedTitleModifier),
                    )
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = OpenCodeTypography.label,
                        color = colors.onSurfaceFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
                if (preview != null) {
                    Text(
                        text = preview,
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }
            // Hoisted: stringResource() is @Composable and cannot be
            // called inside a .semantics {} block.
            val actionsDescription = stringResource(R.string.session_actions)
            Box(modifier = Modifier.padding(start = spacing.xs)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.sm))
                        .clickable(role = Role.Button) { menuExpanded = true }
                        .semantics { contentDescription = actionsDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        Glyph.Dots,
                        tint = colors.onSurfaceMuted,
                        size = 16.dp,
                    )
                }
                SessionActionMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    isPinned = row.isPinned,
                    onPinToggle = onPinToggle,
                    onRenameClick = onRenameClick,
                    onDeleteClick = onDeleteClick,
                    onInboxClick = onInboxClick,
                    onMoveClick = onMoveClick,
                    onExportClick = onExportClick,
                    onInstructionsClick = onInstructionsClick,
                )
            }
        }
    }
}

@Composable
private fun ScopeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.full))
            .background(if (selected) colors.accent.copy(alpha = 0.16f) else colors.fill)
            .border(0.5.dp, if (selected) colors.accent else colors.hairline, RoundedCornerShape(radius.full))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.xs + spacing.xxs),
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = if (selected) colors.accentText else colors.onSurfaceMuted,
        )
    }
}
