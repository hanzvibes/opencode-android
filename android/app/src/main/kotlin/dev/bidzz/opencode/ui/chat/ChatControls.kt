package dev.bidzz.opencode.ui.chat

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import dev.bidzz.opencode.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
 * Session controls — the agent mode selector — sitting between the transcript and the composer.
 *
 * The model picker used to live here too; in Phase 2 it moved to the
 * header (audit 8), so this row is agent-only.
 *
 * Designed to match ChatGPT and Claude model selectors:
 * - Distinct visual badges for agent modes (Build, Plan, Chat) with color-coded dot and mini icons.
 */
@Composable
fun ChatControls(
    agents: List<AgentInfo>,
    activeAgent: String?,
    onAgent: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** False while the agent is working: switching mode mid-run is a no-op. */
    enabled: Boolean = true,
) {
    if (agents.isEmpty()) return

    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AgentSelector(
            agents = agents,
            activeAgent = activeAgent,
            onSelect = onAgent,
            enabled = enabled,
        )
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

/** One row of a selector menu: a section header or a selectable entry. */
private sealed interface SelectorEntry {
    data class Header(val text: String) : SelectorEntry
    data class AgentRow(val agent: AgentInfo) : SelectorEntry
    data class ModelRow(val model: ModelInfo) : SelectorEntry
}

/**
 * The engine may advertise the same model twice (same provider + model
 * id) or the same agent id twice; the menu would then show the row twice.
 * Drop the later copies, keeping the first.
 */
internal fun dedupeModels(models: List<ModelInfo>): List<ModelInfo> =
    models.distinctBy { "${it.providerID}/${it.modelID ?: it.id}" }

internal fun dedupeAgents(agents: List<AgentInfo>): List<AgentInfo> =
    agents.distinctBy { it.id }

/**
 * Recent selections, newest first, kept in order in one preference
 * string (a string set would lose the order). Shared with the empty
 * state's dismissed starters: same "opencode_chat" file.
 */
private const val RECENT_MODELS_KEY = "recent_models"
private const val RECENT_AGENTS_KEY = "recent_agents"
private const val MAX_RECENT_SELECTIONS = 3

private fun readRecentRefs(prefs: SharedPreferences, key: String): List<String> =
    prefs.getString(key, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

/** Prepends [ref] to the stored recents for [key] and returns the new list. */
private fun storeRecentRef(prefs: SharedPreferences, key: String, ref: String): List<String> {
    val next = (listOf(ref) + readRecentRefs(prefs, key).filter { it != ref })
        .take(MAX_RECENT_SELECTIONS)
    prefs.edit().putString(key, next.joinToString("\n")).apply()
    return next
}

private fun modelRefKey(model: ModelInfo): String =
    "${model.providerID}/${model.modelID ?: model.id}"

/**
 * Agent / Mode Selector chip with a distinct color-coded indicator badge and
 * rich popover menu detailing agent capabilities, permissions, and active selection.
 *
 * Menu behaviour (audit 15): search, recents first, scroll-to-selected on
 * open, and the lift offset corrected with the menu's measured height so it
 * neither falls short of the chip nor covers it.
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
    var agentQuery by remember { mutableStateOf("") }

    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("opencode_chat", Context.MODE_PRIVATE)
    }
    var recentAgentIds by remember { mutableStateOf(readRecentRefs(prefs, RECENT_AGENTS_KEY)) }

    val selectableAgents = remember(agents) {
        val filtered = agents.filter { it.hidden != true && it.mode != "subagent" }
        dedupeAgents(if (filtered.isNotEmpty()) filtered else agents)
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

    // Menu entries: with a search query the list is flat and filtered;
    // otherwise recent agents come first under a RECENT header.
    val recentHeader = stringResource(R.string.chat_recent)
    val query = agentQuery.trim().lowercase()
    // Resolved agent descriptions for search matching. Resolved here, not
    // inside remember: remember's calculation forbids @Composable calls
    // (@DisallowComposableCalls), so stringResource cannot run there — not
    // even in a plain loop. The map is a remember key so a language change
    // re-filters on the new strings.
    val agentDescriptions = selectableAgents.associate { agent ->
        val presentation = resolveAgentPresentation(agent, colors)
        agent.id to (presentation.descriptionText ?: stringResource(presentation.descriptionRes))
    }
    val entries: List<SelectorEntry> = remember(selectableAgents, recentAgentIds, query, recentHeader, colors, agentDescriptions) {
        if (query.isNotEmpty()) {
            selectableAgents.filter { agent ->
                val presentation = resolveAgentPresentation(agent, colors)
                listOf(
                    presentation.title,
                    agentDescriptions[agent.id].orEmpty(),
                    agent.id,
                    agent.name,
                    agent.mode.orEmpty(),
                ).any { it.lowercase().contains(query) }
            }.map { SelectorEntry.AgentRow(it) }
        } else {
            val recents = recentAgentIds.mapNotNull { id ->
                selectableAgents.firstOrNull { it.id == id }
            }
            val rest = selectableAgents.filter { agent -> recents.none { it.id == agent.id } }
            buildList {
                if (recents.isNotEmpty()) {
                    add(SelectorEntry.Header(recentHeader))
                    recents.forEach { add(SelectorEntry.AgentRow(it)) }
                }
                rest.forEach { add(SelectorEntry.AgentRow(it)) }
            }
        }
    }

    // Scroll-to-selected: opening the menu lands on the active agent
    // instead of the top of the list. The selected row carries the
    // requester (see the row below); bringIntoView scrolls the menu's
    // Column just enough to reveal it.
    val hasSelectedEntry = entries.any { entry ->
        entry is SelectorEntry.AgentRow &&
            (entry.agent.id == activeAgent || (activeAgent == null && entry.agent == currentAgent))
    }
    val selectedRowBringIntoView = remember { BringIntoViewRequester() }
    val menuScrollState = rememberScrollState()
    LaunchedEffect(expanded) {
        if (expanded && hasSelectedEntry) {
            try {
                selectedRowBringIntoView.bringIntoView()
            } catch (_: IllegalStateException) {
                // The selected row left the composition before the scroll
                // ran; the menu simply opens at the top.
            }
        }
    }

    // Drop-up: the composer chip sits at the bottom of the screen, so the
    // menu opens above it when the chip is in the bottom half of the screen.
    var anchorTopDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val openAbove = anchorTopDp > 0.dp && anchorTopDp > screenHeightDp / 2
    // Header ~36dp plus one ~64dp row per agent, capped like the menu itself.
    val estimatedMenuHeight = (36.dp + 64.dp * selectableAgents.size).coerceAtMost(380.dp)
    // The offset is corrected with the menu's measured height once laid
    // out: the estimate falls short (or overshoots) whenever rows wrap or
    // the search/recents sections change the height, and a wrong estimate
    // leaves a gap above the chip or covers the chip itself.
    var measuredMenuHeight by remember { mutableStateOf<Dp?>(null) }
    val menuHeightForOffset = measuredMenuHeight ?: estimatedMenuHeight

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
            onDismissRequest = {
                expanded = false
                agentQuery = ""
                measuredMenuHeight = null
            },
            modifier = Modifier
                .widthIn(min = 270.dp, max = 320.dp)
                .onGloballyPositioned { coords ->
                    measuredMenuHeight = with(density) { coords.size.height.toDp() }
                },
            offset = selectorMenuOffset(
                openAbove,
                menuHeightForOffset,
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

            // Search field: long agent lists are otherwise scroll-only.
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
                    if (agentQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.agent_search_hint),
                            style = OpenCodeTypography.body,
                            color = colors.onSurfaceFaint,
                        )
                    }
                    BasicTextField(
                        value = agentQuery,
                        onValueChange = { agentQuery = it },
                        textStyle = OpenCodeTypography.body.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.accent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (agentQuery.isNotEmpty()) {
                    // 48dp hit area around the 14dp clear glyph.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { agentQuery = "" },
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

            // A LazyColumn cannot live here: DropdownMenu sizes its content
            // with intrinsic measurements, and a lazy list (a SubcomposeLayout)
            // throws IllegalStateException on any intrinsic query — opening
            // the menu crashed the app. A plain Column with a scroll state
            // measures fine; the lists are short (tens of rows at most).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(menuScrollState)
                    .padding(vertical = spacing.xxs),
            ) {
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.agent_search_no_results, agentQuery.trim()),
                        style = OpenCodeTypography.body,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.md),
                    )
                }
                entries.forEach { entry ->
                    when (entry) {
                        is SelectorEntry.Header -> {
                            Text(
                                text = entry.text,
                                style = OpenCodeTypography.caption,
                                color = colors.onSurfaceFaint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                            )
                        }
                        is SelectorEntry.AgentRow -> {
                            val agent = entry.agent
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
                                    .then(if (isSelected) Modifier.bringIntoViewRequester(selectedRowBringIntoView) else Modifier)
                                    .clickable {
                                        recentAgentIds = storeRecentRef(prefs, RECENT_AGENTS_KEY, agent.id)
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
                                        // Neutral badge, same as the model tier badge:
                                        // dot-coloured text on a dot-coloured tint fails
                                        // AA (light accent #007AFF is 4.02:1 on white
                                        // before the tint). The agent colour is carried
                                        // by the dot in this row.
                                        val badgeText = presentation.badgeText
                                            ?: presentation.badgeRes?.let { stringResource(it) }
                                        if (badgeText != null) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(radius.xs))
                                                    .background(colors.surfaceSunken)
                                                    .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.xs))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    style = OpenCodeTypography.caption,
                                                    color = colors.onSurfaceMuted,
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

                                // Agent descriptions come from the agent definition
                                // (engine data) when present; otherwise the localised
                                // template for this agent kind.
                                Text(
                                    text = presentation.descriptionText ?: stringResource(presentation.descriptionRes),
                                    style = OpenCodeTypography.caption,
                                    color = colors.onSurfaceMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 32.dp, top = 2.dp),
                                )
                            }
                        }
                        is SelectorEntry.ModelRow -> Unit
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
 *
 * Shared with the header: [ChatHeader] calls this directly, because the
 * picker lives in the 56dp header row (audit 8), not in the composer
 * controls. Menu behaviour (audit 15): search, recents first, provider
 * grouping, scroll-to-selected on open, and the lift offset corrected with
 * the menu's measured height so it neither falls short of the chip nor
 * covers it.
 */
@Composable
internal fun ModelSelector(
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

    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("opencode_chat", Context.MODE_PRIVATE)
    }
    var recentModelRefs by remember { mutableStateOf(readRecentRefs(prefs, RECENT_MODELS_KEY)) }

    // Search filters on the rendered name plus the raw identifiers, so
    // "claude", "anthropic" and "gpt-4o" all match; with an empty query
    // the list keeps its default order.
    val dedupedModels = remember(models) { dedupeModels(models) }

    val filteredModels = remember(dedupedModels, modelQuery, colors) {
        val query = modelQuery.trim().lowercase()
        if (query.isEmpty()) {
            dedupedModels
        } else {
            dedupedModels.filter { model ->
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

    // Menu entries: a search query gives a flat filtered list; otherwise
    // recent models come first under a RECENT header and the rest are
    // grouped under their provider, in first-appearance order.
    val recentHeader = stringResource(R.string.chat_recent)
    val query = modelQuery.trim().lowercase()
    val entries: List<SelectorEntry> = remember(dedupedModels, filteredModels, recentModelRefs, query, recentHeader, colors) {
        if (query.isNotEmpty()) {
            filteredModels.map { SelectorEntry.ModelRow(it) }
        } else {
            val recents = recentModelRefs.mapNotNull { ref ->
                dedupedModels.firstOrNull { modelRefKey(it) == ref }
            }
            val rest = dedupedModels.filter { model -> recents.none { it.ref() == model.ref() } }
            buildList {
                if (recents.isNotEmpty()) {
                    add(SelectorEntry.Header(recentHeader))
                    recents.forEach { add(SelectorEntry.ModelRow(it)) }
                }
                rest.groupBy { resolveBrand(it, colors).first }.forEach { (brand, group) ->
                    add(SelectorEntry.Header(brand.uppercase()))
                    group.forEach { add(SelectorEntry.ModelRow(it)) }
                }
            }
        }
    }

    // Scroll-to-selected: opening the menu lands on the active model
    // instead of the top of the list. The selected row carries the
    // requester (see the row below); bringIntoView scrolls the menu's
    // Column just enough to reveal it.
    val hasSelectedEntry = entries.any { entry ->
        entry is SelectorEntry.ModelRow &&
            (entry.model.ref() == activeModel ||
                (activeModel != null && (entry.model.modelID ?: entry.model.id) == activeModel.id))
    }
    val selectedRowBringIntoView = remember { BringIntoViewRequester() }
    val menuScrollState = rememberScrollState()
    LaunchedEffect(expanded) {
        if (expanded && hasSelectedEntry) {
            try {
                selectedRowBringIntoView.bringIntoView()
            } catch (_: IllegalStateException) {
                // The selected row left the composition before the scroll
                // ran; the menu simply opens at the top.
            }
        }
    }

    val currentModel = remember(dedupedModels, activeModel) {
        dedupedModels.firstOrNull { it.ref() == activeModel || (activeModel != null && (it.modelID ?: it.id) == activeModel.id) }
            ?: activeModel?.let { ModelInfo(id = it.id, providerID = it.providerID) }
            ?: dedupedModels.firstOrNull()
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
    // at the bottom of the screen, so its menu opens above it. (From the
    // header the chip is at the top instead, and the menu opens below it —
    // the direction follows the anchor either way.)
    var anchorTopDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val openAbove = anchorTopDp > 0.dp && anchorTopDp > screenHeightDp / 2
    // Search field ~48dp plus header ~36dp plus one ~56dp row per model,
    // capped like the menu itself.
    val estimatedMenuHeight = (84.dp + 56.dp * filteredModels.size).coerceAtMost(380.dp)
    // The offset is corrected with the menu's measured height once laid
    // out: the estimate falls short (or overshoots) whenever rows wrap or
    // the search/recents/group sections change the height, and a wrong
    // estimate leaves a gap above the chip or covers the chip itself.
    var measuredMenuHeight by remember { mutableStateOf<Dp?>(null) }
    val menuHeightForOffset = measuredMenuHeight ?: estimatedMenuHeight

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
                measuredMenuHeight = null
            },
            modifier = Modifier
                .widthIn(min = 290.dp, max = 340.dp)
                .onGloballyPositioned { coords ->
                    measuredMenuHeight = with(density) { coords.size.height.toDp() }
                },
            offset = selectorMenuOffset(
                openAbove,
                menuHeightForOffset,
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
                    text = "${dedupedModels.size} available",
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
                    // 48dp hit area around the 14dp clear glyph.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
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

            // A LazyColumn cannot live here: DropdownMenu sizes its content
            // with intrinsic measurements, and a lazy list (a SubcomposeLayout)
            // throws IllegalStateException on any intrinsic query — opening
            // the menu crashed the app. A plain Column with a scroll state
            // measures fine; the lists are short (tens of rows at most).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(menuScrollState)
                    .padding(vertical = spacing.xxs),
            ) {
                if (entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.model_search_no_results, modelQuery.trim()),
                        style = OpenCodeTypography.body,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.md, vertical = spacing.md),
                    )
                }
                entries.forEach { entry ->
                    when (entry) {
                        is SelectorEntry.Header -> {
                            Text(
                                text = entry.text,
                                style = OpenCodeTypography.caption,
                                color = colors.onSurfaceFaint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                            )
                        }
                        is SelectorEntry.ModelRow -> {
                            val model = entry.model
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
                                    .then(if (isSelected) Modifier.bringIntoViewRequester(selectedRowBringIntoView) else Modifier)
                                    .clickable {
                                        recentModelRefs = storeRecentRef(prefs, RECENT_MODELS_KEY, modelRefKey(model))
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

                                    // Tier badge in the same neutral style as the context
                                    // badge below: brand-coloured text on a brand tint
                                    // failed WCAG AA for most providers (e.g. Mistral
                                    // 3.29:1, Nvidia 3.39:1 in light). The brand is
                                    // already carried by the dot in this row.
                                    if (presentation.tierBadgeRes != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(radius.xs))
                                                .background(colors.surfaceSunken)
                                                .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.xs))
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = stringResource(presentation.tierBadgeRes),
                                                style = OpenCodeTypography.caption,
                                                color = colors.onSurfaceMuted,
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
                                Text(
                                    text = presentation.descriptionArg?.let { stringResource(presentation.descriptionRes, it) }
                                        ?: stringResource(presentation.descriptionRes),
                                    style = OpenCodeTypography.caption,
                                    color = colors.onSurfaceMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 11.dp, top = 2.dp),
                                )
                            }
                        }
                        is SelectorEntry.AgentRow -> Unit
                    }
                }
            }
        }
    }
}

