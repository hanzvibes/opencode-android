package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.CacheUsage
import dev.bidzz.opencode.core.api.dto.MessageTime
import dev.bidzz.opencode.core.api.dto.SessionMessage
import dev.bidzz.opencode.core.api.dto.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fase 2: the merge helpers [ChatRepository] folds contiguous assistant
 * chunks with, and the live-set a resync keeps (01-F3, 03-F1). Pure
 * functions, tested without a server; the wiring is covered by
 * [ChatRepositoryResyncStreamingTest].
 */
class ChatRepositoryMergeTest {

    @Test
    fun `01-F3 tokens sum bucket by bucket like cost`() {
        val merged = mergeTokenUsage(
            TokenUsage(input = 10, output = 5, cache = CacheUsage(read = 2, write = 1)),
            TokenUsage(input = 3, output = 7, reasoning = 4, cache = CacheUsage(read = 1)),
        )
        assertEquals(13L, merged?.input)
        assertEquals(12L, merged?.output)
        assertEquals(4L, merged?.reasoning)
        assertEquals(3L, merged?.cache?.read)
        assertEquals(1L, merged?.cache?.write)
    }

    @Test
    fun `01-F3 a bucket stays null only when both sides report nothing`() {
        val merged = mergeTokenUsage(
            TokenUsage(input = 10),
            TokenUsage(output = 7),
        )
        assertEquals(10L, merged?.input)
        assertEquals(7L, merged?.output)
        assertNull(merged?.reasoning)
        assertNull(merged?.cache)
    }

    @Test
    fun `01-F3 a null side keeps the other side whole`() {
        val usage = TokenUsage(input = 10, output = 5)
        assertEquals(usage, mergeTokenUsage(usage, null))
        assertEquals(usage, mergeTokenUsage(null, usage))
        assertNull(mergeTokenUsage(null, null))
    }

    @Test
    fun `01-F3 time takes created from the first chunk and completed from the last`() {
        val merged = mergeMessageTime(
            MessageTime(created = 100, streamed = 110),
            MessageTime(created = 120, streamed = 130, completed = 200),
        )
        assertEquals(100L, merged?.created)
        assertEquals(130L, merged?.streamed)
        assertEquals(200L, merged?.completed)
    }

    @Test
    fun `01-F3 completed falls back to the first chunk when the last has none`() {
        val merged = mergeMessageTime(
            MessageTime(created = 100, completed = 150),
            MessageTime(created = 120),
        )
        assertEquals(100L, merged?.created)
        assertEquals(150L, merged?.completed)
    }

    @Test
    fun `03-F1 live set is the trailing uncompleted assistant run`() {
        fun assistant(id: String, completed: Long? = null) = SessionMessage(
            id = id,
            type = "assistant",
            time = MessageTime(created = 1, completed = completed),
        )
        val history = listOf(
            SessionMessage(id = "u1", type = "user", text = "hi"),
            assistant("a1", completed = 10),
            assistant("a2"),
            assistant("a3"),
        )
        assertEquals(setOf("a2", "a3"), liveStreamingIds(history, emptySet()))
    }

    @Test
    fun `03-F1 a finished run leaves the live set empty`() {
        val history = listOf(
            SessionMessage(id = "u1", type = "user", text = "hi"),
            SessionMessage(id = "a1", type = "assistant", time = MessageTime(created = 1, completed = 10)),
            SessionMessage(id = "i1", type = "idle"),
        )
        assertEquals(emptySet<String>(), liveStreamingIds(history, emptySet()))
    }

    @Test
    fun `03-F1 a streaming id with no history row yet is kept`() {
        val history = listOf(
            SessionMessage(id = "u1", type = "user", text = "hi"),
        )
        assertEquals(setOf("m-new"), liveStreamingIds(history, setOf("m-new")))
    }

    @Test
    fun `03-F1 a finished message in history is dropped even with a streaming entry`() {
        val history = listOf(
            SessionMessage(id = "a1", type = "assistant", time = MessageTime(created = 1, completed = 10)),
        )
        assertEquals(emptySet<String>(), liveStreamingIds(history, setOf("a1")))
    }
}
