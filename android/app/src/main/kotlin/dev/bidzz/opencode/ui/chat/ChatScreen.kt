package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.design.Status
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
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
                        onSuggestion = onDraftChange,
                        modifier = Modifier.align(Alignment.Center),
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
                        items(state.messages, key = { it.id }) { message ->
                            // Computed here, not when the repository emitted, so the label
                            // upgrades to human names as soon as the catalog loads.
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
                            )
                        }
                        if (state.error != null) {
                            item(key = "error") {
                                MessageBubble(role = Role.ERROR, markdown = state.error)
                            }
                        }
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
 * The status sits under the title as coloured text rather than a pill: it is read at a
 * glance, and tapping it still opens diagnostics.
 */
@Composable
private fun ChatHeader(
    title: String,
    engineState: dev.bidzz.opencode.core.engine.EngineState,
    busy: Boolean,
    onMenu: () -> Unit,
    onStatusClick: () -> Unit,
    onNewSession: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val status = engineState.toStatus(busy)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
    ) {
        HeaderAction(
            glyph = Glyph.Menu,
            description = "Menu",
            onClick = onMenu,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = dims.minTouchTarget + spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = OpenCodeTypography.title,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.full))
                    .clickable(onClick = onStatusClick)
                    .padding(horizontal = spacing.sm, vertical = spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(dims.statusDotSize)
                        .background(colors.statusColor(status), CircleShape),
                )
                Text(
                    text = engineState.statusLabel(busy),
                    style = OpenCodeTypography.label,
                    color = colors.statusColor(status),
                    modifier = Modifier.padding(start = spacing.xs),
                )
            }
        }

        HeaderAction(
            glyph = Glyph.NewChat,
            description = "New session",
            onClick = onNewSession,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun HeaderAction(glyph: Glyph, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions

    Box(
        modifier = modifier
            .size(dims.minTouchTarget)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, tint = colors.onSurface, size = 22.dp)
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

    ModalDrawerSheet(
        drawerContainerColor = colors.surface,
        drawerContentColor = colors.onSurface,
        modifier = Modifier.widthIn(max = 320.dp),
    ) {
        Column(Modifier.padding(top = spacing.lg)) {
            Text(
                text = "OpenCode",
                style = OpenCodeTypography.title,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = spacing.xl),
            )
            Text(
                text = projectTitle,
                style = OpenCodeTypography.label,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.xs),
            )

            DrawerItem("New session", Glyph.NewChat, onNewSession, accent = true)
            DrawerItem("Files", Glyph.File, onFiles)
            DrawerItem("Project", Glyph.Folder, onProject)
            DrawerItem("Settings", Glyph.Sliders, onSettings)

            Box(
                Modifier
                    .padding(vertical = spacing.sm)
                    .fillMaxWidth()
                    .size(0.5.dp)
                    .background(colors.outline),
            )

            Text(
                text = "RECENT",
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
                modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.xs),
            )
            if (sessions.isEmpty()) {
                Text(
                    text = "No sessions yet",
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.sm),
                )
            }
        }

        // Only the list scrolls; the destinations above stay put.
        LazyColumn(Modifier.weight(1f)) {
            items(sessions.take(DRAWER_SESSIONS), key = { it.id }) { row ->
                val selected = row.id == currentSessionID
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) colors.surfaceSunken else colors.surface)
                        .clickable { onOpenSession(row.id) }
                        .padding(horizontal = spacing.xl, vertical = spacing.sm),
                ) {
                    Text(
                        text = row.title.ifBlank { "Untitled" },
                        style = OpenCodeTypography.body,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.age,
                        style = OpenCodeTypography.label,
                        color = colors.onSurfaceFaint,
                    )
                }
            }
        }

        DrawerItem("All sessions", Glyph.Search, onAllSessions)
    }
}

@Composable
private fun DrawerItem(label: String, glyph: Glyph, onClick: () -> Unit, accent: Boolean = false) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val tint = if (accent) colors.accent else colors.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.xl, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, tint = tint, size = 20.dp)
        Text(
            text = label,
            style = OpenCodeTypography.body,
            color = tint,
            modifier = Modifier.padding(start = spacing.lg),
        )
    }
}

private const val DRAWER_SESSIONS = 20

/**
 * Shown before the first message: says what to do, and offers starting points.
 *
 * Tapping a suggestion fills the composer rather than sending, so the user can change it
 * first. While the engine starts there is nothing to suggest yet.
 */
@Composable
private fun EmptyChat(engineReady: Boolean, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    Column(
        modifier = modifier
            .widthIn(max = dims.contentMaxWidth)
            .padding(horizontal = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (engineReady) "What are we working on?" else "Starting engine…",
            style = OpenCodeTypography.title,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!engineReady) {
            Text(
                text = "The local engine is starting. This takes a few seconds.",
                style = OpenCodeTypography.body,
                color = colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.sm),
            )
        } else {
            Column(
                modifier = Modifier.padding(top = spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                SUGGESTIONS.forEach { suggestion ->
                    Text(
                        text = suggestion,
                        style = OpenCodeTypography.body,
                        color = colors.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(radius.lg))
                            .background(colors.surface)
                            .border(0.5.dp, colors.outline, RoundedCornerShape(radius.lg))
                            .clickable { onSuggestion(suggestion) }
                            .padding(horizontal = spacing.lg, vertical = spacing.md),
                    )
                }
            }
        }
    }
}

private val SUGGESTIONS = listOf(
    "Explain how this project is structured",
    "Find the cause of a bug",
    "Review the latest changes",
    "Write tests for a file",
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

private fun dev.bidzz.opencode.core.engine.EngineState.statusLabel(busy: Boolean): String = when (this) {
    dev.bidzz.opencode.core.engine.EngineState.RUNNING -> if (busy) "Working" else "Ready"
    dev.bidzz.opencode.core.engine.EngineState.INITIALIZING -> "Preparing"
    dev.bidzz.opencode.core.engine.EngineState.STARTING -> "Starting"
    dev.bidzz.opencode.core.engine.EngineState.DEGRADED -> "Unhealthy"
    dev.bidzz.opencode.core.engine.EngineState.STOPPING -> "Stopping"
    dev.bidzz.opencode.core.engine.EngineState.FAILED -> "Failed"
    dev.bidzz.opencode.core.engine.EngineState.STOPPED -> "Stopped"
}
