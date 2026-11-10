package dev.bidzz.opencode.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import dev.bidzz.opencode.R
import dev.bidzz.opencode.ui.sessions.label
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.design.Status
import dev.bidzz.opencode.ui.chat.Role
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.IconAction
import dev.bidzz.opencode.ui.IconBadge
import dev.bidzz.opencode.ui.message
import dev.bidzz.opencode.ui.sessions.SessionRow
import kotlinx.coroutines.launch

/**
 * The main screen: a conversation with the agent.
 *
 * Structure is a plain column — header, scrolling transcript, composer — inside a
 * side drawer that holds navigation and recent sessions. Nothing here is decorative:
 * there are no cards, gradients or shadows, per the anti-slop rules in `plan.md` §12.
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    sessions: List<SessionRow>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPermissionReply: (String) -> Unit,
    onAgent: (String) -> Unit,
    onModel: (dev.bidzz.opencode.core.api.dto.ModelRef) -> Unit,
    onStatusClick: () -> Unit,
    onNewSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onDrawerOpen: () -> Unit,
    onSessionsClick: () -> Unit,
    onProjectClick: () -> Unit,
    onFilesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Follow the conversation as it grows, but only while the reader is already at the bottom,
    // so scrolling up during a stream is not yanked back down.
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty() && nearBottom) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    /** Closes the drawer, then runs [action]; the drawer must not stay open over the next screen. */
    fun closeThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                projectTitle = headerTitle(state.workspacePath),
                currentSessionID = state.sessionID,
                sessions = sessions,
                onNewSession = { closeThen(onNewSession) },
                onOpenSession = { id -> closeThen { onOpenSession(id) } },
                onAllSessions = { closeThen(onSessionsClick) },
                onFiles = { closeThen(onFilesClick) },
                onProject = { closeThen(onProjectClick) },
                onSettings = { closeThen(onSettingsClick) },
            )
        },
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .imePadding(),
        ) {
            ChatHeader(
                title = headerTitle(state.workspacePath),
                engineState = state.engineState,
                busy = state.busy,
                onMenu = {
                    onDrawerOpen()
                    scope.launch { drawerState.open() }
                },
                onStatusClick = onStatusClick,
                onNewSession = onNewSession,
            )

            Box(Modifier.weight(1f)) {
                if (state.messages.isEmpty()) {
                    EmptyChat(
                        engineReady = state.workspaceReady,
                        engineState = state.engineState,
                        busy = state.busy,
                        onStatusClick = onStatusClick,
                        onSuggestion = onDraftChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = spacing.lg,
                            end = spacing.lg,
                            top = spacing.md,
                            bottom = spacing.lg,
                        ),
                        verticalArrangement = Arrangement.spacedBy(spacing.lg),
                    ) {
                        itemsIndexed(state.messages, key = { _, it -> it.id }) { index, message ->
                            val prevMessage = if (index > 0) state.messages[index - 1] else null
                            // Only show provenance if the previous message was NOT an assistant message
                            val showProvenance = message.role == Role.ASSISTANT && prevMessage?.role != Role.ASSISTANT

                            MessageBubble(
                                role = message.role,
                                markdown = message.text,
                                streaming = message.streaming,
                                tools = message.tools,
                                provenance = provenanceLabel(
                                    message.agent,
                                    message.model,
                                    state.agents,
                                    state.models,
                                ),
                                showProvenance = showProvenance,
                            )
                        }
                        // Until the first reply token arrives, the only sign of life is this line
                        // under the user's own bubble; once the reply streams it has its spinner.
                        if (state.busy && state.messages.lastOrNull()?.role == Role.USER) {
                            item(key = "working") {
                                Text(
                                    text = stringResource(R.string.chat_working),
                                    style = OpenCodeTypography.label,
                                    color = colors.onSurfaceMuted,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (state.failure != null || state.failureKey != null) {
                            item(key = "error") {
                                MessageBubble(
                                    role = Role.ERROR,
                                    markdown = state.failure?.message()
                                        ?: stringResource(state.failureKey!!),
                                )
                            }
                        }
                    }
                    // Following stops when the reader scrolls up, so give them the way back.
                    if (!nearBottom) {
                        JumpToLatest(
                            onClick = { scope.launch { listState.animateScrollToItem(state.messages.lastIndex) } },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = spacing.lg, bottom = spacing.md),
                        )
                    }
                }
            }

            state.pendingPermission?.let { request ->
                PermissionPrompt(
                    request = request,
                    onReply = onPermissionReply,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                )
            }

            CommandSuggestions(
                commands = state.commands,
                draft = state.draft,
                onPick = { name -> onDraftChange("/$name ") },
            )

            Composer(
                value = state.draft,
                onValueChange = onDraftChange,
                onSend = onSend,
                onStop = onStop,
                busy = state.busy,
                enabled = state.workspaceReady,
                modifier = Modifier.navigationBarsPadding(),
                controls = {
                    ChatControls(
                        agents = state.agents,
                        activeAgent = state.activeAgent,
                        models = state.models,
                        activeModel = state.activeModel,
                        onAgent = onAgent,
                        onModel = onModel,
                    )
                },
            )
        }
    }
}

