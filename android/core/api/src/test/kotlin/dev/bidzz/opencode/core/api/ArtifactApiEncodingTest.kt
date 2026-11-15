package dev.bidzz.opencode.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Audit-200 D3 (m-897): a bare "." / ".." artifact id must never reach the
 * URL — `URLEncoder` lets dots through and OkHttp resolves them away, so
 * get("..") would silently land on /api/experimental/ instead of the
 * artifact. Percent-encoding the dots does not help either (OkHttp
 * normalises %2E too), so the client refuses the segment outright. A dot
 * INSIDE an id is still fine.
 */
class ArtifactApiEncodingTest {

    private val api = ArtifactApi(EngineClient("http://127.0.0.1:1", "Basic dXNlcjp4"))

    @Test
    fun `dot-only artifact ids are refused`() {
        for (id in listOf(".", "..")) {
            try {
                api.viewPath(id)
                fail("expected IllegalArgumentException for id \"$id\"")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun `a normal artifact id builds its view path unchanged`() {
        assertEquals("/api/experimental/artifacts/abc-123/view/", api.viewPath("abc-123"))
    }

    @Test
    fun `a dot inside an artifact id is fine`() {
        assertEquals(
            "/api/experimental/artifacts/ses-abc_123.x/view/",
            api.viewPath("ses-abc_123.x"),
        )
    }
}
