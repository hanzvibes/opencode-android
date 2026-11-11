package dev.bidzz.opencode.ui.chat

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.CommandInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon

/**
 * Rich model for a slash command displayed in the IDE command palette.
 *
 * Each command carries its canonical name, operator documentation, parameter syntax hint,
 * and an expressive glyph.
 */
@Immutable
data class SlashCommand(
    val name: String,
    val description: String,
    /**
     * Localised description for built-in commands. When set, the palette
     * shows this string instead of [description] (which stays as the
     * English fallback, used for search matching and for engine commands
     * that fall back to a built-in). Engine-advertised commands carry
     * dynamic text, so theirs stays null.
     */
    @StringRes val descriptionRes: Int? = null,
    val parameterHint: String? = null,
    val glyph: Glyph = Glyph.Terminal,
)

/**
 * Standard developer slash commands available in OpenCode.
 *
 * Features pro IDE workflows: /init, /test, /review, /fix alongside workspace utilities.
 */
val BUILTIN_SLASH_COMMANDS: List<SlashCommand> = listOf(
    SlashCommand(
        name = "init",
        description = "Initialize project workspace or configuration",
        descriptionRes = R.string.cmd_init_desc,
        parameterHint = "[template | path]",
        glyph = Glyph.Folder,
    ),
    SlashCommand(
        name = "test",
        description = "Run test suite or target test spec",
        descriptionRes = R.string.cmd_test_desc,
        parameterHint = "[filter]",
        glyph = Glyph.Terminal,
    ),
    SlashCommand(
        name = "review",
        description = "Review changes, git diff, or pull request",
        descriptionRes = R.string.cmd_review_desc,
        parameterHint = "[diff | branch]",
        glyph = Glyph.Code,
    ),
    SlashCommand(
        name = "fix",
        description = "Diagnose errors and apply automatic fixes",
        descriptionRes = R.string.cmd_fix_desc,
        parameterHint = "[issue | error]",
        glyph = Glyph.Pencil,
    ),
    SlashCommand(
        name = "clear",
        description = "Clear conversation messages and reset context",
        descriptionRes = R.string.cmd_clear_desc,
        parameterHint = null,
        glyph = Glyph.NewChat,
    ),
    SlashCommand(
        name = "undo",
        description = "Revert last assistant modifications",
        descriptionRes = R.string.cmd_undo_desc,
        parameterHint = "[step]",
        glyph = Glyph.ArrowLeft,
    ),
    SlashCommand(
        name = "help",
        description = "Show available commands and usage guide",
        descriptionRes = R.string.cmd_help_desc,
        parameterHint = "[command]",
        glyph = Glyph.Info,
    ),
    SlashCommand(
        name = "model",
        description = "Switch active model or provider",
        descriptionRes = R.string.cmd_model_desc,
        parameterHint = "<provider/model>",
        glyph = Glyph.Sliders,
    ),
    SlashCommand(
        name = "agent",
        description = "Switch active agent personality or mode",
        parameterHint = "<name>",
        glyph = Glyph.Sliders,
    ),
)

/**
 * Extracts parameter hints formatted as `[hint]` or `<hint>` embedded in descriptions.
 */
fun parseParameterHint(description: String?): Pair<String?, String?> {
    if (description.isNullOrBlank()) return null to null
    val match = Regex("""(\[[^\]]+\]|<[^>]+>)""").find(description)
    return if (match != null) {
        val hint = match.value
        val cleanDesc = description.replace(hint, "")
            .trim()
            .trim('-', ':', '—', ' ')
        hint to cleanDesc
    } else {
        null to description.trim()
    }
}

/**
 * Combines engine-advertised commands with the rich built-in catalog.
 *
 * Engine definitions take precedence for descriptions while inheriting parameter hints and glyphs.
 */