/**
 * Menu on the left, the project and the engine's state centred, new session on the right.
 *
 * The status sits under the title as a refined status badge: it is read at a glance,
 * pulses during active work, provides tactile click feedback, and tapping it opens diagnostics.
 */
@Composable
private fun ChatHeader(
    title: String,
    engineState: dev.bidzz.opencode.core.engine.EngineState,
    busy: Boolean,
    onMenu: () -> Unit,
    onStatusClick: () -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val radius = OpenCodeTheme.tokens.radius
    val status = engineState.toStatus(busy)
    val statusColor = colors.statusColor(status)

    val infiniteTransition = rememberInfiniteTransition(label = "status_dot_pulse")
    val pulsingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "status_dot_alpha",
    )
    val dotAlpha = if (status == Status.WORKING) pulsingAlpha else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = dims.topBarHeight)
                .padding(horizontal = spacing.xs, vertical = spacing.xxs),
        ) {
            IconAction(
                glyph = Glyph.Menu,
                description = stringResource(R.string.chat_menu),
                onClick = onMenu,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = dims.minTouchTarget + spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(spacing.xxs))
                val statusLabel = engineState.statusLabel(busy)
                val diagTitle = stringResource(R.string.settings_diag_title)
                Row(
                    modifier = Modifier
                        .defaultMinSize(minWidth = dims.minTouchTarget, minHeight = 28.dp)
                        .clip(RoundedCornerShape(radius.full))
                        .background(statusColor.copy(alpha = 0.10f))
                        .border(
                            width = 0.5.dp,
                            color = statusColor.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(radius.full),
                        )
                        .semantics {
                            role = SemanticsRole.Button
                            contentDescription = "$statusLabel: $diagTitle"
                        }
                        .clickable(
                            role = SemanticsRole.Button,
                            onClick = onStatusClick,
                        )
                        .padding(horizontal = spacing.md, vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(dims.statusDotSize)
                            .graphicsLayer { alpha = dotAlpha }
                            .background(statusColor, CircleShape),
                    )
                    Spacer(Modifier.width(spacing.xs))
                    Text(
                        text = statusLabel,
                        style = OpenCodeTypography.label.copy(fontSize = 12.sp, lineHeight = 14.sp),
                        color = statusColor,
                        maxLines = 1,
                    )
                }
            }

            IconAction(
                glyph = Glyph.NewChat,
                description = stringResource(R.string.chat_new_session),
                onClick = onNewSession,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.hairline),
        )
    }
}

/**
 * Navigation and recent sessions.
 *
 * What used to be a dropdown behind one glyph: the same destinations, plus the
 * conversations themselves, so switching sessions is one tap instead of a screen.
 */
