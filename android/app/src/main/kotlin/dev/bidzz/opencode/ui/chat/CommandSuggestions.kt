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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.CommandInfo
import dev.bidzz.opencode.core.api.dto.SkillInfo
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
    /** True for engine skills: picking one mentions it in the draft instead of running a command. */
    val isSkill: Boolean = false,
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
        descriptionRes = R.string.cmd_agent_desc,
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
 * Engine skills are appended after the commands: they are not runnable, so
 * picking one only mentions the skill in the draft. A skill whose name
 * collides with a command is dropped — the palette keys rows by name, and
 * a duplicate key crashes composition on open (same reason commands are
 * deduped above).
 */
fun resolveSlashCommands(
    engineCommands: List<CommandInfo>,
    skills: List<SkillInfo> = emptyList(),
): List<SlashCommand> {
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
        // Record the name too: the skill loop below drops names already
        // seen, and a builtin that is not recorded lets a same-named skill
        // through — two rows keyed "test" crash composition on open.
        if (seen.add(builtin.name.lowercase())) {
            result.add(builtin)
        }
    }

    for (skill in skills) {
        val nameLower = skill.name.lowercase().trim().removePrefix("/")
        // A nameless skill is not mentionable, and a name that collides
        // with a command (or an earlier skill) would duplicate a row key.
        if (nameLower.isEmpty() || !seen.add(nameLower)) continue
        val desc = skill.description?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Use the $nameLower skill"
        result.add(
            SlashCommand(
                name = nameLower,
                description = desc,
                // The tag chip reads as the entry's kind, the way the
                // parameter hint chip does for commands; skills take no
                // arguments, so there is no hint to show instead.
                parameterHint = "skill",
                glyph = Glyph.Sparkles,
                isSkill = true,
            )
        )
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
 *
 * Description matching reads [descriptions] — the localised description per
 * command name (11-F4) — falling back to the command's own (English)
 * description for engine commands and skills, which have no translation.
 * Without the map, searching "/hapus" finds nothing even in Indonesian,
 * because every builtin description is English.
 */
fun filterSlashCommands(
    commands: List<SlashCommand>,
    draft: String,
    descriptions: Map<String, String> = emptyMap(),
): List<SlashCommand> {
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
        val description = descriptions[nameLower] ?: cmd.description
        when {
            nameLower == query -> exact.add(cmd)
            nameLower.startsWith(query) -> prefix.add(cmd)
            nameLower.contains(query) -> substring.add(cmd)
            description.contains(query, ignoreCase = true) ||
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
    initialSkills: List<SkillInfo> = emptyList(),
) {
    var rawDraft by mutableStateOf(initialDraft)
    var rawCommands by mutableStateOf(initialCommands)
    var rawSkills by mutableStateOf(initialSkills)

    /**
     * Localised descriptions per command name, set by the composable that
     * owns this state (11-F4). Description matching in [matches] reads
     * these; engine commands and skills have no translation and fall back
     * to their own description.
     */
    var localizedDescriptions: Map<String, String> by mutableStateOf(emptyMap())

    val allCommands: List<SlashCommand>
        get() = resolveSlashCommands(rawCommands, rawSkills)

    val isActive: Boolean
        get() = isCommandPaletteActive(rawDraft)

    val matches: List<SlashCommand>
        get() = filterSlashCommands(allCommands, rawDraft, localizedDescriptions)

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
     *
     * [onSkillPick] receives a picked skill's name; it defaults to [onPick]
     * so callers that treat every row as a command keep working, but the
     * chat composer passes a different handler: a skill is mentioned in
     * the draft as plain text, not run as `/name`.
     */
    fun handleKeyEvent(
        event: KeyEvent,
        onPick: (String) -> Unit,
        onDismiss: () -> Unit = {},
        onSkillPick: (String) -> Unit = onPick,
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
                    if (selected.isSkill) onSkillPick(selected.name) else onPick(selected.name)
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
 * Remembers a [CommandPaletteState] synchronized with current engine commands, skills and draft text.
 */
@Composable
fun rememberCommandPaletteState(
    commands: List<CommandInfo>,
    draft: String,
    skills: List<SkillInfo> = emptyList(),
    localizedDescriptions: Map<String, String> = emptyMap(),
): CommandPaletteState {
    val state = remember { CommandPaletteState(commands, draft, skills) }
    state.rawDraft = draft
    state.rawCommands = commands
    state.rawSkills = skills
    state.localizedDescriptions = localizedDescriptions

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
 * keyboard navigation, and single-tap selection. [onSkillPick] receives a
 * picked skill's name; it defaults to [onPick] so a skill is treated like
 * a command unless the caller says otherwise.
 */
@Composable
fun CommandSuggestions(
    commands: List<CommandInfo>,
    draft: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    skills: List<SkillInfo> = emptyList(),
    onSkillPick: (String) -> Unit = onPick,
) {
    val state = rememberCommandPaletteState(commands = commands, draft = draft, skills = skills)
    CommandSuggestions(
        paletteState = state,
        onPick = onPick,
        modifier = modifier,
        onDismiss = onDismiss,
        onSkillPick = onSkillPick,
    )
}

/** Rows fully visible in the command palette at once. */
private const val PALETTE_VISIBLE_ROWS = 4

/**
 * One [CommandSuggestionRow] is 56dp tall: 8dp vertical padding on each
 * side, and the details column — name line 20sp + 2dp gap + description
 * line 18sp — is taller than the 28dp icon badge and the trailing hint,
 * so the content height is 40dp and the row is 16dp + 40dp. The old
 * 240.dp cap was 4.29 rows, so the last row was sliced mid-glyph.
 */
private val PALETTE_ROW_CONTENT_HEIGHT = 40.dp
private val PALETTE_ROW_VERTICAL_PADDING = 16.dp

/**
 * Overload accepting an explicit [CommandPaletteState] to coordinate with external keyboard input.
 */
@Composable
fun CommandSuggestions(
    paletteState: CommandPaletteState,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onSkillPick: (String) -> Unit = onPick,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val motion = OpenCodeTheme.tokens.motion

    val isVisible = paletteState.isActive
    val matches = paletteState.matches
    val query = extractCommandQuery(paletteState.rawDraft)
    val listState = rememberLazyListState()

    // Palette height is a whole number of rows (PR #9 follow-up): the cap
    // must never slice the last row in half. The cap is measured from the
    // first row at runtime so it stays exact at any font scale; the
    // fallback is the same four rows computed from the font scale, so the
    // first frame is already right and the measured value only confirms
    // it — no snap, and no large empty gap when fewer rows match.
    val density = LocalDensity.current
    var paletteRowHeight by remember { mutableStateOf<Dp?>(null) }
    val paletteMaxHeight = paletteRowHeight?.times(PALETTE_VISIBLE_ROWS)
        ?: (PALETTE_ROW_CONTENT_HEIGHT * density.fontScale + PALETTE_ROW_VERTICAL_PADDING)
            .times(PALETTE_VISIBLE_ROWS)

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
                            text = stringResource(R.string.commands_no_match, "/$query"),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceMuted,
                        )
                        Text(
                            text = stringResource(R.string.commands_standard, "/init, /test, /review, /fix"),
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
                        .heightIn(max = paletteMaxHeight),
                ) {
                    itemsIndexed(matches, key = { _, item -> item.name }) { index, command ->
                        val isSelected = index == paletteState.selectedIndex

                        CommandSuggestionRow(
                            command = command,
                            query = query,
                            isSelected = isSelected,
                            onPick = { if (command.isSkill) onSkillPick(command.name) else onPick(command.name) },
                            onHover = { paletteState.selectIndex(index) },
                            modifier = Modifier
                                .animateItem()
                                .then(
                                    if (index == 0) {
                                        Modifier.onGloballyPositioned { coords ->
                                            paletteRowHeight =
                                                with(density) { coords.size.height.toDp() }
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
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
                        color = colors.accentText,
                    )
                }
                Text(
                    text = stringResource(R.string.commands_palette).uppercase(),
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
            // 47-F2: the keyboard-highlighted command is the Enter target —
            // TalkBack announces it as selected, not a plain button.
            .selectable(selected = isSelected, role = Role.Button, onClick = onPick)
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
                        accentColor = colors.accentText,
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
                            // 47-F1: the selected parameter hint is text —
                            // accent is only 3.75:1 on sunken in light.
                            color = if (isSelected) colors.accentText else colors.onSurfaceMuted,
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
                    color = colors.accentText,
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