fun resolveSlashCommands(engineCommands: List<CommandInfo>): List<SlashCommand> {
    val builtInMap = BUILTIN_SLASH_COMMANDS.associateBy { it.name.lowercase() }
    val result = mutableListOf<SlashCommand>()
    val seen = mutableSetOf<String>()

    for (cmd in engineCommands) {
        val nameLower = cmd.name.lowercase().trim().removePrefix("/")
        // The same command can come from the global commands/ dir and the
        // project one (item 4): without this check the palette held two
        // entries with the same name, and its LazyColumn keys by name — a
        // duplicate key crashes composition on open. The first definition
        // wins; an empty name is not a command at all.
        if (nameLower.isEmpty() || !seen.add(nameLower)) continue
        val builtin = builtInMap[nameLower]
        val (parsedHint, parsedDesc) = parseParameterHint(cmd.description)
        val desc = when {
            !parsedDesc.isNullOrBlank() -> parsedDesc
            builtin != null -> builtin.description
            else -> "Execute /$nameLower"
        }
        // Only the built-in fallback carries a localised description: an
        // engine-supplied description is dynamic text and stays as-is.
        val descRes = if (!parsedDesc.isNullOrBlank()) null else builtin?.descriptionRes
        val hint = parsedHint ?: builtin?.parameterHint
        val glyph = builtin?.glyph ?: Glyph.Terminal
        result.add(
            SlashCommand(
                name = nameLower,
                description = desc,
                descriptionRes = descRes,
                parameterHint = hint,
                glyph = glyph,
            )
        )
    }

    for (builtin in BUILTIN_SLASH_COMMANDS) {
        if (!seen.contains(builtin.name.lowercase())) {
            result.add(builtin)
        }
    }

    return result
}

/**
 * Whether the command palette should be active for the current composer draft.
 *
 * Active while the user begins typing a slash command without having entered arguments yet.
 */
fun isCommandPaletteActive(draft: String): Boolean {
    return draft.startsWith("/") && !draft.contains(' ')
}

/**
 * Extracts the search query excluding the leading slash.
 */
fun extractCommandQuery(draft: String): String {
    if (!draft.startsWith("/")) return ""
    return draft.substring(1).substringBefore(' ').trim().lowercase()
}

/**
 * Filters commands against the draft query with intelligent ranking.
 *
 * Exact matches > Prefix matches > Substring matches > Description matches.
 */
fun filterSlashCommands(commands: List<SlashCommand>, draft: String): List<SlashCommand> {
    if (!draft.startsWith("/")) return emptyList()
    if (draft.contains(' ')) return emptyList()
    val query = extractCommandQuery(draft)
    if (query.isEmpty()) return commands

    val exact = mutableListOf<SlashCommand>()
    val prefix = mutableListOf<SlashCommand>()
    val substring = mutableListOf<SlashCommand>()
    val other = mutableListOf<SlashCommand>()

    for (cmd in commands) {
        val nameLower = cmd.name.lowercase()
        when {
            nameLower == query -> exact.add(cmd)
            nameLower.startsWith(query) -> prefix.add(cmd)
            nameLower.contains(query) -> substring.add(cmd)
            cmd.description.contains(query, ignoreCase = true) ||
                (cmd.parameterHint?.contains(query, ignoreCase = true) == true) -> other.add(cmd)
        }
    }

    return exact + prefix + substring + other
}

/**
 * Builds an annotated string highlighting the matched characters of a command name.
 */
fun buildHighlightedCommandName(
    name: String,
    query: String,
    accentColor: Color,
    onSurfaceColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        pushStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold))
        append("/")
        pop()

        if (query.isBlank()) {
            pushStyle(SpanStyle(color = onSurfaceColor, fontWeight = FontWeight.SemiBold))
            append(name)
            pop()
            return@buildAnnotatedString
        }

        val idx = name.indexOf(query, ignoreCase = true)
        if (idx < 0) {
            pushStyle(SpanStyle(color = onSurfaceColor, fontWeight = FontWeight.SemiBold))
            append(name)
            pop()
        } else {
            if (idx > 0) {
                pushStyle(SpanStyle(color = onSurfaceColor, fontWeight = FontWeight.Normal))
                append(name.substring(0, idx))
                pop()
            }
            pushStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold))
            append(name.substring(idx, idx + query.length))
            pop()
            if (idx + query.length < name.length) {
                pushStyle(SpanStyle(color = onSurfaceColor, fontWeight = FontWeight.SemiBold))
                append(name.substring(idx + query.length))
                pop()
            }
        }
    }
}

/**
 * State holder for IDE command palette keyboard navigation and item selection.
 */
