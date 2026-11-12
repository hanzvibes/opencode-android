package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** One entry of the engine's `todowrite` tool call. */
enum class TodoStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

data class TodoItem(
    val content: String,
    val status: TodoStatus,
)

/**
 * Parses the `todos` array out of a `todowrite` tool call's input JSON.
 *
 * Shape verified against the engine fork source at pin 92b2ca0
 * (`packages/core/src/tool/plugin/todo.ts`): the tool's input is
 * `{ todos: [{ content, status }] }` where status is one of
 * `pending | in_progress | completed | cancelled`. There is no `task` or
 * `todoread` tool in this fork — `todowrite` is the only todo tool the
 * engine can emit, so only it gets the checklist rendering.
 *
 * Returns null when the input is not JSON, has no `todos` array, or has no
 * usable entries — the caller then falls back to the raw input block, so a
 * parse failure never hides the agent's arguments. A single malformed
 * entry is skipped; an unknown status reads as [TodoStatus.PENDING]
 * rather than dropping the row. An empty `todos` array returns an empty
 * list (the agent cleared the list), not null.
 */
fun parseTodoList(inputText: String?): List<TodoItem>? {
    if (inputText.isNullOrBlank()) return null
    val root = try {
        kotlinx.serialization.json.Json.parseToJsonElement(inputText).jsonObject
    } catch (_: Exception) {
        return null
    }
    val todos = root["todos"] as? JsonArray ?: return null
    return todos.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val content = (obj["content"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val status = when ((obj["status"] as? JsonPrimitive)?.contentOrNull) {
            "in_progress" -> TodoStatus.IN_PROGRESS
            "completed" -> TodoStatus.COMPLETED
            "cancelled" -> TodoStatus.CANCELLED
            else -> TodoStatus.PENDING
        }
        TodoItem(content = content, status = status)
    }
}

/**
 * A `todowrite` step rendered as a checklist instead of raw input JSON.
 *
 * The mark reads the status at a glance: a filled check for done, a play
 * mark for the one in progress, an empty box for pending, a close mark for
 * cancelled. Finished and cancelled rows are struck through; the text is
 * the agent's own wording, never summarised.
 */
@Composable
fun TodoChecklist(todos: List<TodoItem>) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.tool_todos_title),
            style = OpenCodeTypography.label,
            color = colors.onSurfaceMuted,
        )
        if (todos.isEmpty()) {
            Text(
                text = stringResource(R.string.tool_todos_cleared),
                style = OpenCodeTypography.body,
                color = colors.onSurfaceMuted,
            )
        } else {
            todos.forEach { item ->
                // A11y (#8c): the checklist is read-only — the engine owns
                // the state — so the status is announced as the row's state
                // description, not as a toggle. The glyph is only visual.
                val statusDescription = stringResource(
                    when (item.status) {
                        TodoStatus.COMPLETED -> R.string.todo_status_completed
                        TodoStatus.IN_PROGRESS -> R.string.todo_status_in_progress
                        TodoStatus.CANCELLED -> R.string.todo_status_cancelled
                        TodoStatus.PENDING -> R.string.todo_status_pending
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { stateDescription = statusDescription },
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (glyph, tint) = when (item.status) {
                        TodoStatus.COMPLETED -> Glyph.CheckCircle to colors.success
                        TodoStatus.IN_PROGRESS -> Glyph.Play to colors.accent
                        TodoStatus.CANCELLED -> Glyph.Close to colors.onSurfaceMuted
                        TodoStatus.PENDING -> Glyph.Square to colors.onSurfaceMuted
                    }
                    GlyphIcon(
                        glyph = glyph,
                        tint = tint,
                        size = 16.dp,
                    )
                    Text(
                        text = item.content,
                        style = OpenCodeTypography.body,
                        color = if (item.status == TodoStatus.CANCELLED) {
                            colors.onSurfaceMuted
                        } else {
                            colors.onSurface
                        },
                        textDecoration = when (item.status) {
                            TodoStatus.COMPLETED, TodoStatus.CANCELLED -> TextDecoration.LineThrough
                            else -> TextDecoration.None
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(top = spacing.xxs),
                    )
                }
            }
        }
    }
}
