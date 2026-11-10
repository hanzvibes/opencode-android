package dev.bidzz.opencode.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.design.Status
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.HairlineSeparator
import dev.bidzz.opencode.ui.IconAction
import dev.bidzz.opencode.ui.LocalizedAlertDialog
import dev.bidzz.opencode.ui.message
import dev.bidzz.opencode.ui.sessions.DeleteSessionDialog
import dev.bidzz.opencode.ui.sessions.RenameSessionDialog
import dev.bidzz.opencode.ui.sessions.SessionActionMenu
import dev.bidzz.opencode.ui.sessions.SessionDateGroup
import dev.bidzz.opencode.ui.sessions.groupSessions
import dev.bidzz.opencode.ui.sessions.label
import dev.bidzz.opencode.ui.sessions.SessionRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    onDiagnosticsClick: () -> Unit = onStatusClick,
    onAboutClick: () -> Unit = {},
    onRenameSession: (String, String) -> Unit = { _, _ -> },
    onDeleteSession: (String) -> Unit = {},
    onTogglePinSession: (String) -> Unit = {},
    onDismissError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Follow the conversation as it grows, but only while the reader is already at the bottom,
    // so scrolling up during a stream is not yanked back down.
    // Pixel-tight: the last item must be the last one *and* fully visible
    // (its bottom edge within ~48dp of the viewport bottom), not merely
    // "within two items", or a half-visible last bubble would still count
    // as "at the bottom" and yank the reader down mid-read.
    val density = LocalDensity.current
    val nearBottom by remember(density) {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf true
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            if (last.index < info.totalItemsCount - 1) return@derivedStateOf false
            val tolerancePx = with(density) { 48.dp.toPx() }
            last.offset + last.size <= info.viewportEndOffset + tolerancePx
        }
    }

    var lastReadMessageCount by remember { mutableStateOf(state.messages.size) }

    LaunchedEffect(nearBottom, state.messages.size) {
        if (nearBottom) {
            lastReadMessageCount = state.messages.size
        }
    }

    // Unread counts assistant messages only: the reader's own messages are
    // never "unread", and counting them inflated the badge after a send.
    val unreadMessagesCount by remember {
        derivedStateOf {
            if (nearBottom) 0 else state.messages.drop(lastReadMessageCount)
                .count { it.role == Role.ASSISTANT }.coerceAtLeast(0)
        }
    }

    // One run timer for the whole screen, started when the run starts
    // (busy false→true) and reset when it ends. It used to live inside
    // each MessageBubble and reset to 0 whenever the streaming flag
    // toggled, and the working placeholder had its own hardcoded 0 — so
    // "Thought for 12s" could jump back to "Thinking…" mid-run and the
    // placeholder→turn transition restarted the clock.
    var busyStartedAtMs by remember { mutableLongStateOf(0L) }
    var busyElapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.busy) {
        if (state.busy) {
            if (busyStartedAtMs == 0L) busyStartedAtMs = System.currentTimeMillis()
            while (isActive) {
                busyElapsedSeconds = maxOf(0, ((System.currentTimeMillis() - busyStartedAtMs) / 1000).toInt())
                delay(500)
            }
        } else {
            busyStartedAtMs = 0L
            busyElapsedSeconds = 0
        }
    }

    val targetLastIndex by remember {
        derivedStateOf {
            val hasWorking = state.busy && state.messages.lastOrNull()?.role == Role.USER
            val hasError = state.failure != null || state.failureKey != null
            val totalCount = state.messages.size + (if (hasWorking) 1 else 0) + (if (hasError) 1 else 0)
            (totalCount - 1).coerceAtLeast(0)
        }
    }

    // Follow keys include the last message's tools and parts: a stream
    // that only runs tools (no new text) used to never trigger a follow.
    LaunchedEffect(
        state.messages.size,
        state.messages.lastOrNull()?.text?.length,
        state.messages.lastOrNull()?.tools?.size,
        state.messages.lastOrNull()?.parts?.size,
        state.busy,
    ) {
        if (state.messages.isNotEmpty() && nearBottom) {
            // Instant while busy so a tap on Jump-to-latest is not fought
            // by a per-token snap; animated only when idle.
            if (state.busy) {
                listState.scrollToItem(targetLastIndex)
            } else {
                listState.animateScrollToItem(targetLastIndex)
            }
        }
    }

    // Opening an old session lands at the bottom (latest) once per session,
    // and marks everything read so the jump-to-latest badge never shows a
    // phantom count carried over from the previous session.
    LaunchedEffect(state.sessionID) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(targetLastIndex)
            lastReadMessageCount = state.messages.size
        }
    }

    // The draft as it was before the "/" that opened the command palette was
    // typed. Dismissing the palette restores it instead of wiping the
    // composer: a "/" typed into an empty composer restores to empty, a "/"
    // prepended to existing text restores that text.
    val paletteActive = isCommandPaletteActive(state.draft)
    var draftBeforePalette by remember { mutableStateOf("") }
    LaunchedEffect(state.draft) {
        if (!paletteActive) draftBeforePalette = state.draft
    }

    /** Cancels palette mode only; the draft before the palette is kept. */
    fun dismissPalette() {
        onDraftChange(draftBeforePalette)
    }

    // System back closes the palette non-destructively; it never exits the
    // screen or clears the draft while the palette is open.
    BackHandler(enabled = paletteActive) { dismissPalette() }

    /** Closes the drawer, then runs [action]; the drawer must not stay open over the next screen. */
    fun closeThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Strong scrim so the drawer reads as modal over the OLED-black
        // chat instead of a floating panel over undimmed bubbles.
        scrimColor = Color.Black.copy(alpha = 0.6f),
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
                onDiagnostics = { closeThen(onDiagnosticsClick) },
                onDeveloper = { closeThen(onAboutClick) },
                onRenameSession = onRenameSession,
                onDeleteSession = onDeleteSession,
                onTogglePinSession = onTogglePinSession,
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
            val isScrolled by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                }
            }

            val activeBranch = remember(state.workspacePath, state.branch) {
                state.branch ?: detectGitBranch(state.workspacePath)
            }

            ChatHeader(
                title = headerTitle(state.workspacePath),
                // The active session's own title, so the header says which
                // conversation this is — not just which folder it is in.
                sessionTitle = sessions.firstOrNull { it.id == state.sessionID }
                    ?.title?.takeIf { it.isNotBlank() },
                workspacePath = state.workspacePath,
                engineState = state.engineState,
                busy = state.busy,
                branch = activeBranch,
                isScrolled = isScrolled,
                onMenu = {
                    onDrawerOpen()
                    scope.launch { drawerState.open() }
                },
                onStatusClick = onStatusClick,
                onNewSession = onNewSession,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (state.messages.isEmpty()) {
                    EmptyChat(
                        engineReady = state.workspaceReady,
                        engineState = state.engineState,
                        busy = state.busy,
                        workspacePath = state.workspacePath,
                        onStatusClick = onStatusClick,
                        onProjectClick = onProjectClick,
                        onSuggestion = onDraftChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .widthIn(max = dims.contentMaxWidth),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = spacing.lg,
                                end = spacing.lg,
                                top = spacing.md,
                                // 64dp of air under the last message so the
                                // 48dp floating jump-to-latest button never
                                // covers its text or copy button.
                                bottom = 64.dp,
                            ),
                        ) {
                            itemsIndexed(state.messages, key = { _, it -> it.id }) { index, message ->
                                val prevMessage = if (index > 0) state.messages[index - 1] else null
                                val isConsecutiveAssistant = prevMessage?.role == Role.ASSISTANT && message.role == Role.ASSISTANT
                                val showProvenance = message.role == Role.ASSISTANT && !isConsecutiveAssistant

                                // A new assistant turn gets 16dp (lg) of air above it, up
                                // from 12dp, so two long assistant turns no longer
                                // run together. Consecutive assistant parts stay
                                // tight at 4dp — they are one turn, not two.
                                val topSpacing = when {
                                    index == 0 -> 0.dp
                                    message.role == Role.USER -> {
                                        // Two user messages in a row used to
                                        // sit only 8dp apart and read as one
                                        // merged bubble.
                                        if (prevMessage?.role == Role.USER) spacing.md else spacing.xxl
                                    }
                                    isConsecutiveAssistant -> spacing.xs
                                    else -> spacing.lg
                                }

                                Column(modifier = Modifier.padding(top = topSpacing)) {
                                    // Hairline turn divider above the first part
                                    // of a new assistant turn (not the first
                                    // message). No card, no background — the
                                    // assistant still has no bubble, per plan.md §12.
                                    if (showProvenance && index > 0) {
                                        HairlineSeparator(
                                            modifier = Modifier.padding(bottom = spacing.sm),
                                        )
                                    }
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
                                        model = message.model,
                                        reasoning = message.reasoning,
                                        parts = message.parts,
                                        thinkingElapsedSeconds = busyElapsedSeconds,
                                        onAnswerQuestion = { answer ->
                                            onDraftChange(answer)
                                            onSend()
                                        },
                                    )
                                }
                            }
                            // When busy right after user message, show assistant's turn beginning immediately
                            if (state.busy && state.messages.lastOrNull()?.role == Role.USER) {
                                item(key = "working") {
                                    AssistantWorkingPlaceholder(
                                        agent = state.activeAgent,
                                        model = state.activeModel,
                                        agents = state.agents,
                                        models = state.models,
                                        // Same screen-owned timer as the real
                                        // turn, so the clock does not restart
                                        // when the placeholder is replaced.
                                        elapsedSeconds = busyElapsedSeconds,
                                        // Same 16dp (lg) as a real new turn, so
                                        // the layout does not jump when the
                                        // placeholder is replaced by the turn.
                                        modifier = Modifier.padding(top = spacing.lg),
                                    )
                                }
                            }
                            if (state.failure != null || state.failureKey != null) {
                                item(key = "error") {
                                    // Hoisted out of the .semantics {} block below:
                                    // stringResource() is @Composable and cannot be
                                    // called inside a SemanticsPropertyReceiver lambda.
                                    val closeDescription =
                                        stringResource(R.string.chat_status_dialog_close)
                                    Box(modifier = Modifier.padding(top = spacing.md)) {
                                        MessageBubble(
                                            role = Role.ERROR,
                                            markdown = state.failure?.message()
                                                ?: stringResource(state.failureKey!!),
                                        )
                                        // Small dismiss cross on the error bubble;
                                        // it only clears the error, it does not retry.
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(colors.surfaceRaised)
                                                .clickable(
                                                    role = SemanticsRole.Button,
                                                    onClick = onDismissError,
                                                )
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
                            }
                        }
                        // Floating jump-to-bottom button with badge and smooth animation
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !nearBottom,
                            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f, animationSpec = tween(180)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(180)),
                            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f, animationSpec = tween(150)) + slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(150)),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = spacing.lg, bottom = spacing.md),
                        ) {
                            JumpToLatest(
                                unreadCount = unreadMessagesCount,
                                isBusy = state.busy,
                                onClick = { scope.launch { listState.animateScrollToItem(targetLastIndex) } },
                            )
                        }
                    }

                    // Subtle scrim overlay over chat history when command palette is active
                    androidx.compose.animation.AnimatedVisibility(
                        visible = paletteActive,
                        enter = fadeIn(tween(OpenCodeTheme.tokens.motion.standard)),
                        exit = fadeOut(tween(OpenCodeTheme.tokens.motion.quick)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { dismissPalette() },
                                ),
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = state.pendingPermission != null,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
            ) {
                state.pendingPermission?.let { request ->
                    PermissionPrompt(
                        request = request,
                        onReply = onPermissionReply,
                        modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                    )
                }
            }

            val paletteState = rememberCommandPaletteState(
                commands = state.commands,
                draft = state.draft,
            )

            CommandSuggestions(
                paletteState = paletteState,
                onPick = { name -> onDraftChange("/$name ") },
                onDismiss = { dismissPalette() },
            )

            Composer(
                value = state.draft,
                onValueChange = onDraftChange,
                onSend = onSend,
                onStop = onStop,
                busy = state.busy,
                enabled = state.workspaceReady,
                modifier = Modifier.navigationBarsPadding(),
                onKeyEvent = { keyEvent ->
                    // Ctrl/Cmd+Enter sends from a physical keyboard; a plain
                    // Enter stays a newline and is left to the text field.
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                        (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)
                    ) {
                        onSend()
                        true
                    } else {
                        paletteState.handleKeyEvent(
                            event = keyEvent,
                            onPick = { name -> onDraftChange("/$name ") },
                            onDismiss = { dismissPalette() },
                        )
                    }
                },
                controls = {
                    ChatControls(
                        agents = state.agents,
                        activeAgent = state.activeAgent,
                        models = state.models,
                        activeModel = state.activeModel,
                        onAgent = onAgent,
                        onModel = onModel,
                        enabled = !state.busy,
                    )
                },
            )
        }
    }
}

