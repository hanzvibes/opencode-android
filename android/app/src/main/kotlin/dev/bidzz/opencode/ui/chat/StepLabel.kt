package dev.bidzz.opencode.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.bidzz.opencode.R
import dev.bidzz.opencode.ui.Glyph

/** How one tool call reads on the timeline: an icon, a verb and what it acted on. */
data class StepLabel(
    val glyph: Glyph,
    /** Which verb to show, so the screen can word it in the app's language. */
    val verb: StepVerb,
    val active: Boolean,
    /** File name or command; empty when the tool gave no input. */
    val target: String,
    /** True when [target] is already a finished phrase (an unknown tool's own name). */
    val literal: Boolean = false,
)

/** The verbs a tool call can read as. Wording lives in the screen, not here. */
enum class StepVerb { READ, WRITE, EDIT, RUN, SEARCH, LIST, FETCH, OTHER }

/**
 * Turns a tool call into a verb and a target: "Read README.md", "Running npm test".
 *
 * The raw input is a long absolute path or a whole command, which says nothing at a glance;
 * the verb and the file name do. The full input is still one tap away on the step itself.
 * Present tense while the tool is running, past once it has finished.
 *
 * This returns the parts, not the sentence: the sentence depends on the app's language, and
 * a function with no Context cannot know it.
 */
fun stepLabel(name: String, summary: String, status: String): StepLabel {
    val active = status == "running" || status == "streaming"
    val kind = name.lowercase()

    val (glyph, verb) = when (kind) {
        "read" -> Glyph.File to StepVerb.READ
        "write" -> Glyph.Pencil to StepVerb.WRITE
        "edit", "patch", "apply_patch", "multiedit" -> Glyph.Pencil to StepVerb.EDIT
        "bash", "shell", "command" -> Glyph.Terminal to StepVerb.RUN
        "grep", "search" -> Glyph.Search to StepVerb.SEARCH
        "glob", "list", "ls" -> Glyph.Folder to StepVerb.LIST
        "webfetch", "fetch", "websearch" -> Glyph.Globe to StepVerb.FETCH
        else -> {
            // A tool this app has no verb for shows its own name, which is not translated.
            val own = name.ifEmpty { "tool" }.replaceFirstChar { it.uppercase() }
            return StepLabel(Glyph.Sliders, StepVerb.OTHER, active, own + gist(summary), literal = true)
        }
    }
    val target = if (kind in PATH_TOOLS) summary.replace('\\', '/').trimEnd('/').substringAfterLast('/') else summary.take(MAX_TARGET)
    return StepLabel(glyph, verb, active, target)
}

private val PATH_TOOLS = setOf("read", "write", "edit", "patch", "apply_patch", "multiedit", "list", "ls")
private const val MAX_TARGET = 56

private fun gist(summary: String): String = if (summary.isEmpty()) "" else " ${summary.take(MAX_TARGET)}"

/**
 * The sentence for this step, in the app's language.
 *
 * A verb plus a target is two words in English and two in Indonesian, but the order and the
 * separator are the language's business, so the join goes through a string resource too.
 */
@Composable
fun StepLabel.text(): String {
    if (literal) return target
    val verb = stringResource(
        when (verb) {
            StepVerb.READ -> if (active) R.string.step_read_present else R.string.step_read_past
            StepVerb.WRITE -> if (active) R.string.step_write_present else R.string.step_write_past
            StepVerb.EDIT -> if (active) R.string.step_edit_present else R.string.step_edit_past
            StepVerb.RUN -> if (active) R.string.step_run_present else R.string.step_run_past
            StepVerb.SEARCH -> if (active) R.string.step_search_present else R.string.step_search_past
            StepVerb.LIST -> if (active) R.string.step_list_present else R.string.step_list_past
            StepVerb.FETCH -> if (active) R.string.step_fetch_present else R.string.step_fetch_past
            StepVerb.OTHER -> return target
        },
    )
    return if (target.isEmpty()) verb else stringResource(R.string.step_target, verb, target)
}
