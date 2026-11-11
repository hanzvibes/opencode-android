package dev.bidzz.opencode.ui.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import android.content.Context
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import dev.bidzz.opencode.ui.announceForAccessibility
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.bidzz.opencode.core.design.OpenCodeColors
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.core.design.Status
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.HairlineSeparator
import dev.bidzz.opencode.ui.IconAction
import dev.bidzz.opencode.ui.files.FileRow
import dev.bidzz.opencode.ui.files.rankQuickOpen
import dev.bidzz.opencode.ui.PersistentSidebarWidth
import dev.bidzz.opencode.ui.LocalizedAlertDialog
import dev.bidzz.opencode.ui.message
import dev.bidzz.opencode.ui.sessions.DeleteSessionDialog
import dev.bidzz.opencode.ui.sessions.RenameSessionDialog
import dev.bidzz.opencode.ui.sessions.SessionActionMenu
import dev.bidzz.opencode.ui.sessions.SessionDateGroup
import dev.bidzz.opencode.ui.sessions.formatCompactTokens
import dev.bidzz.opencode.ui.sessions.groupSessions
import dev.bidzz.opencode.ui.sessions.label
import dev.bidzz.opencode.ui.sessions.SessionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** Re-sends the last prompt after a failure; no-op when there is none. */
    onRetryError: () -> Unit = {},
    /** Grows the transcript window and fetches older messages (item 28). */
    onLoadEarlier: () -> Unit = {},
    /** Re-auth after an auth failure: restarts the engine so fresh
     * credentials are issued (the banner routes REAUTH here). */
    onReauth: () -> Unit = {},
    /** Edit/resend (Phase 2, finding 3): load a user message into the
     * composer in editing mode, and re-send one user message's text. */
    onEditMessage: (String, String) -> Unit = { _, _ -> },
    onResendMessage: (String) -> Unit = {},
    /** Answers a question card without touching the draft (item 9). */
    onAnswerQuestion: (String) -> Unit = {},
    /** Submit / cancel the edit in progress in the composer. */
    onSubmitEdit: () -> Unit = {},
    onCancelEdit: () -> Unit = {},
    /** Defer a permission request, and bring a deferred one back. */
    onDeferPermission: () -> Unit = {},
    onRestoreDeferredPermission: () -> Unit = {},
    /** Workspace file index for the @-mention / attach picker: the
     * explorer's quick-open index, shared — the picker never builds its
     * own listing. Empty until the index is built. */
    fileIndex: List<FileRow> = emptyList(),
    /** True while that index is being built. */
    fileIndexLoading: Boolean = false,
    /** True when the index walk hit a failure: the index is partial. */
    fileIndexFailed: Boolean = false,
    /** Builds the index lazily on first picker open; no-op once built. */
    onEnsureFileIndex: () -> Unit = {},
    /**
     * SAF attachments (files outside the workspace): the screen owns the
     * system picker launcher and the read — the view model owns the state
     * and never sees a Context or a Uri. [onAttachmentAdded] receives a
     * file already read in as text; [onAttachmentRefused] receives the
     * refusal string plus the filename to format into it.
     */
    onRemoveAttachment: (String) -> Unit = {},
    onAttachmentAdded: (AttachedFile) -> Unit = {},
    onAttachmentRefused: (Int, String?) -> Unit = { _, _ -> },
    /**
     * Opens the system file picker for a file outside the workspace (SAF).
     * The launcher lives in MainActivity: a Compose launcher here crashed
     * on launch because the composition has no ActivityResultRegistryOwner.
     * The picked file arrives on [safFilePicked].
     */
    onRequestSafFile: () -> Unit = {},
    /** URIs picked in the system file picker; read in as text below. */
    safFilePicked: Flow<Uri> = emptyFlow(),
    onDismissAttachNotice: () -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * True at >=840dp wide (see [dev.bidzz.opencode.ui.isExpandedWidth]):
     * the drawer becomes a persistent sidebar instead of a modal sheet.
     * False keeps the phone layout exactly.
     */
    isExpanded: Boolean = false,
    /**
     * False when this screen is embedded as the detail pane of a
     * list-detail layout that already shows a session list: no sidebar
     * and no modal drawer, just the conversation.
     */
    showSidebar: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
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

    // Unanswered questions below the read watermark: a pending question
    // scrolled out of view is easy to miss, so it gets its own badge on
    // the jump button — never an auto-scroll, which would yank the reader
    // (same rule as A-3). A question whose call is done no longer waits.
    val pendingQuestionCount by remember {
        derivedStateOf {
            if (nearBottom) 0 else state.messages.drop(lastReadMessageCount)
                .sumOf { message ->
                    message.parts.orEmpty()
                        .filterIsInstance<ChatTurnPart.Question>()
                        .count { !it.call.isDone }
                }
        }
    }

    // The first unread assistant message: the jump button and the "New
    // messages" divider both target this index, not the last item — the
    // reader asked to return to where they left off, not to the bottom.
    val firstUnreadIndex by remember {
        derivedStateOf {
            state.messages.withIndex().firstOrNull { (index, message) ->
                index >= lastReadMessageCount && message.role == Role.ASSISTANT
            }?.index ?: -1
        }
    }

    // List-space twin of firstUnreadIndex: the LazyColumn has one extra
    // head item while older messages are windowed out ("load earlier"),
    // so message indices are shifted by one in list space. The divider
    // above still compares message indices, but the visibility check
    // and the jump scroll below compare LazyColumn indices.
    val firstUnreadListIndex by remember {
        derivedStateOf {
            if (firstUnreadIndex < 0) -1
            else firstUnreadIndex + (if (state.hasMoreOlder) 1 else 0)
        }
    }

    // The badge stays until the reader actually reaches the unread message:
    // once the first unread item is on screen it counts as read and the
    // watermark advances past it, so the badge drains as the reader scrolls
    // through instead of clearing on any scroll. Keyed on the index as
    // well as the visibility: several unread messages can be on screen at
    // once, and the effect must re-run for each one it advances past.
    val firstUnreadVisible by remember {
        derivedStateOf {
            if (firstUnreadListIndex < 0) false
            else listState.layoutInfo.visibleItemsInfo.any { it.index == firstUnreadListIndex }
        }
    }
    LaunchedEffect(firstUnreadVisible, firstUnreadIndex) {
        if (firstUnreadVisible && firstUnreadIndex >= 0) {
            lastReadMessageCount = firstUnreadIndex + 1
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

    // Accessibility announcements (Phase 2, finding 14): ONLY on
    // transitions, never for progress — a run finishing or failing, and a
    // permission arriving. The stream itself is never narrated.
    val a11yContext = LocalContext.current
    val runFinishedText = stringResource(R.string.a11y_run_finished)
    val runFailedText = stringResource(R.string.a11y_run_failed)
    val permissionArrivedText = stringResource(R.string.a11y_permission_arrived)
    var wasBusyForAnnouncement by remember { mutableStateOf(false) }
    LaunchedEffect(state.busy) {
        if (wasBusyForAnnouncement && !state.busy) {
            announceForAccessibility(
                a11yContext,
                if (state.failure != null || state.failureKey != null) runFailedText else runFinishedText,
            )
        }
        wasBusyForAnnouncement = state.busy
    }

    // A permission arriving moves focus to the prompt as well as announcing
    // it: the agent is blocked until it is answered, so the operator must
    // land on the one control that unblocks the conversation.
    val permissionFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.pendingPermission?.id) {
        if (state.pendingPermission != null) {
            announceForAccessibility(a11yContext, permissionArrivedText)
            permissionFocusRequester.requestFocus()
        }
    }

    // Refocuses the composer after a send (see the Composer call below):
    // a timestamp, not a boolean, so repeated sends re-trigger it.
    var composerFocusSignal by remember { mutableLongStateOf(0L) }

    val targetLastIndex by remember {
        derivedStateOf {
            val hasWorking = state.busy && state.messages.lastOrNull()?.role == Role.USER
            // One extra head item while older messages are windowed out
            // ("load earlier"), so message indices are shifted by one in
            // list space; without the shift the follow scroll targets the
            // second-to-last message and the newest one hangs below the
            // fold. The turn-tail slot always sits last and is already
            // counted by the hasWorking term below.
            val headOffset = if (state.hasMoreOlder) 1 else 0
            // No error term: the error is a pinned banner above the list
            // now, not a tail item, so it never takes a list index.
            val totalCount = headOffset + state.messages.size + (if (hasWorking) 1 else 0)
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
        // Never fight an in-progress gesture: a token landing mid-drag
        // used to yank the list out from under the reader's finger, which
        // is half of the "stuck scroll" feeling. After the gesture ends
        // the next token re-evaluates and follows normally.
        if (state.messages.isNotEmpty() && nearBottom && !listState.isScrollInProgress) {
            // Bottom-pin, not top-pin (see scrollToItemBottom): instant
            // while busy so a tap on Jump-to-latest is not fought by a
            // per-token snap; animated only when idle.
            if (state.busy) {
                listState.scrollToItemBottom(targetLastIndex)
            } else {
                listState.animateScrollToItemBottom(targetLastIndex)
            }
        }
    }

    // Opening an old session lands at the bottom (latest) once per session,
    // and marks everything read so the jump-to-latest badge never shows a
    // phantom count carried over from the previous session.
    // Keyed on messages.size as well as the session: history loads
    // asynchronously, so an effect keyed only on sessionID fires while
    // messages are still empty, its guard fails, and the scroll never
    // happens — the reader lands at the top of the history instead.
    var scrolledSessionID by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.sessionID, state.messages.size) {
        if (state.messages.isNotEmpty() && scrolledSessionID != state.sessionID) {
            listState.scrollToItemBottom(targetLastIndex)
            lastReadMessageCount = state.messages.size
            scrolledSessionID = state.sessionID
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

    // Attach / @-mention picker: one picker, two entry points — the @ chip
    // opens it as a dialog with its own search field, typing @ in the draft
    // opens it inline with the composer as the search field. The mention
    // query is derived from the draft, so no extra composer state is kept.
    var attachPickerOpen by remember { mutableStateOf(false) }
    var attachPickerQuery by remember { mutableStateOf("") }
    var attachSelectedIndex by remember { mutableIntStateOf(0) }

    val mentionQuery = remember(state.draft) { extractMentionQuery(state.draft) }
    // The command palette wins over a mention: a draft cannot be both, but
    // "/@x" would otherwise match both patterns.
    val mentionActive = mentionQuery != null && !paletteActive && !attachPickerOpen
    val mentionResults = remember(fileIndex, mentionQuery) {
        if (mentionQuery == null) emptyList() else rankQuickOpen(fileIndex, mentionQuery)
    }
    val attachResults = remember(fileIndex, attachPickerQuery) {
        rankQuickOpen(fileIndex, attachPickerQuery)
    }

    // The index is built lazily on first picker open, not on entering chat:
    // opening the picker is the first moment it is needed.
    LaunchedEffect(attachPickerOpen, mentionActive) {
        if (attachPickerOpen || mentionActive) onEnsureFileIndex()
    }

    // A session change ends the dialog picker's context: the draft it
    // would insert into belongs to the old session (a switch clears it,
    // and the lazy first-session creation on send moves the draft to a new
    // one). The inline picker needs nothing — it is derived from the draft
    // and dies with it.
    LaunchedEffect(state.sessionID) { attachPickerOpen = false }

    /** Picks a file into the draft as an `@relative/path` mention. */
    fun pickAttachFile(row: FileRow) {
        onDraftChange(insertMention(state.draft, row.path))
        attachPickerOpen = false
        // Focus returns to the composer so typing continues without a tap:
        // the picker's search field held focus in dialog mode.
        composerFocusSignal = System.currentTimeMillis()
    }

    /** Dismisses the inline mention picker by dropping the `@query` token. */
    fun dismissMention() {
        onDraftChange(removeMentionToken(state.draft))
    }

    BackHandler(enabled = attachPickerOpen) { attachPickerOpen = false }
    BackHandler(enabled = mentionActive) { dismissMention() }

    // System file picker for files outside the workspace (SAF): reached
    // from the "Browse device files" row at the foot of the attach picker.
    // The launcher lives in MainActivity and the picked URI arrives on
    // [safFilePicked] — a Compose launcher here crashed on launch because
    // the composition has no ActivityResultRegistryOwner. OpenDocument
    // (not GetContent): same contract family the app already uses for the
    // workspace folder (MainActivity), and it returns a content:// URI the
    // resolver can read without any storage permission. "*/*" on purpose —
    // the text check happens after the pick (see readSafFile), so a
    // .py/.md/.json the provider does not call text/* is still pickable
    // and a binary is refused with a message, not by being unpickable.
    // The read runs on IO: a 64 KB file is small, but the resolver can
    // block on a slow provider. The resolver is read here, in the
    // composable body — LocalContext.current is a @Composable call and
    // cannot be used inside the suspend LaunchedEffect below.
    val safContentResolver = LocalContext.current.contentResolver
    LaunchedEffect(safFilePicked) {
        safFilePicked.collect { uri ->
            val result = withContext(Dispatchers.IO) {
                readSafFile(safContentResolver, uri)
            }
            when (result) {
                is SafReadResult.Success -> onAttachmentAdded(result.file)
                is SafReadResult.TooLarge -> onAttachmentRefused(R.string.attach_file_too_large, result.name)
                is SafReadResult.NotText -> onAttachmentRefused(R.string.attach_not_text_file, result.name)
                is SafReadResult.Unreadable -> onAttachmentRefused(R.string.attach_unreadable_file, result.name)
            }
        }
    }

    /** Closes the drawer, then runs [action]; the drawer must not stay open over the next screen. */
    fun closeThen(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    // The header + transcript + composer column, shared by the
    // modal-drawer (phones), persistent-sidebar (tablets) and bare-detail
    // (embedded in a list-detail layout) shells below. A local lambda, not
    // a separate composable, so every piece of screen state above stays in
    // scope and the phone path renders exactly what it rendered before.
    val chatContent: @Composable () -> Unit = {
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
                models = state.models,
                activeModel = state.activeModel,
                onModel = onModel,
                // Context meter (Tier 2): tokens in the session's context
                // over the active model's window. The limit is the model's
                // own `limit.context` — never the name-based fallback the
                // selector badge uses, so a guessed denominator can never
                // paint a wrong percentage.
                contextUsedTokens = state.contextUsedTokens,
                contextLimit = state.models.firstOrNull { it.ref() == state.activeModel }?.limit?.context,
                // No menu button at expanded widths: the sidebar is already
                // visible, so there is no drawer to open.
                showMenuButton = !isExpanded,
                onMenu = {
                    onDrawerOpen()
                    scope.launch { drawerState.open() }
                },
                onStatusClick = onStatusClick,
                onNewSession = onNewSession,
            )

            // Pinned status banners: the error and the reconnecting state
            // live here, above the transcript, so they stay visible even
            // when the reader has scrolled up — a tail item in the
            // LazyColumn scrolls away exactly when it is needed most.
            // Both are capped at the content width like the transcript.
            val hasError = state.failure != null || state.failureKey != null
            if (hasError || state.connecting) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = dims.contentMaxWidth)
                            .padding(horizontal = spacing.md),
                    ) {
                        if (state.connecting) {
                            ReconnectingIndicator(
                                modifier = Modifier.padding(top = spacing.sm),
                            )
                        }
                        if (hasError) {
                            PinnedErrorBanner(
                                message = state.failure?.message()
                                    ?: stringResource(state.failureKey!!),
                                // Typed retry (Phase 2, finding 18): Retry is
                                // only offered when the view model classified
                                // the failure as retryable. Auth routes out to
                                // the engine restart (re-auth); the rest go
                                // through the single onRetry callback.
                                canRetry = state.retryKind != RetryKind.NONE,
                                onRetry = {
                                    if (state.retryKind == RetryKind.REAUTH) onReauth() else onRetryError()
                                },
                                onDismiss = onDismissError,
                                modifier = Modifier.padding(
                                    top = spacing.sm,
                                    bottom = spacing.sm,
                                ),
                            )
                        }
                    }
                }
            }

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
                        activeModel = state.activeModel,
                        models = state.models,
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
                            // The transcript is windowed (item 28): only the
                            // newest messages load up front, and older ones
                            // arrive one tap at a time.
                            if (state.hasMoreOlder) {
                                item(key = "load-earlier") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (state.loadingEarlier) {
                                                stringResource(R.string.common_loading)
                                            } else {
                                                stringResource(R.string.chat_load_earlier)
                                            },
                                            style = OpenCodeTypography.caption.copy(
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            color = colors.accent,
                                            modifier = Modifier
                                                .clickable(
                                                    role = SemanticsRole.Button,
                                                    enabled = !state.loadingEarlier,
                                                    onClick = onLoadEarlier,
                                                )
                                                .padding(
                                                    horizontal = spacing.md,
                                                    vertical = spacing.sm,
                                                ),
                                        )
                                    }
                                }
                            }
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

                                Column(
                                    modifier = Modifier
                                        .padding(top = topSpacing)
                                        .animateItem(),
                                ) {
                                    // Date divider (audit C-A): a new day in
                                    // the transcript gets a labelled hairline
                                    // ("Today", "Yesterday", or the date).
                                    // Messages without an engine timestamp
                                    // never start one — a divider must come
                                    // from real dates, not from message order.
                                    val createdAt = message.createdAtMillis
                                    val prevCreatedAt = prevMessage?.createdAtMillis
                                    val showDateDivider = createdAt != null &&
                                        (prevCreatedAt == null || !isSameDay(prevCreatedAt, createdAt))
                                    if (showDateDivider && createdAt != null) {
                                        // Fades in on entry instead of
                                        // popping in with the message.
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = true,
                                            enter = fadeIn(
                                                animationSpec = tween(motion.standard),
                                            ),
                                        ) {
                                            DateDivider(
                                                millis = createdAt,
                                                modifier = Modifier.padding(bottom = spacing.sm),
                                            )
                                        }
                                    }
                                    // "New messages" divider (audit 15):
                                    // the same hairline language as the date
                                    // divider, marking the first message the
                                    // reader has not seen yet.
                                    if (index == firstUnreadIndex) {
                                        NewMessagesDivider(
                                            modifier = Modifier.padding(bottom = spacing.sm),
                                        )
                                    }
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
                                        // Per-turn thinking time (Phase 2,
                                        // finding 7): a finished turn shows
                                        // its own stored seconds; a streaming
                                        // turn falls back to the live run
                                        // timer; an idle turn shows nothing —
                                        // the run clock (0 when idle) is never
                                        // passed to every turn.
                                        thinkingElapsedSeconds = message.thinkingSeconds
                                            ?: if (message.streaming) busyElapsedSeconds else null,
                                        // Turn footer (Phase 2, finding 6)
                                        // and per-tool durations (finding 20).
                                        usageTokens = message.usageTokens,
                                        usageCostUsd = message.usageCostUsd,
                                        turnDurationMs = message.turnDurationMs,
                                        stopReason = message.stopReason,
                                        toolDurations = message.toolDurations,
                                        // Edit/resend (Phase 2, finding 3):
                                        // user bubbles only.
                                        onEdit = if (message.role == Role.USER) {
                                            { onEditMessage(message.id, message.text) }
                                        } else {
                                            null
                                        },
                                        onResend = if (message.role == Role.USER) {
                                            { onResendMessage(message.id) }
                                        } else {
                                            null
                                        },
                                        // Delivered (C-B): a user message counts
                                        // as answered once any assistant turn
                                        // appears after it — a local read
                                        // receipt, not a server status.
                                        delivered = message.role == Role.USER &&
                                            state.messages.drop(index + 1).any { it.role == Role.ASSISTANT },
                                        // Turn start time for the tool trace
                                        // (C-C): the engine records no
                                        // per-tool timestamps, so the trace
                                        // shows when its turn started.
                                        toolsStartedAtMillis = message.createdAtMillis,
                                        // Quote into the composer (C-E): the
                                        // quote is appended after the draft,
                                        // never over it.
                                        onQuote = { quote ->
                                            val prefix = if (state.draft.isBlank()) "" else state.draft.trimEnd() + "\n"
                                            onDraftChange(prefix + quote)
                                        },
                                        onAnswerQuestion = onAnswerQuestion,
                                    )
                                }
                            }
                            // Placeholder → answer transition. This slot stays
                            // in the list so the AnimatedContent can animate
                            // the working placeholder out (fade + ~8dp slide,
                            // motion.standard) as the answering turn arrives;
                            // a slot that is removed the moment busy ends
                            // cannot animate at all. Keyed on the working
                            // state vs the answering message's id, so each
                            // new turn re-triggers it. Empty when idle, and
                            // it sits last so targetLastIndex never lands on
                            // it — the follow-scroll math is untouched.
                            item(key = "turn-tail") {
                                val tailState = when {
                                    state.busy && state.messages.lastOrNull()?.role == Role.USER -> "working"
                                    else -> state.messages.lastOrNull()?.let { "answer-${it.id}" } ?: "empty"
                                }
                                AnimatedContent(
                                    targetState = tailState,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(motion.standard)) +
                                            slideInVertically(animationSpec = tween(motion.standard)) { it / 12 }) togetherWith
                                            (fadeOut(animationSpec = tween(motion.standard)) +
                                                slideOutVertically(animationSpec = tween(motion.standard)) { -it / 12 })
                                    },
                                    modifier = Modifier.animateItem(),
                                    label = "placeholder_to_answer",
                                ) { key ->
                                    if (key == "working") {
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
                                pendingQuestions = pendingQuestionCount,
                                isBusy = state.busy,
                                onClick = {
                                    scope.launch {
                                        // First unread, not the bottom: the
                                        // reader asked to resume where they
                                        // left off, so it stays top-pinned.
                                        // With nothing unread it still falls
                                        // through to the latest, bottom-pinned.
                                        if (firstUnreadListIndex >= 0) {
                                            listState.animateScrollToItem(firstUnreadListIndex)
                                        } else {
                                            listState.animateScrollToItemBottom(targetLastIndex)
                                        }
                                    }
                                },
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

            // Composer, command palette and permission prompt share one
            // centred 720dp column (audit 9): on a tablet they must not
            // stretch edge to edge while the transcript above is capped.
            // The model picker moved to the header, so the composer
            // controls row is agent-only now.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = dims.contentMaxWidth),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.pendingPermission != null,
                        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                        exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
                    ) {
                        state.pendingPermission?.let { request ->
                            PermissionPrompt(
                                request = request,
                                onReply = onPermissionReply,
                                onAnswerLater = onDeferPermission,
                                replyFailed = state.permissionReplyFailed,
                                focusRequester = permissionFocusRequester,
                                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                            )
                        }
                    }

                    // A deferred permission waits here until it is answered:
                    // the engine is still blocked on it, so it must stay one
                    // tap away rather than vanishing. It disappears on its
                    // own when the run ends (see ChatViewModel.onRepoState).
                    if (state.pendingPermission == null && state.deferredPermission != null) {
                        DeferredPermissionRow(
                            onRestore = onRestoreDeferredPermission,
                            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                        )
                    }

                    val paletteState = rememberCommandPaletteState(
                        commands = state.commands,
                        draft = state.draft,
                        skills = state.skills,
                    )

                    CommandSuggestions(
                        paletteState = paletteState,
                        onPick = { name -> onDraftChange("/$name ") },
                        onDismiss = { dismissPalette() },
                        // A skill is mentioned in the draft as plain text, not
                        // run as a command: the agent loads it from the mention.
                        onSkillPick = { name -> onDraftChange("$name ") },
                    )

                    // One attach picker for both entry points: the @ chip
                    // (dialog mode, its own search field) and typing @ in
                    // the draft (inline mode, the composer is the field).
                    if (attachPickerOpen || mentionActive) {
                        AttachFilePicker(
                            results = if (attachPickerOpen) attachResults else mentionResults,
                            totalFiles = fileIndex.size,
                            loading = fileIndexLoading,
                            failed = fileIndexFailed,
                            onRetry = onEnsureFileIndex,
                            query = if (attachPickerOpen) attachPickerQuery else mentionQuery.orEmpty(),
                            onQueryChange = { if (attachPickerOpen) attachPickerQuery = it },
                            selectedIndex = attachSelectedIndex,
                            onSelectedIndexChange = { attachSelectedIndex = it },
                            onPick = { pickAttachFile(it) },
                            onClose = {
                                if (attachPickerOpen) attachPickerOpen = false else dismissMention()
                            },
                            showSearchField = attachPickerOpen,
                            // Device files live outside the workspace, so
                            // they are not @-mentions: close this panel
                            // first, then open the system picker — the
                            // picked file is read in as text and inlined.
                            onBrowseDeviceFiles = {
                                attachPickerOpen = false
                                onRequestSafFile()
                            },
                        )
                    }

                    // A refused attachment (too large, not a text file,
                    // unreadable): a slim dismissable row above the
                    // composer, not the pinned error banner — that banner
                    // is for engine failures, and this file never reached
                    // the engine at all.
                    state.attachNoticeKey?.let { noticeKey ->
                        AttachNoticeRow(
                            message = state.attachNoticeArg?.let { arg ->
                                stringResource(noticeKey, arg)
                            } ?: stringResource(noticeKey),
                            onDismiss = onDismissAttachNotice,
                            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                        )
                    }

                    // Placeholder names the model/agent (Phase 2, finding 24):
                    // the human model label when the catalog knows it, else
                    // the raw model id; the agent name the same way.
                    val modelLabel = remember(state.activeModel, state.models) {
                        state.models.firstOrNull { it.ref() == state.activeModel }?.label()
                            ?: state.activeModel?.id
                    }
                    val agentLabel = remember(state.activeAgent, state.agents) {
                        state.agents.firstOrNull { it.id == state.activeAgent }
                            ?.name?.takeIf { it.isNotBlank() } ?: state.activeAgent
                    }

                    // IME Send and Ctrl/Cmd+Enter share one chain (item 17):
                    // palette → mention → edit-submit → send. The
                    // Ctrl/Cmd+Enter branch used to call onSend() raw, so
                    // "/te" + Ctrl+Enter sent the raw token, and Ctrl+Enter
                    // while editing sent a new prompt while editingMessageId
                    // stayed set, wedging the composer in Editing mode.
                    val submitFromComposer: () -> Unit = {
                        val picked = if (paletteActive) paletteState.currentSelectedCommand() else null
                        // Complete the selected mention, mirroring the command
                        // palette above — but only when there is a match.
                        val pickedMention = if (mentionActive) {
                            mentionResults.getOrNull(attachSelectedIndex)
                        } else {
                            null
                        }
                        if (picked != null) {
                            onDraftChange("/${picked.name} ")
                        } else if (pickedMention != null) {
                            onDraftChange(insertMention(state.draft, pickedMention.path))
                        } else if (state.editingMessageId != null) {
                            onSubmitEdit()
                        } else {
                            onSend()
                        }
                        // Refocus the composer after a send so the next
                        // message can be typed without tapping it again.
                        composerFocusSignal = System.currentTimeMillis()
                    }

                    Composer(
                        value = state.draft,
                        onValueChange = onDraftChange,
                        onSend = submitFromComposer,
                        onStop = onStop,
                        // A real attach action: the @ chip renders only
                        // when this is non-null (Composer.kt), and the
                        // picker opens over the shared workspace index.
                        onAttachFile = {
                            attachPickerQuery = ""
                            attachSelectedIndex = 0
                            attachPickerOpen = true
                        },
                        attachments = state.pendingAttachments,
                        onRemoveAttachment = onRemoveAttachment,
                        busy = state.busy,
                        stopping = state.stopping,
                        enabled = state.workspaceReady,
                        promptHistory = state.sentPrompts,
                        // The queue is a list (item 6): show the first
                        // follow-up, with a count when more are waiting.
                        queuedText = when {
                            state.queuedPrompts.isEmpty() -> null
                            state.queuedPrompts.size == 1 -> state.queuedPrompts[0].typedText.ifBlank { state.queuedPrompts[0].fullText }
                            else -> state.queuedPrompts[0].typedText.ifBlank { state.queuedPrompts[0].fullText } + " (+${state.queuedPrompts.size - 1})"
                        },
                        isEditing = state.editingMessageId != null,
                        onCancelEdit = onCancelEdit,
                        modelLabel = modelLabel,
                        agentLabel = agentLabel,
                        autofocus = true,
                        focusRequestSignal = composerFocusSignal,
                        modifier = Modifier.navigationBarsPadding(),
                        onKeyEvent = { keyEvent ->
                            // Ctrl/Cmd+Enter sends from a physical keyboard; a plain
                            // Enter stays a newline and is left to the text field.
                            // It runs the same chain as IME Send (item 17) — palette,
                            // mention, edit-submit — never the raw onSend().
                            if (keyEvent.type == KeyEventType.KeyDown &&
                                (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                                (keyEvent.isCtrlPressed || keyEvent.isMetaPressed)
                            ) {
                                submitFromComposer()
                                true
                            } else if (mentionActive) {
                                // Typing-@ picker owns the navigation keys,
                                // mirroring the command palette below.
                                handleAttachPickerKeyEvent(
                                    event = keyEvent,
                                    results = mentionResults,
                                    selectedIndex = attachSelectedIndex,
                                    onSelectedIndexChange = { attachSelectedIndex = it },
                                    onPickSelected = {
                                        mentionResults.getOrNull(attachSelectedIndex)?.let { row ->
                                            onDraftChange(insertMention(state.draft, row.path))
                                        }
                                    },
                                    onClose = { dismissMention() },
                                )
                            } else {
                                paletteState.handleKeyEvent(
                                    event = keyEvent,
                                    onPick = { name -> onDraftChange("/$name ") },
                                    onDismiss = { dismissPalette() },
                                    onSkillPick = { name -> onDraftChange("$name ") },
                                )
                            }
                        },
                        controls = {
                            ChatControls(
                                agents = state.agents,
                                activeAgent = state.activeAgent,
                                onAgent = onAgent,
                                enabled = !state.busy,
                            )
                        },
                    )
                }
            }
        }
    }

    // Persistent sidebar (expanded) vs modal drawer (compact) vs bare
    // detail (embedded in a list-detail layout): the conversation column
    // above is shared by all three, so the phone layout is untouched.
    val showPersistentSidebar = isExpanded && showSidebar
    when {
        showPersistentSidebar -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background),
            ) {
                ChatSidebar(
                    projectTitle = headerTitle(state.workspacePath),
                    currentSessionID = state.sessionID,
                    sessions = sessions,
                    // No close-then-open here: there is no drawer to close,
                    // and selecting a session opens it in the detail pane
                    // beside the sidebar without a screen switch.
                    onNewSession = onNewSession,
                    onOpenSession = onOpenSession,
                    onAllSessions = onSessionsClick,
                    onFiles = onFilesClick,
                    onProject = onProjectClick,
                    onSettings = onSettingsClick,
                    onDiagnostics = onDiagnosticsClick,
                    onDeveloper = onAboutClick,
                    onRenameSession = onRenameSession,
                    onDeleteSession = onDeleteSession,
                    onTogglePinSession = onTogglePinSession,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    chatContent()
                }
            }
        }
        isExpanded -> chatContent()
        else -> {
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
                chatContent()
            }
        }
    }
}