@Stable
class CommandPaletteState(
    initialCommands: List<CommandInfo> = emptyList(),
    initialDraft: String = "",
) {
    var rawDraft by mutableStateOf(initialDraft)
    var rawCommands by mutableStateOf(initialCommands)

    val allCommands: List<SlashCommand>
        get() = resolveSlashCommands(rawCommands)

    val isActive: Boolean
        get() = isCommandPaletteActive(rawDraft)

    val matches: List<SlashCommand>
        get() = filterSlashCommands(allCommands, rawDraft)

    var selectedIndex by mutableIntStateOf(0)
        private set

    fun selectNext() {
        if (matches.isNotEmpty()) {
            selectedIndex = (selectedIndex + 1) % matches.size
        }
    }

    fun selectPrevious() {
        if (matches.isNotEmpty()) {
            selectedIndex = if (selectedIndex <= 0) matches.lastIndex else selectedIndex - 1
        }
    }

    fun selectIndex(index: Int) {
        if (matches.isNotEmpty()) {
            selectedIndex = index.coerceIn(0, matches.lastIndex)
        }
    }

    fun currentSelectedCommand(): SlashCommand? {
        if (matches.isEmpty()) return null
        val idx = selectedIndex.coerceIn(0, matches.lastIndex)
        return matches.getOrNull(idx)
    }

    /**
     * Intercepts physical keyboard navigation events (Arrow Up/Down, Tab, Enter, Escape).
     */
    fun handleKeyEvent(
        event: KeyEvent,
        onPick: (String) -> Unit,
        onDismiss: () -> Unit = {},
    ): Boolean {
        if (!isActive) return false
        if (event.type != KeyEventType.KeyDown) {
            return when (event.key) {
                Key.DirectionDown, Key.DirectionUp,
                Key.Tab, Key.Enter, Key.NumPadEnter, Key.Escape -> true
                else -> false
            }
        }

        return when (event.key) {
            Key.DirectionDown -> {
                selectNext()
                true
            }
            Key.DirectionUp -> {
                selectPrevious()
                true
            }
            Key.Tab, Key.Enter, Key.NumPadEnter -> {
                val selected = currentSelectedCommand()
                if (selected != null) {
                    onPick(selected.name)
                    true
                } else {
                    false
                }
            }
            Key.Escape -> {
                onDismiss()
                true
            }
            else -> false
        }
    }
}

/**
 * Remembers a [CommandPaletteState] synchronized with current engine commands and draft text.
 */
@Composable
fun rememberCommandPaletteState(
    commands: List<CommandInfo>,
    draft: String,
): CommandPaletteState {
    val state = remember { CommandPaletteState(commands, draft) }
    state.rawDraft = draft
    state.rawCommands = commands

    val matches = state.matches
    LaunchedEffect(matches) {
        state.selectIndex(0)
    }
    return state
}

/**
 * Pro IDE Command Palette popover anchored above the message composer.
 *
 * Features prefix highlighting, parameter syntax hints, smooth slide/expand animations,
 * keyboard navigation, and single-tap selection.
 */
@Composable
fun CommandSuggestions(
    commands: List<CommandInfo>,
    draft: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val state = rememberCommandPaletteState(commands = commands, draft = draft)
    CommandSuggestions(
        paletteState = state,
        onPick = onPick,
        modifier = modifier,
        onDismiss = onDismiss,
    )
}

/**
 * Overload accepting an explicit [CommandPaletteState] to coordinate with external keyboard input.
 */
