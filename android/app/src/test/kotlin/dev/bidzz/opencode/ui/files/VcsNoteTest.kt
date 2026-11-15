package dev.bidzz.opencode.ui.files

import dev.bidzz.opencode.core.api.dto.VcsFileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VcsNoteTest {

    @Test
    fun `non-repository has its own note`() {
        assertEquals(
            VcsNote.NOT_A_REPOSITORY,
            vcsNoteFor(isRepository = false, changed = emptyMap(), statusFailed = false),
        )
    }

    @Test
    fun `failed status is unknown never clean`() {
        // Audit-200 C14 (M-210): a failed `GET /api/vcs/status` must not
        // be reported as "no changes" — the tree's state is unknown.
        assertEquals(
            VcsNote.UNKNOWN,
            vcsNoteFor(isRepository = true, changed = emptyMap(), statusFailed = true),
        )
    }

    @Test
    fun `empty status on a healthy call is clean`() {
        assertEquals(
            VcsNote.NO_CHANGES,
            vcsNoteFor(isRepository = true, changed = emptyMap(), statusFailed = false),
        )
    }

    @Test
    fun `changes mean no note`() {
        assertNull(
            vcsNoteFor(
                isRepository = true,
                changed = mapOf("a.kt" to VcsFileStatus(file = "a.kt", status = "modified")),
                statusFailed = false,
            ),
        )
    }
}
