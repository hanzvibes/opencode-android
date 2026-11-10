package dev.bidzz.opencode.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SessionGroupTest {

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 10, 14, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun session(
        id: String,
        timestamp: Long? = null,
        age: SessionAge? = null,
        isPinned: Boolean = false,
    ) = SessionRow(
        id = id,
        title = "Session $id",
        directory = "/test",
        age = age,
        cost = null,
        timestamp = timestamp,
        isPinned = isPinned,
    )

    @Test
    fun `pinned sessions take priority into pinned group`() {
        val s1 = session("1", timestamp = now, isPinned = true)
        val s2 = session("2", timestamp = now, isPinned = false)

        val groups = groupSessions(listOf(s1, s2), nowMillis = now)
        assertEquals(listOf(s1), groups[SessionDateGroup.PINNED])
        assertEquals(listOf(s2), groups[SessionDateGroup.TODAY])
    }

    @Test
    fun `groups sessions correctly across calendar boundaries`() {
        // Today (2 hours ago)
        val sToday = session("today", timestamp = now - 2 * 3600_000L)

        // Yesterday (26 hours ago)
        val sYesterday = session("yesterday", timestamp = now - 26 * 3600_000L)

        // 4 days ago
        val s4Days = session("4days", timestamp = now - 4 * 86400_000L)

        // 15 days ago
        val sOlder = session("older", timestamp = now - 15 * 86400_000L)

        val groups = groupSessions(listOf(sToday, sYesterday, s4Days, sOlder), nowMillis = now)

        assertEquals(listOf(sToday), groups[SessionDateGroup.TODAY])
        assertEquals(listOf(sYesterday), groups[SessionDateGroup.YESTERDAY])
        assertEquals(listOf(s4Days), groups[SessionDateGroup.PREVIOUS_7_DAYS])
        assertEquals(listOf(sOlder), groups[SessionDateGroup.OLDER])
    }

    @Test
    fun `falls back to age unit when timestamp is missing`() {
        val sNow = session("now", age = SessionAge(AgeUnit.NOW))
        val sMinutes = session("min", age = SessionAge(AgeUnit.MINUTES, 10))
        val sHours = session("hrs", age = SessionAge(AgeUnit.HOURS, 3))
        val sYest = session("yest", age = SessionAge(AgeUnit.DAYS, 1))
        val s7Days = session("7d", age = SessionAge(AgeUnit.DAYS, 5))
        val sOld = session("old", age = SessionAge(AgeUnit.MONTHS, 2))

        val groups = groupSessions(
            listOf(sNow, sMinutes, sHours, sYest, s7Days, sOld),
            nowMillis = now,
        )

        assertEquals(listOf(sNow, sMinutes, sHours), groups[SessionDateGroup.TODAY])
        assertEquals(listOf(sYest), groups[SessionDateGroup.YESTERDAY])
        assertEquals(listOf(s7Days), groups[SessionDateGroup.PREVIOUS_7_DAYS])
        assertEquals(listOf(sOld), groups[SessionDateGroup.OLDER])
    }

    @Test
    fun `omits empty groups`() {
        val sToday = session("today", timestamp = now)
        val groups = groupSessions(listOf(sToday), nowMillis = now)

        assertEquals(1, groups.size)
        assertTrue(groups.containsKey(SessionDateGroup.TODAY))
    }
}
