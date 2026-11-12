package dev.bidzz.opencode.ui.files

import androidx.lifecycle.SavedStateHandle
import dev.bidzz.opencode.api.EngineApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The save/draft safety fixes (audit-100 Fase 1b): typing that lands while a
 * save runs is kept, a save's result never lands in another file's viewer,
 * discarding cancels an in-flight save, a held file switch survives an
 * "Unchanged" save, a loading retry keeps a restored draft, and a draft
 * restored on top of newer disk content is flagged stale.
 *
 * The pure decisions are tested directly with synthetic states, following the
 * ChatViewModelTest pattern; the SavedStateHandle behaviour is tested through
 * the view model with an engine that is not running (load bails out before
 * any network call, which is exactly the path that must preserve the draft).
 */
class FileViewSaveTest {

    private fun snapshot() = FileViewState(
        path = "a.kt",
        content = "one\n",
        editing = true,
        draft = "one\ntwo\n",
    )

    // resolveSaveSuccess (35-F1, 35-F3)

    @Test
    fun `an untouched draft clears and the file is marked saved`() {
        val now = snapshot().copy(saving = true)

        val resolved = resolveSaveSuccess(now, snapshot())

        assertEquals(false, resolved!!.saving)
        assertEquals(false, resolved.editing)
        assertEquals("one\ntwo\n", resolved.content)
        assertEquals("", resolved.draft)
        assertEquals(true, resolved.saved)
        assertEquals(false, resolved.draftStale)
    }

    @Test
    fun `typing that landed during the save is kept and editing continues`() {
        val now = snapshot().copy(saving = true, draft = "one\ntwo\nthree\n")

        val resolved = resolveSaveSuccess(now, snapshot())

        assertEquals(false, resolved!!.saving)
        assertEquals(true, resolved.editing)
        assertEquals("one\ntwo\n", resolved.content)
        assertEquals("one\ntwo\nthree\n", resolved.draft)
        assertEquals(false, resolved.saved)
    }

    @Test
    fun `typing during the save keeps the stale flag`() {
        val now = snapshot().copy(saving = true, draft = "one\ntwo\nthree\n", draftStale = true)

        val resolved = resolveSaveSuccess(now, snapshot())

        assertTrue(resolved!!.draftStale)
        assertTrue(resolved.editing)
    }

    @Test
    fun `a save result for another file is dropped, not applied to the viewer`() {
        val now = snapshot().copy(path = "b.kt", saving = true)

        assertNull(resolveSaveSuccess(now, snapshot()))
    }

    // resolveSaveStart (89-F3)

    @Test
    fun `a stale draft needs an explicit overwrite`() {
        val stale = snapshot().copy(draftStale = true)

        assertEquals(SaveStart.StaleConflict, resolveSaveStart(stale, overwrite = false))
        assertEquals(SaveStart.Proceed, resolveSaveStart(stale, overwrite = true))
        assertEquals(SaveStart.Proceed, resolveSaveStart(snapshot(), overwrite = false))
    }

    // resolveRestoreDecision (89-F3)

    @Test
    fun `a draft restores when the disk still holds its basis`() {
        val basis = sha256Hex("one\n")

        val decision = resolveRestoreDecision(
            restoredPath = "a.kt",
            restoredEditing = true,
            truncated = false,
            path = "a.kt",
            diskContent = "one\n",
            basisHash = basis,
        )

        assertEquals(RestoreDecision.Apply, decision)
    }

    @Test
    fun `a draft restores as stale when the file moved on`() {
        val basis = sha256Hex("one\n")

        val decision = resolveRestoreDecision(
            restoredPath = "a.kt",
            restoredEditing = true,
            truncated = false,
            path = "a.kt",
            diskContent = "one\nagent edit\n",
            basisHash = basis,
        )

        assertEquals(RestoreDecision.StaleConflict, decision)
    }

    @Test
    fun `a draft for another file, a non-editing restore, or a truncated preview is skipped`() {
        val basis = sha256Hex("one\n")
        val args = { path: String, editing: Boolean, truncated: Boolean ->
            resolveRestoreDecision("a.kt", editing, truncated, path, "one\n", basis)
        }

        assertEquals(RestoreDecision.Skip, args("b.kt", true, false))
        assertEquals(RestoreDecision.Skip, args("a.kt", false, false))
        assertEquals(RestoreDecision.Skip, args("a.kt", true, true))
    }

    @Test
    fun `a handle written before basis tracking still restores`() {
        val decision = resolveRestoreDecision(
            restoredPath = "a.kt",
            restoredEditing = true,
            truncated = false,
            path = "a.kt",
            diskContent = "one\nagent edit\n",
            basisHash = null,
        )

        assertEquals(RestoreDecision.Apply, decision)
    }

    // sha256Hex

    @Test
    fun `the basis hash is stable, 64 hex chars, and changes with the content`() {
        val first = sha256Hex("one\n")
        val second = sha256Hex("one\n")
        val other = sha256Hex("one\ntwo\n")

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(first == other)
    }

    // retryLoad vs reloadFromDisk (35-F10 / 89-F2)

    private fun viewModelWithRestoredDraft(): Pair<FileViewViewModel, SavedStateHandle> {
        val handle = SavedStateHandle(
            mapOf<String, Any?>(
                FileViewViewModel.FILE_DRAFT_PATH_KEY to "a.kt",
                FileViewViewModel.FILE_DRAFT_TEXT_KEY to "one\ntwo\n",
                FileViewViewModel.FILE_DRAFT_EDITING_KEY to true,
            ),
        )
        val vm = FileViewViewModel(EngineApi { "/tmp" }, { "/tmp" }, savedStateHandle = handle)
        // The engine is not running, so the load fails before any network call —
        // but the restored draft must survive it either way.
        vm.load("a.kt")
        return vm to handle
    }

    @Test
    fun `a loading retry keeps the restored draft`() {
        val (vm, handle) = viewModelWithRestoredDraft()

        vm.retryLoad()

        assertTrue(handle.keys().contains(FileViewViewModel.FILE_DRAFT_TEXT_KEY))
        assertEquals("one\ntwo\n", handle.get<String>(FileViewViewModel.FILE_DRAFT_TEXT_KEY))
    }

    @Test
    fun `reload from disk deliberately drops the restored draft`() {
        val (vm, handle) = viewModelWithRestoredDraft()

        vm.reloadFromDisk()

        assertFalse(handle.keys().contains(FileViewViewModel.FILE_DRAFT_TEXT_KEY))
    }

    @Test
    fun `discarding bumps the save generation so an in-flight save is cancelled`() {
        val vm = FileViewViewModel(EngineApi { "/tmp" }, { "/tmp" })
        val before = vm.saveGeneration

        vm.stopEditing()

        assertEquals(before + 1, vm.saveGeneration)
    }
}