@Composable
private fun ChatDrawer(
    projectTitle: String,
    currentSessionID: String?,
    sessions: List<SessionRow>,
    onNewSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onAllSessions: () -> Unit,
    onFiles: () -> Unit,
    onProject: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = radius.lg, bottomEnd = radius.lg),
        drawerContainerColor = colors.surface,
        drawerContentColor = colors.onSurface,
        drawerTonalElevation = 0.dp,
        modifier = Modifier.width(320.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.lg),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.xs),
            ) {
                Text(
                    text = "OpenCode",
                    style = OpenCodeTypography.titleLarge,
                    color = colors.onSurface,
                )
                if (projectTitle.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .padding(top = spacing.xs)
                            .clip(RoundedCornerShape(radius.full))
                            .background(colors.surfaceVariant)
                            .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.full))
                            .clickable(role = Role.Button, onClick = onProject)
                            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlyphIcon(
                            glyph = Glyph.Folder,
                            tint = colors.onSurfaceMuted,
                            size = 12.dp,
                        )
                        Text(
                            text = projectTitle,
                            style = OpenCodeTypography.label,
                            color = colors.onSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = spacing.xs),
                        )
                    }
                }
            }

            DrawerItem(
                label = stringResource(R.string.chat_new_session),
                glyph = Glyph.NewChat,
                onClick = onNewSession,
                accent = true,
            )
            DrawerItem(
                label = stringResource(R.string.drawer_files),
                glyph = Glyph.File,
                onClick = onFiles,
            )
            DrawerItem(
                label = stringResource(R.string.drawer_project),
                glyph = Glyph.Folder,
                onClick = onProject,
            )
            DrawerItem(
                label = stringResource(R.string.settings_title),
                glyph = Glyph.Sliders,
                onClick = onSettings,
            )

            Box(
                Modifier
                    .padding(horizontal = spacing.lg, vertical = spacing.sm)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.hairline),
            )

            Text(
                text = stringResource(R.string.drawer_recent).uppercase(),
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
            if (sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.drawer_no_sessions),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                )
            }
        }

        // Only the list scrolls; the destinations above stay put.
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = spacing.xs),
        ) {
            items(sessions.take(DRAWER_SESSIONS), key = { it.id }) { row ->
                val selected = row.id == currentSessionID
                SessionDrawerItem(
                    row = row,
                    selected = selected,
                    onClick = { onOpenSession(row.id) },
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.hairline),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = spacing.xs),
        ) {
            DrawerItem(
                label = stringResource(R.string.drawer_all_sessions),
                glyph = Glyph.Search,
                onClick = onAllSessions,
                trailing = {
                    GlyphIcon(
                        glyph = Glyph.ChevronRight,
                        tint = colors.onSurfaceFaint,
                        size = 16.dp,
                    )
                },
            )
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    glyph: Glyph,
    onClick: () -> Unit,
    active: Boolean = false,
    accent: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    val itemBg = if (active) colors.accent.copy(alpha = 0.12f) else Color.Transparent
    val badgeBg = when {
        active -> colors.accent.copy(alpha = 0.16f)
        accent -> colors.accent.copy(alpha = 0.12f)
        else -> colors.surfaceVariant
    }
    val contentTint = when {
        active || accent -> colors.accent
        else -> colors.onSurface
    }
    val iconTint = when {
        active || accent -> colors.accent
        else -> colors.onSurfaceMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = spacing.xxs)
            .clip(RoundedCornerShape(radius.md))
            .background(itemBg)
            .then(
                if (active) {
                    Modifier.border(0.5.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(radius.md))
                } else Modifier
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(radius.full))
                .background(if (active) colors.accent else Color.Transparent),
        )

        Box(
            modifier = Modifier
                .padding(start = spacing.xs)
                .size(32.dp)
                .clip(RoundedCornerShape(radius.sm))
                .background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, tint = iconTint, size = dims.iconSmall)
        }

        Text(
            text = label,
            style = if (active) OpenCodeTypography.bodyStrong else OpenCodeTypography.body,
            color = contentTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.md),
        )

        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun SessionDrawerItem(
    row: SessionRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    val age = row.age?.label()
    val meta = listOfNotNull(age, row.cost).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = spacing.xxs)
            .clip(RoundedCornerShape(radius.md))
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(0.5.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(radius.md))
                } else Modifier
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(radius.full))
                .background(if (selected) colors.accent else Color.Transparent),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.sm),
        ) {
            Text(
                text = row.title.ifBlank { stringResource(R.string.drawer_untitled) },
                style = if (selected) OpenCodeTypography.bodyStrong else OpenCodeTypography.body,
                color = if (selected) colors.accent else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = OpenCodeTypography.label,
                    color = if (selected) colors.accent.copy(alpha = 0.75f) else colors.onSurfaceFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }
        }
    }
}

