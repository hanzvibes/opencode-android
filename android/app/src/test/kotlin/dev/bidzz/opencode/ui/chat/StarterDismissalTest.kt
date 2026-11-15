package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Audit-200 D2 (m-1241): dismissed starters used to be stored as the
 * decimal `R.string` int of the prompt, and R ids shift between builds —
 * an update could resurrect a dismissed card, or hide one never
 * dismissed. Dismissals are stable ids now ("explain", "bugs", "tests",
 * "review"); old numeric entries migrate once on load.
 */
class StarterDismissalTest {

    // Stand-in R ids: unit tests run with stubbed R values (all zero), so
    // the migration takes the mapping as a parameter.
    private val resIds = mapOf(1001 to "explain", 1002 to "bugs", 1003 to "tests", 1004 to "review")

    @Test
    fun `an old numeric dismissal migrates to the stable id`() {
        assertEquals(
            setOf("explain"),
            migrateDismissedStarterIds(setOf("1001"), resIds),
        )
    }

    @Test
    fun `an unmapped numeric id is dropped, not kept as a mystery dismissal`() {
        assertEquals(emptySet<String>(), migrateDismissedStarterIds(setOf("999999"), resIds))
    }

    @Test
    fun `stable ids pass through untouched`() {
        assertEquals(
            setOf("explain", "review"),
            migrateDismissedStarterIds(setOf("explain", "review"), resIds),
        )
    }

    @Test
    fun `a mixed set migrates the known and drops the unknown`() {
        assertEquals(
            setOf("explain", "bugs"),
            migrateDismissedStarterIds(setOf("explain", "1002", "999999"), resIds),
        )
    }

    @Test
    fun `an empty set stays empty`() {
        assertEquals(emptySet<String>(), migrateDismissedStarterIds(emptySet(), resIds))
    }
}