private fun isSameDay(aMillis: Long, bMillis: Long): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(aMillis).atZone(zone).toLocalDate() ==
        Instant.ofEpochMilli(bMillis).atZone(zone).toLocalDate()
}

/**
 * Text-safe status colour: WORKING reads [OpenCodeColors.accentText] in
 * light (accent #007AFF is only 4.02:1 as text), while READY / ERROR /
 * IDLE already are the dark AA text variants. Dots, fills, borders and
 * backgrounds keep [OpenCodeColors.statusColor] — only text goes here.
 */
private fun statusTextColor(colors: OpenCodeColors, status: Status): Color =
    if (status == Status.WORKING) colors.accentText else colors.statusColor(status)

/**
 * Vivid status colour for dots only: in light the text roles are dark AA
 * variants, but a dot wants the vivid colour (successBright/dangerBright).
 * WORKING stays [OpenCodeColors.activity] and IDLE stays neutral — neither
 * has a Bright role. In dark the Bright roles equal the text roles, so
 * this is a no-op there.
 */
private fun statusDotColor(colors: OpenCodeColors, status: Status): Color = when (status) {
    Status.READY -> colors.successBright
    Status.WORKING -> colors.activity
    Status.ERROR -> colors.dangerBright
    Status.IDLE -> colors.neutral
}

