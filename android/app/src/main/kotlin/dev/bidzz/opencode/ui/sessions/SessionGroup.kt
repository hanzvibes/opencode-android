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
