package dev.bidzz.opencode.ui.jobs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 Fase B1 (M-289): a slow load for the filter the user already
 * left must not land after the load for the new filter. The guard is
 * pure, so the rule is tested here rather than through the view model,
 * whose scope needs a Main dispatcher the JVM tests do not have.
 */
class JobsViewModelTest {

    private val current = JobsUiState(
        kindFilter = JobKindFilter.SHELL,
        statusFilter = JobStatusFilter.RUNNING,
    )

    @Test
    fun `a load still owns the list when the filters match`() {
        assertTrue(
            isCurrentJobsLoad(JobKindFilter.SHELL, JobStatusFilter.RUNNING, current),
        )
    }

    @Test
    fun `a load no longer owns the list when the kind changed`() {
        assertFalse(
            isCurrentJobsLoad(JobKindFilter.SUBAGENT, JobStatusFilter.RUNNING, current),
        )
    }

    @Test
    fun `a load no longer owns the list when the status changed`() {
        assertFalse(
            isCurrentJobsLoad(JobKindFilter.SHELL, JobStatusFilter.COMPLETED, current),
        )
    }
}
