package dev.bidzz.opencode.ui.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.api.dto.VcsFileStatus
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Every file the agent changed, in one list.
 *
 * This is the "what did it actually do" screen: it answers the question a diff of
 * a single file cannot, by showing the whole working tree at once.
 */
@Composable
fun DiffListScreen(
    changed: Map<String, VcsFileStatus>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

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
                    .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.sm))
                    .clickable(onClick = onBack)
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
            ) {
                Text("Back", style = OpenCodeTypography.label, color = colors.accent)
            }
            Text(
                text = "Changes (${changed.size})",
                style = OpenCodeTypography.title,
                color = colors.onSurface,
                modifier = Modifier.padding(start = spacing.md),
            )
        }

        if (changed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No changes in the working tree.\n\n" +
                        "Either the agent has not edited anything yet, or git is not available.",
                    style = OpenCodeTypography.body,
                    color = colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(spacing.xxl),
                )
            }
            return
        }

        val sorted = changed.values.sortedBy { it.file }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            items(sorted, key = { it.file }) { entry ->
                ChangedFileRow(entry, onClick = { onOpen(entry.file) })
            }
        }
    }
}

@Composable
private fun ChangedFileRow(entry: VcsFileStatus, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.status.first().uppercase(),
            style = OpenCodeTypography.code,
            color = when (entry.status) {
                "added" -> colors.success
                "deleted" -> colors.danger
                else -> colors.warning
            },
            modifier = Modifier.padding(end = spacing.md),
        )
        Text(
            text = entry.file,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "+${entry.additions} −${entry.deletions}",
            style = OpenCodeTypography.label,
            color = colors.onSurfaceFaint,
        )
    }
}
