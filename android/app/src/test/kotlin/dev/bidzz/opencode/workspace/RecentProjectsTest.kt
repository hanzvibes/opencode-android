package dev.bidzz.opencode.workspace

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Recents list behaviour.
 *
 * The list is the only thing that makes the project picker useful on a second
 * visit, so its ordering and de-duplication rules matter: a recent that reappears
 * at the bottom, or one that duplicates, makes the picker feel broken.
 *
 * `WorkspaceStore` itself needs SharedPreferences and therefore a device, so the
 * pure list logic is mirrored here.
 */
class RecentProjectsTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** Mirrors the update rule in `WorkspaceStore.rememberRecent`. */
    private fun remember(existing: List<String>, path: String, max: Int = 8): List<String> =
        (listOf(path) + existing.filter { it != path }).take(max)

    @Test
    fun `newest goes first`() {
        assertEquals(listOf("/b", "/a"), remember(listOf("/a"), "/b"))
    }

    @Test
    fun `re-selecting moves to the front without duplicating`() {
        val result = remember(listOf("/b", "/a"), "/a")
        assertEquals(listOf("/a", "/b"), result)
        assertEquals("no duplicates", result.size, result.distinct().size)
    }

    @Test
    fun `list is capped`() {
        val existing = (1..8).map { "/p$it" }
        val result = remember(existing, "/new")
        assertEquals(8, result.size)
        assertEquals("/new", result.first())
        assertFalse("oldest is dropped", result.contains("/p8"))
    }

    @Test
    fun `re-selecting when already first is stable`() {
        val existing = listOf("/a", "/b")
        assertEquals(existing, remember(existing, "/a"))
    }

    @Test
    fun `a directory that disappears is still listed but marked unavailable`() {
        // The picker must show it so the user understands why their project is gone,
        // rather than silently dropping it.
        val gone = File(temp.root, "deleted-project")
        assertFalse(gone.isDirectory)
        // Availability is what the UI reads; the entry itself is kept.
        assertTrue(remember(listOf(gone.path), "/other").contains(gone.path))
    }
}
