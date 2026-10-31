package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.design.Status

/**
 * The main screen: a conversation with the agent.
 *
 * Structure is a plain column — header, scrolling transcript, composer — because
 * that is the whole interaction. Nothing here is decorative: there are no cards,
 * gradients or shadows, per the anti-slop rules in `plan.md` §12.
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPermissionReply: (String) -> Unit,
    onStatusClick: () -> Unit,
    onNewSession: () -> Unit,
    onSessionsClick: () -> Unit,
    onProjectClick: () -> Unit,
    onFilesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val listState = rememberLazyListState()

    // Follow the conversation as it grows, including while text streams in.
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        ChatHeader(
            engineState = state.engineState,
            busy = state.busy,
            onStatusClick = onStatusClick,
            onNewSession = onNewSession,
            onSessionsClick = onSessionsClick,
            onProjectClick = onProjectClick,
            onFilesClick = onFilesClick,
            onSettingsClick = onSettingsClick,
        )

        Box(Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                EmptyChat(
                    engineReady = state.workspaceReady,
                    workspacePath = state.workspacePath,
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
                        MessageBubble(
                            role = message.role,
                            markdown = message.text,
                            streaming = message.streaming,
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

        Composer(
            value = state.draft,
            onValueChange = onDraftChange,
            onSend = onSend,
            onStop = onStop,
            busy = state.busy,
            enabled = state.workspaceReady,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun ChatHeader(
    engineState: dev.bidzz.opencode.core.engine.EngineState,
    busy: Boolean,
    onStatusClick: () -> Unit,
    onNewSession: () -> Unit,
    onSessionsClick: () -> Unit,
    onProjectClick: () -> Unit,
    onFilesClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "OpenCode",
            style = OpenCodeTypography.title,
            color = colors.onSurface,
        )

        StatusChip(
            status = engineState.toStatus(busy),
            label = engineState.statusLabel(busy),
            onClick = onStatusClick,
            modifier = Modifier.padding(start = spacing.md),
        )

        Box(Modifier.weight(1f))

        HeaderAction("Project", onProjectClick)
        HeaderAction("Files", onFilesClick, Modifier.padding(start = spacing.sm))
        HeaderAction("History", onSessionsClick, Modifier.padding(start = spacing.sm))
        HeaderAction("⋯", onSettingsClick, Modifier.padding(start = spacing.sm))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .size(0.5.dp)
            .background(colors.outline),
    )
}

@Composable
private fun HeaderAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
    ) {
        Text(
            text = text,
            style = OpenCodeTypography.label,
            color = colors.onSurfaceMuted,
        )
    }
}

/** Engine state as a small pill. Tapping it opens diagnostics. */
@Composable
private fun StatusChip(status: Status, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    val color = colors.statusColor(status)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(radius.full))
            .background(colors.surfaceSunken)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dims.statusDotSize)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(start = spacing.xs),
        )
    }
}

/** Shown before the first message: explains what to do, not just "no data". */
@Composable
private fun EmptyChat(engineReady: Boolean, workspacePath: String?, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions

    Column(
        modifier = modifier
            .widthIn(max = dims.contentMaxWidth)
            .padding(horizontal = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (engineReady) "Ready to build" else "Starting engine…",
            style = OpenCodeTypography.title,
            color = colors.onSurface,
        )
        Text(
            text = if (engineReady) {
                "Ask a question, describe a change, or paste an error. The agent works inside your project folder."
            } else {
                "The local engine is starting. This takes a few seconds."
            },
            style = OpenCodeTypography.body,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.sm),
        )
        if (workspacePath != null) {
            Text(
                text = workspacePath,
                style = OpenCodeTypography.code,
                color = colors.onSurfaceFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing.md),
            )
        }
    }
}

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
