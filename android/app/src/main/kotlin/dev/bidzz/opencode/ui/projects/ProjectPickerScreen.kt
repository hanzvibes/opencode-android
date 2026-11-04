package dev.bidzz.opencode.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.CardGroup
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconBadge
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold

/** One selectable project directory. */
data class ProjectOption(
    val path: String,
    val label: String,
    val isCurrent: Boolean,
    /** True when the engine can reach it; false when the folder disappeared. */
    val available: Boolean,
)

/**
 * Project picker.
 *
 * Two ways to change project: the built-in app-private workspace, which always
 * works and needs no permission, or a folder on shared storage chosen through the
 * system picker. Both are shown as equal choices so the app does not present a
 * permission wall as the only path.
 *
 * The folder in use is one prominent card; the others follow as a list to switch to. The
 * current one is not repeated in that list, since there is nothing to do with it there.
 */
@Composable
fun ProjectPickerScreen(
    currentPath: String,
    internalPath: String,
    hasAllFilesAccess: Boolean,
    options: List<ProjectOption>,
    onBack: () -> Unit,
    onSelect: (ProjectOption) -> Unit,
    onChooseFolder: () -> Unit,
    onGrantAccess: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val others = options.filter { !it.isCurrent }

    ScreenScaffold(title = stringResource(R.string.drawer_project), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            SectionLabel(stringResource(R.string.project_current))
            CardGroup { CurrentProject(currentPath) }

            if (!hasAllFilesAccess) {
                PermissionNotice(onGrantAccess, Modifier.padding(top = spacing.lg))
            }

            if (others.isNotEmpty()) {
                SectionLabel(stringResource(R.string.drawer_recent))
                CardGroup {
                    others.forEachIndexed { index, option ->
                        if (index > 0) RowDivider(inset = spacing.md + 36.dp + spacing.md)
                        ProjectRow(option = option, onClick = { onSelect(option) })
                    }
                }
            }

            ChooseFolderButton(onChooseFolder, Modifier.padding(top = spacing.xl))

            Text(
                text = stringResource(R.string.project_internal_note, internalPath),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(top = spacing.lg, bottom = spacing.lg),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = OpenCodeTypography.label,
        color = OpenCodeTheme.colors.onSurfaceFaint,
        modifier = Modifier.padding(
            start = OpenCodeTheme.tokens.spacing.xs,
            top = OpenCodeTheme.tokens.spacing.xl,
            bottom = OpenCodeTheme.tokens.spacing.sm,
        ),
    )
}

/** The folder in use: its name is what you recognise, the full path is reference. */
@Composable
private fun CurrentProject(path: String) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Glyph.Folder, size = 48.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.md),
        ) {
            Text(
                text = path.trimEnd('/').substringAfterLast('/').ifEmpty { path },
                style = OpenCodeTypography.title,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = path,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Why a folder outside the app is off limits, and the one action that fixes it. */
@Composable
private fun PermissionNotice(onGrantAccess: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val shape = RoundedCornerShape(OpenCodeTheme.tokens.radius.lg)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.warning.copy(alpha = 0.12f))
            .border(0.5.dp, colors.warning.copy(alpha = 0.45f), shape)
            .padding(spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.project_grant_text),
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(R.string.project_grant_button),
            style = OpenCodeTypography.label,
            color = colors.accent,
            modifier = Modifier
                .padding(top = spacing.xs)
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClick = onGrantAccess)
                .padding(vertical = spacing.md),
        )
    }
}

@Composable
private fun ProjectRow(option: ProjectOption, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            // A folder that disappeared stays visible, dimmed, so its absence is explained
            // rather than the row silently vanishing.
            .alpha(if (option.available) 1f else 0.6f)
            .clickable(enabled = option.available, role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Glyph.Folder, tint = if (option.available) colors.accent else colors.onSurfaceMuted)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.md),
        ) {
            Text(
                text = option.label,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (option.available) option.path else stringResource(R.string.project_unavailable, option.path),
                style = OpenCodeTypography.bodySmall,
                color = if (option.available) colors.onSurfaceMuted else colors.danger,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (option.available) {
            GlyphIcon(
                Glyph.ChevronRight,
                tint = colors.onSurfaceFaint,
                size = 18.dp,
                modifier = Modifier.padding(start = spacing.sm),
            )
        }
    }
}

@Composable
private fun ChooseFolderButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.lg))
            .background(colors.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Folder, tint = colors.onAccent, size = 20.dp)
        Text(
            text = stringResource(R.string.project_choose),
            style = OpenCodeTypography.label,
            color = colors.onAccent,
            modifier = Modifier.padding(start = spacing.sm),
        )
    }
}
