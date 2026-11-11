package dev.bidzz.opencode.ui.chat

/**
 * One-tap quick replies for an agent question (Fase 1, item 1).
 *
 * A question card already answers on a tap of a full option row; the chips
 * are the shortcut for the common case — a short, single-choice answer
 * ("Ya", "Tidak", "Fitur / UI") that is sent at once as an ordinary user
 * message, through the same [ChatViewModel.answerQuestion] path as the
 * card, without opening anything else. The card itself stays: chips only
 * ever cover the first few short options, and the full list (plus the
 * write-in) is still there for anything longer.
 */
data class QuickReplyChip(
    /** Id of the option this chip answers with. */
    val optionId: String,
    /** Short label shown on the chip. */
    val label: String,
    /** Text sent as the user message, header included, like the card's. */
    val answer: String,
)

/** Chips are a shortcut, not a second list: at most this many are shown. */
private const val MAX_QUICK_REPLY_CHIPS = 4

/** An option label longer than this is not a quick reply; tap the row instead. */
private const val MAX_QUICK_REPLY_LABEL_LENGTH = 24

/**
 * Quick-reply chips for one question, or empty when there is nothing quick
 * to offer: a multi-choice question (it needs the Submit button), a
 * question with no options (nothing to chip — an answer is never invented,
 * so no synthetic "Ya / Tidak" appears), or options whose labels are all
 * too long. Labels are deduplicated, in option order.
 */
fun quickReplyChips(
    item: QuestionItem,
    maxChips: Int = MAX_QUICK_REPLY_CHIPS,
    maxLabelLength: Int = MAX_QUICK_REPLY_LABEL_LENGTH,
): List<QuickReplyChip> {
    if (item.multiple) return emptyList()
    return item.options
        .filter { it.label.isNotBlank() && it.label.length <= maxLabelLength }
        .distinctBy { it.label }
        .take(maxChips)
        .map { option ->
            QuickReplyChip(
                optionId = option.id,
                label = option.label,
                answer = formatQuestionAnswer(item, listOf(option.label)),
            )
        }
}

/**
 * Formats an answer the way the question card does: the option labels,
 * comma-separated, prefixed with the question header when it has one
 * ("Pembaruan: Fitur / UI"). Shared by the card and the chips so a tap on
 * either one sends exactly the same text.
 */
fun formatQuestionAnswer(item: QuestionItem, labels: List<String>): String {
    val summary = labels.joinToString(", ")
    return if (item.header != null) "${item.header}: $summary" else summary
}
