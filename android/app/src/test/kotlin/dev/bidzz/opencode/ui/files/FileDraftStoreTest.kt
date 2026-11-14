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
 * A JVM test cannot drive the editing flow itself: the view model starts
 * loading, and only a successful load (a live engine) reaches an
 * editable state. What is tested here is the store's own contract and
 * the wiring a test can reach — discarding and reloading from disk
 * clear the stored draft, a failed load keeps it, and a fresh view model
 * over the same store still finds it. The write path (typing persists
 * the draft through [FileViewViewModel.persistDraft]) and the restore
 * on a successful load use the same restored* fields the handle path
 * already uses, so they are covered by compile + fast-verify.
 */
class FileDraftStoreTest {

    private fun viewModel(store: FileDraftStore = InMemoryFileDraftStore()) =
        FileViewViewModel(EngineApi { "/tmp" }, { "/tmp" }, fileDraftStore = store)

    private fun seededStore(): InMemoryFileDraftStore {
        val store = InMemoryFileDraftStore()
        store.put(FileDraft(path = "a.kt", text = "one\ntwo\n", editing = true, basisHash = "abc"))
        return store
    }

    @Test
    fun `the store round-trips a draft`() {
        val store = InMemoryFileDraftStore()

        store.put(FileDraft(path = "a.kt", text = "one\ntwo\n", editing = true, basisHash = "abc"))

        val draft = store.get()
        assertEquals("a.kt", draft?.path)
        assertEquals("one\ntwo\n", draft?.text)
        assertEquals(true, draft?.editing)
        assertEquals("abc", draft?.basisHash)

        store.clear()
        assertNull(store.get())
    }

    @Test
    fun `discarding clears the stored draft`() {
        val store = seededStore()
        val vm = viewModel(store)

        vm.stopEditing()

        assertNull(store.get())
    }

    @Test
    fun `reload from disk deliberately drops the stored draft`() {
        val store = seededStore()
        val vm = viewModel(store)

        vm.reloadFromDisk()

        assertNull(store.get())
    }

    @Test
    fun `a failed load keeps the stored draft`() {
        val store = seededStore()
        val vm = viewModel(store)

        // The engine is not running, so the load fails before any network
        // call — but the draft must survive it either way.
        vm.load("a.kt")

        assertEquals("one\ntwo\n", store.get()?.text)
    }

    @Test
    fun `a second view model over the same store still finds the draft`() {
        val store = seededStore()

        // A new process constructs a fresh view model over the same store:
        // the draft is still there to restore from, and a load attempt
        // that fails does not clear it.
        val second = viewModel(store)
        second.load("a.kt")

        assertEquals("one\ntwo\n", store.get()?.text)
    }
}
