package dev.bidzz.opencode.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import dev.bidzz.opencode.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.api.dto.AgentInfo
import dev.bidzz.opencode.core.api.dto.ModelInfo
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.design.OpenCodeColors
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon

/**
 * Session controls — mode and model — sitting between the transcript and the composer.
 *
 * Designed to match ChatGPT and Claude model selectors:
 * - Distinct visual badges for agent modes (Build, Plan, Chat) with color-coded dot and mini icons.
 * - Model switcher chip displaying brand and model tier (Claude 3.7 Sonnet, GPT-4o, DeepSeek R1).
 * - Premium popover menu with model descriptions, context window badges, and active checkmarks.
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
    /** False while the agent is working: switching mode/model mid-run is a no-op. */
    enabled: Boolean = true,
) {
    if (agents.isEmpty() && models.isEmpty()) return

    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (agents.isNotEmpty()) {
            AgentSelector(
                agents = agents,
                activeAgent = activeAgent,
                onSelect = onAgent,
                enabled = enabled,
            )
        }
        if (models.isNotEmpty()) {
            ModelSelector(
                models = models,
                activeModel = activeModel,
                onSelect = onModel,
                enabled = enabled,
            )
        }
    }
}

/**
 * Offset for a selector [DropdownMenu] anchored to a chip in the composer.
 *
 * The composer sits at the bottom of the screen, so a menu that opens
 * downward runs off the bottom on short screens. When the chip sits in the
 * bottom half of the screen the menu opens upward instead: the negative
 * offset lifts it by the chip height plus its own (capped) height, so its
 * bottom edge lands on the chip's top edge. The 380dp height cap stays, so
 * the estimate is capped the same way. The selectors stay in the composer
 * — nothing moves to the header — only the menu direction changes.
 */
private fun selectorMenuOffset(
    openAbove: Boolean,
    menuHeight: Dp,
    chipHeight: Dp,
    gap: Dp,
): DpOffset = if (openAbove) {
    DpOffset(0.dp, -(chipHeight + menuHeight + gap))
} else {
    DpOffset(0.dp, gap)
}

/**
 * Agent / Mode Selector chip with a distinct color-coded indicator badge and
 * rich popover menu detailing agent capabilities, permissions, and active selection.
 */