// ---- Agent Mode Presentation Helpers ----

private data class AgentPresentation(
    val title: String,
    /** Engine-supplied description; null means use [descriptionRes]. */
    val descriptionText: String?,
    @StringRes val descriptionRes: Int,
    /** Fixed badge template; null means use [badgeText] (agent mode). */
    @StringRes val badgeRes: Int?,
    val badgeText: String?,
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
            descriptionText = customDesc,
            descriptionRes = R.string.agent_desc_build,
            badgeRes = R.string.agent_badge_full_access,
            badgeText = null,
            dotColor = customColor ?: colors.warning,
            icon = Glyph.Terminal,
        )
        "plan" in key || "architect" in key || "designer" in key -> AgentPresentation(
            title = agent.name.ifBlank { "Plan" }.replaceFirstChar { it.uppercase() },
            descriptionText = customDesc,
            descriptionRes = R.string.agent_desc_plan,
            badgeRes = R.string.agent_badge_read_only,
            badgeText = null,
            dotColor = customColor ?: colors.syntaxKeyword,
            icon = Glyph.Pencil,
        )
        "chat" in key || "ask" in key || "general" in key || "discuss" in key -> AgentPresentation(
            title = agent.name.ifBlank { "Chat" }.replaceFirstChar { it.uppercase() },
            descriptionText = customDesc,
            descriptionRes = R.string.agent_desc_chat,
            badgeRes = R.string.agent_badge_discussion,
            badgeText = null,
            dotColor = customColor ?: colors.success,
            icon = Glyph.NewChat,
        )
        "review" in key || "audit" in key -> AgentPresentation(
            title = agent.name.ifBlank { "Review" }.replaceFirstChar { it.uppercase() },
            descriptionText = customDesc,
            descriptionRes = R.string.agent_desc_review,
            badgeRes = R.string.agent_badge_review,
            badgeText = null,
            dotColor = customColor ?: colors.accent,
            icon = Glyph.Search,
        )
        else -> AgentPresentation(
            title = agent.name.ifBlank { agent.id }.replaceFirstChar { it.uppercase() },
            descriptionText = customDesc,
            descriptionRes = R.string.agent_desc_default,
            badgeRes = null,
            badgeText = agent.mode?.takeIf { it.isNotBlank() }?.uppercase(),
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
    @StringRes val tierBadgeRes: Int?,
    val contextWindow: String?,
    @StringRes val descriptionRes: Int,
    /** Family name for the one description template that embeds it. */
    val descriptionArg: String? = null,
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
        "nvidia" in combined || "nemotron" in combined -> Triple("NVIDIA", "Nemotron", brandColor)
        else -> {
            val name = model.providerID.ifBlank { "Model" }.replaceFirstChar { it.uppercase() }
            Triple(name, name, brandColor)
        }
    }
}