/**
 * Sleek minimalist top app bar with project title, branch/status chip, smooth navigation triggers,
 * interactive engine status preview dialog on tap, animated new conversation shortcut, and a
 * crisp hairline bottom separator that disappears seamlessly at top scroll.
 */
@Composable
fun ChatHeader(
    title: String,
    engineState: dev.bidzz.opencode.core.engine.EngineState,
    busy: Boolean,
    onMenu: () -> Unit,
    onStatusClick: () -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
    branch: String? = null,
    isScrolled: Boolean = false,
    sessionTitle: String? = null,
    workspacePath: String? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val radius = OpenCodeTheme.tokens.radius
    val status = engineState.toStatus(busy)
    val statusColor = colors.statusColor(status)

    var showStatusDialog by remember { mutableStateOf(false) }
    var newChatClickTrigger by remember { mutableIntStateOf(0) }

    val newChatRotation by animateFloatAsState(
        targetValue = newChatClickTrigger * 360f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "new_chat_rotation",
    )

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

    val separatorAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(
            durationMillis = OpenCodeTheme.tokens.motion.quick,
            easing = FastOutSlowInEasing,
        ),
        label = "chat_header_separator_alpha",
    )

    val chipInteractionSource = remember { MutableInteractionSource() }
    val isChipPressed by chipInteractionSource.collectIsPressedAsState()
    val chipScale by animateFloatAsState(
        targetValue = if (isChipPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "status_chip_scale",
    )

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
                // The active session's title, under the project name: the
                // header used to show the folder only, so two sessions in
                // the same project looked identical.
                if (!sessionTitle.isNullOrBlank()) {
                    Text(
                        text = sessionTitle,
                        style = OpenCodeTypography.label.copy(fontSize = 12.sp),
                        color = colors.onSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(spacing.xxs))
                val statusLabel = engineState.statusLabel(busy)
                val statusDescription = buildString {
                    append(statusLabel)
                    if (!branch.isNullOrBlank()) {
                        append(", ")
                        append(branch)
                    }
                }

                Row(
                    modifier = Modifier
                        .defaultMinSize(minWidth = dims.minTouchTarget, minHeight = 40.dp)
                        .graphicsLayer {
                            scaleX = chipScale
                            scaleY = chipScale
                        }
                        .clip(RoundedCornerShape(radius.full))
                        .background(statusColor.copy(alpha = 0.10f))
                        .border(
                            width = dims.hairline,
                            color = statusColor.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(radius.full),
                        )
                        .semantics {
                            role = SemanticsRole.Button
                            contentDescription = statusDescription
                        }
                        .clickable(
                            interactionSource = chipInteractionSource,
                            indication = ripple(bounded = true),
                            role = SemanticsRole.Button,
                            onClick = { showStatusDialog = true },
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
                    if (!branch.isNullOrBlank()) {
                        Spacer(Modifier.width(spacing.xs))
                        Text(
                            text = "•",
                            style = OpenCodeTypography.label.copy(fontSize = 11.sp, lineHeight = 14.sp),
                            color = colors.onSurfaceFaint,
                        )
                        Spacer(Modifier.width(spacing.xs))
                        Text(
                            text = branch,
                            style = OpenCodeTypography.label.copy(
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = colors.onSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            IconAction(
                glyph = Glyph.NewChat,
                description = stringResource(R.string.chat_new_session),
                onClick = {
                    newChatClickTrigger++
                    onNewSession()
                },
                rotation = newChatRotation,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        HairlineSeparator(alpha = separatorAlpha)
    }

    if (showStatusDialog) {
        EngineStatusDialog(
            engineState = engineState,
            busy = busy,
            projectTitle = title,
            workspacePath = workspacePath,
            branch = branch,
            onOpenDiagnostics = {
                showStatusDialog = false
                onStatusClick()
            },
            onDismiss = { showStatusDialog = false },
        )
    }
}

/**
 * Interactive preview dialog displaying live engine status, runtime details,
 * and quick access to full diagnostics.
 */
@Composable
private fun EngineStatusDialog(
    engineState: dev.bidzz.opencode.core.engine.EngineState,
    busy: Boolean,
    projectTitle: String,
    branch: String?,
    onOpenDiagnostics: () -> Unit,
    onDismiss: () -> Unit,
    workspacePath: String? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val radius = OpenCodeTheme.tokens.radius
    val status = engineState.toStatus(busy)
    val statusColor = colors.statusColor(status)
    val statusLabel = engineState.statusLabel(busy)

    val descRes = when (engineState) {
        dev.bidzz.opencode.core.engine.EngineState.RUNNING ->
            if (busy) R.string.chat_status_desc_working else R.string.chat_status_desc_ready
        dev.bidzz.opencode.core.engine.EngineState.STARTING,
        dev.bidzz.opencode.core.engine.EngineState.INITIALIZING -> R.string.chat_status_desc_starting
        dev.bidzz.opencode.core.engine.EngineState.FAILED,
        dev.bidzz.opencode.core.engine.EngineState.DEGRADED -> R.string.chat_status_desc_unhealthy
        dev.bidzz.opencode.core.engine.EngineState.STOPPING,
        dev.bidzz.opencode.core.engine.EngineState.STOPPED -> R.string.chat_status_desc_stopped
    }

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Box(
                    Modifier
                        .size(dims.statusDotSize)
                        .background(statusColor, CircleShape),
                )
                Text(
                    text = stringResource(R.string.chat_status_dialog_title),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius.sm))
                        .background(statusColor.copy(alpha = 0.10f))
                        .border(dims.hairline, statusColor.copy(alpha = 0.25f), RoundedCornerShape(radius.sm))
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = statusLabel,
                        style = OpenCodeTypography.label.copy(fontWeight = FontWeight.SemiBold),
                        color = statusColor,
                    )
                    Text(
                        text = engineState.name,
                        style = OpenCodeTypography.caption,
                        color = statusColor.copy(alpha = 0.8f),
                    )
                }

                Text(
                    text = stringResource(descRes),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius.sm))
                        .background(colors.surfaceSunken)
                        .border(dims.hairline, colors.hairline, RoundedCornerShape(radius.sm))
                        .padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    // The full workspace path, wrapping instead of an
                    // ellipsised folder name, with a copy button — the
                    // dialog used to repeat the header's short name, which
                    // says nothing when two projects share a folder name.
                    val clipboardManager = LocalClipboardManager.current
                    val fullPath = workspacePath?.takeIf { it.isNotBlank() } ?: projectTitle
                    DialogDetailRow(
                        label = stringResource(R.string.chat_status_dialog_workspace),
                        value = fullPath,
                        wrapValue = true,
                        onCopy = { clipboardManager.setText(AnnotatedString(fullPath)) },
                    )
                    if (!branch.isNullOrBlank()) {
                        DialogDetailRow(
                            label = stringResource(R.string.chat_status_dialog_branch),
                            value = branch,
                        )
                    }
                    DialogDetailRow(
                        label = stringResource(R.string.chat_status_dialog_state),
                        value = if (busy) stringResource(R.string.chat_status_working) else statusLabel,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenDiagnostics) {
                Text(
                    text = stringResource(R.string.chat_status_dialog_diagnostics),
                    color = colors.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.chat_status_dialog_close),
                    color = colors.onSurfaceMuted,
                )
            }
        },
    )
}

