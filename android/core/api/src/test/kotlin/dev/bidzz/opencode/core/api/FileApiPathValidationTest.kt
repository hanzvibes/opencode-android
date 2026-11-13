package dev.bidzz.opencode.core.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 6 batch 6f (35-F6): every path FileApi sends to the engine is
 * validated client-side first — a `..` that climbs out of the workspace
 * (or an absolute path) never reaches the engine at all.
 */
class FileApiPathValidationTest {

    @Test
    fun `normalizeRelativePath resolves dot segments that stay inside`() {
        assertEquals("a/c.txt", normalizeRelativePath("a/b/../c.txt"))
        assertEquals("a/b.txt", normalizeRelativePath("a/./b.txt"))
        assertEquals("a/b.txt", normalizeRelativePath("a//b.txt"))
        assertEquals("a/b.txt", normalizeRelativePath("./a/b.txt"))
        assertEquals("", normalizeRelativePath(""))
        assertEquals("", normalizeRelativePath("."))
    }

    @Test
    fun `normalizeRelativePath refuses every climb out of the workspace`() {
        assertNull(normalizeRelativePath("a/../../secret.txt"))
        assertNull(normalizeRelativePath("../secret.txt"))
        assertNull(normalizeRelativePath("./../secret.txt"))
        assertNull(normalizeRelativePath(".//../secret.txt"))
        assertNull(normalizeRelativePath("/etc/passwd"))
        assertNull(normalizeRelativePath("a\\..\\..\\secret.txt"))
    }

    @Test
    fun `readText refuses an escaping path without touching the network`() {
        // Port 1 never answers: any real request fails with code 0, while
        // the guard returns InvalidPath — so this proves the guard runs
        // before the request is even built.
        val client = EngineClient("http://127.0.0.1:1", "Basic dXNlcjp4")
        val result = runBlocking { FileApi(client).readText("/tmp", "a/../../secret.txt") }
        assertTrue(result is ApiResult.Failure)
        val failure = result as ApiResult.Failure
        assertEquals("InvalidPath", failure.tag)
        assertEquals(-1, failure.code)
    }

    @Test
    fun `list refuses an absolute path without touching the network`() {
        val client = EngineClient("http://127.0.0.1:1", "Basic dXNlcjp4")
        val result = runBlocking { FileApi(client).list("/tmp", "/etc") }
        assertTrue(result is ApiResult.Failure)
        assertEquals("InvalidPath", (result as ApiResult.Failure).tag)
    }

    @Test
    fun `writeText refuses an escaping path without touching the network`() {
        val client = EngineClient("http://127.0.0.1:1", "Basic dXNlcjp4")
        val result = runBlocking { FileApi(client).writeText("/tmp", "../secret.txt", "x") }
        assertTrue(result is ApiResult.Failure)
        assertEquals("InvalidPath", (result as ApiResult.Failure).tag)
    }
}
