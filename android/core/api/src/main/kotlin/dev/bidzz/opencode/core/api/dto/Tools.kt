package dev.bidzz.opencode.core.api.dto

import android.util.Log
import dev.bidzz.opencode.core.api.ApiJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.serializer

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
    /**
     * The tool succeeded but its output could not be decoded (audit-200
     * D11, m-103/m-1134): the `tool.success` event carried a content list
     * that does not parse, so [output] is null — and the timeline must say
     * the output could not be displayed instead of showing a bare
     * "Success" with nothing under it. False for history rows, whose
     * content already decoded with the message.
     */
    val outputDecodeFailed: Boolean = false,
    val error: String? = null,
    /**
     * The shell behind a running bash tool (audit-200 C14, M-195):
     * reported by the engine's `session.tool.progress` event, so the
     * client can page the command's live output from
     * `GET /api/shell/{id}/output` while the tool runs. Null for every
     * other tool and for history rows.
     */
    val shellID: String? = null,
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

/** What decoding a `tool.success` event's content list produced. */
internal data class ParsedToolOutput(
    /** Display text, or null when there was no content to show. */
    val text: String?,
    /** True when content was present but could not be decoded. */
    val decodeFailed: Boolean,
)

/**
 * The `content` list of a `tool.success` event as display text.
 *
 * Audit-200 D11 (m-103/m-1134): this used to be a bare
 * `runCatching { … }.getOrNull()` inside ChatRepository — a malformed
 * content list vanished silently, and the step showed a bare "Success"
 * with no output at all. Now a present-but-undecodable list is logged
 * and flagged, so the timeline can say the output could not be
 * displayed instead. Missing content is not a failure: some tools
 * legitimately return nothing.
 */
internal fun parseToolOutput(p: JsonObject): ParsedToolOutput {
    val content = p["content"] ?: return ParsedToolOutput(null, false)
    return try {
        ParsedToolOutput(
            ApiJson.decodeFromJsonElement(
                ListSerializer(serializer<MessageContent>()),
                content,
            ).toolOutput(),
            false,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("ChatRepository", "tool.success content could not be decoded: ${e.message}")
        ParsedToolOutput(null, true)
    }
}

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