@Composable
private fun DialogDetailRow(
    label: String,
    value: String,
    wrapValue: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    val colors = OpenCodeTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.caption,
            color = colors.onSurfaceFaint,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text(
                text = value,
                style = OpenCodeTypography.label,
                color = colors.onSurface,
                maxLines = if (wrapValue) 3 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onCopy != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(role = SemanticsRole.Button, onClick = onCopy),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        glyph = Glyph.Copy,
                        tint = colors.onSurfaceMuted,
                        size = 12.dp,
                    )
                }
            }
        }
    }
}

/**
 * Navigation and recent sessions.
 *
 * Matching modern Cursor / ChatGPT sidebar experience:
 * - Clean workspace switch / project picker integration at top
 * - New chat and file explorer shortcuts
 * - Conversation history grouped by date (Pinned, Today, Yesterday, Previous 7 days, Older)
 * - Quick action menu (Rename, Delete, Pin) on every session
 * - Settings, Diagnostics, and Developer profile links at bottom
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
    onDiagnostics: () -> Unit,
    onDeveloper: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onTogglePinSession: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    var sessionToRename by remember { mutableStateOf<SessionRow?>(null) }
    var sessionToDelete by remember { mutableStateOf<SessionRow?>(null) }

    sessionToRename?.let { target ->
        RenameSessionDialog(
            initialTitle = target.title,
            onDismiss = { sessionToRename = null },
            onConfirm = { newTitle ->
                sessionToRename = null
                onRenameSession(target.id, newTitle)
            },
        )
    }

    sessionToDelete?.let { target ->
        DeleteSessionDialog(
            sessionTitle = target.title,
            onDismiss = { sessionToDelete = null },
            onConfirm = {
                sessionToDelete = null
                onDeleteSession(target.id)
            },
        )
    }

    // Drawer covers at most 85% of the width on narrow phones, so a sliver
    // of the chat stays visible; wide screens keep the 320dp sheet.
    BoxWithConstraints {
        ModalDrawerSheet(
            drawerShape = RoundedCornerShape(topEnd = radius.lg, bottomEnd = radius.lg),
            drawerContainerColor = colors.surface,
            drawerContentColor = colors.onSurface,
            drawerTonalElevation = 0.dp,
            modifier = Modifier.width(minOf(320.dp, maxWidth * 0.85f)),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.md),
        ) {
            // Workspace switch / project picker integration
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.sm, vertical = spacing.xxs)
                    .clip(RoundedCornerShape(radius.md))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.md))
                    .clickable(role = SemanticsRole.Button, onClick = onProject)
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(radius.sm))
                        .background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Folder, tint = colors.accent, size = dims.iconMedium)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.sm),
                ) {
                    Text(
                        text = projectTitle.ifBlank { "OpenCode" },
                        style = OpenCodeTypography.bodyStrong,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.drawer_switch_workspace),
                        style = OpenCodeTypography.caption,
                        color = colors.onSurfaceFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlyphIcon(
                    glyph = Glyph.ChevronDown,
                    tint = colors.onSurfaceFaint,
                    size = 14.dp,
                )
            }

            Spacer(Modifier.height(spacing.xxs))

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
                label = stringResource(R.string.drawer_all_sessions),
                glyph = Glyph.History,
                onClick = onAllSessions,
            )

            Box(
                Modifier
                    .padding(horizontal = spacing.lg, vertical = spacing.xs)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.hairline),
            )
        }

        // Grouped conversation history, capped at DRAWER_SESSIONS: the
        // drawer used to render every session the engine knew, and the
        // constant that was supposed to cap it was never read.
        val drawerSessions = remember(sessions) { sessions.take(DRAWER_SESSIONS) }
        val groupedSessions = remember(drawerSessions) { groupSessions(drawerSessions) }

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg, vertical = spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.drawer_no_sessions),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = spacing.xxs),
            ) {
                groupedSessions.forEach { (group, rows) ->
                    item(key = "header_${group.name}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.lg, vertical = spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (group == SessionDateGroup.PINNED) {
                                GlyphIcon(
                                    glyph = Glyph.Pin,
                                    tint = colors.accent,
                                    size = 12.dp,
                                    modifier = Modifier.padding(end = spacing.xs),
                                )
                            }
                            Text(
                                text = stringResource(group.titleRes()).uppercase(),
                                style = OpenCodeTypography.label,
                                color = if (group == SessionDateGroup.PINNED) colors.accent else colors.onSurfaceFaint,
                            )
                        }
                    }

                    items(rows, key = { it.id }) { row ->
                        val selected = row.id == currentSessionID
                        SessionDrawerItem(
                            row = row,
                            selected = selected,
                            onClick = { onOpenSession(row.id) },
                            onPinToggle = { onTogglePinSession(row.id) },
                            onRenameClick = { sessionToRename = row },
                            onDeleteClick = { sessionToDelete = row },
                        )
                    }
                }
            }

            // Past the cap, the full history lives one tap away on the
            // sessions screen instead of scrolling forever in the drawer.
            if (sessions.size > DRAWER_SESSIONS) {
                DrawerItem(
                    label = stringResource(R.string.drawer_view_all),
                    glyph = Glyph.History,
                    onClick = onAllSessions,
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.hairline),
        )

        // Bottom footer with Settings, Diagnostics, and Developer Profile
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                DrawerFooterButton(
                    label = stringResource(R.string.settings_title),
                    glyph = Glyph.Sliders,
                    onClick = onSettings,
                    modifier = Modifier.weight(1f),
                )
                DrawerFooterButton(
                    label = stringResource(R.string.drawer_diagnostics),
                    glyph = Glyph.Pulse,
                    onClick = onDiagnostics,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.xs)
                    .clip(RoundedCornerShape(radius.md))
                    .clickable(role = SemanticsRole.Button, onClick = onDeveloper)
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "R",
                        style = OpenCodeTypography.bodyStrong.copy(fontSize = 13.sp),
                        color = colors.onAccent,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.sm),
                ) {
                    Text(
                        text = "Raihan Ramadhani",
                        style = OpenCodeTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@hanzvibes",
                        style = OpenCodeTypography.caption,
                        color = colors.onSurfaceFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlyphIcon(
                    glyph = Glyph.ChevronRight,
                    tint = colors.onSurfaceFaint,
                    size = 14.dp,
                )
            }
        }
    }
    }
}

@Composable
private fun DrawerFooterButton(
    label: String,
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    // Footer buttons are a full 48dp touch target (dims.minTouchTarget):
    // at caption size with only xs vertical padding they used to be ~30dp
    // tall and read as cramped next to the 40dp composer chips.
    Row(
        modifier = modifier
            .height(dims.minTouchTarget)
            .clip(RoundedCornerShape(radius.md))
            .background(colors.surfaceVariant.copy(alpha = 0.6f))
            .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.md))
            .clickable(role = SemanticsRole.Button, onClick = onClick)
            .padding(horizontal = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GlyphIcon(glyph, tint = colors.onSurfaceMuted, size = dims.iconMedium)
        Text(
            text = label,
            style = OpenCodeTypography.label.copy(fontWeight = FontWeight.Medium),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = spacing.xs),
        )
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
            .clickable(role = SemanticsRole.Button, onClick = onClick)
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
    onPinToggle: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    var menuExpanded by remember { mutableStateOf(false) }

    val age = row.age?.label()
    val meta = listOfNotNull(age, row.cost).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = 1.dp)
            .clip(RoundedCornerShape(radius.md))
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(0.5.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(radius.md))
                } else Modifier
            )
            .clickable(role = SemanticsRole.Button, onClick = onClick)
            .padding(start = spacing.sm, end = spacing.xs, top = spacing.xs, bottom = spacing.xs),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (row.isPinned) {
                    GlyphIcon(
                        glyph = Glyph.Pin,
                        tint = if (selected) colors.accent else colors.onSurfaceMuted,
                        size = 12.dp,
                        modifier = Modifier.padding(end = spacing.xs),
                    )
                }
                Text(
                    text = row.title.ifBlank { stringResource(R.string.drawer_untitled) },
                    style = if (selected) OpenCodeTypography.bodyStrong else OpenCodeTypography.body,
                    color = if (selected) colors.accent else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = OpenCodeTypography.label,
                    color = if (selected) colors.accent.copy(alpha = 0.75f) else colors.onSurfaceFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Box {
            // Full 48dp hit area: the session "…" used to be a 28dp
            // target, the smallest in the drawer.
            Box(
                modifier = Modifier
                    .size(dims.minTouchTarget)
                    .clip(RoundedCornerShape(radius.sm))
                    .clickable(role = SemanticsRole.Button) { menuExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.Dots,
                    tint = if (selected) colors.accent else colors.onSurfaceMuted,
                    size = 14.dp,
                )
            }
            SessionActionMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                isPinned = row.isPinned,
                onPinToggle = onPinToggle,
                onRenameClick = onRenameClick,
                onDeleteClick = onDeleteClick,
            )
        }
    }
}

private const val DRAWER_SESSIONS = 20

/**
 * Assistant turn entry indicator displayed immediately when the user sends a prompt.
 * Bridges the gap before the first streaming delta or tool execution, establishing the AI thought state on the left.
 */