/** "Today" / "Yesterday", otherwise the date — in the device locale. */
@Composable
private fun dateDividerLabel(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> stringResource(R.string.date_today)
        today.minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }
}

/** Centred date pill on a hairline, inserted when the day changes (C-A). */
@Composable
private fun DateDivider(
    millis: Long,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HairlineSeparator(modifier = Modifier.weight(1f))
        Text(
            text = dateDividerLabel(millis),
            style = OpenCodeTypography.caption,
            color = colors.onSurfaceFaint,
            modifier = Modifier.padding(horizontal = spacing.sm),
        )
        HairlineSeparator(modifier = Modifier.weight(1f))
    }
}

/** Centred "New messages" label on a hairline, marking the first unread message (audit 15). */
@Composable
private fun NewMessagesDivider(
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HairlineSeparator(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.chat_new_messages),
            style = OpenCodeTypography.caption,
            color = colors.accent,
            modifier = Modifier.padding(horizontal = spacing.sm),
        )
        HairlineSeparator(modifier = Modifier.weight(1f))
    }
}

/**
 * Slim row for a deferred permission request (Phase 2, finding 17): the
 * engine is still blocked on it, so it stays one tap away. Tapping
 * restores the full prompt; it never answers anything by itself.
 */
@Composable
private fun DeferredPermissionRow(
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dims.minTouchTarget)
            .clip(RoundedCornerShape(radius.md))
            .background(colors.surface)
            .border(0.5.dp, colors.warning.copy(alpha = 0.35f), RoundedCornerShape(radius.md))
            .clickable(role = SemanticsRole.Button, onClick = onRestore)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        GlyphIcon(
            glyph = Glyph.Warning,
            tint = colors.warning,
            size = 16.dp,
        )
        Text(
            text = stringResource(R.string.permission_deferred),
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.permission_deferred_action),
            style = OpenCodeTypography.label,
            color = colors.accent,
        )
    }
}

