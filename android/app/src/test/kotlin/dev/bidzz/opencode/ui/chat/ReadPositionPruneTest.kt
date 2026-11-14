package dev.bidzz.opencode.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-200 B5 (M-025): [ReadPositionStore.prune] drops watermarks for
 * sessions the engine no longer lists, and only those. The pure key
 * filter is tested here (no Context needed); the store applies it.
 */
class ReadPositionPruneTest {

    @Test
    fun `stale read ids are returned, live ones are kept`() {
        val keys = setOf("read_id_s1", "read_id_s2", "read_id_s3")

        val stale = ReadPositionStore.staleReadPositionKeys(keys, setOf("s1", "s3"))

        assertEquals(listOf("read_id_s2"), stale)
    }

    @Test
    fun `legacy count watermarks are pruned the same way`() {
        val keys = setOf("read_count_s1", "read_count_s9")

        val stale = ReadPositionStore.staleReadPositionKeys(keys, setOf("s1"))

        assertEquals(listOf("read_count_s9"), stale)
    }

    @Test
    fun `keys that are not watermarks are never returned`() {
        val keys = setOf("pinned_s1", "composer_s1", "read_id_s1")

        val stale = ReadPositionStore.staleReadPositionKeys(keys, setOf("s1"))

        assertTrue(stale.isEmpty())
    }

    @Test
    fun `an empty known set prunes every watermark`() {
        val keys = setOf("read_id_s1", "read_count_s2")

        val stale = ReadPositionStore.staleReadPositionKeys(keys, emptySet())

        assertEquals(2, stale.size)
    }
}
