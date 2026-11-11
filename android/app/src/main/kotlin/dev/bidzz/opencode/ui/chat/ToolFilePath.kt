package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.ToolCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The file a tool call acted on, for tap-to-open (Fase 1, item 3).
 *
 * Only file tools (read / write / edit and their aliases) have one: a
 * shell command's summary is a command, a search's is a pattern, and
 * opening either as a file would be nonsense. The path is read from the
 * tool input first (`filePath`, then `path`, `file`, `file_path`); the
 * call's summary is the fallback — it is derived from the same keys, so
 * it only ever fills in when the input was a partial stream the JSON
 * parse could not read yet. Null when the tool is not a file tool or no
 * path can be read.
 */
fun toolFilePath(call: ToolCall): String? {
    if (!isFileToolName(call.name)) return null
    val obj = call.input as? JsonObject
        ?: (call.input as? JsonPrimitive)?.contentOrNull
            ?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }.getOrNull() }
    val fromInput = obj?.let { input ->
        FILE_PATH_KEYS.firstNotNullOfOrNull { key ->
            (input[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
    }
    if (fromInput != null) return fromInput
    return call.summary.trim().takeIf { it.isNotBlank() && '\n' !in it }
}

private val FILE_PATH_KEYS = listOf("filePath", "path", "file", "file_path")

/** True for the tools whose input names a file. Directories (list/glob) are out. */
fun isFileToolName(name: String): Boolean = when (name.lowercase()) {
    "read", "write", "edit", "patch", "apply_patch", "multiedit" -> true
    else -> false
}

/**
 * Resolves a tool-reported path to the workspace-relative path the file
 * viewer takes, or null when it must not be opened.
 *
 * The engine's file endpoints take paths relative to the workspace
 * ([FileApi.readText]), and the viewer shows the engine's failure message
 * for a file that is missing or unreadable — so a resolvable path is safe
 * to navigate to. An absolute path outside the workspace is refused
 * outright (it could be anywhere on the device, and the viewer would read
 * it through the engine's location parameter); a relative path that climbs
 * out (`../…`) is refused the same way. A null workspace cannot verify an
 * absolute path, so only an already-relative one passes then.
 */
fun relativizeToWorkspace(path: String, workspacePath: String?): String? {
    val normalized = path.replace('\\', '/').trim()
    if (normalized.isBlank()) return null
    if (normalized.startsWith("/")) {
        val workspace = workspacePath?.replace('\\', '/')?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: return null
        if (normalized == workspace) return null
        if (!normalized.startsWith("$workspace/")) return null
        return normalized.removePrefix("$workspace/")
    }
    val stripped = normalized.removePrefix("./")
    if (stripped.isBlank() || stripped == ".." || stripped.startsWith("../")) return null
    return stripped
}
