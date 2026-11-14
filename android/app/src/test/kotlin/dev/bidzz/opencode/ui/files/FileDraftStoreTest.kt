package dev.bidzz.opencode.ui.files

import dev.bidzz.opencode.api.EngineApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Audit-200 Fase A9-3 (DL-033): the file-editor draft lives on disk too,
 * so a swipe from recents or a force-stop — which clears the saved-state
 * handle — still finds it.
 *
 * The restore itself is applied on a successful load through the same
 * restored* fields the handle path already uses, so it is covered by
 * compile + fast-verify; what is tested here is the disk behaviour a
 * JVM test can see — persisting without a handle (the old code returned
 * early there), discarding, and surviving a failed load.
 */
class FileDraftStoreTest {

    private fun viewModel(store: FileDraftStore = InMemoryFileDraftStore()) =
        FileViewViewModel(EngineApi { "/tmp" }, { "/tmp" }, fileDraftStore = store)

    @Test
    fun `typing persists the draft to the store even without a handle`() {
        val store = InMemoryFileDraftStore()
        val vm = viewModel(store)

        vm.startEditing()
        vm.editDraft("one\ntwo\n")

        val draft = store.get()
        assertEquals("", draft?.path)
        assertEquals("one\ntwo\n", draft?.text)
        assertEquals(true, draft?.editing)
    }

    @Test
    fun `discarding clears the store`() {
        val store = InMemoryFileDraftStore()
        val vm = viewModel(store)
        vm.startEditing()
        vm.editDraft("one\ntwo\n")

        vm.stopEditing()

        assertNull(store.get())
    }

    @Test
    fun `a failed load keeps the stored draft`() {
        val store = InMemoryFileDraftStore()
        val vm = viewModel(store)
        vm.startEditing()
        vm.editDraft("one\ntwo\n")

        // The engine is not running, so the load fails before any network
        // call — but the draft must survive it either way.
        vm.load("a.kt")

        assertEquals("one\ntwo\n", store.get()?.text)
    }

    @Test
    fun `reload from disk deliberately drops the stored draft`() {
        val store = InMemoryFileDraftStore()
        val vm = viewModel(store)
        vm.startEditing()
        vm.editDraft("one\ntwo\n")

        vm.reloadFromDisk()

        assertNull(store.get())
    }

    @Test
    fun `a second view model sees the stored draft`() {
        val store = InMemoryFileDraftStore()
        val first = viewModel(store)
        first.startEditing()
        first.editDraft("one\ntwo\n")

        // A new process constructs a fresh view model over the same store:
        // the draft is still there to restore from, and a load attempt
        // that fails does not clear it.
        val second = viewModel(store)
        second.load("a.kt")

        assertEquals("one\ntwo\n", store.get()?.text)
    }
}