@Composable
private fun AssistantWorkingPlaceholder(
    agent: String?,
    model: dev.bidzz.opencode.core.api.dto.ModelRef?,
    agents: List<dev.bidzz.opencode.core.api.dto.AgentInfo>,
    models: List<dev.bidzz.opencode.core.api.dto.ModelInfo>,
    modifier: Modifier = Modifier,
    /** Seconds since this run started, owned by the screen (see A-19). */
    elapsedSeconds: Int = 0,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val provenance = provenanceLabel(agent, model, agents, models)

    Column(modifier = modifier.fillMaxWidth()) {
        // No identity yet → no badge at all, instead of "OpenCode"
        // jumping to "Build · model" when the turn arrives (A-47).
        if (provenance != null) {
            ProvenanceBadge(
                provenance = provenance,
                modelRef = model,
                streaming = false,
                modifier = Modifier.padding(bottom = spacing.sm),
            )
        }

        ThinkingDisclosure(
            isThinkingActive = true,
            reasoningText = null,
            elapsedSeconds = elapsedSeconds,
        )
    }
}

/** A floating button with entry/exit animation and unread/busy badge that returns to the newest message. */
@Composable
private fun JumpToLatest(
    unreadCount: Int,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions
    val label = stringResource(R.string.chat_jump_latest)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .semantics {
                this.contentDescription = label
                this.role = SemanticsRole.Button
            },
    ) {
        Box(
            modifier = Modifier
                .size(dims.minTouchTarget)
                .clip(CircleShape)
                .background(colors.surfaceRaised)
                .border(0.5.dp, colors.hairline, CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    role = SemanticsRole.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.ArrowDown, tint = colors.onSurface, size = 18.dp)
        }

        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    style = OpenCodeTypography.caption.copy(
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.onAccent,
                )
            }
        } else if (isBusy) {
            val infiniteTransition = rememberInfiniteTransition(label = "jump_busy_pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 650, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "jump_pulse_alpha",
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 1.dp, y = (-1).dp)
                    .size(10.dp)
                    .graphicsLayer { alpha = pulseAlpha }
                    .background(colors.accent, CircleShape)
                    .border(1.5.dp, colors.surfaceRaised, CircleShape),
            )
        }
    }
}

