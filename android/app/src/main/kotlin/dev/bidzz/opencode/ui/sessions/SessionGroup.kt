package dev.bidzz.opencode.ui.sessions

import dev.bidzz.opencode.R
import java.util.Calendar

/**
 * Groupings for conversations matching modern Cursor / ChatGPT layout.
 */
enum class SessionDateGroup {
    PINNED,
    TODAY,
    YESTERDAY,
    PREVIOUS_7_DAYS,
    OLDER;

    fun titleRes(): Int = when (this) {
        PINNED -> R.string.session_group_pinned
        TODAY -> R.string.session_group_today
        YESTERDAY -> R.string.session_group_yesterday
        PREVIOUS_7_DAYS -> R.string.session_group_7_days
        OLDER -> R.string.session_group_older
    }
}

/**
 * Extra per-session detail the list API does not return with the rows.
 *
 * The engine's session list carries no transcript text, model or outcome, so
 * previews and model/status come from a separate lookup (first message per
 * session, via the messages endpoint) supplied by the caller. Until that
 * lookup is wired, every field is null and the row falls back to title-only.
 */
data class SessionDetail(
    /** First user message, trimmed — the row preview. */
    val preview: String? = null,
    /** Model that ran the session, e.g. "anthropic/claude-sonnet-4". */
    val model: String? = null,
    /** Last known outcome, e.g. "succeeded" / "failed". */
    val status: String? = null,
)

/** True when [query] matches the title, the transcript preview, the model, the status or the directory. */
fun matchesSessionQuery(query: String, row: SessionRow, detail: SessionDetail? = null): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim()
    return row.title.contains(needle, ignoreCase = true)
        || detail?.preview?.contains(needle, ignoreCase = true) == true
        || detail?.model?.contains(needle, ignoreCase = true) == true
        || detail?.status?.contains(needle, ignoreCase = true) == true
        || row.directory?.contains(needle, ignoreCase = true) == true
}

/** Filters rows by [query] across titles and transcript previews (see [matchesSessionQuery]). */
fun filterSessionRows(
    rows: List<SessionRow>,
    query: String,
    details: Map<String, SessionDetail> = emptyMap(),
): List<SessionRow> {
    if (query.isBlank()) return rows
    return rows.filter { matchesSessionQuery(query, it, details[it.id]) }
}

/**
 * Groups session rows into date-based buckets:
 * Pinned -> Today -> Yesterday -> Previous 7 days -> Older.
 */
fun groupSessions(
    sessions: List<SessionRow>,
    nowMillis: Long = System.currentTimeMillis(),
): Map<SessionDateGroup, List<SessionRow>> {
    val pinned = mutableListOf<SessionRow>()
    val today = mutableListOf<SessionRow>()
    val yesterday = mutableListOf<SessionRow>()
    val previous7Days = mutableListOf<SessionRow>()
    val older = mutableListOf<SessionRow>()

    val cal = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfToday = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val startOfYesterday = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, -6)
    val startOf7Days = cal.timeInMillis

    for (row in sessions) {
        if (row.isPinned) {
            pinned.add(row)
            continue
        }
        val ts = row.timestamp
        if (ts != null && ts > 0) {
            when {
                ts >= startOfToday -> today.add(row)
                ts >= startOfYesterday -> yesterday.add(row)
                ts >= startOf7Days -> previous7Days.add(row)
                else -> older.add(row)
            }
        } else {
            when (row.age?.unit) {
                AgeUnit.NOW, AgeUnit.MINUTES, AgeUnit.HOURS -> today.add(row)
                AgeUnit.DAYS -> {
                    if (row.age.value <= 1) yesterday.add(row)
                    else if (row.age.value <= 7) previous7Days.add(row)
                    else older.add(row)
                }
                AgeUnit.MONTHS, null -> older.add(row)
            }
        }
    }

    val result = linkedMapOf<SessionDateGroup, List<SessionRow>>()
    if (pinned.isNotEmpty()) result[SessionDateGroup.PINNED] = pinned
    if (today.isNotEmpty()) result[SessionDateGroup.TODAY] = today
    if (yesterday.isNotEmpty()) result[SessionDateGroup.YESTERDAY] = yesterday
    if (previous7Days.isNotEmpty()) result[SessionDateGroup.PREVIOUS_7_DAYS] = previous7Days
    if (older.isNotEmpty()) result[SessionDateGroup.OLDER] = older

    return result
}
