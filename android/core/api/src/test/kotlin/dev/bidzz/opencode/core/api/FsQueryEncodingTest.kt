package dev.bidzz.opencode.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Query encoding for the filesystem endpoints.
 *
 * Every path here is user-visible data — a project folder or file name with a space is
 * ordinary — and the engine reads query values as percent-encoded, so a form-encoded
 * `+` arrives as a literal plus and the file simply does not exist.
 */
class FsQueryEncodingTest {

    private val api = FileApi(EngineClient("http://127.0.0.1:1", "Basic dGVzdA=="))

    @Test
    fun `a path with a space reaches the engine as percent-encoding`() {
        val url = api.writeUrl("/data/user/0/dev.bidzz.opencode/files/workspace", "with space.md")
        assertTrue(url, url.contains("path=with%20space.md"))
        assertFalse(url, url.contains("+"))
    }

    @Test
    fun `a directory with a space is encoded the same way`() {
        val param = api.locationParam("/storage/emulated/0/Projects/my app")
        assertTrue(param, param.contains("Projects%2Fmy%20app"))
        assertFalse(param, param.contains("+"))
    }

    @Test
    fun `the location parameter keeps its bracketed name`() {
        // `style: deepObject` in the engine's schema: the parameter is literally
        // `location[directory]`, and the brackets must arrive encoded.
        assertTrue(api.locationParam("/data/x").startsWith("location%5Bdirectory%5D="))
    }

    @Test
    fun `an ordinary path is unchanged apart from the encoded slashes`() {
        // Control for the two tests above: if nothing were encoded at all, the space
        // assertions would pass by accident of a broken encoder.
        assertEquals(
            "/api/experimental/fs/write?location%5Bdirectory%5D=%2Fdata%2Fx&path=main.py",
            api.writeUrl("/data/x", "main.py"),
        )
    }
}
