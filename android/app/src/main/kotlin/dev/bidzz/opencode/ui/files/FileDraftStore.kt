package dev.bidzz.opencode.ui.files

import android.content.Context

/** One unsaved file-editor draft: the file, its text, and the content it was made against. */
data class FileDraft(
    val path: String,
    val text: String,
    val editing: Boolean,
    val basisHash: String?,
)

/**
 * Persists the file-editor draft across process death (audit-200 A9-3,
 * DL-033).
 *
 * The draft used to live only in the saved-state handle: a low-memory
 * kill restores it, but a swipe from recents or a force-stop clears the
 * handle, and all unsaved file edits vanished with no warning. The same
 * draft now lives on disk too, so either kind of death finds it — the
 * handle wins when both have it, because it is the fresher copy.
 *
 * One draft at a time — the editor edits one file — so the record is a
 * single value, not a map; the path is part of the record. Writes use
 * `commit()`, not `apply()`: the draft must be on disk before the call
 * returns, or a death in the flush window loses exactly the edits this
 * store exists to keep.
 */
interface FileDraftStore {
    fun get(): FileDraft?
    fun put(draft: FileDraft)
    fun clear()
}

/** In-memory store: the default for unit tests, and what a view model constructed without a store uses. */
class InMemoryFileDraftStore : FileDraftStore {
    private var draft: FileDraft? = null

    override fun get(): FileDraft? = draft

    override fun put(draft: FileDraft) {
        this.draft = draft
    }

    override fun clear() {
        draft = null
    }
}

/** SharedPreferences-backed store: the production store, passed in by OpenCodeApp. */
class SharedPrefsFileDraftStore(context: Context) : FileDraftStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(): FileDraft? {
        val path = prefs.getString(KEY_PATH, null) ?: return null
        val text = prefs.getString(KEY_TEXT, null) ?: return null
        return FileDraft(
            path = path,
            text = text,
            editing = prefs.getBoolean(KEY_EDITING, false),
            basisHash = prefs.getString(KEY_BASIS, null),
        )
    }

    override fun put(draft: FileDraft) {
        prefs.edit()
            .putString(KEY_PATH, draft.path)
            .putString(KEY_TEXT, draft.text)
            .putBoolean(KEY_EDITING, draft.editing)
            .apply { if (draft.basisHash == null) remove(KEY_BASIS) else putString(KEY_BASIS, draft.basisHash) }
            .commit()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val PREFS_NAME = "file_draft"
        const val KEY_PATH = "path"
        const val KEY_TEXT = "text"
        const val KEY_EDITING = "editing"
        const val KEY_BASIS = "basis"
    }
}
