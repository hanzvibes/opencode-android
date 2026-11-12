package dev.bidzz.opencode.ui.sessions

import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 5 (19-F7/65-F1/80-F2): a failed delete restores the row and
 * reports itself as a banner, never as the screen's full-screen error.
 *
 * The decision is pure ([resolveDeleteFailure]) so it is testable
 * without a live engine; the clear methods are plain state copies.
 * [SessionListViewModel.delete] itself runs the network call inside a
 * coroutine the JVM unit tests cannot drive (no Main dispatcher on the
 * test classpath).
 */
class SessionDeleteFailureTest {

    private fun row(id: String, title: String = "Sesi", pinned: Boolean = false) =
        SessionRow(id = id, title = title, directory = "/tmp", age = null, cost = null, isPinned = pinned)

    private fun failure() = ApiResult.Failure(code = 500, message = "engine error")

    private fun state(vararg rows: SessionRow) =
        SessionListUiState(loading = false, rows = rows.toList())

    @Test
    fun `19-F7 failed delete restores the row and never sets the full-screen failure`() {
        val before = state(row("a"), row("b", pinned = true), row("c"))
        // The row was already removed optimistically, like delete() does.
        val removed = state(row("a"), row("c"))

        val after = resolveDeleteFailure(removed, row("b", pinned = true), 1, "b", failure())

        assertEquals(listOf("a", "b", "c"), after.rows.map { it.id })
        assertTrue(after.rows[1].isPinned)
        assertEquals(failure(), after.deleteFailure)
        assertEquals("b", after.deleteFailedSessionID)
        assertNull(after.failure)
        assertEquals(before.rows, after.rows)
    }

    @Test
    fun `19-F7 a failed delete never duplicates a row that is already back`() {
        val current = state(row("a"), row("b"))

        val after = resolveDeleteFailure(current, row("b"), 1, "b", failure())

        assertEquals(listOf("a", "b"), after.rows.map { it.id })
    }

    @Test
    fun `19-F7 an out-of-range index clamps instead of crashing`() {
        val current = state(row("a"))

        val after = resolveDeleteFailure(current, row("b"), 99, "b", failure())

        assertEquals(listOf("a", "b"), after.rows.map { it.id })
    }

    @Test
    fun `19-F7 the clear methods leave a fresh view model untouched`() {
        // The banner state can only be seeded by delete() itself, which
        // needs a live engine; what is testable here is that clearing is
        // a plain state copy that never touches the rows.
        val vm = SessionListViewModel(EngineApi { "/tmp" }, { "/tmp" })

        vm.clearDeleteFailure()
        vm.clearLoadFailure()

        assertNull(vm.state.value.deleteFailure)
        assertNull(vm.state.value.deleteFailedSessionID)
        assertNull(vm.state.value.failure)
        assertNull(vm.state.value.failureKey)
        assertTrue(vm.state.value.rows.isEmpty())
    }

    @Test
    fun `80-F2 a load failure keeps its own field, separate from a delete failure`() {
        val loaded = state(row("a")).copy(failure = failure())

        val after = resolveDeleteFailure(loaded, row("b"), 1, "b", failure())

        assertEquals(failure(), after.failure)
        assertEquals(failure(), after.deleteFailure)
    }
}