/**
 * Turns a raw model id into a readable name: "nemotron-3.5-light" →
 * "Nemotron 3.5 Light". Words are capitalised, version numbers kept.
 */
private fun prettifyModelId(candidate: String): String {
    return candidate.split('-', ' ', '_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            if (token.any { it.isDigit() }) token
            else token.replaceFirstChar { it.uppercase() }
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
        // Current generation (added Phase 2): Claude 4, GPT-5, Nemotron.
        // No "Claude 4 / GPT-5 / Nemotron" entry existed before — the old
        // table stopped at Claude 3.7 / GPT-4o — so an engine advertising
        // them fell through to the raw id.
        "claude-opus-4" in lower || "opus-4" in lower || "opus 4" in lower -> {
            if ("thinking" in lower) "Claude Opus 4 (Thinking)" else "Claude Opus 4"
        }
        "claude-sonnet-4" in lower || "sonnet-4" in lower || "sonnet 4" in lower -> "Claude Sonnet 4"
        "claude-haiku-4" in lower || "haiku-4" in lower || "haiku 4" in lower -> "Claude Haiku 4"
        "gpt-5-mini" in lower -> "GPT-5 mini"
        "gpt-5" in lower -> "GPT-5"
        "nemotron" in lower -> prettifyModelId(candidate)
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
        "gpt-5" in lower -> "400k"
        "gemini-1.5-pro" in lower || "gemini-2.0" in lower || "gemini-1.5" in lower -> "1M"
        "3-7-sonnet" in lower || "3-5-sonnet" in lower || "haiku" in lower || "claude" in lower -> "200k"
        "gpt-4o" in lower || "4o" in lower || "o1" in lower || "o3" in lower -> "128k"
        "deepseek-r1" in lower || "deepseek" in lower -> "128k"
        "llama-3" in lower -> "128k"
        else -> null
    }
}