@Composable
fun CommandSuggestions(
    paletteState: CommandPaletteState,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val motion = OpenCodeTheme.tokens.motion

    val isVisible = paletteState.isActive
    val matches = paletteState.matches
    val query = extractCommandQuery(paletteState.rawDraft)
    val listState = rememberLazyListState()

    // Keep active selection scrolled into view during keyboard navigation
    LaunchedEffect(paletteState.selectedIndex) {
        if (matches.isNotEmpty() && paletteState.selectedIndex in matches.indices) {
            listState.animateScrollToItem(paletteState.selectedIndex)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = motion.tweenStandard()) +
            slideInVertically(animationSpec = motion.tweenStandard()) { it / 3 } +
            expandVertically(animationSpec = motion.tweenStandard(), expandFrom = Alignment.Bottom),
        exit = fadeOut(animationSpec = motion.tweenQuick()) +
            slideOutVertically(animationSpec = motion.tweenQuick()) { it / 3 } +
            shrinkVertically(animationSpec = motion.tweenQuick(), shrinkTowards = Alignment.Bottom),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(radius.lg)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.xs)
                // Menu elevation comes from the token (Tokens.Elevation.menu),
                // same 8dp as before — no hand-tuned alpha on the shadow.
                .shadow(elevation = OpenCodeTheme.tokens.elevation.menu, shape = shape)
                .clip(shape)
                .background(colors.surfaceRaised)
                .border(OpenCodeTheme.tokens.dimensions.hairline, colors.outline.copy(alpha = 0.30f), shape),
        ) {
            // Header bar
            CommandPaletteHeader(
                matchesCount = matches.size,
                totalCount = paletteState.allCommands.size,
                onDismiss = onDismiss,
            )

            // Content list or empty notification
            if (matches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg, vertical = spacing.md),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                        Text(
                            text = "No command matches \"/$query\"",
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceMuted,
                        )
                        Text(
                            text = "Standard commands: /init, /test, /review, /fix",
                            style = OpenCodeTypography.caption,
                            color = colors.onSurfaceFaint,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                ) {
                    itemsIndexed(matches, key = { _, item -> item.name }) { index, command ->
                        val isSelected = index == paletteState.selectedIndex

                        CommandSuggestionRow(
                            command = command,
                            query = query,
                            isSelected = isSelected,
                            onPick = { onPick(command.name) },
                            onHover = { paletteState.selectIndex(index) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandPaletteHeader(
    matchesCount: Int,
    totalCount: Int,
    onDismiss: () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    // Hoisted out of the .semantics {} block below: stringResource() is
    // @Composable and cannot be called inside a SemanticsPropertyReceiver lambda.
    val closeDescription = stringResource(R.string.chat_status_dialog_close)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.xs))
                        .background(colors.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "/",
                        style = OpenCodeTypography.code,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                    )
                }
                Text(
                    text = "COMMAND PALETTE",
                    style = OpenCodeTypography.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceMuted,
                    letterSpacing = 0.5.sp,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = if (matchesCount == totalCount) "$totalCount available" else "$matchesCount of $totalCount",
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceFaint,
                )

                // Explicit close: dismissing the palette only cancels palette
                // mode and restores the draft that was there before it.
                // 48dp hit area around the 12dp close glyph.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .semantics {
                            contentDescription = closeDescription
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        glyph = Glyph.Close,
                        tint = colors.onSurfaceMuted,
                        size = 12.dp,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(OpenCodeTheme.tokens.dimensions.hairline)
                .background(colors.hairline),
        )
    }
}

@Composable
private fun CommandSuggestionRow(
    command: SlashCommand,
    query: String,
    isSelected: Boolean,
    onPick: () -> Unit,
    onHover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val motion = OpenCodeTheme.tokens.motion

    val rowBackground by animateColorAsState(
        targetValue = if (isSelected) {
            colors.accent.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        animationSpec = motion.tweenQuick(),
        label = "palette_row_bg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(
                role = Role.Button,
                onClick = onPick,
            )
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // Icon badge with rounded background
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(radius.sm))
                .background(if (isSelected) colors.accent.copy(alpha = 0.18f) else colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = command.glyph,
                tint = if (isSelected) colors.accent else colors.onSurfaceMuted,
                size = 14.dp,
            )
        }

        // Command details: name, parameter hint, and description
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = buildHighlightedCommandName(
                        name = command.name,
                        query = query,
                        accentColor = colors.accent,
                        onSurfaceColor = colors.onSurface,
                    ),
                    style = OpenCodeTypography.codeInline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!command.parameterHint.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius.xs))
                            .background(colors.surfaceSunken)
                            .border(OpenCodeTheme.tokens.dimensions.hairline, colors.hairline, RoundedCornerShape(radius.xs))
                            .padding(horizontal = spacing.xs, vertical = 1.dp),
                    ) {
                        Text(
                            text = command.parameterHint,
                            style = OpenCodeTypography.code,
                            fontSize = 11.sp,
                            color = if (isSelected) colors.accent else colors.onSurfaceMuted,
                        )
                    }
                }
            }

            if (command.description.isNotBlank()) {
                Text(
                    text = command.descriptionRes?.let { stringResource(it) } ?: command.description,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Trailing selection hint
        if (isSelected) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(radius.xs))
                    .background(colors.accent.copy(alpha = 0.15f))
                    .padding(horizontal = spacing.xs, vertical = 2.dp),
            ) {
                Text(
                    text = "↵",
                    style = OpenCodeTypography.caption,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                )
            }
        } else {
            GlyphIcon(
                glyph = Glyph.ChevronRight,
                tint = colors.onSurfaceFaint,
                size = 12.dp,
            )
        }
    }
}
