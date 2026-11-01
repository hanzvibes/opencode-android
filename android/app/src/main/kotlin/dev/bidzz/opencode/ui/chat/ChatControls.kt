package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.api.dto.AgentInfo
import dev.bidzz.opencode.core.api.dto.CommandInfo
import dev.bidzz.opencode.core.api.dto.ModelInfo
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Session controls — mode and model — sitting between the transcript and the composer.
 *
 * They live here rather than in the header because both are per-session decisions made
 * right before typing, and the header is already carrying navigation.
 */
@Composable
fun ChatControls(
    agents: List<AgentInfo>,
    activeAgent: String?,
    models: List<ModelInfo>,
    activeModel: ModelRef?,
    onAgent: (String) -> Unit,
    onModel: (ModelRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (agents.isEmpty() && models.isEmpty()) return

    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (agents.isNotEmpty()) {
            ChoiceChip(
                label = agents.firstOrNull { it.id == activeAgent }?.name ?: "mode",
                placeholder = "mode",
                items = agents.map { it.id to it.name },
                selected = activeAgent,
                onSelect = onAgent,
            )
        }
        if (models.isNotEmpty()) {
            ChoiceChip(
                label = models.firstOrNull { it.ref() == activeModel }?.label() ?: "model",
                placeholder = "model",
                items = models.map { it.ref().id to it.label() },
                selected = activeModel?.id,
                onSelect = { id -> models.firstOrNull { it.ref().id == id }?.let { onModel(it.ref()) } },
            )
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    placeholder: String,
    items: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing
    var expanded by remember { mutableStateOf(false) }
    val chosen = selected != null

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(radius.full))
                .background(if (chosen) colors.surfaceRaised else colors.surfaceSunken)
                .border(0.5.dp, colors.outline, RoundedCornerShape(radius.full))
                .clickable { expanded = true }
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = if (chosen) label else placeholder,
                style = OpenCodeTypography.label,
                color = if (chosen) colors.onSurface else colors.onSurfaceFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = "▾", style = OpenCodeTypography.label, color = colors.onSurfaceFaint)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (id, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            style = OpenCodeTypography.body,
                            color = if (id == selected) colors.accent else colors.onSurface,
                        )
                    },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Slash-command completion, shown while the draft starts with `/`.
 *
 * Tapping a row replaces the draft with `name ` so the operator can type arguments,
 * rather than sending immediately — a command with the wrong argument is worse than
 * an extra tap.
 */
@Composable
fun CommandSuggestions(
    commands: List<CommandInfo>,
    draft: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!draft.startsWith("/")) return
    val typed = draft.substring(1).substringBefore(' ').lowercase()
    val matches = commands.filter { it.name.lowercase().startsWith(typed) }
    if (matches.isEmpty()) return

    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Column(
        modifier = modifier
            .padding(horizontal = spacing.md, vertical = spacing.xs)
            .clip(RoundedCornerShape(radius.md))
            .background(colors.surfaceRaised)
            .border(0.5.dp, colors.outline, RoundedCornerShape(radius.md)),
    ) {
        matches.take(6).forEach { command ->
            val description = command.description
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(command.name) }
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "/${command.name}",
                    style = OpenCodeTypography.codeInline,
                    color = colors.accent,
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}