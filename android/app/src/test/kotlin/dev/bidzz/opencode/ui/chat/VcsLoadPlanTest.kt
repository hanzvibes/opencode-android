package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The VCS status dialog retries what failed (audit-100 Fase 6b, 24-F5):
 * a loaded branch list must not block retrying the details, and a
 * workspace switch reloads both.
 */
class VcsLoadPlanTest {

    @Test
    fun `nothing loaded loads both`() {
        assertEquals(
            VcsLoadPlan(loadBranches = true, loadDetails = true),
            planVcsLoad(false, null, null, "/repo"),
        )
    }

    @Test
    fun `loaded branches still retry failed details`() {
        assertEquals(
            VcsLoadPlan(loadBranches = false, loadDetails = true),
            planVcsLoad(true, "/repo", null, "/repo"),
        )
    }

    @Test
    fun `everything loaded for this workspace loads nothing`() {
        assertEquals(
            VcsLoadPlan(loadBranches = false, loadDetails = false),
            planVcsLoad(true, "/repo", "/repo", "/repo"),
        )
    }

    @Test
    fun `a workspace switch reloads both`() {
        assertEquals(
            VcsLoadPlan(loadBranches = true, loadDetails = true),
            planVcsLoad(true, "/repo", "/repo", "/other"),
        )
    }
}
