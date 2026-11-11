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
 * Follow-up suggestions shown after a turn finishes (Fase 2, item 7).
 *
 * Deliberately NOT a model call: the chips are derived from the last
 * reply's text with cheap string rules, so they cost no tokens and never
 * wait on the engine. One tap sends the chip text as the user's next
 * message — the chips never fill the draft for review, so a tap is a send.
 *
 * The pure rule lives in [followUpSuggestions] so it is unit-testable
 * without Compose; the row below only resolves the string resources and
 * lays the chips out.
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
 * Picks 2–3 follow-up suggestions for [replyText], as string resource ids.
 *
 * - The reply shows code (a fence, a source-file name, a code keyword) →
 *   test it, explain it, show an example of it.
 * - The reply reports a failure (error / gagal / bug / crash) → try another
 *   approach, explain more, give an example.
 * - The reply is a long explanation → explain more, give an example,
 *   summarise the key points.
 * - Anything else, including a blank reply → the generic set.
 *
 * Always returns distinct ids, in display order, never more than three.
 */
fun followUpSuggestions(replyText: String): List<Int> {
    val text = replyText.trim().lowercase()
    if (text.isBlank()) return genericSuggestions()
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
            R.string.followup_try_another,
            R.string.followup_explain_more,
            R.string.followup_give_example,
        )
    }
    if (text.length >= LONG_EXPLANATION_CHARS) {
        return listOf(
            R.string.followup_explain_more,
            R.string.followup_give_example,
            R.string.followup_summarize,
        )
    }
    return genericSuggestions()
}

private fun genericSuggestions(): List<Int> = listOf(
    R.string.followup_explain_more,
    R.string.followup_give_example,
    R.string.followup_continue,
)

/**
 * A horizontally scrolling row of follow-up chips, in the same quiet pill
 * language as the composer's quick-action chips (28dp pill, hairline,
 * no accent fill): the suggestions are a nudge, not a call to action.
 *
 * [onPick] receives the resolved chip text, which the caller sends as a
 * user message. The row itself keeps no state — it disappears when the
 * caller stops showing it (busy again, a new message sent, draft typed).
 */
@Composable
fun FollowUpChips(
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
            .padding(horizontal = spacing.md, vertical = spacing.xs),
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
