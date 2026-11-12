package dev.bidzz.opencode.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.HairlineSeparator
import dev.bidzz.opencode.ui.whiteCard

/**
 * Message input styled as an AI IDE omnibar (like Cursor, Claude, Antigravity).
 *
 * Features:
 * - Floating white card (16dp, `Modifier.whiteCard` since Redesign C)
 *   with a hairline border and an accent focus ring — no glow.
 * - Multi-line auto-expand behavior with comfortable line-height.
 * - Quick action shortcuts: `/` command prefix, `@` mention and file-attach chips, clear icon.
 * - Modern action button (Send / Stop / Voice / Abort) with dynamic colors and haptic-like press feel.
 * - Working progress row while the agent runs: the live step + run clock.
 */
@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onVoice: (() -> Unit)? = null,
    /** Opens the workspace mention picker: pick a file to insert as `@path`. */
    onAttachFile: (() -> Unit)? = null,
    /** Opens the system file picker for a file outside the workspace (SAF). */
    onAttachDeviceFile: (() -> Unit)? = null,
    /** Takes a photo with the system camera (Native C). */
    onTakePhoto: (() -> Unit)? = null,
    /** Files picked from outside the workspace, waiting to be inlined on send. */
    attachments: List<AttachedFile> = emptyList(),
    /** Removes one pending attachment by id. */
    onRemoveAttachment: (String) -> Unit = {},
    onKeyEvent: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
    controls: @Composable () -> Unit = {},
    /** Prompts sent in this session, oldest first, for history navigation (C-F).
     * In-memory only, owned by the view model; empty means no history yet. */
    promptHistory: List<String> = emptyList(),
    /** Follow-up queued while the agent works; sent when the run ends. */
    queuedText: String? = null,
    /** Composer C suggestion chips (resource ids from [composerSuggestions]):
     * shown above the text field while the draft is blank; a tap fills the
     * draft, never sends. Empty means no suggestions for this moment. */
    suggestions: List<Int> = emptyList(),
    /** Writes a tapped suggestion chip into the draft. */
    onSuggestionPick: (String) -> Unit = {},
    /** Composer C voice input: the recognizer state owned by the screen. */
    voiceState: VoiceInputState = VoiceInputState.IDLE,
    /** False when the device has no speech recognition service: the mic
     * renders as unavailable instead of pretending to work. */
    voiceAvailable: Boolean = true,
    /** Elapsed recording time in seconds, while listening. */
    voiceElapsedSeconds: Int = 0,
    /** Interim (partial) transcript while listening; display-only. */
    voiceInterimText: String? = null,
    /** Stops listening; the final result (or error) follows. */
    onVoiceStop: () -> Unit = {},
    /** The unavailable mic was tapped: the screen shows why. */
    onVoiceUnavailable: () -> Unit = {},
    /** True after Stop was tapped until the run actually ends ("Stopping…"). */
    stopping: Boolean = false,
    /** Sentence for the tool running right now ("Reading ChatScreen.kt"),
     * from the streaming turn's live tools; null while the agent thinks
     * before the first tool, so the progress row falls back to the
     * generic busy label. Owned by the screen — the composer never reads
     * the engine. */
    busyStepText: String? = null,
    /** Seconds since the run started, owned by the screen (the same timer
     * the turn header reads). Only shown while busy, beside the step. */
    busyElapsedSeconds: Int = 0,
    /** True while a user message is being edited; shows the editing header. */
    isEditing: Boolean = false,
    onCancelEdit: () -> Unit = {},
    /** Human labels for the placeholder: model first, agent as fallback. */
    modelLabel: String? = null,
    agentLabel: String? = null,
    /** Focus the field on first composition (entering chat). */
    autofocus: Boolean = false,
    /** A timestamp bumped after every send; each bump refocuses the field. */
    focusRequestSignal: Long = 0,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    val motion = OpenCodeTheme.tokens.motion

    var isFocused by remember { mutableStateOf(false) }

    // Autofocus on entering chat, and refocus after every send (the signal
    // is a timestamp so repeated sends re-trigger the effect).
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (autofocus) focusRequester.requestFocus()
    }
    LaunchedEffect(focusRequestSignal) {
        if (focusRequestSignal > 0) focusRequester.requestFocus()
    }

    // Prompt-history position (C-F): -1 means not navigating. Typing
    // anything by hand abandons navigation; sending clears the draft,
    // which resets the position through the effect below.
    var historyIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(value) {
        if (value.isEmpty()) historyIndex = -1
    }
    val onTextChange: (String) -> Unit = { text ->
        onValueChange(text)
        historyIndex = -1
    }

    // A message that is only attachments still sends: picking a file and
    // hitting Send with no typed text means "show the agent this file".
    val canSend = enabled && (value.isNotBlank() || attachments.isNotEmpty()) && !busy
    // Redesign C: the composer floats as a white card — `Modifier.whiteCard`
    // (16dp radius, 1dp card border, card shadow in light; flat hairline
    // in dark), the Muse-like capsule inside the design system's own
    // 16dp surface cap. The old 12dp omnibar shape is gone; a full pill
    // would break plan.md §12, and 16dp plus the shadow is the floating
    // treatment that rule allows.
    val shape = RoundedCornerShape(radius.lg)

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accent.copy(alpha = 0.70f) else colors.hairline,
        animationSpec = motion.tweenQuick(),
        label = "composer_border_color",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 1.dp else 0.5.dp,
        animationSpec = motion.tweenQuick(),
        label = "composer_border_width",
    )
    // Redesign C: the omnibar floats as a white card — `Modifier.whiteCard`
    // (16dp radius, 1dp card border, card shadow in light; flat hairline
    // in dark). The animated focus border draws on top of it with the
    // same shape. The background crossfades between the focused gradient
    // and the solid unfocused surface; multi-line growth eases on
    // motion.springSmooth().
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm)
            .animateContentSize(motion.springSmooth())
            .whiteCard()
            .border(borderWidth, borderColor, shape),
    ) {
        Crossfade(
            targetState = isFocused,
            animationSpec = motion.tweenQuick(),
            label = "composer_background",
            modifier = Modifier.matchParentSize(),
        ) { focused ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (focused) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    colors.surfaceRaised,
                                    colors.surface,
                                ),
                            )
                        } else {
                            SolidColor(colors.surface)
                        },
                        shape,
                    ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Opsi A: symmetric vertical padding, matching the top —
                // the omnibar used to sit 4dp tighter at the bottom.
                .padding(top = spacing.md, bottom = spacing.md),
        ) {
            // Editing header (Phase 2, finding 3): the composer is editing a
            // sent message, not a new draft — Submit sends the edit, Cancel
            // restores the draft as it was. The header is the only visible
            // difference; the field, send and history all behave as usual.
            if (isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = spacing.lg, end = spacing.sm, bottom = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.composer_editing),
                        style = OpenCodeTypography.label,
                        color = colors.accent,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = dims.minTouchTarget)
                            .clip(RoundedCornerShape(radius.full))
                            .clickable(role = Role.Button, onClick = onCancelEdit)
                            .padding(horizontal = spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.session_action_cancel),
                            style = OpenCodeTypography.label,
                            color = colors.onSurfaceMuted,
                        )
                    }
                }
            }

            // Queued follow-up (Phase 2, finding 10): typed while the agent
            // works, sent when the run ends. Shown so the queue is never a
            // silent black box — the text is the user's, verbatim.
            if (queuedText != null) {
                Text(
                    text = stringResource(R.string.composer_queued, queuedText),
                    style = OpenCodeTypography.caption,
                    color = colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.xs),
                )
            }

            // Pending attachments (SAF, outside the workspace): one
            // removable chip per file, above the text field. The chip is
            // the pending send payload made visible — dismissing it removes
            // the file from the next send.
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.xs)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    attachments.forEach { attachment ->
                        AttachmentChip(
                            file = attachment,
                            onRemove = { onRemoveAttachment(attachment.id) },
                        )
                    }
                }
            }

            // Composer C suggestion chips: contextual prompts above the
            // text field. Shown only on a blank draft (a tap fills it,
            // which hides the row) and only while idle — never while the
            // agent works, never while editing, and never together with
            // the follow-up chips, which the caller hides these for.
            if (suggestions.isNotEmpty() && value.isBlank() && !busy) {
                ComposerSuggestionChips(
                    suggestions = suggestions,
                    onPick = onSuggestionPick,
                )
            }

            // Composer C recording bar: live while the recognizer listens —
            // elapsed time, interim transcript, and a stop button. The final
            // transcript lands in the draft when listening ends.
            if (voiceState == VoiceInputState.LISTENING) {
                VoiceRecordingBar(
                    elapsedSeconds = voiceElapsedSeconds,
                    interimText = voiceInterimText,
                    onStop = onVoiceStop,
                )
            }

            // Redesign C working progress: while the agent runs, the
            // composer says what it is doing right now — the live step
            // sentence from the streaming turn, plus the run clock — so
            // the wait reads as progress, not a stall. The step comes
            // from the screen (busyStepText); without a running tool yet
            // it falls back to the generic busy label. The Stop button
            // below is the control; this row is display-only.
            if (busy) {
                WorkingProgressRow(
                    stepText = busyStepText ?: stringResource(R.string.composer_busy),
                    elapsedSeconds = busyElapsedSeconds,
                )
            }

            // Multi-line text field with comfortable line height
            BasicTextField(
                value = value,
                onValueChange = onTextChange,
                textStyle = OpenCodeTypography.input.copy(
                    color = if (enabled) colors.onSurface else colors.onSurfaceMuted,
                    lineHeight = 22.sp,
                ),
                cursorBrush = SolidColor(colors.accent),
                enabled = enabled,
                // The soft keyboard's action key reads "Send" and sends; a plain
                // Enter on a physical keyboard still inserts a newline (sending
                // from hardware keys is Ctrl/Cmd+Enter, handled in ChatScreen).
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg)
                    .heightIn(min = 28.dp, max = dims.composerMaxHeight)
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .then(if (onKeyEvent != null) Modifier.onPreviewKeyEvent(onKeyEvent) else Modifier),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        if (value.isEmpty()) {
                            // Crossfade between the not-ready / busy / hint
                            // states so the placeholder swaps instead of popping.
                            // The idle hint names the model (or the agent when
                            // no model is known) so the composer says who will
                            // answer, not just "ask anything".
                            val placeholderText = when {
                                !enabled -> stringResource(R.string.composer_not_ready)
                                busy -> stringResource(R.string.composer_busy)
                                modelLabel != null -> stringResource(R.string.composer_hint_named, modelLabel)
                                agentLabel != null -> stringResource(R.string.composer_hint_named, agentLabel)
                                else -> stringResource(R.string.composer_hint)
                            }
                            Crossfade(
                                targetState = placeholderText,
                                animationSpec = motion.tweenQuick(),
                                label = "composer_placeholder",
                            ) { text ->
                                Text(
                                    text = text,
                                    style = OpenCodeTypography.input.copy(lineHeight = 22.sp),
                                    color = colors.onSurfaceMuted,
                                )
                            }
                        }
                        inner()
                    }
                },
            )

            Spacer(modifier = Modifier.height(spacing.xs))
            HairlineSeparator(modifier = Modifier.padding(horizontal = spacing.lg))
            Spacer(modifier = Modifier.height(spacing.xs))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = spacing.md, end = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Omnibar shortcuts: the / and @ pills are pinned outside the
                // scrolling row, so the agent/model selectors can scroll on a
                // narrow phone without ever pushing the primary command trigger
                // off-screen. Only the mode/model controls scroll.
                QuickActionChip(
                    label = "/",
                    glyph = null,
                    isActive = value.startsWith("/"),
                    enabled = enabled && !busy,
                    contentDescription = stringResource(R.string.composer_commands),
                    onClick = {
                        if (value.startsWith("/")) {
                            onValueChange(value.removePrefix("/").trimStart())
                        } else {
                            onValueChange("/$value")
                        }
                    },
                )

                // Two separate chips, one job each — the old single chip
                // wore a file icon AND an "@" label while doing both
                // attach and mention, so it read as two buttons in one.
                // "@" opens the workspace mention picker (a picked file is
                // inserted into the draft as `@relative/path` text); the
                // file chip opens the system picker (SAF) for a file
                // outside the workspace, which arrives as a pending
                // attachment instead. Each chip only exists when its real
                // action is wired in, and each stays hidden otherwise.
                if (onAttachFile != null) {
                    QuickActionChip(
                        label = "@",
                        glyph = null,
                        isActive = false,
                        enabled = enabled && !busy,
                        contentDescription = stringResource(R.string.composer_mention),
                        onClick = { onAttachFile() },
                    )
                }

                if (onAttachDeviceFile != null) {
                    QuickActionChip(
                        label = "",
                        glyph = Glyph.File,
                        isActive = false,
                        enabled = enabled && !busy,
                        contentDescription = stringResource(R.string.composer_attach),
                        onClick = { onAttachDeviceFile() },
                    )
                }

                if (onTakePhoto != null) {
                    QuickActionChip(
                        label = "",
                        glyph = Glyph.Camera,
                        isActive = false,
                        enabled = enabled && !busy,
                        contentDescription = stringResource(R.string.composer_take_photo),
                        onClick = { onTakePhoto() },
                    )
                }

                Spacer(modifier = Modifier.width(spacing.xs))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    controls()
                }

                Spacer(modifier = Modifier.width(spacing.xs))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    // Prompt history (C-F): Up/Down step through prompts sent
                    // in this session. Up only starts from an empty draft, so
                    // it never overwrites text being typed; Down past the
                    // newest prompt restores an empty draft.
                    if (promptHistory.isNotEmpty() && enabled) {
                        HistoryNavButton(
                            glyph = Glyph.ArrowUp,
                            contentDescription = stringResource(R.string.prompt_history_previous),
                            enabled = historyIndex != 0 && (historyIndex >= 0 || value.isEmpty()),
                            onClick = {
                                if (historyIndex < 0) {
                                    if (value.isNotBlank()) return@HistoryNavButton
                                    historyIndex = promptHistory.lastIndex
                                } else if (historyIndex > 0) {
                                    historyIndex--
                                } else {
                                    return@HistoryNavButton
                                }
                                onValueChange(promptHistory[historyIndex])
                            },
                        )
                        HistoryNavButton(
                            glyph = Glyph.ArrowDown,
                            contentDescription = stringResource(R.string.prompt_history_next),
                            enabled = historyIndex >= 0,
                            onClick = {
                                if (historyIndex < promptHistory.lastIndex) {
                                    historyIndex++
                                    onValueChange(promptHistory[historyIndex])
                                } else {
                                    historyIndex = -1
                                    onValueChange("")
                                }
                            },
                        )
                    }

                    // Clear stays available while the agent works: the draft
                    // is the user's text, and hiding the only way to drop it
                    // mid-run was a trap, not a guard.
                    AnimatedVisibility(
                        visible = value.isNotEmpty() && enabled,
                        enter = fadeIn(motion.tweenQuick()) + scaleIn(motion.tweenQuick(), initialScale = 0.8f),
                        exit = fadeOut(motion.tweenQuick()) + scaleOut(motion.tweenQuick(), targetScale = 0.8f),
                    ) {
                        ClearButton(
                            onClick = { onValueChange("") },
                        )
                    }

                    ActionButton(
                        busy = busy,
                        stopping = stopping,
                        canSend = canSend,
                        // No voice affordance while attachments wait: an
                        // empty draft with files pending means "send the
                        // files", and the mic would swallow that send.
                        // Composer C: the mic is only a live affordance
                        // while the recognizer exists and is not already
                        // listening; an unavailable mic still shows (grey,
                        // disabled-looking) so its tap can say why.
                        hasVoice = onVoice != null && voiceAvailable && value.isBlank() && attachments.isEmpty() && !busy &&
                            voiceState != VoiceInputState.LISTENING,
                        voiceListening = voiceState == VoiceInputState.LISTENING,
                        voiceUnavailable = onVoice != null && !voiceAvailable && value.isBlank() && attachments.isEmpty() && !busy,
                        onClick = {
                            when {
                                busy -> onStop()
                                voiceState == VoiceInputState.LISTENING -> onVoiceStop()
                                canSend -> onSend()
                                voiceState == VoiceInputState.UNAVAILABLE -> onVoiceUnavailable()
                                onVoice != null && value.isBlank() -> onVoice()
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * One pending attachment (SAF, outside the workspace): a file pill with
 * its name and a dismiss cross. Same 28dp pill / hairline / mono-name
 * language as [QuickActionChip], so the row reads as composer chrome, not
 * a new component. Only the cross removes the file — tapping the name
 * does nothing, so a stray tap cannot silently drop the pending payload.
 * The row is 40dp tall like the other composer controls; the cross itself
 * is a 28dp target inside the pill, matching the clear button's visual.
 */
@Composable
private fun AttachmentChip(
    file: AttachedFile,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val haptic = LocalHapticFeedback.current
    val removeDescription = stringResource(R.string.attach_chip_remove, file.name)

    Box(
        modifier = modifier.height(dims.composerChipTouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(radius.full))
                .background(colors.fill)
                .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.full))
                .padding(start = spacing.sm, end = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            GlyphIcon(
                glyph = Glyph.File,
                tint = colors.onSurfaceMuted,
                size = 13.dp,
            )
            Text(
                text = file.name,
                style = OpenCodeTypography.caption.copy(fontFamily = OpenCodeTypography.mono),
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            // Composer C: the size of the text that will be inlined, so a
            // 64 KB file does not look the same as a 12-byte note.
            Text(
                text = formatAttachmentSize(attachmentByteSize(file)),
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceFaint,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRemove()
                        },
                    )
                    .semantics { contentDescription = removeDescription },
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

/**
 * Composer C recording bar: shown above the text field while the
 * recognizer listens. A danger dot, the elapsed time, the interim
 * transcript (display-only — it only enters the draft as a final result),
 * and a stop button. Stopping delivers the final transcript (or an error);
 * the bar itself never invents text.
 */
@Composable
private fun VoiceRecordingBar(
    elapsedSeconds: Int,
    interimText: String?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val haptic = LocalHapticFeedback.current
    val stopDescription = stringResource(R.string.voice_stop_recording)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = spacing.lg, end = spacing.sm, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(colors.danger, CircleShape),
        )
        Text(
            text = stringResource(R.string.voice_recording, formatVoiceElapsed(elapsedSeconds)),
            style = OpenCodeTypography.label,
            color = colors.danger,
            maxLines = 1,
        )
        if (!interimText.isNullOrBlank()) {
            Text(
                text = interimText,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        Box(
            modifier = Modifier
                .size(dims.composerChipTouchTarget)
                .clip(CircleShape)
                .clickable(
                    role = Role.Button,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onStop()
                    },
                )
                .semantics { contentDescription = stopDescription },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = Glyph.Stop,
                tint = colors.danger,
                size = 14.dp,
            )
        }
    }
}

/**
 * Redesign C working progress: a live accent pulse plus the current step
 * sentence and the run clock ("Reading ChatScreen.kt · 12s"), above the
 * text field while the agent works. Display-only — stopping is the
 * ActionButton's job, never this row's.
 */
@Composable
private fun WorkingProgressRow(
    stepText: String,
    elapsedSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        LivePulseIndicator(
            color = colors.accent,
            size = 8.dp,
        )
        Text(
            text = stringResource(R.string.composer_working_progress, stepText, elapsedSeconds),
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Omnibar quick action pill button (`/`, `@`).
 */
@Composable
private fun QuickActionChip(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    isActive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    val motion = OpenCodeTheme.tokens.motion
    val haptic = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = motion.springBouncy(),
        label = "chip_press_scale",
    )

    val chipShape = RoundedCornerShape(radius.full)

    val targetBg = when {
        isActive -> colors.accent.copy(alpha = 0.14f)
        else -> colors.fill
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = motion.tweenQuick(),
        label = "chip_bg",
    )

    val targetBorder = when {
        isActive -> colors.accent.copy(alpha = 0.60f)
        else -> colors.hairline
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = motion.tweenQuick(),
        label = "chip_border",
    )

    val contentColor = when {
        !enabled -> colors.onSurfaceFaint
        isActive -> colors.accent
        else -> colors.onSurfaceMuted
    }

    // Hit area is 40dp; the pill itself stays 28dp, centred inside it, so
    // the chip is easier to tap without the composer controls growing taller.
    Box(
        modifier = modifier
            .height(dims.composerChipTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(28.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(chipShape)
                .background(backgroundColor)
                .border(0.5.dp, borderColor, chipShape)
                .padding(horizontal = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            if (glyph != null) {
                GlyphIcon(
                    glyph = glyph,
                    tint = contentColor,
                    size = 13.dp,
                )
            }
            // A glyph-only chip (the file-attach chip) renders no label
            // at all: an empty Text would still take the row's inter-item
            // spacing and leave the glyph off-centre.
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = OpenCodeTypography.caption.copy(fontFamily = OpenCodeTypography.mono),
                    color = contentColor,
                )
            }
        }
    }
}

/**
 * Small Up/Down button for prompt-history navigation (C-F): a 40dp touch
 * target with a 14dp arrow, dimmed when there is nowhere to go.
 */
@Composable
private fun HistoryNavButton(
    glyph: Glyph,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    Box(
        modifier = modifier
            .size(40.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = glyph,
            tint = if (enabled) colors.onSurfaceMuted else colors.onSurfaceFaint,
            size = 14.dp,
        )
    }
}

/**
 * Clear button (dismiss cross) shown when text is present.
 */
@Composable
private fun ClearButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions
    val motion = OpenCodeTheme.tokens.motion
    val haptic = LocalHapticFeedback.current
    val label = stringResource(R.string.composer_clear)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = motion.springBouncy(),
        label = "clear_button_scale",
    )

    // Hit area is 40dp; the 28dp circle stays the visual, centred inside it.
    Box(
        modifier = modifier
            .size(dims.composerChipTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(colors.fill),
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
 * Modern Action Button (Send / Stop / Voice / Abort) with dynamic colors,
 * breathing pulse when busy, and spring tactile press feel.
 */
@Composable
private fun ActionButton(
    busy: Boolean,
    canSend: Boolean,
    hasVoice: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** True after Stop was tapped until the run ends: reads "Stopping…". */
    stopping: Boolean = false,
    /** True while the recognizer listens: the button becomes Stop. */
    voiceListening: Boolean = false,
    /** True when the device has no recognition service: the mic shows grey
     * and disabled-looking, and a tap only explains why. */
    voiceUnavailable: Boolean = false,
) {
    val colors = OpenCodeTheme.colors
    val dims = OpenCodeTheme.tokens.dimensions
    val motion = OpenCodeTheme.tokens.motion
    val elevation = OpenCodeTheme.tokens.elevation
    val haptic = LocalHapticFeedback.current

    val active = busy || canSend || hasVoice || voiceListening || voiceUnavailable
    val label = stringResource(
        when {
            stopping -> R.string.composer_stopping
            busy -> R.string.composer_stop
            voiceListening -> R.string.voice_stop_recording
            canSend -> R.string.composer_send
            hasVoice -> R.string.composer_voice
            voiceUnavailable -> R.string.voice_unavailable
            else -> R.string.composer_send
        },
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile press feel: snappy spring bounce compression
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = motion.springBouncy(),
        label = "composer_action_press_scale",
    )

    // Breathing pulse during agent execution (Abort / Stop mode), at the
    // single live pulse period (motion.pulsePeriodMs). The infinite
    // transition only exists while busy — nothing ticks while idle.
    val pulseScale: Float
    val pulseGlow: Float
    if (busy) {
        val infiniteTransition = rememberInfiniteTransition(label = "busy_pulse_transition")
        pulseScale = infiniteTransition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = motion.pulsePeriodMs, easing = motion.easingStandard),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "busy_pulse_scale",
        ).value
        pulseGlow = infiniteTransition.animateFloat(
            initialValue = 0.30f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = motion.pulsePeriodMs, easing = motion.easingStandard),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "busy_pulse_glow",
        ).value
    } else {
        pulseScale = 1f
        pulseGlow = 0.40f
    }

    val targetBg = when {
        busy -> colors.danger
        voiceListening -> colors.danger
        canSend -> colors.accent
        hasVoice -> colors.accent.copy(alpha = 0.15f)
        else -> colors.fill
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = motion.tweenQuick(),
        label = "composer_action_bg",
    )

    val targetTint = when {
        busy || canSend || voiceListening -> Color.White
        hasVoice -> colors.accent
        // Clearly grey and dim when there is nothing to send, so the accent
        // state above reads as "ready" instead of "slightly different grey".
        // An unavailable mic reads the same way: grey, not accent.
        else -> colors.onSurfaceFaint
    }
    val iconTint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = motion.tweenQuick(),
        label = "composer_action_tint",
    )

    val shadowElevation by animateDpAsState(
        targetValue = when {
            busy || voiceListening -> elevation.sendButtonBusy
            canSend -> elevation.sendButton
            else -> 0.dp
        },
        animationSpec = motion.tweenQuick(),
        label = "composer_action_shadow_elevation",
    )
    val shadowColor by animateColorAsState(
        targetValue = when {
            busy || voiceListening -> colors.danger.copy(alpha = pulseGlow)
            canSend -> colors.accent.copy(alpha = 0.40f)
            else -> Color.Transparent
        },
        animationSpec = motion.tweenQuick(),
        label = "composer_action_shadow_color",
    )

    val finalScale = pressScale * (if (busy) pulseScale else 1.0f)
    // Disabled state gets a hairline ring so it still reads as a button,
    // just an unavailable one, instead of a flat grey dot.
    val isDisabled = !busy && !canSend && !hasVoice && !voiceListening

    // Hit area is 44dp; the 36dp circle stays the visual, centred inside it.
    Box(
        modifier = modifier
            .size(dims.composerActionTouchTarget)
            .semantics { contentDescription = label }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = active,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    scaleX = finalScale
                    scaleY = finalScale
                }
                .shadow(
                    elevation = shadowElevation,
                    shape = CircleShape,
                    ambientColor = shadowColor.copy(alpha = shadowColor.alpha * 0.5f),
                    spotColor = shadowColor,
                )
                .clip(CircleShape)
                .background(backgroundColor)
                .then(
                    if (isDisabled) Modifier.border(0.5.dp, colors.hairline, CircleShape)
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
        Crossfade(
            targetState = when {
                busy || voiceListening -> ActionState.BUSY
                hasVoice || voiceUnavailable -> ActionState.VOICE
                else -> ActionState.SEND
            },
            animationSpec = motion.tweenQuick(),
            label = "composer_action_icon",
        ) { state ->
            // Animation C: the incoming icon pops in from 0.85 with a
            // responsive spring on top of the crossfade, so Send → Stop
            // (and back) reads as a swap, not a dissolve. Each state
            // composes fresh inside Crossfade, so the pop re-runs on
            // every swap and never on a plain recomposition.
            val iconScale = remember { Animatable(0.85f) }
            LaunchedEffect(state) { iconScale.animateTo(1f, motion.springResponsive()) }
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale.value
                    scaleY = iconScale.value
                },
                contentAlignment = Alignment.Center,
            ) {
            when (state) {
                ActionState.BUSY -> {
                    // 14dp, up from 11dp: the stop square was the
                    // smallest glyph in the composer and read as a speck
                    // next to the 20dp send arrow. (The button keeps its
                    // "Stop" contentDescription for screen readers; the
                    // 36dp circle has no room for a visible label.)
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(iconTint, RoundedCornerShape(2.5.dp)),
                    )
                }
                ActionState.VOICE -> {
                    GlyphIcon(
                        glyph = Glyph.Mic,
                        tint = iconTint,
                        size = 18.dp,
                    )
                }
                ActionState.SEND -> {
                    GlyphIcon(
                        glyph = Glyph.ArrowUp,
                        tint = iconTint,
                        size = dims.iconMedium,
                    )
                }
            }
            }
        }
        }
    }
}

private enum class ActionState {
    BUSY, SEND, VOICE
}
