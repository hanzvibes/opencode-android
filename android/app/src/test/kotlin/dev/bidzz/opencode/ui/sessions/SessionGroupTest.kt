package dev.bidzz.opencode.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `blank query returns every row`() {
        val rows = listOf(session("1"), session("2"))
        assertEquals(rows, filterSessionRows(rows, ""))
        assertEquals(rows, filterSessionRows(rows, "   "))
    }

    @Test
    fun `query matches titles case-insensitively`() {
        val s1 = session("1").copy(title = "Fix login crash")
        val s2 = session("2").copy(title = "Refactor parser")
        assertEquals(listOf(s1), filterSessionRows(listOf(s1, s2), "LOGIN"))
    }

    @Test
    fun `query matches transcript previews from details`() {
        val s1 = session("1").copy(title = "Untitled")
        val s2 = session("2").copy(title = "Untitled")
        val details = mapOf("1" to SessionDetail(preview = "How do I fix the sidebar?"))
        assertEquals(listOf(s1), filterSessionRows(listOf(s1, s2), "sidebar", details))
    }

    @Test
    fun `query matches model and outcome from details`() {
        val s1 = session("1")
        val s2 = session("2")
        val details = mapOf(
            "1" to SessionDetail(model = "anthropic/claude-sonnet-4", status = "succeeded"),
            "2" to SessionDetail(model = "google/gemini-3-flash", status = "failed"),
        )
        assertEquals(listOf(s1), filterSessionRows(listOf(s1, s2), "claude", details))
        assertEquals(listOf(s2), filterSessionRows(listOf(s1, s2), "failed", details))
    }

    @Test
    fun `query with no match returns an empty list`() {
        val rows = listOf(session("1"), session("2"))
        assertTrue(filterSessionRows(rows, "zzz-no-such-session").isEmpty())
    }

    @Test
    fun `80-F8 an Indonesian status query matches the English engine outcome`() {
        val detail = SessionDetail(preview = null, model = null, status = "failed")
        val row = session("a", timestamp = now)

        assertTrue(matchesSessionQuery("gagal", row, detail))
        assertTrue(matchesSessionQuery("failed", row, detail))
        assertTrue(matchesSessionQuery("error", row, detail))
    }

    @Test
    fun `80-F8 a succeeded outcome is searchable as berhasil`() {
        val detail = SessionDetail(preview = null, model = null, status = "succeeded")
        val row = session("a", timestamp = now)

        assertTrue(matchesSessionQuery("berhasil", row, detail))
        assertTrue(matchesSessionQuery("selesai", row, detail))
        assertTrue(matchesSessionQuery("succeeded", row, detail))
    }

    @Test
    fun `80-F8 an unknown outcome stays searchable under its own word`() {
        val detail = SessionDetail(preview = null, model = null, status = "interrupted")
        val row = session("a", timestamp = now)

        assertTrue(matchesSessionQuery("interrupted", row, detail))
        assertTrue(!matchesSessionQuery("gagal", row, detail))
    }

    @Test
    fun `80-F12 a 3-hour-old session at 01 00 is yesterday by the calendar`() {
        val earlyMorning = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 10, 1, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val threeHoursAgo = session("late", age = SessionAge(AgeUnit.HOURS, 3))
        val groups = groupSessions(listOf(threeHoursAgo), nowMillis = earlyMorning)

        assertEquals(listOf(threeHoursAgo), groups[SessionDateGroup.YESTERDAY])
        assertNull(groups[SessionDateGroup.TODAY])
    }

    @Test
    fun `80-F12 the estimate keeps the old fallback buckets at midday`() {
        val sYest = session("yest", age = SessionAge(AgeUnit.DAYS, 1))
        val s7Days = session("7d", age = SessionAge(AgeUnit.DAYS, 5))
        val sOld = session("old", age = SessionAge(AgeUnit.MONTHS, 2))

        val groups = groupSessions(listOf(sYest, s7Days, sOld), nowMillis = now)

        assertEquals(listOf(sYest), groups[SessionDateGroup.YESTERDAY])
        assertEquals(listOf(s7Days), groups[SessionDateGroup.PREVIOUS_7_DAYS])
        assertEquals(listOf(sOld), groups[SessionDateGroup.OLDER])
    }
}