/**
 * Slim row for a refused attachment (file too large, not a text file,
 * unreadable): the file never reached the engine, so this is not the
 * pinned error banner — it is a local refusal with its reason, one tap
 * to dismiss. Mirrors [DeferredPermissionRow]'s shape; the danger tint
 * marks it as a refusal rather than a waiting request.
 */
@Composable
private fun AttachNoticeRow(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    // Hoisted out of the .semantics {} block below: stringResource() is a
    // @Composable and cannot be called inside a semantics lambda.
    val closeDescription = stringResource(R.string.chat_status_dialog_close)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dims.minTouchTarget)
            .clip(RoundedCornerShape(radius.md))
            .background(colors.surface)
            .border(0.5.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(radius.md))
            .padding(start = spacing.md, top = spacing.xs, bottom = spacing.xs, end = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        GlyphIcon(
            glyph = Glyph.Warning,
            tint = colors.danger,
            size = 16.dp,
        )
        Text(
            text = message,
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceMuted,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(
                    role = SemanticsRole.Button,
                    onClick = onDismiss,
                )
                .semantics { contentDescription = closeDescription },
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

/**
 * Pinned error banner (audit 18): sits above the transcript, outside the
 * LazyColumn, so it stays visible while scrolled up. One callback only —
 * the typed retry decision lives in the view model ([RetryKind]): the
 * banner never invents retry behaviour for non-retryable errors, and
 * Retry is only offered when the kind is not NONE.
 */
@Composable
private fun PinnedErrorBanner(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    val retryLabel = stringResource(R.string.chat_retry)
    val closeDescription = stringResource(R.string.chat_status_dialog_close)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.md))
            .background(colors.danger.copy(alpha = 0.12f))
            .border(0.5.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(radius.md))
            .padding(start = spacing.md, top = spacing.xs, bottom = spacing.xs, end = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(
            glyph = Glyph.Warning,
            tint = colors.danger,
            size = 16.dp,
        )
        Text(
            text = message,
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.sm),
        )
        if (canRetry) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = dims.minTouchTarget)
                    .clip(RoundedCornerShape(radius.full))
                    .background(colors.accent)
                    .semantics {
                        role = SemanticsRole.Button
                        contentDescription = retryLabel
                    }
                    .clickable(role = SemanticsRole.Button, onClick = onRetry)
                    .padding(horizontal = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    GlyphIcon(
                        glyph = Glyph.Refresh,
                        tint = colors.onAccent,
                        size = 16.dp,
                    )
                    Text(
                        text = retryLabel,
                        style = OpenCodeTypography.label,
                        color = colors.onAccent,
                    )
                }
            }
            Spacer(Modifier.width(spacing.xs))
        }
        // Dismiss: a full 48dp hit area around the 12dp cross.
        Box(
            modifier = Modifier
                .size(dims.minTouchTarget)
                .clip(CircleShape)
                .clickable(role = SemanticsRole.Button, onClick = onDismiss)
                .semantics { contentDescription = closeDescription },
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

/**
 * Reconnecting indicator (audit 18): the existing [ChatUiState.connecting]
 * flag, rendered as a slim pinned row under the header while the engine
 * session is being (re)created. No new ViewModel state.
 */
@Composable
private fun ReconnectingIndicator(
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    // Gated behind composition (this only composes while connecting) and
    // on the one live period, like the header status dot.
    val infiniteTransition = rememberInfiniteTransition(label = "reconnecting_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = OpenCodeTheme.tokens.motion.pulsePeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "reconnecting_pulse_alpha",
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(
            Modifier
                .size(dims.statusDotSize)
                .graphicsLayer { alpha = pulseAlpha }
                .background(colors.accent, CircleShape),
        )
        Text(
            text = stringResource(R.string.chat_reconnecting),
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceMuted,
        )
    }
}

/**
 * One-line 56dp header (audit 8): menu, breadcrumb, model picker, status
 * dot, new session — a single fixed-height row. The old three-row stack
 * (title + session title + status chip, ~90-110dp) shifted the whole
 * transcript whenever the session title loaded; a fixed 56dp row with
 * single-line ellipsised text cannot change height at all.
 *
 * The branch no longer shows here — it lives in the status dialog — and
 * the status chip collapsed to a dot: the dialog is one tap away.
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
    /** Hidden at expanded widths, where the sidebar is persistent. */
    showMenuButton: Boolean = true,
    branch: String? = null,
    isScrolled: Boolean = false,
    sessionTitle: String? = null,
    workspacePath: String? = null,
    models: List<dev.bidzz.opencode.core.api.dto.ModelInfo> = emptyList(),
    activeModel: dev.bidzz.opencode.core.api.dto.ModelRef? = null,
    onModel: (dev.bidzz.opencode.core.api.dto.ModelRef) -> Unit = {},
    /** Context meter (Tier 2): tokens in the session's context, and the
     * active model's context-window limit. Both null while unknown — the
     * bar and the dialog row hide instead of guessing. */
    contextUsedTokens: Long? = null,
    contextLimit: Long? = null,
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val status = engineState.toStatus(busy)

    var showStatusDialog by remember { mutableStateOf(false) }

    // The status dot pulses on the one live period (pulsePeriodMs), and the
    // infinite transition only exists while WORKING: a ready/error/idle dot
    // must not keep an animation running behind a static dot.
    val dotAlpha = if (status == Status.WORKING) {
        val infiniteTransition = rememberInfiniteTransition(label = "status_dot_pulse")
        val pulsingAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = motion.pulsePeriodMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "status_dot_alpha",
        )
        pulsingAlpha
    } else {
        1f
    }

    val separatorAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(
            durationMillis = OpenCodeTheme.tokens.motion.quick,
            easing = FastOutSlowInEasing,
        ),
        label = "chat_header_separator_alpha",
    )

    val statusLabel = engineState.statusLabel(busy)
    val statusDescription = buildString {
        append(statusLabel)
        if (!branch.isNullOrBlank()) {
            append(", ")
            append(branch)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background),
    ) {
        // Content is capped at the 720dp content width and centred, like
        // the transcript below; the background still spans edge to edge.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = dims.contentMaxWidth)
                    .height(dims.topBarHeight)
                    .padding(horizontal = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showMenuButton) {
                    IconAction(
                        glyph = Glyph.Menu,
                        description = stringResource(R.string.chat_menu),
                        onClick = onMenu,
                    )
                }

                // Breadcrumb: "project / session", one line. The project
                // name takes what it needs and the session title gets the
                // rest; both ellipsise instead of wrapping, so the row
                // stays 56dp whatever loads when.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = OpenCodeTypography.title,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .semantics { heading() },
                    )
                    if (!sessionTitle.isNullOrBlank()) {
                        Text(
                            text = " / ",
                            style = OpenCodeTypography.label,
                            color = colors.onSurfaceFaint,
                            maxLines = 1,
                        )
                        Text(
                            text = sessionTitle,
                            style = OpenCodeTypography.label,
                            color = colors.onSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (models.isNotEmpty()) {
                    ModelSelector(
                        models = models,
                        activeModel = activeModel,
                        onSelect = onModel,
                        enabled = !busy,
                    )
                    Spacer(Modifier.width(spacing.xs))
                }

                // Status dot: a full 48dp tap target around the dot.
                // Opens the same status dialog the old chip opened.
                Box(
                    modifier = Modifier
                        .size(dims.minTouchTarget)
                        .clip(CircleShape)
                        .semantics {
                            role = SemanticsRole.Button
                            contentDescription = statusDescription
                        }
                        .clickable(
                            role = SemanticsRole.Button,
                            onClick = { showStatusDialog = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(dims.statusDotSize)
                            .graphicsLayer { alpha = dotAlpha }
                            .background(statusDotColor(colors, status), CircleShape),
                    )
                }

                IconAction(
                    glyph = Glyph.NewChat,
                    description = stringResource(R.string.chat_new_session),
                    onClick = onNewSession,
                )
            }
        }

        // Context meter (Tier 2): a 2dp bar along the header's bottom edge —
        // the fraction of the active model's context window the session
        // currently uses. Accent until 80%, warning to 95%, danger after;
        // over the limit it pins at full width rather than overflowing.
        // Unknown usage or limit: no bar, just the hairline below.
        if (contextUsedTokens != null && contextLimit != null && contextLimit > 0) {
            val fraction = (contextUsedTokens.toFloat() / contextLimit).coerceIn(0f, 1f)
            val barColor = when {
                fraction >= 0.95f -> colors.danger
                fraction >= 0.8f -> colors.warning
                else -> colors.accent
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(2.dp).background(colors.hairline),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(barColor),
                )
            }
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
            contextUsedTokens = contextUsedTokens,
            contextLimit = contextLimit,
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
    /** Context meter (Tier 2); both null while unknown, and the row hides. */
    contextUsedTokens: Long? = null,
    contextLimit: Long? = null,
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val radius = OpenCodeTheme.tokens.radius
    val status = engineState.toStatus(busy)
    val statusColor by animateColorAsState(
        targetValue = colors.statusColor(status),
        animationSpec = tween(durationMillis = motion.quick, easing = FastOutSlowInEasing),
        label = "dialog_status_color",
    )
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
                        .background(statusDotColor(colors, status), CircleShape),
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
                        color = statusTextColor(colors, status),
                    )
                    Text(
                        text = engineState.name,
                        style = OpenCodeTypography.caption,
                        color = statusTextColor(colors, status).copy(alpha = 0.8f),
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
                    // Context meter (Tier 2): used tokens over the model's
                    // window, in the footer's compact form. Without a known
                    // limit only the used figure shows — a percentage off a
                    // guessed denominator would be a lie.
                    if (contextUsedTokens != null) {
                        DialogDetailRow(
                            label = stringResource(R.string.chat_status_dialog_context),
                            value = if (contextLimit != null && contextLimit > 0) {
                                val percent = (contextUsedTokens * 100L / contextLimit).coerceIn(0L, 100L)
                                "${formatCompactTokens(contextUsedTokens)} / ${formatCompactTokens(contextLimit)} · $percent%"
                            } else {
                                formatCompactTokens(contextUsedTokens)
                            },
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
                    color = colors.accentText,
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
    // Stays true while the dialog is open (same pattern as the ToolRow
    // copy buttons): the glyph flips to a green check so the tap is
    // visible. The state dies with the dialog, so a reopened dialog
    // starts un-copied again.
    var copied by remember { mutableStateOf(false) }
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
                // 48dp hit area around the 12dp copy glyph.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = SemanticsRole.Button, onClick = { copied = true; onCopy() }),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        glyph = if (copied) Glyph.Check else Glyph.Copy,
                        tint = if (copied) colors.success else colors.onSurfaceMuted,
                        size = 12.dp,
                    )
                }
            }
        }
    }
}