/**
 * Shown before the first message: welcoming hero landing state with Apple minimalism,
 * system environment & workspace readiness, and actionable prompt starter cards.
 *
 * Tapping a suggestion fills the composer rather than sending, so the user can review
 * and adapt the prompt first.
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
    workspacePath: String? = null,
    onStatusClick: (() -> Unit)? = null,
    onProjectClick: (() -> Unit)? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val radius = OpenCodeTheme.tokens.radius
    val status = engineState.toStatus(busy)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val isWide = maxWidth >= 540.dp
        val isNarrow = maxWidth < 360.dp

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
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Hero Branding with subtle ambient glow and minimalist Apple typography
                HeroBranding(
                    engineReady = engineReady,
                )

                Spacer(Modifier.height(spacing.md))

                // One-line readiness status (engine • workspace • git) instead
                // of the full card: the details are one tap away on this row
                // and on the header status, so the starters fit above the fold.
                ReadinessStatusRow(
                    engineState = engineState,
                    busy = busy,
                    workspacePath = workspacePath,
                    onStatusClick = onStatusClick,
                    onProjectClick = onProjectClick,
                )

                if (!engineReady) {
                    if (status == Status.WORKING) {
                        Spacer(Modifier.height(spacing.xl))
                        CircularProgressIndicator(
                            modifier = Modifier.size(dims.iconLarge),
                            strokeWidth = 2.dp,
                            color = colors.accent,
                        )
                    } else {
                        // A failed/stopped engine used to leave the empty
                        // state with no spinner, no error and no action —
                        // a dead screen. Say what happened and offer the
                        // way out (diagnostics).
                        Spacer(Modifier.height(spacing.xl))
                        Text(
                            text = stringResource(
                                if (status == Status.ERROR) R.string.chat_status_desc_unhealthy
                                else R.string.chat_status_desc_stopped,
                            ),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.danger,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 380.dp),
                        )
                        if (onStatusClick != null) {
                            Spacer(Modifier.height(spacing.md))
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = dims.minTouchTarget)
                                    .clip(RoundedCornerShape(radius.full))
                                    .background(colors.accent)
                                    .clickable(role = SemanticsRole.Button, onClick = onStatusClick)
                                    .padding(horizontal = spacing.lg, vertical = spacing.sm),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.chat_status_dialog_diagnostics),
                                    style = OpenCodeTypography.label.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.onAccent,
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(spacing.lg))

                    // Actionable prompt starter cards section
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = spacing.xs, vertical = spacing.xxs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.starter_section_heading).uppercase(),
                                style = OpenCodeTypography.caption,
                                color = colors.onSurfaceFaint,
                            )
                        }

                        // Two-column grid on every width, including phones:
                        // all four starters are visible without scrolling.
                        // On a narrow phone the cards are compact (icon +
                        // title only) so the titles still fit. Below 360dp
                        // even the compact titles need a third line, so the
                        // grid drops to a single full-width column there.
                        STARTER_PROMPTS.chunked(if (isNarrow) 1 else 2).forEach { rowPrompts ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                rowPrompts.forEach { item ->
                                    val title = stringResource(item.titleRes)
                                    val desc = stringResource(item.descRes)
                                    val prompt = stringResource(item.promptRes)
                                    ActionableStarterCard(
                                        title = title,
                                        description = desc,
                                        glyph = item.glyph,
                                        onClick = { onSuggestion(prompt) },
                                        modifier = Modifier.weight(1f),
                                        compact = !isWide,
                                    )
                                }
                                if (rowPrompts.size == 1 && !isNarrow) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Hero branding with subtle glowing gradient and minimalist Apple typography. */