private const val DRAWER_SESSIONS = 20

/** A small round button that returns to the newest message. */
@Composable
private fun JumpToLatest(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val label = stringResource(R.string.chat_jump_latest)

    Box(
        modifier = modifier
            .size(40.dp)
            .background(colors.surface, CircleShape)
            .border(0.5.dp, colors.hairline, CircleShape)
            .clip(CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.ArrowDown, tint = colors.onSurface, size = 20.dp)
    }
}

/**
 * Shown before the first message: says what to do, and offers starting points.
 *
 * Tapping a suggestion fills the composer rather than sending, so the user can change it
 * first. While the engine starts there is nothing to suggest yet.
 */
@Composable
private fun EmptyChat(
    engineReady: Boolean,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
    engineState: dev.bidzz.opencode.core.engine.EngineState = if (engineReady) {
        dev.bidzz.opencode.core.engine.EngineState.RUNNING
    } else {
        dev.bidzz.opencode.core.engine.EngineState.STARTING
    },
    busy: Boolean = false,
    onStatusClick: (() -> Unit)? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val status = engineState.toStatus(busy)
    val statusLabel = engineState.statusLabel(busy)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = spacing.xl, vertical = spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StatusPillBadge(
                    status = status,
                    label = statusLabel,
                    onClick = onStatusClick,
                )

                Spacer(Modifier.height(spacing.md))

                Text(
                    text = stringResource(if (engineReady) R.string.empty_title_ready else R.string.empty_title_starting),
                    style = OpenCodeTypography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(spacing.xs))

                Text(
                    text = stringResource(if (engineReady) R.string.empty_ready_body else R.string.empty_starting_body),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 380.dp),
                )

                if (!engineReady) {
                    if (status == Status.WORKING) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(top = spacing.xl)
                                .size(dims.iconLarge),
                            strokeWidth = 2.dp,
                            color = colors.accent,
                        )
                    }
                } else {
                    Spacer(Modifier.height(spacing.xxl))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        SUGGESTIONS.forEach { item ->
                            val suggestion = stringResource(item.textRes)
                            SuggestionCard(
                                suggestion = suggestion,
                                glyph = item.glyph,
                                onClick = { onSuggestion(suggestion) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Status and readiness pill badge with live state dot and touch feedback. */
@Composable
private fun StatusPillBadge(
    status: Status,
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val radius = OpenCodeTheme.tokens.radius
    val shape = RoundedCornerShape(radius.full)
    val statusColor = colors.statusColor(status)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgModifier = if (onClick != null) {
        Modifier
            .clip(shape)
            .background(
                when {
                    isPressed -> statusColor.copy(alpha = 0.20f)
                    isHovered -> statusColor.copy(alpha = 0.15f)
                    else -> statusColor.copy(alpha = 0.10f)
                },
            )
            .border(0.5.dp, statusColor.copy(alpha = 0.25f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
    } else {
        Modifier
            .clip(shape)
            .background(statusColor.copy(alpha = 0.10f))
            .border(0.5.dp, statusColor.copy(alpha = 0.25f), shape)
    }

    Row(
        modifier = modifier
            .then(bgModifier)
            .padding(horizontal = spacing.md, vertical = spacing.xs + spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
    ) {
        Box(
            modifier = Modifier
                .size(dims.statusDotSize)
                .background(statusColor, CircleShape),
        )
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = statusColor,
        )
    }
}

/** Suggestion starter card with 16dp radius, hairline border, hover/press state, and ripple. */
@Composable
private fun SuggestionCard(
    suggestion: String,
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val shape = RoundedCornerShape(radius.lg)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val cardBg by animateColorAsState(
        targetValue = when {
            isPressed -> colors.surfacePressed
            isHovered -> colors.surfaceRaised
            else -> colors.surface
        },
        animationSpec = tween(durationMillis = OpenCodeTheme.tokens.motion.quick),
        label = "suggestionCardBg",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isPressed -> colors.outline
            isHovered -> colors.onSurfaceMuted.copy(alpha = 0.35f)
            else -> colors.hairline
        },
        animationSpec = tween(durationMillis = OpenCodeTheme.tokens.motion.quick),
        label = "suggestionCardBorder",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
            .border(0.5.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            glyph = glyph,
            size = 28.dp,
            modifier = Modifier.padding(end = spacing.md),
        )
        Text(
            text = suggestion,
            style = OpenCodeTypography.body,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        GlyphIcon(
            glyph = Glyph.ChevronRight,
            tint = colors.onSurfaceFaint,
            size = 14.dp,
            modifier = Modifier.padding(start = spacing.sm),
        )
    }
}

private data class StarterSuggestion(
    val textRes: Int,
    val glyph: Glyph,
)

private val SUGGESTIONS = listOf(
    StarterSuggestion(R.string.suggestion_structure, Glyph.Folder),
    StarterSuggestion(R.string.suggestion_bug, Glyph.Terminal),
    StarterSuggestion(R.string.suggestion_review, Glyph.Code),
    StarterSuggestion(R.string.suggestion_tests, Glyph.Pencil),
)

private fun dev.bidzz.opencode.core.engine.EngineState.toStatus(busy: Boolean): Status = when (this) {
    dev.bidzz.opencode.core.engine.EngineState.RUNNING -> if (busy) Status.WORKING else Status.READY
    dev.bidzz.opencode.core.engine.EngineState.STARTING,
    dev.bidzz.opencode.core.engine.EngineState.INITIALIZING,
    dev.bidzz.opencode.core.engine.EngineState.STOPPING -> Status.WORKING
    dev.bidzz.opencode.core.engine.EngineState.FAILED -> Status.ERROR
    dev.bidzz.opencode.core.engine.EngineState.DEGRADED -> Status.ERROR
    dev.bidzz.opencode.core.engine.EngineState.STOPPED -> Status.IDLE
}

@Composable
private fun dev.bidzz.opencode.core.engine.EngineState.statusLabel(busy: Boolean): String = stringResource(
    when (this) {
        dev.bidzz.opencode.core.engine.EngineState.RUNNING ->
            if (busy) R.string.chat_status_working else R.string.chat_status_ready
        dev.bidzz.opencode.core.engine.EngineState.INITIALIZING -> R.string.chat_status_preparing
        dev.bidzz.opencode.core.engine.EngineState.STARTING -> R.string.chat_status_starting
        dev.bidzz.opencode.core.engine.EngineState.DEGRADED -> R.string.chat_status_unhealthy
        dev.bidzz.opencode.core.engine.EngineState.STOPPING -> R.string.chat_status_stopping
        dev.bidzz.opencode.core.engine.EngineState.FAILED -> R.string.chat_status_failed
        dev.bidzz.opencode.core.engine.EngineState.STOPPED -> R.string.chat_status_stopped
    },
)
