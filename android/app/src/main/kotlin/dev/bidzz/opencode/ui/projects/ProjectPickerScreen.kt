package dev.bidzz.opencode.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

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
    val radius = OpenCodeTheme.tokens.radius

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
                    .clickable(onClick = onBack)
                    .padding(end = spacing.lg, top = spacing.xs, bottom = spacing.xs),
            ) {
                Text("Back", style = OpenCodeTypography.label, color = colors.accent)
            }
            Text("Project", style = OpenCodeTypography.title, color = colors.onSurface)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.lg),
        ) {
            Text("CURRENT", style = OpenCodeTypography.label, color = colors.onSurfaceFaint)
            Text(
                text = currentPath,
                style = OpenCodeTypography.code,
                color = colors.onSurface,
                modifier = Modifier.padding(top = spacing.xs),
            )

            if (!hasAllFilesAccess) {
                Text(
                    text = "To work on a folder outside the app, grant all-files access. " +
                        "The engine needs a real file path, which only works with that permission.",
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = spacing.lg),
                )
                PrimaryButton("Grant file access", onGrantAccess, Modifier.padding(top = spacing.sm))
            }

            Text(
                text = "RECENT",
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(top = spacing.xxl, bottom = spacing.sm),
            )

            options.forEach { option ->
                ProjectRow(option = option, onClick = { onSelect(option) })
            }

            PrimaryButton(
                text = "Choose another folder…",
                onClick = onChooseFolder,
                modifier = Modifier.padding(top = spacing.lg),
            )

            Text(
                text = "The app's own folder ($internalPath) is always available and needs no permission.",
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(top = spacing.lg),
            )
        }
    }
}

@Composable
private fun ProjectRow(option: ProjectOption, onClick: () -> Unit) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xxs)
            .clip(RoundedCornerShape(radius.sm))
            .background(if (option.isCurrent) colors.userBubble else colors.surfaceSunken)
            .clickable(enabled = option.available && !option.isCurrent, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = OpenCodeTypography.body,
                color = if (option.available) colors.onSurface else colors.onSurfaceFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (option.available) option.path else "${option.path} (unavailable)",
                style = OpenCodeTypography.code,
                color = if (option.available) colors.onSurfaceMuted else colors.danger,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (option.isCurrent) {
            Text("Current", style = OpenCodeTypography.label, color = colors.accent)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.sm))
            .background(colors.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = OpenCodeTypography.label, color = colors.onAccent)
    }
}