/** The modal navigation drawer (phones): the shared content in a 320dp sheet. */
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
    val radius = OpenCodeTheme.tokens.radius

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
            ChatDrawerContent(
                projectTitle = projectTitle,
                currentSessionID = currentSessionID,
                sessions = sessions,
                onNewSession = onNewSession,
                onOpenSession = onOpenSession,
                onAllSessions = onAllSessions,
                onFiles = onFiles,
                onProject = onProject,
                onSettings = onSettings,
                onDiagnostics = onDiagnostics,
                onDeveloper = onDeveloper,
                onRenameSession = onRenameSession,
                onDeleteSession = onDeleteSession,
                onTogglePinSession = onTogglePinSession,
            )
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
 *
 * Shared by the modal drawer ([ChatDrawer], phones) and the persistent
 * sidebar ([ChatSidebar], expanded widths), so the two never drift apart.
 * One Column fills the available height in both hosts: the session list
 * takes the remaining space and the footer stays at the bottom.
 */
@Composable
private fun ChatDrawerContent(
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
    modifier: Modifier = Modifier,
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
    // One Column owns the whole height in both hosts (item 19): the
    // session list takes the remaining space (weight(1f)) and the footer
    // sits at the bottom. The header used to be its own Column with the
    // divider and footer as siblings, which only stacked correctly
    // inside ModalDrawerSheet; in the sidebar's Box the footer drew on
    // top of the workspace switcher and New Chat.
    Column(
        modifier = modifier
            .fillMaxSize()
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
                            .animateItem()
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
                            color = if (group == SessionDateGroup.PINNED) colors.accentText else colors.onSurfaceFaint,
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
                        modifier = Modifier.animateItem(),
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

        // 48dp minimum height: the 30dp avatar + xs padding used
        // to make this row ~40dp tall, shorter than the footer
        // buttons above it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(top = spacing.xs)
                .clip(RoundedCornerShape(radius.md))
                .clickable(role = SemanticsRole.Button, onClick = onDeveloper)
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(radius.sm)),
            ) {
                // The logomark, 30dp: the drawer's identity mark since the
                // name was removed (user decision 2026-11-11). It replaces
                // the "R" initial circle.
                Image(
                    painter = painterResource(R.drawable.ic_logomark),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = "@hanzvibes",
                style = OpenCodeTypography.caption,
                // onSurfaceMuted (#5F5F66) clears AA at 11sp in
                // the light theme; onSurfaceFaint did not.
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = spacing.sm),
            )
            GlyphIcon(
                glyph = Glyph.ChevronRight,
                tint = colors.onSurfaceMuted,
                size = 14.dp,
            )
        }
    }
    }
}

