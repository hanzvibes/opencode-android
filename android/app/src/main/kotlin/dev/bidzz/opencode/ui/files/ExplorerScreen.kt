package dev.bidzz.opencode.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message

/**
 * Project file explorer with:
 * - Directory tree indentation and guideline connectors
 * - Rich file type badges and icons
 * - Interactive breadcrumb navigation
 * - Git change markers and diff integration
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
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    val atRoot = state.currentPath.isEmpty()
    var searchFilter by remember { mutableStateOf("") }

    val filteredRows = remember(state.rows, searchFilter) {
        val q = searchFilter.trim().lowercase()
        if (q.isEmpty()) state.rows else state.rows.filter { it.name.lowercase().contains(q) }
    }

    ScreenScaffold(
        title = stringResource(R.string.drawer_files),
        onBack = { if (atRoot) onBack() else onUp() },
        backLabel = if (atRoot) null else stringResource(R.string.files_up),
        actions = {
            if (state.changed.isNotEmpty()) {
                TextAction(stringResource(R.string.files_diff, state.changed.size), onDiff)
            }
            TextAction(stringResource(R.string.common_refresh), onRefresh)
        },
    ) {
        // Breadcrumb Navigation
        Breadcrumbs(state.breadcrumbs, onBreadcrumb)

        // Search bar when there are multiple items
        if (state.rows.size > 5) {
            ExplorerSearchBar(
                query = searchFilter,
                onQueryChange = { searchFilter = it },
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
                        onClick = { if (row.isDirectory) onOpenDirectory(row) else onOpenFile(row) },
                    )
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
            .background(colors.surface),
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
            text = status.first().uppercase(),
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