@Composable
private fun HeroBranding(
    engineReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Subtle ambient glowing gradient container behind hero emblem.
        // 64dp, not 84dp: the hero is a signpost, not the content — the
        // starter prompts below it are what a new user actually needs.
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = 0.22f),
                            colors.accent.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(radius.lg))
                    .background(colors.surfaceRaised)
                    .border(
                        0.5.dp,
                        colors.accent.copy(alpha = 0.30f),
                        RoundedCornerShape(radius.lg),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.Code,
                    tint = colors.accent,
                    size = dims.iconMedium,
                )
            }
        }

        Spacer(Modifier.height(spacing.md))

        Text(
            text = stringResource(if (engineReady) R.string.empty_title_ready else R.string.empty_title_starting),
            // The titleLarge token (22sp / 28sp), not a hardcoded 24sp.
            style = OpenCodeTypography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
            ),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(spacing.xs))

        Text(
            text = stringResource(if (engineReady) R.string.empty_ready_body else R.string.empty_starting_body),
            style = OpenCodeTypography.bodySmall.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp),
        )
    }
}

/** One-line readiness status: engine • workspace • git.
 *
 * Replaces the full WorkspaceReadinessCard (three rows plus a section
 * header). The full diagnostics are still one tap away — this whole row
 * opens them via [onStatusClick] — and the header status pill does the
 * same, so nothing is lost, only the vertical space.
 */
