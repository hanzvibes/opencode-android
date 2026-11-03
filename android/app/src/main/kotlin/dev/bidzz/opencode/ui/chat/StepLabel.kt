package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.ui.Glyph

/** How one tool call reads on the timeline: an icon and a short sentence. */
data class StepLabel(val glyph: Glyph, val text: String)

/**
 * Turns a tool call into "Read README.md" or "Running npm test".
 *
 * The raw input is a long absolute path or a whole command, which says nothing at a glance;
 * the verb and the file name do. The full input is still one tap away on the step itself.
 * Present tense while the tool is running, past once it has finished.
 */
fun stepLabel(name: String, summary: String, status: String): StepLabel {
    val active = status == "running" || status == "streaming"
    val kind = name.lowercase()

    val (glyph, present, past) = when (kind) {
        "read" -> Triple(Glyph.File, "Reading", "Read")
        "write" -> Triple(Glyph.Pencil, "Writing", "Wrote")
        "edit", "patch", "apply_patch", "multiedit" -> Triple(Glyph.Pencil, "Editing", "Edited")
        "bash", "shell", "command" -> Triple(Glyph.Terminal, "Running", "Ran")
        "grep", "search" -> Triple(Glyph.Search, "Searching", "Searched")
        "glob", "list", "ls" -> Triple(Glyph.Folder, "Listing", "Listed")
        "webfetch", "fetch", "websearch" -> Triple(Glyph.Globe, "Fetching", "Fetched")
        else -> return StepLabel(Glyph.Sliders, name.ifEmpty { "tool" }.replaceFirstChar { it.uppercase() } + gist(summary))
    }
    val verb = if (active) present else past
    val target = if (kind in PATH_TOOLS) summary.replace('\\', '/').trimEnd('/').substringAfterLast('/') else summary.take(MAX_TARGET)
    return StepLabel(glyph, if (target.isEmpty()) verb else "$verb $target")
}

private val PATH_TOOLS = setOf("read", "write", "edit", "patch", "apply_patch", "multiedit", "list", "ls")
private const val MAX_TARGET = 56

private fun gist(summary: String): String = if (summary.isEmpty()) "" else " ${summary.take(MAX_TARGET)}"
