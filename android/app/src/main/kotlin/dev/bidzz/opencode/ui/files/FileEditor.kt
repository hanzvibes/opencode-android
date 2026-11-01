package dev.bidzz.opencode.ui.files

/**
 * What pressing save means for the file on disk.
 *
 * The agent writes the same files the editor shows, and it does so while the user is
 * typing. A save that always wrote the draft would silently delete whatever the agent
 * produced in the meantime, so the decision is taken from all three versions — what the
 * editor loaded, what the user typed, and what the file holds now — before any write.
 */
enum class SaveAction {
    /**
     * The draft equals what was loaded, so nothing is written.
     *
     * This wins over every other case: a draft typed and then undone is byte-identical
     * to the version that was loaded, and writing it would overwrite the agent's newer
     * content with the older text the editor still holds.
     */
    Unchanged,

    /** The file is still what the editor loaded, and the draft differs from it. */
    Write,

    /** Something changed the file since it was loaded. */
    Conflict,

    /**
     * The current content could not be read, so a change underneath cannot be ruled
     * out. Refusing here costs one retry; guessing wrong costs the agent's work.
     */
    Unverifiable,
}

/**
 * [original] is what the editor loaded, [draft] what is in the field now, and
 * [onDisk] what the file holds at save time — null when that could not be read.
 *
 * [overwrite] is the user's answer to a conflict, never a default.
 */
fun saveAction(original: String, draft: String, onDisk: String?, overwrite: Boolean = false): SaveAction = when {
    draft == original -> SaveAction.Unchanged
    onDisk == null -> SaveAction.Unverifiable
    onDisk != original && !overwrite -> SaveAction.Conflict
    else -> SaveAction.Write
}