/**
 * Persistent navigation sidebar for expanded widths: the same drawer
 * content in a fixed 320dp column with a hairline end divider, instead
 * of the modal sheet. The conversation sits beside it (see ChatScreen),
 * so selecting a session opens it in the detail pane without a screen
 * switch. The footer already pads for the navigation bar; the status bar
 * is padded here because there is no sheet to do it.
 */
@Composable
private fun ChatSidebar(
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
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions

    Box(
        modifier = modifier
            .width(PersistentSidebarWidth)
            .fillMaxHeight()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        ChatDrawerContent(
            projectTitle = projectTitle,
            currentSessionID = currentSessionID,
            sessions = sessions,
            onNewSession = onNewSession,
            onOpenSession = onOpenSession,
            onAllSessions = onAllSessions,
            onFiles = onFiles,
            onProject = onProject,
            onSettings = onSettings,
            onDiagnostics = onDiagnostics,
            onDeveloper = onDeveloper,
            onRenameSession = onRenameSession,
            onDeleteSession = onDeleteSession,
            onTogglePinSession = onTogglePinSession,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(dims.hairline)
                .background(colors.hairline),
        )
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
            // Opaque surfaceRaised: the 0.6-alpha surfaceVariant washed
            // out against the drawer background in the light theme.
            .background(colors.surfaceRaised)
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
        active || accent -> colors.accentText
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
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions

    var menuExpanded by remember { mutableStateOf(false) }

    val age = row.age?.label()
    val tokenLabel = row.tokenTotal?.let { stringResource(R.string.turn_footer_tokens, formatCompactTokens(it)) }
    val meta = listOfNotNull(age, row.cost, tokenLabel).joinToString(" · ")

    // The selected background animates instead of snapping when the
    // selection moves between rows.
    val rowBg by animateColorAsState(
        targetValue = if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = motion.quick, easing = FastOutSlowInEasing),
        label = "session_row_bg",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm, vertical = 1.dp)
            .clip(RoundedCornerShape(radius.md))
            .background(rowBg)
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
                    color = if (selected) colors.accentText else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = OpenCodeTypography.label,
                    color = if (selected) colors.accentText.copy(alpha = 0.75f) else colors.onSurfaceFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Box {
            // Full 48dp hit area: the session "…" used to be a 28dp
            // target, the smallest in the drawer.
            // Hoisted: stringResource() is @Composable and cannot be
            // called inside a .semantics {} block.
            val actionsDescription = stringResource(R.string.session_actions)
            Box(
                modifier = Modifier
                    .size(dims.minTouchTarget)
                    .clip(RoundedCornerShape(radius.sm))
                    .clickable(role = SemanticsRole.Button) { menuExpanded = true }
                    .semantics { contentDescription = actionsDescription },
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

/**
 * Scrolls so the BOTTOM edge of the item at [index] sits at the viewport
 * bottom — the newest content — instead of [LazyListState.scrollToItem]'s
 * default of pinning the item's TOP to the viewport top.
 *
 * Why this exists: the follow effect used to call scrollToItem(last) on
 * every streamed token. For a reply taller than the viewport that shows
 * the START of the reply and hides every new token below the fold — and
 * once the reader scrolled down, nearBottom flipped true and the next
 * token snapped the list back to the top of the message. That snap is
 * the jump-to-top / stuck-scroll bug: scroll down, snap to top, scroll
 * down, snap to top, forever. Pinning the bottom keeps the newest token
 * visible and keeps nearBottom true, so following is a steady pin
 * instead of a yank.
 *
 * The fast path (item already laid out, the streaming case) measures the
 * item from layoutInfo and scrolls once with the exact offset — no
 * intermediate top-pin frame, so no flicker. The slow path (item not
 * laid out yet, e.g. opening a session) snaps it into view first,
 * waits for a layout pass, then corrects the offset; up to three frames
 * are tried because the first frame after a scroll may not have laid
 * the item out yet.
 */
private suspend fun LazyListState.scrollToItemBottom(index: Int) {
    val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (visible != null) {
        scrollToItem(index, bottomOffsetFor(visible))
        return
    }
    scrollToItem(index)
    repeat(3) {
        withFrameNanos { }
        val laidOut = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@repeat
        val offset = bottomOffsetFor(laidOut)
        if (offset > 0) scrollToItem(index, offset)
        return
    }
}

/** Animated twin of [scrollToItemBottom]: same bottom pin, smooth. */
private suspend fun LazyListState.animateScrollToItemBottom(index: Int) {
    val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (visible != null) {
        animateScrollToItem(index, bottomOffsetFor(visible))
        return
    }
    animateScrollToItem(index)
    repeat(3) {
        withFrameNanos { }
        val laidOut = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@repeat
        val offset = bottomOffsetFor(laidOut)
        if (offset > 0) animateScrollToItem(index, offset)
        return
    }
}

/**
 * The scrollOffset that pins [item]'s bottom edge to the viewport
 * bottom: how far the item must be scrolled past the viewport start.
 * Zero when the item fits — plain scrollToItem already lands at the
 * true bottom then, because the list clamps to its max scroll.
 * Uses the same coordinate space as nearBottom (offsets against
 * viewportStart/EndOffset, which already exclude content padding).
 */
private fun LazyListState.bottomOffsetFor(item: LazyListItemInfo): Int {
    val info = layoutInfo
    val viewportHeight = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(0)
    return (item.size - viewportHeight).coerceAtLeast(0)
}

/** A floating button with entry/exit animation and unread/busy badge that returns to the newest message. */
@Composable
private fun JumpToLatest(
    unreadCount: Int,
    pendingQuestions: Int = 0,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions
    val spacing = OpenCodeTheme.tokens.spacing
    // The unread count is part of what TalkBack announces, not just a
    // visual badge: "Jump to latest message, 3 unread".
    val buttonDescription = if (unreadCount > 0) {
        stringResource(R.string.chat_jump_latest_unread, unreadCount)
    } else {
        stringResource(R.string.chat_jump_latest)
    }
    val waitingLabel = if (pendingQuestions == 1) {
        stringResource(R.string.chat_question_waiting_one)
    } else {
        stringResource(R.string.chat_question_waiting_other, pendingQuestions)
    }
    val interactionSource = remember { MutableInteractionSource() }

    // A pending-question pill sits above the circle, right-aligned: it is
    // a label, not a second button, so it shares the jump click.
    Column(
        modifier = modifier
            .semantics {
                this.contentDescription = buttonDescription
                this.role = SemanticsRole.Button
            },
        horizontalAlignment = Alignment.End,
    ) {
        if (pendingQuestions > 0) {
            Box(
                modifier = Modifier
                    .padding(bottom = spacing.xs)
                    .height(24.dp)
                    .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.full))
                    .background(colors.accent)
                    .padding(horizontal = spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = waitingLabel,
                    style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.onAccent,
                )
            }
        }
        Box {
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
            // Gated behind isBusy (this branch only composes then) and on
            // the one live period, like the header status dot.
            val infiniteTransition = rememberInfiniteTransition(label = "jump_busy_pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = OpenCodeTheme.tokens.motion.pulsePeriodMs, easing = LinearEasing),
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
    activeModel: dev.bidzz.opencode.core.api.dto.ModelRef? = null,
    models: List<dev.bidzz.opencode.core.api.dto.ModelInfo> = emptyList(),
    onStatusClick: (() -> Unit)? = null,
    onProjectClick: (() -> Unit)? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val radius = OpenCodeTheme.tokens.radius
    val status = engineState.toStatus(busy)

    // Dismissed starters are remembered by prompt resource id in
    // SharedPreferences, so a dismissed card stays gone across restarts.
    // There is no "recent prompts" list and no reset: the four starters
    // are fixed suggestions, and dismissing one is a per-item choice.
    val context = LocalContext.current
    val starterPrefs = remember(context) {
        context.getSharedPreferences("opencode_chat", Context.MODE_PRIVATE)
    }
    var dismissedStarterIds by remember {
        mutableStateOf(
            starterPrefs.getStringSet(DISMISSED_STARTERS_KEY, emptySet())?.toSet().orEmpty(),
        )
    }
    // No git repo → no "Review changes" starter: reviewing diffs in a
    // workspace without version control is a dead suggestion (audit 1).
    val hasGitRepo = remember(workspacePath) { detectGitBranch(workspacePath) != null }
    val visibleStarters = remember(dismissedStarterIds, hasGitRepo) {
        STARTER_PROMPTS.filter { item ->
            (hasGitRepo || item.promptRes != R.string.starter_review_prompt) &&
                item.promptRes.toString() !in dismissedStarterIds
        }
    }

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
                // Hero branding with the logomark and minimalist typography
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

                // Provider / free-tier chip (audit 1): which account the
                // agent runs on, before the first prompt is sent.
                val providerChipText = when {
                    activeModel != null -> {
                        val model = models.firstOrNull { it.ref() == activeModel }
                        val modelName = model?.let {
                            cleanModelName(it.name.orEmpty(), it.modelID ?: it.id)
                        } ?: cleanModelName("", activeModel.id)
                        val provider = (model?.providerID ?: activeModel.providerID)
                            .replaceFirstChar { it.uppercase() }
                        stringResource(R.string.empty_state_provider_model, provider, modelName)
                    }
                    engineReady && models.isEmpty() -> {
                        // No catalogued models: nothing to pick from, so
                        // the agent runs on opencode's free tier (see the
                        // providers screen). Real provider state belongs
                        // in ChatUiState; until then this is the signal.
                        stringResource(R.string.providers_status_free_tier)
                    }
                    else -> null
                }
                if (providerChipText != null) {
                    Spacer(Modifier.height(spacing.md))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius.full))
                            .background(colors.fill)
                            .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.full))
                            .padding(horizontal = spacing.md, vertical = spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = providerChipText,
                            style = OpenCodeTypography.label,
                            color = colors.onSurfaceMuted,
                        )
                    }
                }

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
                } else if (visibleStarters.isNotEmpty()) {
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
                        visibleStarters.chunked(if (isNarrow) 1 else 2).forEach { rowPrompts ->
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
                                        onDismiss = {
                                            val next = dismissedStarterIds + item.promptRes.toString()
                                            dismissedStarterIds = next
                                            starterPrefs.edit()
                                                .putStringSet(DISMISSED_STARTERS_KEY, next)
                                                .apply()
                                        },
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

/** Hero branding with the logomark and minimalist Apple typography. */
@Composable
private fun HeroBranding(
    engineReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The logomark, 40dp. No radial accent glow behind it: the quiet
        // terminal has no halos, and the mark carries its own dark tile.
        Image(
            painter = painterResource(R.drawable.ic_logomark),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(radius.lg)),
        )

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
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    val shape = RoundedCornerShape(radius.full)

    val status = engineState.toStatus(busy)
    val statusLabel = engineState.statusLabel(busy)
    val statusColor by animateColorAsState(
        targetValue = colors.statusColor(status),
        animationSpec = tween(durationMillis = motion.quick, easing = FastOutSlowInEasing),
        label = "readiness_status_color",
    )
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
                .background(statusDotColor(colors, status), CircleShape),
        )
        Text(
            text = statusLabel,
            style = OpenCodeTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = statusTextColor(colors, status),
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
    /** Null when the card cannot be dismissed; otherwise a small × at its top end. */
    onDismiss: (() -> Unit)? = null,
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

    // Hoisted out of the .semantics {} block below: stringResource() is
    // @Composable and cannot be called inside a SemanticsPropertyReceiver.
    val dismissDescription = stringResource(R.string.chat_status_dialog_close)
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
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
        // The × is the innermost clickable at its touch point, so tapping
        // it dismisses the card instead of filling the composer. Hit area
        // is 48dp; the 28dp circle stays the visual, centred inside it.
        if (onDismiss != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = SemanticsRole.Button, onClick = onDismiss)
                    .semantics { contentDescription = dismissDescription },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        glyph = Glyph.Close,
                        tint = colors.onSurfaceFaint,
                        size = 12.dp,
                    )
                }
            }
        }
    }
}

private data class ActionablePrompt(
    val titleRes: Int,
    val descRes: Int,
    val promptRes: Int,
    val glyph: Glyph,
)

/** SharedPreferences key for the set of dismissed starter prompt ids. */
private const val DISMISSED_STARTERS_KEY = "dismissed_starters"

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
    val motion = OpenCodeTheme.tokens.motion
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val radius = OpenCodeTheme.tokens.radius
    val shape = RoundedCornerShape(radius.full)
    val statusColor by animateColorAsState(
        targetValue = colors.statusColor(status),
        animationSpec = tween(durationMillis = motion.quick, easing = FastOutSlowInEasing),
        label = "pill_status_color",
    )

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
                .background(statusDotColor(colors, status), CircleShape),
        )
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = statusTextColor(colors, status),
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
