package dev.bidzz.opencode.ui.diff

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.VcsFileStatus
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.pressScale
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.files.FileTypeBadge
import dev.bidzz.opencode.ui.files.FileTypes

/**
 * Overview of all working tree changes with Git and Claude Code-inspired status tracking.
 *
 * Displays file type icons, path hierarchies, search filtering, and overall git stats.
 */
@Composable
fun DiffListScreen(
    changed: Map<String, VcsFileStatus>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    var filterQuery by remember { mutableStateOf("") }
    val totalAdditions = remember(changed) { changed.values.sumOf { it.additions } }
    val totalDeletions = remember(changed) { changed.values.sumOf { it.deletions } }

    val filteredList = remember(changed, filterQuery) {
        val query = filterQuery.trim().lowercase()
        val all = changed.values.sortedBy { it.file }
        if (query.isEmpty()) all else all.filter { it.file.lowercase().contains(query) }
    }

    ScreenScaffold(
        title = stringResource(R.string.difflist_title, changed.size),
        onBack = onBack,
        subtitle = if (changed.isNotEmpty()) "+$totalAdditions −$totalDeletions" else null,
        subtitleColor = if (changed.isNotEmpty()) colors.accent else null,
    ) {
        if (changed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.difflist_empty),
                    style = OpenCodeTypography.body,
                    color = colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(spacing.xxl),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Filter search bar when there are several files
                if (changed.size > 2) {
                    DiffFilterBar(
                        query = filterQuery,
                        onQueryChange = { filterQuery = it },
                    )
                }

                // Summary Stats Strip
                DiffSummaryHeader(
                    fileCount = changed.size,
                    additions = totalAdditions,
                    deletions = totalDeletions,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
                ) {
                    itemsIndexed(filteredList, key = { _, item -> item.file }) { index, entry ->
                        ChangedFileCardItem(
                            entry = entry,
                            isFirst = index == 0,
                            isLast = index == filteredList.lastIndex,
                            onClick = { onOpen(entry.file) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius.full

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs)
            .clip(RoundedCornerShape(radius))
            .background(colors.fill)
            .border(0.5.dp, colors.hairline, RoundedCornerShape(radius))
            .padding(horizontal = spacing.md, vertical = spacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Search, tint = colors.onSurfaceMuted, size = 16.dp)
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
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.diff_filter_hint),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceFaint,
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun DiffSummaryHeader(
    fileCount: Int,
    additions: Int,
    deletions: Int,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$fileCount files changed",
            style = OpenCodeTypography.caption,
            color = colors.onSurfaceMuted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.xs))
                    .background(colors.success.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "+$additions",
                    style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.success,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.xs))
                    .background(colors.danger.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "−$deletions",
                    style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.danger,
                )
            }
        }
    }
}

@Composable
private fun ChangedFileCardItem(
    entry: VcsFileStatus,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    /** Carries LazyColumn placement animation (animateItem) from the list. */
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val r = OpenCodeTheme.tokens.radius.md

    val shape = RoundedCornerShape(
        topStart = if (isFirst) r else 0.dp,
        topEnd = if (isFirst) r else 0.dp,
        bottomStart = if (isLast) r else 0.dp,
        bottomEnd = if (isLast) r else 0.dp,
    )

    val dir = entry.file.substringBeforeLast('/', "")
    val name = entry.file.substringAfterLast('/')
    val fileInfo = remember(entry.file) { FileTypes.resolve(entry.file) }

    // Animation C: shared press feedback on the whole file card; the
    // interaction source is shared with the row's clickable below.
    val cardPressSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(motion, cardPressSource)
            .clip(shape)
            .background(colors.surface),
    ) {
        if (!isFirst) RowDivider(inset = spacing.md + 34.dp + spacing.md)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(
                    interactionSource = cardPressSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // File type badge
            FileTypeBadge(info = fileInfo, size = 32.dp)

            // File and path
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.md, end = spacing.sm),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = name,
                    style = OpenCodeTypography.bodyStrong,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (dir.isNotEmpty()) {
                    Text(
                        text = dir,
                        style = OpenCodeTypography.caption,
                        color = colors.onSurfaceFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Line delta stats
            if (entry.additions > 0 || entry.deletions > 0) {
                Text(
                    text = "+${entry.additions} −${entry.deletions}",
                    style = OpenCodeTypography.caption.copy(fontFamily = OpenCodeTypography.mono, fontSize = 11.sp),
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(end = spacing.xs),
                )
            }

            // Git Status Badge
            GitStatusBadge(status = entry.status)

            GlyphIcon(
                Glyph.ChevronRight,
                tint = colors.onSurfaceFaint,
                size = 16.dp,
                modifier = Modifier.padding(start = spacing.xs),
            )
        }
    }
}

@Composable
private fun GitStatusBadge(status: String) {
    val colors = OpenCodeTheme.colors
    val (tint, label) = when (status) {
        "added" -> colors.success to "A"
        "deleted" -> colors.danger to "D"
        else -> colors.warning to "M"
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.xs))
            .background(tint.copy(alpha = 0.16f))
            .border(0.5.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(OpenCodeTheme.tokens.radius.xs))
            .semantics { contentDescription = status },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.caption.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                fontFamily = OpenCodeTypography.mono,
            ),
            color = tint,
        )
    }
}