@Composable
private fun ReadinessStatusRow(
    engineState: dev.bidzz.opencode.core.engine.EngineState,
    busy: Boolean,
    workspacePath: String?,
    onStatusClick: (() -> Unit)?,
    onProjectClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    val shape = RoundedCornerShape(radius.full)

    val status = engineState.toStatus(busy)
    val statusLabel = engineState.statusLabel(busy)
    val statusColor = colors.statusColor(status)
    val gitBranch = remember(workspacePath) { detectGitBranch(workspacePath) }
    val projectName = headerTitle(workspacePath)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // One row, two destinations (diagnostics everywhere, workspace
    // switch on the project name) — TalkBack used to read the nested
    // clickables as one confusing control. The row now announces itself
    // as a whole, and the project name gets a full 48dp tap area.
    val rowDescription = listOfNotNull(
        statusLabel,
        projectName,
        gitBranch,
    ).joinToString(", ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isPressed) colors.surfacePressed else colors.surface)
            .border(0.5.dp, colors.hairline, shape)
            .semantics { contentDescription = rowDescription }
            .then(
                if (onStatusClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        role = SemanticsRole.Button,
                        onClick = onStatusClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(dims.statusDotSize)
                .background(statusColor, CircleShape),
        )
        Text(
            text = statusLabel,
            style = OpenCodeTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Capped so a long status label cannot starve the weighted
            // project name of space on a narrow phone.
            modifier = Modifier.widthIn(max = 120.dp),
        )
        Text(text = "•", style = OpenCodeTypography.bodySmall, color = colors.onSurfaceFaint)
        // Tapping the project name still switches workspace, as the old
        // card's workspace row did; tapping anywhere else on the row opens
        // diagnostics. The inner click wins over the row click in Compose.
        Text(
            text = projectName,
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .defaultMinSize(minHeight = dims.minTouchTarget)
                .then(
                    if (onProjectClick != null) {
                        Modifier.clickable(
                            role = SemanticsRole.Button,
                            onClick = onProjectClick,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        Text(text = "•", style = OpenCodeTypography.bodySmall, color = colors.onSurfaceFaint)
        Text(
            text = gitBranch ?: stringResource(R.string.workspace_git_untracked),
            style = if (gitBranch != null) OpenCodeTypography.code else OpenCodeTypography.bodySmall,
            color = if (gitBranch != null) colors.onSurface else colors.onSurfaceFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Weighted like the project name, so the two share whatever
            // space is left instead of the branch taking its full intrinsic
            // width first and squeezing the project name to zero.
            modifier = Modifier.weight(1f, fill = false),
        )
        GlyphIcon(
            glyph = Glyph.ChevronRight,
            tint = colors.onSurfaceFaint,
            size = 12.dp,
        )
    }
}

/** Actionable prompt starter card with hover/press states and vector glyph. */
@Composable
private fun ActionableStarterCard(
    title: String,
    description: String,
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Compact form for the two-column grid on phones: icon + title only,
     * no description or chevron, so the title still fits in a ~150dp column.
     */
    compact: Boolean = false,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val shape = RoundedCornerShape(radius.lg)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "starterCardScale",
    )

    val cardBg by animateColorAsState(
        targetValue = when {
            isPressed -> colors.surfacePressed
            isHovered -> colors.surfaceRaised
            else -> colors.surface
        },
        animationSpec = tween(durationMillis = OpenCodeTheme.tokens.motion.quick),
        label = "starterCardBg",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isPressed -> colors.accent.copy(alpha = 0.50f)
            isHovered -> colors.accent.copy(alpha = 0.35f)
            else -> colors.hairline
        },
        animationSpec = tween(durationMillis = OpenCodeTheme.tokens.motion.quick),
        label = "starterCardBorder",
    )

    val iconBg by animateColorAsState(
        targetValue = when {
            isPressed || isHovered -> colors.accent.copy(alpha = 0.16f)
            else -> colors.fill
        },
        animationSpec = tween(durationMillis = OpenCodeTheme.tokens.motion.quick),
        label = "starterIconBg",
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            isPressed || isHovered -> colors.accent
            else -> colors.onSurfaceMuted
        },
        animationSpec = tween(durationMillis = OpenCodeTheme.tokens.motion.quick),
        label = "starterIconTint",
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
            .border(0.5.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = SemanticsRole.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = spacing.md + spacing.xxs,
                vertical = if (compact) spacing.sm else spacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 28.dp else 36.dp)
                .clip(RoundedCornerShape(radius.md))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = glyph,
                tint = iconTint,
                size = if (compact) 14.dp else 18.dp,
            )
        }

        Spacer(Modifier.width(if (compact) spacing.sm else spacing.md))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = OpenCodeTypography.bodyStrong.copy(
                    fontSize = if (compact) 13.sp else 14.5.sp,
                    lineHeight = if (compact) 17.sp else 18.sp,
                ),
                color = colors.onSurface,
                maxLines = if (compact) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact && description.isNotBlank()) {
                Spacer(Modifier.height(spacing.xxs))
                Text(
                    text = description,
                    style = OpenCodeTypography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                    ),
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!compact) {
            Spacer(Modifier.width(spacing.xs))

            GlyphIcon(
                glyph = Glyph.ChevronRight,
                tint = if (isHovered || isPressed) colors.accent else colors.onSurfaceFaint,
                size = 14.dp,
            )
        }
    }
}

