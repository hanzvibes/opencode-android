package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography

/**
 * Composer suggestion chips (Composer Opsi C): contextual prompts shown
 * above the composer that FILL the draft for review — they never send.
 *
 * That is the deliberate difference from [followUpSuggestions] (Fase 2):
 * a follow-up tap sends immediately, so follow-ups only appear on a
 * finished assistant turn with an empty draft; a composer suggestion tap
 * only fills the draft, so these appear in the moments follow-ups do not
 * cover — after an error, or when the last message is the user's own
 * (a stopped run, a reopened session ending on a user message). The two
 * rows never show at the same time, so the same wording can never mean
 * "tap to send" in one row and "tap to fill" in the other.
 *
 * The empty session is NOT covered here on purpose: the empty state's
 * starter cards already fill the draft with the same starters ("Explain
 * how this codebase is structured…", "Help me find and fix bugs…"), and
 * a second row of chips above the composer would duplicate them.
 *
 * Visibility rule (owned by the caller, mirrored in Composer itself):
 * only when idle, not editing, no pending permission, and the draft is
 * blank. Tapping a chip fills the draft, which hides the row — the chips
 * disappear as soon as the user starts typing.
 */

/** A reply this long reads as an explanation, not a one-liner. */
private const val LONG_EXPLANATION_CHARS = 240

private val CODE_FILE_EXTENSIONS =
    Regex("""\.(kt|kts|java|ts|tsx|js|jsx|py|go|rs|swift|xml|gradle|md|json|yaml|yml)\b""")

private val CODE_KEYWORDS = listOf("fun ", "class ", "def ", "import ", "package ")

private val ERROR_KEYWORDS = listOf(
    "error", "failed", "gagal", "bug", "crash", "exception",
    "tidak bisa", "cannot", "could not", "couldn't", "tidak berhasil",
)

/**
 * Picks 0–3 composer suggestions for the current context, as string
 * resource ids.
 *
 * - No messages at all → none: the empty-state starter cards own that moment.
 * - A failure (error banner) → fix it, explain it.
 * - The last assistant reply shows code → test it, explain it, give an example.
 * - The last assistant reply reports a failure in its text → fix it, explain it.
 * - The last assistant reply is a long explanation → explain more, give an
 *   example, summarise the key points.
 * - Anything else, including no assistant reply yet → the generic set.
 *
 * Always returns distinct ids, in display order, never more than three.
 * The generic / code / long sets reuse the follow-up wordings on purpose:
 * the two rows never co-exist, so one wording means one thing at a time.
 */
fun composerSuggestions(
    lastAssistantText: String?,
    hasFailure: Boolean,
    hasMessages: Boolean,
): List<Int> {
    if (!hasMessages) return emptyList()
    if (hasFailure) {
        return listOf(
            R.string.composer_suggest_fix_error,
            R.string.composer_suggest_explain_error,
        )
    }
    val text = lastAssistantText?.trim()?.lowercase().orEmpty()
    if (text.isBlank()) return genericComposerSuggestions()
    val mentionsCode = text.contains("```") ||
        CODE_FILE_EXTENSIONS.containsMatchIn(text) ||
        CODE_KEYWORDS.any { text.contains(it) }
    if (mentionsCode) {
        return listOf(
            R.string.followup_make_test,
            R.string.followup_explain_code,
            R.string.followup_give_example,
        )
    }
    if (ERROR_KEYWORDS.any { text.contains(it) }) {
        return listOf(
            R.string.composer_suggest_fix_error,
            R.string.composer_suggest_explain_error,
        )
    }
    if (text.length >= LONG_EXPLANATION_CHARS) {
        return listOf(
            R.string.followup_explain_more,
            R.string.followup_give_example,
            R.string.followup_summarize,
        )
    }
    return genericComposerSuggestions()
}

private fun genericComposerSuggestions(): List<Int> = listOf(
    R.string.followup_explain_more,
    R.string.followup_give_example,
    R.string.followup_continue,
)

/**
 * Merges a tapped suggestion into the draft: an empty draft is replaced
 * outright; anything already typed keeps its text, and the suggestion is
 * appended on a new line so it never glues itself onto a half-typed
 * sentence. A blank suggestion leaves the draft untouched.
 */
fun appendSuggestionToDraft(currentDraft: String, suggestion: String): String {
    val text = suggestion.trim()
    if (text.isEmpty()) return currentDraft
    if (currentDraft.isBlank()) return text
    return "${currentDraft.trimEnd()}\n$text"
}

/**
 * A horizontally scrolling row of composer suggestion chips, in the same
 * quiet pill language as [FollowUpChips] (28dp pill, hairline, no accent
 * fill): the suggestions are a nudge, not a call to action.
 *
 * [onPick] receives the resolved chip text, which the caller writes into
 * the draft — it never sends. The row itself keeps no state; it disappears
 * when the caller stops showing it (draft typed, a send, busy again).
 */
@Composable
fun ComposerSuggestionChips(
    suggestions: List<Int>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        suggestions.forEach { res ->
            val text = stringResource(res)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(radius.full))
                    .background(colors.fill)
                    .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.full))
                    .clickable(role = Role.Button) { onPick(text) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = text,
                    style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Medium),
                    color = colors.onSurface,
                )
            }
        }
    }
}
