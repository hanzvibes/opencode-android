package dev.bidzz.opencode.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import kotlinx.coroutines.launch

/**
 * An interactive inline question card in chat.
 *
 * Allows the operator to answer questions directly from the assistant,
 * selecting single/multiple choices or typing a write-in response.
 */
@Composable
fun InlineQuestionCard(
    call: ToolCall,
    questions: List<QuestionItem>,
    modifier: Modifier = Modifier,
    /**
     * Answers the engine already confirmed, by question index
     * (audit-200 B5, M-065). The view model records them and stamps
     * them into the part on every rebuild — without this, a history
     * reload recreated the card with no memory of the confirmed
     * answers and a multi-question card showed "expired" for every
     * question. "" = unanswered.
     */
    answered: List<String> = emptyList(),
    /**
     * Sends one answer to the engine. True only when the engine
     * confirmed the reply — the card marks the question answered
     * only then (audit-70 44-F1): marking it before the reply lands
     * would show "Answered" for an answer the engine never got, with
     * no way to try again.
     */
    onAnswer: suspend (QuestionAnswer) -> Boolean = { false },
    /**
     * Skips a question instead of answering it (audit-200 C3, M-066):
     * cancels the form, so the agent carries on without the answer.
     * True only when the engine confirmed the cancel. Receives the
     * call id, the question index and the card's question count.
     */
    onSkip: suspend (String, Int, Int) -> Boolean = { _, _, _ -> false },
) {
    if (questions.isEmpty()) return

    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val shape = RoundedCornerShape(radius.md)
    val scope = rememberCoroutineScope()

    // One answer per question, indexed by qIndex ("" = unanswered). A single
    // shared answer used to mark every question done — and show the same
    // answer on all of them — as soon as the first one was submitted.
    // Seeded from [answered]: answers the engine already confirmed
    // (audit-200 B5, M-065) — a card recreated after a history reload
    // starts from the recorded answers instead of empty.
    var submittedAnswers by rememberSaveable(call.id) { mutableStateOf(answered) }

    // Questions with a reply in flight: a second tap while the first is
    // still travelling is ignored, so the same answer is never sent
    // twice (the second reply would fail as already-settled and drop
    // the answer back into the composer).
    var submitting by remember { mutableStateOf(emptySet<Int>()) }

    // Questions with a skip in flight (audit-200 C3, M-066): same
    // guard as `submitting` — a second tap while the cancel is
    // travelling is ignored.
    var skipping by remember { mutableStateOf(emptySet<Int>()) }

    // The card stays "open" while any question is still unanswered.
    val anyPending = questions.indices.any { qIndex ->
        !call.isDone && submittedAnswers.getOrNull(qIndex).isNullOrBlank()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceSunken)
            .border(
                width = 0.5.dp,
                color = if (anyPending) colors.accent.copy(alpha = 0.45f) else colors.hairline,
                shape = shape,
            )
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        questions.forEachIndexed { qIndex, item ->
            // Per-section done state and answer: only this question is
            // resolved by its own submission. A previous run's output is a
            // fallback for history reloads, where no per-question answers
            // were recorded.
            val sectionAnswer = submittedAnswers.getOrNull(qIndex)?.takeIf { it.isNotBlank() }
            // The old run's output is a fallback for history reloads only
            // when there is a single question: with several questions the
            // same output would be stamped on every section.
            val sectionResolved = sectionAnswer
                ?: call.output?.takeIf { questions.size == 1 && it.isNotBlank() && call.isDone }
            // "Answered" only when there is actually an answer to show;
            // a finished call with a blank output is not an answered one.
            val sectionDone = sectionResolved != null
            // A skipped section (audit-200 C3, M-066) carries the
            // marker instead of an answer: the card shows "Skipped",
            // never the marker text itself.
            val sectionSkipped = sectionAnswer == SKIPPED_QUESTION_MARKER
            // A call that is done with no recorded answer is expired
            // (10-F2): the engine already moved on, so the question can
            // no longer be answered — showing the form invites a tap that
            // fails and drops the answer back into the composer.
            val sectionExpired = call.isDone && sectionResolved == null
            // Keyed per question: without the key, a section's selected
            // option / write-in state could survive into the next question
            // when the list changes.
            key(qIndex) {
                SingleQuestionSection(
                    item = item,
                    qIndex = qIndex,
                    totalQuestions = questions.size,
                    isDone = sectionDone,
                    resolvedAnswer = sectionResolved,
                    isExpired = sectionExpired,
                    isSkipped = sectionSkipped,
                    isSkipping = qIndex in skipping,
                    onSkip = {
                        // A second tap while the cancel is still
                        // travelling is ignored (see `skipping`).
                        if (qIndex in skipping) return@SingleQuestionSection
                        skipping = skipping + qIndex
                        scope.launch {
                            val skipped = onSkip(call.id, qIndex, questions.size)
                            skipping = skipping - qIndex
                            // Only an engine-confirmed cancel marks the
                            // sections skipped. The cancel ended the
                            // whole form, so every still-unanswered
                            // section on the card is skipped too — none
                            // of them can be answered anymore. On a
                            // failure the card stays open for another try.
                            if (skipped) {
                                val updated = submittedAnswers.toMutableList()
                                while (updated.size < questions.size) updated.add("")
                                updated[qIndex] = SKIPPED_QUESTION_MARKER
                                for (i in updated.indices) {
                                    if (updated[i].isBlank()) updated[i] = SKIPPED_QUESTION_MARKER
                                }
                                submittedAnswers = updated
                            }
                        }
                    },
                    onSubmit = { labels, displayText ->
                        // A second tap while the reply is still
                        // travelling is ignored (see `submitting`).
                        if (qIndex in submitting) return@SingleQuestionSection
                        submitting = submitting + qIndex
                        scope.launch {
                            val delivered = onAnswer(
                                QuestionAnswer(
                                    callId = call.id,
                                    questionIndex = qIndex,
                                    labels = labels,
                                    multiple = item.multiple,
                                    displayText = displayText,
                                ),
                            )
                            submitting = submitting - qIndex
                            // Only an engine-confirmed answer marks the
                            // question done (audit-70 44-F1). On a
                            // failure the answer is back in the composer
                            // and the card stays open for another try.
                            if (delivered) {
                                val updated = submittedAnswers.toMutableList()
                                while (updated.size <= qIndex) updated.add("")
                                updated[qIndex] = displayText
                                submittedAnswers = updated
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SingleQuestionSection(
    item: QuestionItem,
    qIndex: Int,
    totalQuestions: Int,
    isDone: Boolean,
    resolvedAnswer: String?,
    isExpired: Boolean = false,
    /** The section was skipped instead of answered (audit-200 C3,
     * M-066): the badge and the answer row say "Skipped", muted. */
    isSkipped: Boolean = false,
    /** A skip is in flight: the Skip button is disabled meanwhile. */
    isSkipping: Boolean = false,
    onSubmit: (labels: List<String>, displayText: String) -> Unit,
    onSkip: () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    // Keyed by question index, so each question keeps its own selection
    // and write-in instead of inheriting the previous one's.
    var selectedOptionIds by rememberSaveable(qIndex) { mutableStateOf(emptyList<String>()) }
    var writeInText by rememberSaveable(qIndex) { mutableStateOf("") }
    // A question with no options has nothing to tap: the write-in column
    // opens active by default, with the keyboard, so the user can answer
    // straight away instead of finding the "Other" row first.
    var writeInActive by rememberSaveable(qIndex) {
        mutableStateOf(item.options.isEmpty())
    }

    /** Builds the labels plus "Header: answer1, answer2" and hands both to the card. */
    fun submitAnswer(selected: List<String>, writeIn: String, writeInOn: Boolean) {
        val chosenLabels = item.options
            .filter { it.id in selected }
            .map { it.label }
        val allAnswers = chosenLabels + (if (writeInOn && writeIn.isNotBlank()) listOf(writeIn.trim()) else emptyList())
        onSubmit(allAnswers, formatQuestionAnswer(item, allAnswers))
    }

    // Header Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = Glyph.Question,
                tint = colors.accent,
                size = 13.dp,
            )
        }

        Text(
            text = item.header ?: stringResource(R.string.question_title),
            style = OpenCodeTypography.label.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            ),
            color = colors.accentText,
            // A long header used to push the "1/3" counter and the badge
            // out of the row; it now ellipsises instead.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        Spacer(Modifier.weight(1f))

        // "2/3" — with several questions in one card, the reader needs
        // to know which one this is and how many are left.
        if (totalQuestions > 1) {
            Text(
                text = "${qIndex + 1}/$totalQuestions",
                style = OpenCodeTypography.caption.copy(
                    fontFamily = OpenCodeTypography.mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.onSurfaceFaint,
            )
        }

        // Badge: Multi-choice / Single-choice / Answered / Skipped
        val badgeText = when {
            isSkipped -> stringResource(R.string.question_skipped)
            isDone -> stringResource(R.string.question_answered)
            item.multiple -> stringResource(R.string.question_multi_choice)
            else -> stringResource(R.string.question_single_choice)
        }
        val badgeColor = when {
            isSkipped -> colors.onSurfaceMuted
            isDone -> colors.toolSuccess
            else -> colors.onSurfaceMuted
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(radius.full))
                .background(badgeColor.copy(alpha = 0.12f))
                .padding(horizontal = spacing.xs + 2.dp, vertical = 2.dp),
        ) {
            Text(
                text = badgeText,
                style = OpenCodeTypography.caption.copy(
                    fontFamily = OpenCodeTypography.mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = badgeColor,
            )
        }
    }

    // Question Prompt
    if (item.question.isNotBlank()) {
        Text(
            text = item.question,
            style = OpenCodeTypography.body.copy(
                fontWeight = FontWeight.Medium,
                lineHeight = 21.sp,
            ),
            color = colors.onSurface,
            modifier = Modifier.padding(top = spacing.xxs),
        )
    }

    if (isDone) {
        // Confirmed Answer View
        if (isSkipped) {
            // Skipped (audit-200 C3, M-066): the marker is never shown
            // as text — the row says "Skipped", muted, like the badge.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius.sm))
                    .background(colors.fill)
                    .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.sm))
                    .padding(horizontal = spacing.sm, vertical = spacing.xs + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GlyphIcon(
                    glyph = Glyph.ChevronRight,
                    tint = colors.onSurfaceMuted,
                    size = 12.dp,
                )
                Text(
                    text = stringResource(R.string.question_skipped),
                    style = OpenCodeTypography.caption.copy(fontSize = 12.sp),
                    color = colors.onSurfaceMuted,
                )
            }
        } else if (!resolvedAnswer.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius.sm))
                    .background(colors.toolSuccess.copy(alpha = 0.08f))
                    .border(0.5.dp, colors.toolSuccess.copy(alpha = 0.25f), RoundedCornerShape(radius.sm))
                    .padding(horizontal = spacing.sm, vertical = spacing.xs + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GlyphIcon(
                    glyph = Glyph.Check,
                    tint = colors.toolSuccess,
                    size = 12.dp,
                )
                Text(
                    text = resolvedAnswer,
                    style = OpenCodeTypography.caption.copy(fontSize = 12.sp),
                    color = colors.toolSuccess,
                )
            }
        }
    } else if (isExpired) {
        // Expired (10-F2): the call finished without this question being
        // answered. No form — the engine will not accept a reply any more.
        Text(
            text = stringResource(R.string.question_expired),
            style = OpenCodeTypography.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = spacing.xxs),
        )
    } else {
        // Quick-reply chips (Fase 1, item 1): the short single-choice
        // answers as one-tap chips above the full list. A chip sends at
        // once, through the same submitAnswer/onSubmit path as tapping
        // the option row — the card then shows the answered state. No
        // chips for multi-choice (it needs Submit), long labels, or a
        // question with no options; the full list below still covers
        // those, so nothing is lost by the chips not appearing.
        val chips = remember(item) { quickReplyChips(item) }
        if (chips.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                chips.forEach { chip ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius.full))
                            .background(colors.accent.copy(alpha = 0.12f))
                            .border(0.5.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(radius.full))
                            .clickable(role = Role.Button) {
                                selectedOptionIds = listOf(chip.optionId)
                                writeInActive = false
                                submitAnswer(listOf(chip.optionId), "", false)
                            }
                            .padding(horizontal = spacing.sm, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = chip.label,
                            style = OpenCodeTypography.label.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.accentText,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Interactive choices list
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            item.options.forEach { option ->
                val isSelected = option.id in selectedOptionIds
                SelectableOptionRow(
                    option = option,
                    isSelected = isSelected,
                    isMultiple = item.multiple,
                    onToggle = {
                        if (item.multiple) {
                            selectedOptionIds = if (isSelected) {
                                selectedOptionIds - option.id
                            } else {
                                selectedOptionIds + option.id
                            }
                        } else {
                            // Single-choice submits on the tap itself: a
                            // second tap on a small Submit button in the
                            // corner was the whole interaction, with no
                            // hint that it was required.
                            selectedOptionIds = listOf(option.id)
                            writeInActive = false
                            submitAnswer(listOf(option.id), "", false)
                        }
                    },
                )
            }

            // Write-in custom response option
            WriteInOptionRow(
                isActive = writeInActive,
                value = writeInText,
                isMultiple = item.multiple,
                onActiveChange = { active ->
                    writeInActive = active
                    if (active && !item.multiple) {
                        selectedOptionIds = emptyList()
                    }
                },
                onValueChange = { writeInText = it },
                onDone = {
                    if (writeInText.isNotBlank()) {
                        submitAnswer(selectedOptionIds, writeInText, true)
                    }
                },
            )
        }

        // Submit Button Row: full-width and 48dp tall, so it is a real
        // target instead of a ~30dp pill in the corner. Single-choice
        // answers submit on the option tap above; this stays for
        // multi-choice and write-in answers.
        val hasSelection = selectedOptionIds.isNotEmpty() || (writeInActive && writeInText.isNotBlank())
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(radius.sm))
                    .background(if (hasSelection) colors.accentFill else colors.fill)
                    .clickable(enabled = hasSelection, role = Role.Button) {
                        submitAnswer(selectedOptionIds, writeInText, writeInActive)
                    }
                    .padding(horizontal = spacing.md, vertical = spacing.sm - 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(
                        text = stringResource(R.string.question_submit),
                        style = OpenCodeTypography.label.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                        color = if (hasSelection) colors.onAccent else colors.onSurfaceFaint,
                    )
                    GlyphIcon(
                        glyph = Glyph.Send,
                        tint = if (hasSelection) colors.onAccent else colors.onSurfaceFaint,
                        size = 11.dp,
                    )
                }
            }
        }

        // Skip (audit-200 C3, M-066): the only way out of a question
        // the operator cannot or will not answer — it cancels the form
        // instead of answering at random or killing the run. A quiet
        // text button under Submit, not a second big target: skipping
        // is the escape hatch, not the main action.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.question_skip),
                style = OpenCodeTypography.label.copy(fontSize = 12.sp),
                color = if (isSkipping) colors.onSurfaceFaint else colors.onSurfaceMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(radius.sm))
                    .clickable(enabled = !isSkipping, role = Role.Button) { onSkip() }
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
            )
        }
    }
}

