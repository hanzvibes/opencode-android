package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A tool call as the UI shows it, built from either a history block or live events.
 *
 * [status] is `streaming`, `running`, `completed` or `error` from the
 * engine — plus `interrupted`, a client-side display status (audit-200
 * C2, M-050): when no run is in flight, a history tool still marked
 * running can never finish, so the view model shows it as interrupted
 * instead of letting it tick forever. It is never written back to the
 * engine; a later resync re-derives it from history.
 */
data class ToolCall(
    val id: String,
    val name: String,
    val status: String,
    /** Raw input: a JSON object once running, a partial JSON string while streaming. */
    val input: JsonElement? = null,
    val output: String? = null,
    val error: String? = null,
) {
    val isDone: Boolean get() = status == "completed" || status == "error" || status == "interrupted"

    /** Input as text, so UI modules need not depend on kotlinx.serialization. */
    val inputText: String? get() = input?.toString()

    /** One-line gist of the input, e.g. the shell command or the file path. */
    val summary: String
        get() {
            val obj = input as? JsonObject ?: return ""
            val key = SUMMARY_KEYS.firstOrNull { obj[it] is JsonPrimitive }
            val value = if (key != null) obj[key] else obj.values.firstOrNull { it is JsonPrimitive }
            return (value as? JsonPrimitive)?.contentOrNull?.lineSequence()?.firstOrNull().orEmpty()
        }

    companion object {
        private val SUMMARY_KEYS = listOf("command", "filePath", "path", "pattern", "url", "query", "description")
    }
}

/** Text of a tool result; file blocks are shown by name since bytes are not inlined. */
fun List<MessageContent>.toolOutput(): String? =
    mapNotNull { if (it.type == "text") it.text else it.name?.let { n -> "[file: $n]" } }
        .joinToString("\n")
        .ifEmpty { null }

/** Converts a history `tool` block; null for any other block type. */
fun MessageContent.toToolCall(): ToolCall? {
    if (type != "tool") return null
    val id = id ?: return null
    val st = state
    return ToolCall(
        id = id,
        name = name.orEmpty(),
        status = st?.status ?: "running",
        input = st?.input,
        output = st?.content?.toolOutput(),
        error = st?.error?.let { it.message ?: it.type },
    )
}