private data class ActionablePrompt(
    val titleRes: Int,
    val descRes: Int,
    val promptRes: Int,
    val glyph: Glyph,
)

private val STARTER_PROMPTS = listOf(
    ActionablePrompt(
        titleRes = R.string.starter_explain_title,
        descRes = R.string.starter_explain_desc,
        promptRes = R.string.starter_explain_prompt,
        glyph = Glyph.Folder,
    ),
    ActionablePrompt(
        titleRes = R.string.starter_bugs_title,
        descRes = R.string.starter_bugs_desc,
        promptRes = R.string.starter_bugs_prompt,
        glyph = Glyph.Terminal,
    ),
    ActionablePrompt(
        titleRes = R.string.starter_tests_title,
        descRes = R.string.starter_tests_desc,
        promptRes = R.string.starter_tests_prompt,
        glyph = Glyph.Pencil,
    ),
    ActionablePrompt(
        titleRes = R.string.starter_review_title,
        descRes = R.string.starter_review_desc,
        promptRes = R.string.starter_review_prompt,
        glyph = Glyph.Code,
    ),
)

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
                role = SemanticsRole.Button,
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

/** Suggestion starter card for backward compatibility. */
@Composable
private fun SuggestionCard(
    suggestion: String,
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionableStarterCard(
        title = suggestion,
        description = "",
        glyph = glyph,
        onClick = onClick,
        modifier = modifier,
    )
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
