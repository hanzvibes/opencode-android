package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.LocationRef
import dev.bidzz.opencode.core.api.dto.SessionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Opening a session by id must be refused — without touching the current
 * session — when the engine says it is gone, or when it belongs to another
 * project (audit-100 56-F2/56-F6). The decision is pure, so it is tested
 * here; the view-model wiring (GET before park/open) is covered by code
 * review, since viewModelScope cannot be driven in JVM unit tests.
 */
class SessionOpenRefusalTest {

    private fun info(directory: String?): SessionInfo =
        SessionInfo(id = "ses-x", location = directory?.let { LocationRef(directory = it) })

    @Test
    fun `a missing session is refused as gone`() {
        assertEquals(
            R.string.session_link_gone,
            sessionOpenRefusal(meta = null, isMissing = true, directoryMatches = null),
        )
    }

    @Test
    fun `a transient failure is not a refusal`() {
        assertNull(sessionOpenRefusal(meta = null, isMissing = false, directoryMatches = null))
    }

    @Test
    fun `a session from another project is refused`() {
        assertEquals(
            R.string.session_link_wrong_workspace,
            sessionOpenRefusal(meta = info("/other/project"), isMissing = false, directoryMatches = false),
        )
    }

    @Test
    fun `a session in the active workspace is allowed`() {
        assertNull(sessionOpenRefusal(meta = info("/active/project"), isMissing = false, directoryMatches = true))
    }

    @Test
    fun `a null location cannot be verified, so it is allowed`() {
        assertNull(sessionOpenRefusal(meta = info(null), isMissing = false, directoryMatches = null))
    }
}
