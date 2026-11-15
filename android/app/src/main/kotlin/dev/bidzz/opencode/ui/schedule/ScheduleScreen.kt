package dev.bidzz.opencode.ui.schedule

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.OpenCodeApplication
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.ScheduleInfo
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.LoadingState
import dev.bidzz.opencode.ui.LocalizedAlertDialog
import dev.bidzz.opencode.ui.RowDivider
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.SelectorChip
import dev.bidzz.opencode.ui.TextAction
import dev.bidzz.opencode.ui.message
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scheduled prompts for one session (audit-200 C17, M-259).
 *
 * A schedule wakes its session with a prompt — once after a delay, at
 * a time, every interval, or on a cron. The list is per session (the
 * endpoint requires a `sessionID`), so the screen opens from the chat
 * status dialog, not from Settings. Each row shows the name (or "Unnamed
 * schedule"), when it fires, its status, the next due time and how many
 * times it ran; pause / resume / delete act on the row.
 */
@Composable
fun ScheduleScreen(
    sessionID: String,
    state: ScheduleUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreate: (String, String, String, String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearActionFailure: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val context = LocalContext.current

    LaunchedEffect(sessionID) { onRefresh() }

    // Action failures arrive as values; show once, then clear.
    val actionFailureMessage = state.actionFailure?.message()
    LaunchedEffect(state.actionFailure) {
        val message = actionFailureMessage ?: return@LaunchedEffect
        (context.applicationContext as OpenCodeApplication).showSnackbar(message)
        onClearActionFailure()
    }

    var showCreate by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(R.string.schedule_title),
        onBack = onBack,
        contentMaxWidth = 720.dp,
        actions = {
            TextAction(
                label = stringResource(R.string.schedule_new),
                onClick = { showCreate = true },
                enabled = !state.loading,
            )
            TextAction(
                label = stringResource(R.string.common_refresh),
                onClick = onRefresh,
                enabled = !state.loading,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = stringResource(R.string.schedule_body),
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(
                    horizontal = spacing.lg,
                    vertical = spacing.sm,
                ),
            )
            when {
                state.loading -> LoadingState(stringResource(R.string.common_loading), onRetry = onRefresh)
                state.failure != null -> EmptyState(state.failure.message(), isError = true, onRetry = onRefresh)
                state.schedules.isEmpty() -> EmptyState(stringResource(R.string.schedule_empty))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.lg),
                    ) {
                        itemsIndexed(
                            state.schedules,
                            key = { _, schedule -> schedule.id },
                        ) { index, schedule ->
                            if (index > 0) RowDivider(inset = spacing.md)
                            ScheduleRow(
                                schedule = schedule,
                                busy = schedule.id in state.busyIds,
                                onPause = { onPause(schedule.id) },
                                onResume = { onResume(schedule.id) },
                                onDelete = { onDelete(schedule.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateScheduleDialog(
            creating = state.creating,
            onDismiss = { showCreate = false },
            onCreate = { name, kind, value, prompt ->
                onCreate(name, kind, value, prompt)
                showCreate = false
            },
        )
    }
}

@Composable
private fun ScheduleRow(
    schedule: ScheduleInfo,
    busy: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val statusColor = when (schedule.status) {
        "active" -> colors.success
        "paused" -> colors.warning
        "done" -> colors.onSurfaceMuted
        else -> colors.onSurfaceMuted
    }
    val metaText = buildString {
        append(schedule.whenText())
        formatScheduleInstant(schedule.nextDueAt)?.let {
            if (isNotEmpty()) append(" · ")
            append(stringResource(R.string.schedule_next_due, it))
        }
        if (schedule.runCount > 0) {
            if (isNotEmpty()) append(" · ")
            append(stringResource(R.string.schedule_runs, schedule.runCount))
        }
        schedule.remainingRuns?.let {
            if (isNotEmpty()) append(" · ")
            append(stringResource(R.string.schedule_remaining, it))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = schedule.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.schedule_unnamed),
                style = OpenCodeTypography.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = scheduleStatusWord(schedule.status),
                style = OpenCodeTypography.label,
                color = statusColor,
                modifier = Modifier.padding(start = spacing.xs),
            )
        }
        if (metaText.isNotEmpty()) {
            Text(
                text = metaText,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (schedule.isActive()) {
                TextButton(onClick = onPause, enabled = !busy) {
                    Text(
                        text = stringResource(R.string.schedule_pause),
                        style = OpenCodeTypography.label,
                        color = colors.accent,
                    )
                }
            } else if (schedule.status == "paused") {
                TextButton(onClick = onResume, enabled = !busy) {
                    Text(
                        text = stringResource(R.string.schedule_resume),
                        style = OpenCodeTypography.label,
                        color = colors.accent,
                    )
                }
            }
            TextButton(onClick = onDelete, enabled = !busy) {
                Text(
                    text = stringResource(R.string.schedule_delete),
                    style = OpenCodeTypography.label,
                    color = colors.danger,
                )
            }
        }
    }
}

@Composable
private fun scheduleStatusWord(status: String): String = stringResource(
    when (status) {
        "active" -> R.string.schedule_status_active
        "paused" -> R.string.schedule_status_paused
        "done" -> R.string.schedule_status_done
        else -> R.string.schedule_status_unknown
    },
)

/**
 * Formats a schedule instant (`DateTimeUtcFromMillis` — a number of
 * millis in JSON, or an ISO string on older engines) as "27 Oct 09:00".
 * Null when the element carries nothing readable.
 */
private fun formatScheduleInstant(element: JsonElement?): String? {
    val primitive = element as? JsonPrimitive ?: return null
    val millis = primitive.longOrNull
        ?: primitive.contentOrNull?.toLongOrNull()
        ?: return primitive.contentOrNull?.takeIf { it.isNotBlank() }
    val ms = if (millis < 1_000_000_000_000L) millis * 1_000 else millis
    return SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(ms))
}

@Composable
private fun CreateScheduleDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("in") }
    var value by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    val valid = value.isNotBlank() && prompt.isNotBlank()

    LocalizedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, kind, value.trim(), prompt.trim()) },
                enabled = !creating && valid,
            ) {
                Text(
                    text = stringResource(R.string.schedule_create),
                    color = colors.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = colors.onSurfaceMuted,
                )
            }
        },
        title = { Text(text = stringResource(R.string.schedule_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.schedule_name_label)) },
                    singleLine = true,
                    enabled = !creating,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.schedule_when_label),
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceMuted,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    listOf("in", "at", "every", "cron").forEach { option ->
                        SelectorChip(
                            label = stringResource(
                                when (option) {
                                    "at" -> R.string.schedule_when_at
                                    "every" -> R.string.schedule_when_every
                                    "cron" -> R.string.schedule_when_cron
                                    else -> R.string.schedule_when_in
                                },
                            ),
                            selected = kind == option,
                            onClick = { kind = option },
                        )
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = {
                        Text(
                            text = stringResource(
                                when (kind) {
                                    "at" -> R.string.schedule_when_at_hint
                                    "every" -> R.string.schedule_when_every_hint
                                    "cron" -> R.string.schedule_when_cron_hint
                                    else -> R.string.schedule_when_in_hint
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    enabled = !creating,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(text = stringResource(R.string.schedule_prompt_label)) },
                    enabled = !creating,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
