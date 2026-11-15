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
    /**
     * Searchable text from the session's user messages (audit-200 C5,
     * M-097): the preview alone is only the FIRST user message, so a
     * search could not find a session by what the user asked later.
     * The preview walk accumulates every user message it passes, in
     * visit order, capped (see [accumulateSearchText]) — enough to
     * match on, never the whole transcript. Null until the walk ran.
     */
    val searchText: String? = null,
    /**
     * True when the preview walk COMPLETED for this session (audit-200
     * C5, M-089): the walk reached the oldest page, so a null preview
     * means the session is genuinely empty — not "not looked up yet".
     * A walk that failed mid-way leaves this false, so the next load
     * retries it. Only a completed walk is cached as known.
     */
    val previewKnown: Boolean = false,
)

/**
 * Words a status can be searched by (80-F8).
 *
 * The engine reports outcomes in English ("succeeded" / "failed"), so an
 * Indonesian query like "gagal" never matched the raw status. The known
 * outcomes gain their Indonesian and plain-English synonyms; an unknown
 * outcome stays searchable under its own word, never hidden.
 */
internal fun statusSearchTerms(status: String?): List<String> = when (status?.lowercase()) {
    "succeeded" -> listOf("succeeded", "berhasil", "selesai", "success")
    "failed" -> listOf("failed", "gagal", "error")
    null -> emptyList()
    else -> listOf(status)
}

/** True when [query] matches the title, the transcript preview, the model, the status or the directory.
 *
 * Audit-200 D1 (m-176): the query is tokenised on whitespace and EVERY token
 * must match some field (AND) — "fix login" finds "Fix the login crash"
 * whatever the word order. A single token behaves exactly as before.
 */
fun matchesSessionQuery(query: String, row: SessionRow, detail: SessionDetail? = null): Boolean {
    if (query.isBlank()) return true
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val fields = listOfNotNull(
        row.title,
        detail?.preview,
        // Audit-200 C5 (M-097): the preview is only the first user
        // message — the capped text of every user message the preview
        // walk passed is what makes a session findable by a later ask.
        detail?.searchText,
        detail?.model,
        row.directory,
    ) + statusSearchTerms(detail?.status)
    return tokens.all { token -> fields.any { it.contains(token, ignoreCase = true) } }
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
 * Whether the "Load more" footer stays under an empty search result
 * (audit-200 B6, M-090/M-098): a query that matches nothing on the loaded
 * pages must not hide the next page — the sessions on page 2+ are only
 * reachable by paging on while searching.
 */
fun shouldShowLoadMoreOnEmptySearch(query: String, hasMoreSessions: Boolean): Boolean =
    query.isNotBlank() && hasMoreSessions

/**
 * Whether History shows the full-screen loading state (audit-200 B6,
 * M-213): only the first load, when no rows are on screen yet. A reload
 * with rows keeps the list (and its scroll position) and shows a thin
 * progress bar instead of emptying the screen.
 */
fun shouldShowFullScreenLoading(loading: Boolean, rowsEmpty: Boolean): Boolean =
    loading && rowsEmpty

/**
 * An instant estimated from a relative age (80-F12).
 *
 * A relative age is a duration, not a day: "3 hours ago" at 01:00 is
 * yesterday by the calendar. Estimating the instant first lets the
 * no-timestamp fallback use the same calendar boundaries as the
 * timestamp path instead of raw 24-hour buckets. The estimate is exact
 * for minutes and hours; days and months are approximate by nature (a
 * "1 day" age spans 24–47 hours), so a session near midnight can still
 * land one bucket off — the same imprecision the age itself carries.
 */
internal fun estimateTimestamp(age: SessionAge?, nowMillis: Long): Long? = when (age?.unit) {
    null -> null
    AgeUnit.NOW -> nowMillis
    AgeUnit.MINUTES -> nowMillis - age.value * 60_000L
    AgeUnit.HOURS -> nowMillis - age.value * 3_600_000L
    AgeUnit.DAYS -> nowMillis - age.value * 86_400_000L
    AgeUnit.MONTHS -> nowMillis - age.value * 30L * 86_400_000L
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
            // No timestamp: estimate the instant from the relative age and
            // bucket by the same calendar boundaries as the timestamp path
            // (80-F12). The old fallback used raw 24-hour buckets, so a
            // session from 3 hours ago at 01:00 landed in Today even though
            // it was yesterday by the calendar. A null age means nothing
            // is known at all, and the session stays in Older.
            val estimated = estimateTimestamp(row.age, nowMillis)
            if (estimated != null) {
                when {
                    estimated >= startOfToday -> today.add(row)
                    estimated >= startOfYesterday -> yesterday.add(row)
                    estimated >= startOf7Days -> previous7Days.add(row)
                    else -> older.add(row)
                }
            } else {
                older.add(row)
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
