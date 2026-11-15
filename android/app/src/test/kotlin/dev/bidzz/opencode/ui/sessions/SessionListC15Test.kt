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
}