@Composable
private fun SelectableOptionRow(
    option: QuestionOption,
    isSelected: Boolean,
    isMultiple: Boolean,
    onToggle: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val shape = RoundedCornerShape(radius.sm)

    val borderColor = if (isSelected) colors.accent else colors.hairline
    val bgColor = if (isSelected) colors.accent.copy(alpha = 0.12f) else colors.surfaceVariant.copy(alpha = 0.35f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(bgColor)
            .border(if (isSelected) 1.dp else 0.5.dp, borderColor, shape)
            // A11y (#8b/#8c): the row is the selectable node — single-select
            // rows announce as radio buttons, multi-select as checkboxes,
            // each with its selected state. The hand-drawn circle/square
            // is only the visual; it carries no semantics of its own.
            .then(
                if (isMultiple) {
                    Modifier.toggleable(
                        value = isSelected,
                        role = Role.Checkbox,
                        onValueChange = { onToggle() },
                    )
                } else {
                    Modifier.selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = onToggle,
                    )
                },
            )
            .padding(horizontal = spacing.sm, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        // Radio / Checkbox Indicator
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isMultiple) {
                GlyphIcon(
                    glyph = if (isSelected) Glyph.CheckSquare else Glyph.Square,
                    tint = if (isSelected) colors.accent else colors.onSurfaceMuted,
                    size = 16.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, if (isSelected) colors.accent else colors.onSurfaceMuted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                        )
                    }
                }
            }
        }

        // Option Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = OpenCodeTypography.body.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp,
                ),
                // 47-F1: the selected option label is text — accent is
                // only 3.17:1 on its tint in light.
                color = if (isSelected) colors.accentText else colors.onSurface,
            )
            if (!option.description.isNullOrBlank()) {
                Text(
                    text = option.description,
                    style = OpenCodeTypography.caption.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                    ),
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun WriteInOptionRow(
    isActive: Boolean,
    value: String,
    isMultiple: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit = {},
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val motion = OpenCodeTheme.tokens.motion
    val shape = RoundedCornerShape(radius.sm)

    // Tapping "Other" focuses the field and opens the keyboard; without
    // this the write-in row stayed closed and the field never took focus.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isActive) {
        if (isActive) focusRequester.requestFocus()
    }

    val borderColor = if (isActive) colors.accent else colors.hairline
    val bgColor = if (isActive) colors.accent.copy(alpha = 0.08f) else colors.surfaceVariant.copy(alpha = 0.2f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .border(if (isActive) 1.dp else 0.5.dp, borderColor, shape)
            .padding(horizontal = spacing.sm, vertical = spacing.xs + 2.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A11y (#8b/#8c): same as the option rows — the "Other"
                // toggle announces its state instead of reading as a
                // nameless tap target.
                .then(
                    if (isMultiple) {
                        Modifier.toggleable(
                            value = isActive,
                            role = Role.Checkbox,
                            onValueChange = { onActiveChange(it) },
                        )
                    } else {
                        Modifier.selectable(
                            selected = isActive,
                            role = Role.RadioButton,
                            onClick = { onActiveChange(!isActive) },
                        )
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isMultiple) {
                    GlyphIcon(
                        glyph = if (isActive) Glyph.CheckSquare else Glyph.Square,
                        tint = if (isActive) colors.accent else colors.onSurfaceMuted,
                        size = 16.dp,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, if (isActive) colors.accent else colors.onSurfaceMuted, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.question_other_option),
                style = OpenCodeTypography.caption.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                // 47-F1: the active "Other" label is text — accent is
                // only 3.34:1 on its tint in light.
                color = if (isActive) colors.accentText else colors.onSurfaceMuted,
            )
        }

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(animationSpec = motion.tweenQuick()) +
                expandVertically(animationSpec = motion.tweenStandard()),
            exit = fadeOut(animationSpec = motion.tweenQuick()) +
                shrinkVertically(animationSpec = motion.tweenQuick()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.xxs)
                    .clip(RoundedCornerShape(radius.xs))
                    .background(colors.surfaceSunken)
                    .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.xs))
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.question_write_in_hint),
                        style = OpenCodeTypography.body.copy(fontSize = 12.sp),
                        color = colors.onSurfaceFaint,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = OpenCodeTypography.body.copy(
                        fontSize = 12.sp,
                        color = colors.onSurface,
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        }
    }
}