@Composable
private fun AgentSelector(
    agents: List<AgentInfo>,
    activeAgent: String?,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    var expanded by remember { mutableStateOf(false) }

    val selectableAgents = remember(agents) {
        val filtered = agents.filter { it.hidden != true && it.mode != "subagent" }
        if (filtered.isNotEmpty()) filtered else agents
    }

    val currentAgent = remember(selectableAgents, activeAgent) {
        selectableAgents.firstOrNull { it.id == activeAgent || it.name.equals(activeAgent, ignoreCase = true) }
            ?: activeAgent?.let { AgentInfo(id = it, name = it) }
            ?: selectableAgents.firstOrNull()
    }

    val currentPresentation = currentAgent?.let { resolveAgentPresentation(it, colors) }

    // One glyph rotating 0°↔180° (down = open) instead of a static chevron.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.tweenQuick(),
        label = "agent_chevron_rotation",
    )
    val chipShape = RoundedCornerShape(radius.full)

    // Drop-up: the composer chip sits at the bottom of the screen, so the
    // menu opens above it when the chip is in the bottom half of the screen.
    var anchorTopDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val openAbove = anchorTopDp > 0.dp && anchorTopDp > screenHeightDp / 2
    // Header ~36dp plus one ~64dp row per agent, capped like the menu itself.
    val estimatedMenuHeight = (36.dp + 64.dp * selectableAgents.size).coerceAtMost(380.dp)

    // Selector chips are 40dp tall (the composer touch target), not 28dp:
    // they sit in the same controls row as the composer chips, and a 28dp
    // chip next to a 40dp hit area was the smallest tap target in the row.
    Box(
        modifier = Modifier.onGloballyPositioned {
            anchorTopDp = with(density) { it.positionInWindow().y.toDp() }
        },
    ) {
        Row(
            modifier = Modifier
                .height(dims.composerChipTouchTarget)
                .clip(chipShape)
                .background(if (expanded) colors.surfacePressed else colors.fill)
                .border(0.5.dp, if (expanded) colors.outline else colors.hairline, chipShape)
                .semantics {
                    role = Role.Button
                    contentDescription = "Mode: ${currentPresentation?.title ?: "Mode"}"
                }
                .clickable(enabled = enabled) { expanded = true }
                .padding(start = spacing.sm, end = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            // Distinct visual badge: color-coded dot with soft radiant ring
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background((currentPresentation?.dotColor ?: colors.accent).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(currentPresentation?.dotColor ?: colors.accent),
                )
            }

            Text(
                text = currentPresentation?.title ?: "Mode",
                style = OpenCodeTypography.label,
                color = if (!enabled) colors.onSurfaceFaint
                    else if (activeAgent != null || currentAgent != null) colors.onSurface
                    else colors.onSurfaceFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp),
            )

            GlyphIcon(
                glyph = Glyph.ChevronDown,
                tint = if (expanded) colors.accent else colors.onSurfaceMuted,
                size = 11.dp,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 270.dp, max = 320.dp),
            offset = selectorMenuOffset(
                openAbove,
                estimatedMenuHeight,
                dims.composerChipTouchTarget,
                spacing.xs,
            ),
            shape = RoundedCornerShape(radius.lg),
            containerColor = colors.surfaceRaised,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(0.5.dp, colors.hairline),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "AGENT MODE",
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceFaint,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.hairline),
            )

            // Capped like the model menu: an uncapped agent menu could
            // run off the bottom of a short screen with no way to scroll.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = spacing.xxs),
            ) {
                selectableAgents.forEach { agent ->
                    val isSelected = agent.id == activeAgent || (activeAgent == null && agent == currentAgent)
                    val presentation = resolveAgentPresentation(agent, colors)
                    val rowBg by animateColorAsState(
                        targetValue = if (isSelected) colors.accent.copy(alpha = 0.10f) else Color.Transparent,
                        animationSpec = motion.tweenQuick(),
                        label = "agent_row_bg",
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.xs, vertical = 2.dp)
                            .clip(RoundedCornerShape(radius.sm))
                            .background(rowBg)
                            .clickable {
                                onSelect(agent.id)
                                expanded = false
                            }
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            // Mini icon badge in circular tinted frame
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(presentation.dotColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                GlyphIcon(
                                    glyph = presentation.icon,
                                    tint = presentation.dotColor,
                                    size = 13.dp,
                                )
                            }

                            // Mode name and capability tag
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                Text(
                                    text = presentation.title,
                                    style = OpenCodeTypography.bodyStrong,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (presentation.badge != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(radius.xs))
                                            .background(presentation.dotColor.copy(alpha = 0.12f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = presentation.badge,
                                            style = OpenCodeTypography.caption,
                                            color = presentation.dotColor,
                                        )
                                    }
                                }
                            }

                            if (isSelected) {
                                GlyphIcon(
                                    glyph = Glyph.Check,
                                    tint = colors.accent,
                                    size = 16.dp,
                                )
                            }
                        }

                        if (presentation.description.isNotBlank()) {
                            Text(
                                text = presentation.description,
                                style = OpenCodeTypography.caption,
                                color = colors.onSurfaceMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 32.dp, top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Model Switcher chip matching Claude / ChatGPT style:
 * - Brand-coded dot or tier indicator.
 * - Human-readable model brand & tier in chip.
 * - Premium popover with model descriptions, context window badges, and active checkmarks.
 */
@Composable
private fun ModelSelector(
    models: List<ModelInfo>,
    activeModel: ModelRef?,
    onSelect: (ModelRef) -> Unit,
    enabled: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    var expanded by remember { mutableStateOf(false) }
    var modelQuery by remember { mutableStateOf("") }

    // Search filters on the rendered name plus the raw identifiers, so
    // "claude", "anthropic" and "gpt-4o" all match; with an empty query
    // the list keeps its default order.
    val filteredModels = remember(models, modelQuery, colors) {
        val query = modelQuery.trim().lowercase()
        if (query.isEmpty()) {
            models
        } else {
            models.filter { model ->
                val presentation = resolveModelPresentation(model, colors)
                listOf(
                    presentation.displayName,
                    presentation.brandTag,
                    model.providerID,
                    model.modelID ?: model.id,
                ).any { it.lowercase().contains(query) }
            }
        }
    }

    val currentModel = remember(models, activeModel) {
        models.firstOrNull { it.ref() == activeModel || (activeModel != null && (it.modelID ?: it.id) == activeModel.id) }
            ?: activeModel?.let { ModelInfo(id = it.id, providerID = it.providerID) }
            ?: models.firstOrNull()
    }

    val currentPresentation = currentModel?.let { resolveModelPresentation(it, colors) }

    // One glyph rotating 0°↔180° (down = open) instead of a static chevron.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.tweenQuick(),
        label = "model_chevron_rotation",
    )
    val chipShape = RoundedCornerShape(radius.full)

    // Drop-up, same as the agent menu: the model chip sits in the composer
    // at the bottom of the screen, so its menu opens above it.
    var anchorTopDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val openAbove = anchorTopDp > 0.dp && anchorTopDp > screenHeightDp / 2
    // Search field ~48dp plus header ~36dp plus one ~56dp row per model,
    // capped like the menu itself.
    val estimatedMenuHeight = (84.dp + 56.dp * filteredModels.size).coerceAtMost(380.dp)

    Box(
        modifier = Modifier.onGloballyPositioned {
            anchorTopDp = with(density) { it.positionInWindow().y.toDp() }
        },
    ) {
        Row(
            modifier = Modifier
                .height(dims.composerChipTouchTarget)
                .clip(chipShape)
                .background(if (expanded) colors.surfacePressed else colors.fill)
                .border(0.5.dp, if (expanded) colors.outline else colors.hairline, chipShape)
                .semantics {
                    role = Role.Button
                    contentDescription = "Model: ${currentPresentation?.displayName ?: "Model"}"
                }
                .clickable(enabled = enabled) { expanded = true }
                .padding(start = spacing.sm, end = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            // Model brand indicator dot
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background((currentPresentation?.brandColor ?: colors.accent).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(currentPresentation?.brandColor ?: colors.accent),
                )
            }

            Text(
                text = currentPresentation?.displayName ?: "Model",
                style = OpenCodeTypography.label,
                color = if (!enabled) colors.onSurfaceFaint
                    else if (activeModel != null || currentModel != null) colors.onSurface
                    else colors.onSurfaceFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )

            GlyphIcon(
                glyph = Glyph.ChevronDown,
                tint = if (expanded) colors.accent else colors.onSurfaceMuted,
                size = 11.dp,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                modelQuery = ""
            },
            modifier = Modifier.widthIn(min = 290.dp, max = 340.dp),
            offset = selectorMenuOffset(
                openAbove,
                estimatedMenuHeight,
                dims.composerChipTouchTarget,
                spacing.xs,
            ),
            shape = RoundedCornerShape(radius.lg),
            containerColor = colors.surfaceRaised,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(0.5.dp, colors.hairline),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "SELECT MODEL",
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceFaint,
                )
                Text(
                    text = "${models.size} available",
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceFaint,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.hairline),
            )

            // Search field: long model lists are otherwise scroll-only.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md, vertical = spacing.xs)
                    .clip(RoundedCornerShape(radius.sm))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GlyphIcon(
                    glyph = Glyph.Search,
                    tint = colors.onSurfaceMuted,
                    size = 14.dp,
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (modelQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.model_search_hint),
                            style = OpenCodeTypography.body,
                            color = colors.onSurfaceFaint,
                        )
                    }
                    BasicTextField(
                        value = modelQuery,
                        onValueChange = { modelQuery = it },
                        textStyle = OpenCodeTypography.body.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.accent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (modelQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { modelQuery = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(
                            glyph = Glyph.Close,
                            tint = colors.onSurfaceMuted,
                            size = 14.dp,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = spacing.xxs),
            ) {
                if (filteredModels.isEmpty()) {
                    Text(
                        text = stringResource(R.string.model_search_no_results, modelQuery.trim()),
                        style = OpenCodeTypography.body,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.md),
                    )
                }
                filteredModels.forEach { model ->
                    val isSelected = model.ref() == activeModel || (activeModel != null && (model.modelID ?: model.id) == activeModel.id)
                    val presentation = resolveModelPresentation(model, colors)
                    val rowBg by animateColorAsState(
                        targetValue = if (isSelected) colors.accent.copy(alpha = 0.10f) else Color.Transparent,
                        animationSpec = motion.tweenQuick(),
                        label = "model_row_bg",
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.xs, vertical = 2.dp)
                            .clip(RoundedCornerShape(radius.sm))
                            .background(rowBg)
                            .clickable {
                                onSelect(model.ref())
                                expanded = false
                                modelQuery = ""
                            }
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                    ) {
                        // Top line: Brand dot, Model display name, Tier badge, Context Window, Checkmark
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(presentation.brandColor),
                            )

                            Text(
                                text = presentation.displayName,
                                style = OpenCodeTypography.bodyStrong,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )

                            if (presentation.tierBadge != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(radius.xs))
                                        .background(presentation.brandColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = presentation.tierBadge,
                                        style = OpenCodeTypography.caption,
                                        color = presentation.brandColor,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Context window badge
                            if (presentation.contextWindow != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(radius.xs))
                                        .background(colors.surfaceSunken)
                                        .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.xs))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = "${presentation.contextWindow} ctx",
                                        style = OpenCodeTypography.caption,
                                        color = colors.onSurfaceMuted,
                                    )
                                }
                            }

                            if (isSelected) {
                                GlyphIcon(
                                    glyph = Glyph.Check,
                                    tint = colors.accent,
                                    size = 16.dp,
                                )
                            }
                        }

                        // Description line
                        if (presentation.description.isNotBlank()) {
                            Text(
                                text = presentation.description,
                                style = OpenCodeTypography.caption,
                                color = colors.onSurfaceMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 11.dp, top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Agent Mode Presentation Helpers ----

private data class AgentPresentation(
    val title: String,
    val description: String,
    val badge: String?,
    val dotColor: Color,
    val icon: Glyph,
)

private fun resolveAgentPresentation(agent: AgentInfo, colors: OpenCodeColors): AgentPresentation {
    val key = "${agent.id} ${agent.name} ${agent.mode.orEmpty()}".lowercase()
    // A colour the engine itself sent for this agent wins; the fallbacks
    // below are design tokens, never hardcoded literals.
    val customColor = parseHexColor(agent.color)
    val customDesc = agent.description?.takeIf { it.isNotBlank() }

    return when {
        "build" in key || "coder" in key || "developer" in key || "code" in key -> AgentPresentation(
            title = agent.name.ifBlank { "Build" }.replaceFirstChar { it.uppercase() },
            description = customDesc ?: "Full access to edit files, run commands & build features",
            badge = "Full Access",
            dotColor = customColor ?: colors.warning,
            icon = Glyph.Terminal,
        )
        "plan" in key || "architect" in key || "designer" in key -> AgentPresentation(
            title = agent.name.ifBlank { "Plan" }.replaceFirstChar { it.uppercase() },
            description = customDesc ?: "Analyzes codebase & designs architecture without edits",
            badge = "Read Only",
            dotColor = customColor ?: colors.syntaxKeyword,
            icon = Glyph.Pencil,
        )
        "chat" in key || "ask" in key || "general" in key || "discuss" in key -> AgentPresentation(
            title = agent.name.ifBlank { "Chat" }.replaceFirstChar { it.uppercase() },
            description = customDesc ?: "Conversational guidance, reasoning & design advice",
            badge = "Discussion",
            dotColor = customColor ?: colors.success,
            icon = Glyph.NewChat,
        )
        "review" in key || "audit" in key -> AgentPresentation(
            title = agent.name.ifBlank { "Review" }.replaceFirstChar { it.uppercase() },
            description = customDesc ?: "Inspects code changes & identifies potential issues",
            badge = "Review",
            dotColor = customColor ?: colors.accent,
            icon = Glyph.Search,
        )
        else -> AgentPresentation(
            title = agent.name.ifBlank { agent.id }.replaceFirstChar { it.uppercase() },
            description = customDesc ?: "Specialized agent configuration",
            badge = agent.mode?.takeIf { it.isNotBlank() }?.uppercase(),
            dotColor = customColor ?: colors.accent,
            icon = Glyph.Sliders,
        )
    }
}

// ---- Model Presentation Helpers ----

private data class ModelPresentation(
    val brandName: String,
    val brandTag: String,
    val displayName: String,
    val tierBadge: String?,
    val contextWindow: String?,
    val description: String,
    val brandColor: Color,
)

/**
 * Brand name, tag, and colour for a model. The colour comes from
 * OpenCodeColors.providerColor (Theme.kt), the single source of truth —
 * no brand hex lives here.
 */
private fun resolveBrand(model: ModelInfo, colors: OpenCodeColors): Triple<String, String, Color> {
    val combined = "${model.providerID} ${model.family.orEmpty()} ${model.name.orEmpty()} ${model.id} ${model.modelID.orEmpty()}".lowercase()
    val brandColor = colors.providerColor(combined)
    return when {
        "anthropic" in combined || "claude" in combined -> Triple("Anthropic", "Claude", brandColor)
        "openai" in combined || "gpt" in combined || "o1" in combined || "o3" in combined -> Triple("OpenAI", "GPT", brandColor)
        "deepseek" in combined -> Triple("DeepSeek", "DeepSeek", brandColor)
        "google" in combined || "gemini" in combined -> Triple("Google", "Gemini", brandColor)
        "meta" in combined || "llama" in combined -> Triple("Meta", "Llama", brandColor)
        "mistral" in combined || "codestral" in combined -> Triple("Mistral", "Mistral", brandColor)
        "qwen" in combined || "alibaba" in combined -> Triple("Alibaba", "Qwen", brandColor)
        else -> {
            val name = model.providerID.ifBlank { "Model" }.replaceFirstChar { it.uppercase() }
            Triple(name, name, brandColor)
        }
    }
}

/**
 * Shared with the transcript's provenance badge ([ProvenanceBadge]), so
 * the model name reads the same in the selector and in the transcript.
 */
internal fun cleanModelName(rawName: String, rawId: String): String {
    val candidate = rawName.ifBlank { rawId }.substringAfterLast('/')
    val lower = candidate.lowercase()
    return when {
        "claude-3-7-sonnet" in lower || "3.7-sonnet" in lower || "3.7 sonnet" in lower -> {
            if ("thinking" in lower) "Claude 3.7 Sonnet (Thinking)" else "Claude 3.7 Sonnet"
        }
        "claude-3-5-sonnet" in lower || "3.5-sonnet" in lower || "3.5 sonnet" in lower -> "Claude 3.5 Sonnet"
        "claude-3-5-haiku" in lower || "3.5-haiku" in lower || "3.5 haiku" in lower -> "Claude 3.5 Haiku"
        "claude-3-opus" in lower || "3-opus" in lower || "3 opus" in lower -> "Claude 3 Opus"
        "gpt-4o-mini" in lower || "4o-mini" in lower -> "GPT-4o mini"
        "gpt-4o" in lower || "4o" in lower -> "GPT-4o"
        "o3-mini" in lower -> "o3-mini"
        "o1-preview" in lower -> "o1-preview"
        "o1-mini" in lower -> "o1-mini"
        lower == "o1" || lower.startsWith("o1-") -> "o1"
        "deepseek-r1" in lower || lower == "r1" -> "DeepSeek R1"
        "deepseek-v3" in lower || "deepseek-chat" in lower -> "DeepSeek V3"
        "gemini-2.0-flash" in lower || "gemini-2-flash" in lower -> "Gemini 2.0 Flash"
        "gemini-1.5-pro" in lower || "gemini-pro" in lower -> "Gemini 1.5 Pro"
        "gemini-1.5-flash" in lower -> "Gemini 1.5 Flash"
        "llama-3.3-70b" in lower -> "Llama 3.3 70B"
        "llama-3.1-70b" in lower -> "Llama 3.1 70B"
        "llama-3.1-8b" in lower -> "Llama 3.1 8B"
        "qwen-2.5-coder" in lower -> "Qwen 2.5 Coder"
        rawName.isNotBlank() -> rawName
        else -> candidate.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }
}

private fun formatContextWindow(limitContext: Long?, rawText: String): String? {
    if (limitContext != null && limitContext > 0) {
        return when {
            limitContext >= 1_000_000L -> "${limitContext / 1_000_000L}M"
            limitContext >= 1_000L -> "${limitContext / 1_000L}k"
            else -> "$limitContext"
        }
    }
    val lower = rawText.lowercase()
    return when {
        "gemini-1.5-pro" in lower || "gemini-2.0" in lower || "gemini-1.5" in lower -> "1M"
        "3-7-sonnet" in lower || "3-5-sonnet" in lower || "haiku" in lower || "claude" in lower -> "200k"
        "gpt-4o" in lower || "4o" in lower || "o1" in lower || "o3" in lower -> "128k"
        "deepseek-r1" in lower || "deepseek" in lower -> "128k"
        "llama-3" in lower -> "128k"
        else -> null
    }
}

private fun resolveTierBadge(text: String): String? {
    val lower = text.lowercase()
    return when {
        "r1" in lower || "o1" in lower || "o3" in lower || "thinking" in lower || "reasoning" in lower || "3-7" in lower || "3.7" in lower -> "Reasoning"
        "mini" in lower || "haiku" in lower || "flash" in lower || "small" in lower -> "Fast"
        "sonnet" in lower || "opus" in lower || "4o" in lower || "pro" in lower || "plus" in lower || "large" in lower -> "Pro"
        else -> null
    }
}

private fun resolveModelDescription(text: String, family: String?): String {
    val lower = text.lowercase()
    return when {
        "3-7-sonnet" in lower || "3.7" in lower -> "Hybrid reasoning & coding with top-tier precision"
        "3-5-sonnet" in lower || ("sonnet" in lower && "3-5" in lower) -> "Flagship intelligence for complex code architecture"
        "haiku" in lower -> "High-speed compact model for quick edits & responses"
        "opus" in lower -> "Deep contextual understanding for intricate problems"
        "gpt-4o-mini" in lower || "4o-mini" in lower -> "Fast, cost-efficient model for focused coding tasks"
        "gpt-4o" in lower || "4o" in lower -> "Versatile flagship model balancing speed & reasoning"
        "o1" in lower || "o3-mini" in lower -> "Advanced chain-of-thought logic & math capabilities"
        "deepseek-r1" in lower || (lower.contains("deepseek") && lower.contains("r1")) -> "Open reasoning model with transparent step-by-step thinking"
        "deepseek" in lower -> "High-performance open model optimized for coding & chat"
        "gemini-2" in lower && "flash" in lower -> "Ultra-fast multimodal model with massive 1M context"
        "gemini" in lower && "pro" in lower -> "Google flagship model with deep 2M token context"
        "gemini" in lower -> "Multimodal intelligence with expanded context capacity"
        "llama" in lower -> "State-of-the-art open weights model for developer workflows"
        "qwen" in lower -> "Specialized open coding model with broad language support"
        "mistral" in lower || "codestral" in lower -> "Efficient code synthesis & reasoned explanations"
        !family.isNullOrBlank() -> "$family architecture optimized for programming"
        else -> "General purpose language model for development"
    }
}

private fun resolveModelPresentation(model: ModelInfo, colors: OpenCodeColors): ModelPresentation {
    val (brandName, brandTag, brandColor) = resolveBrand(model, colors)
    val rawId = model.modelID ?: model.id
    val rawName = model.name.orEmpty()
    val cleanName = cleanModelName(rawName, rawId)
    val combinedText = "$brandName $cleanName $rawName $rawId"
    val tier = resolveTierBadge(combinedText)
    val context = formatContextWindow(model.limit?.context, combinedText)
    val description = resolveModelDescription(combinedText, model.family)

    return ModelPresentation(
        brandName = brandName,
        brandTag = brandTag,
        displayName = cleanName,
        tierBadge = tier,
        contextWindow = context,
        description = description,
        brandColor = brandColor,
    )
}

private fun parseHexColor(colorStr: String?): Color? {
    if (colorStr.isNullOrBlank()) return null
    return try {
        val clean = colorStr.trim().removePrefix("#")
        val hex = when (clean.length) {
            6 -> "FF$clean"
            8 -> clean
            3 -> {
                val r = clean[0]
                val g = clean[1]
                val b = clean[2]
                "FF$r$r$g$g$b$b"
            }
            else -> return null
        }
        val value = hex.toLong(16)
        Color(
            alpha = ((value shr 24) and 0xFF).toInt(),
            red = ((value shr 16) and 0xFF).toInt(),
            green = ((value shr 8) and 0xFF).toInt(),
            blue = (value and 0xFF).toInt(),
        )
    } catch (_: Throwable) {
        null
    }
}