private fun resolveTierBadge(text: String): Int? {
    val lower = text.lowercase()
    return when {
        "gpt-5" in lower -> R.string.model_tier_pro
        "r1" in lower || "o1" in lower || "o3" in lower || "thinking" in lower || "reasoning" in lower || "3-7" in lower || "3.7" in lower -> R.string.model_tier_reasoning
        "mini" in lower || "haiku" in lower || "flash" in lower || "small" in lower -> R.string.model_tier_fast
        "sonnet" in lower || "opus" in lower || "4o" in lower || "pro" in lower || "plus" in lower || "large" in lower -> R.string.model_tier_pro
        else -> null
    }
}

/**
 * A localised model description: a string resource, plus the family name
 * for the one template that embeds it.
 */
private data class ModelDescription(@StringRes val res: Int, val arg: String? = null)

private fun resolveModelDescription(text: String, family: String?): ModelDescription {
    val lower = text.lowercase()
    return when {
        "claude-opus-4" in lower || "opus-4" in lower -> ModelDescription(R.string.model_desc_opus4)
        "claude-sonnet-4" in lower || "sonnet-4" in lower -> ModelDescription(R.string.model_desc_sonnet4)
        "claude-haiku-4" in lower || "haiku-4" in lower -> ModelDescription(R.string.model_desc_haiku4)
        "gpt-5" in lower -> ModelDescription(R.string.model_desc_gpt5)
        "nemotron" in lower -> ModelDescription(R.string.model_desc_nemotron)
        "3-7-sonnet" in lower || "3.7" in lower -> ModelDescription(R.string.model_desc_sonnet37)
        "3-5-sonnet" in lower || ("sonnet" in lower && "3-5" in lower) -> ModelDescription(R.string.model_desc_sonnet35)
        "haiku" in lower -> ModelDescription(R.string.model_desc_haiku)
        "opus" in lower -> ModelDescription(R.string.model_desc_opus)
        "gpt-4o-mini" in lower || "4o-mini" in lower -> ModelDescription(R.string.model_desc_gpt4o_mini)
        "gpt-4o" in lower || "4o" in lower -> ModelDescription(R.string.model_desc_gpt4o)
        "o1" in lower || "o3-mini" in lower -> ModelDescription(R.string.model_desc_o1)
        "deepseek-r1" in lower || (lower.contains("deepseek") && lower.contains("r1")) -> ModelDescription(R.string.model_desc_deepseek_r1)
        "deepseek" in lower -> ModelDescription(R.string.model_desc_deepseek)
        "gemini-2" in lower && "flash" in lower -> ModelDescription(R.string.model_desc_gemini_flash)
        "gemini" in lower && "pro" in lower -> ModelDescription(R.string.model_desc_gemini_pro)
        "gemini" in lower -> ModelDescription(R.string.model_desc_gemini)
        "llama" in lower -> ModelDescription(R.string.model_desc_llama)
        "qwen" in lower -> ModelDescription(R.string.model_desc_qwen)
        "mistral" in lower || "codestral" in lower -> ModelDescription(R.string.model_desc_mistral)
        !family.isNullOrBlank() -> ModelDescription(R.string.model_desc_family, family)
        else -> ModelDescription(R.string.model_desc_default)
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
        tierBadgeRes = tier,
        contextWindow = context,
        descriptionRes = description.res,
        descriptionArg = description.arg,
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

