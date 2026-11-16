package dev.bidzz.opencode.ui.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Audit-200 Fase C15 (M-214): the preview walk toward a session's first
 * user message must use the widest page the engine allows, so a long
 * session does not cost one request per 50 messages.
 */
class SessionListC15Test {

    @Test
    fun `M-214 the preview walk uses the engine maximum page size`() {
        // The engine's SessionMessagesQuery caps limit at 200 (verified
        // against the pinned engine source). Anything smaller makes a
        // 1,000-message session cost 20 requests; anything larger the
        // server rejects.
        assertEquals(200, PREVIEW_PAGE_SIZE)
    }

    @Test
    fun `m-355 the preview walk is capped at 25 pages`() {
        // Audit-200 D11 (m-355): a 10,000-message session used to walk
        // its whole history before its row showed a preview. 25 pages
        // at 200 messages bounds one walk at 5,000 messages — every
        // ordinary session still finds its true first user message —
        // and a capped walk reports incomplete (see
        // fetchFirstUserPreview), so the D10 note says so.
        assertEquals(25, MAX_PREVIEW_WALK_PAGES)
        assertEquals(5_000, MAX_PREVIEW_WALK_PAGES * PREVIEW_PAGE_SIZE)
    }
}
