package dev.bidzz.opencode

import android.content.res.AssetManager
import android.util.Log
import dev.bidzz.opencode.workspace.isAcceptableWorkspacePath
import java.io.File
import java.io.InputStream
import java.nio.file.Files

/**
 * Seeds the agent's runtime guide (`AGENTS.md`) into the workspace.
 *
 * The engine reads `AGENTS.md` from the project directory as its working
 * instructions, so the file is how the agent learns what this device can
 * and cannot do (Node, npm via `node npm-cli.js`, no bash, no git HTTPS).
 *
 * The template lives in `assets/templates/AGENTS.md` and is copied once per
 * workspace, on app start and on every workspace switch. A file the user
 * already has — including one they edited, or an empty one — is never
 * touched: only a missing file is created. Deleting the file brings it back
 * on next launch; that is the documented behaviour, not a bug.
 *
 * Nothing here throws: a failed copy is logged and the app carries on, the
 * same way a missing npm tree only costs npm (see NodeAssets).
 */
object WorkspaceAgentsMd {
    private const val TAG = "WorkspaceAgentsMd"

    const val FILE_NAME = "AGENTS.md"

    private const val ASSET_PATH = "templates/AGENTS.md"

    /**
     * Copies the template into [workspace] when no `AGENTS.md` is there yet.
     * Returns true when the file was written.
     */
    fun ensure(assets: AssetManager, workspace: File): Boolean {
        val written = runCatching {
            assets.open(ASSET_PATH).use { template -> copyIfMissing(template, workspace) }
        }.getOrDefault(false)
        if (written) {
            Log.i(TAG, "AGENTS.md seeded in ${workspace.absolutePath}")
        } else if (!File(workspace, FILE_NAME).exists()) {
            Log.w(TAG, "AGENTS.md not seeded in ${workspace.absolutePath}")
        }
        return written
    }

    /**
     * The pure half of [ensure], kept free of Android classes so it can be
     * tested on the JVM: writes [template] to `AGENTS.md` in [workspace]
     * unless the file already exists. Returns true when written.
     *
     * The write goes through a uniquely-named scratch file renamed into
     * place, so a failed copy never leaves a half-written guide behind to
     * be mistaken for a complete one — and the scratch name can never
     * collide with a user's file (audit-70 42-F1). The target is re-checked
     * right before the rename, so a file that appeared mid-copy wins over
     * the seed instead of being replaced by it (audit-70 42-F3).
     */
    internal fun copyIfMissing(template: InputStream, workspace: File): Boolean {
        val canonical = runCatching {
            workspace.canonicalPath.trimEnd(File.separatorChar)
        }.getOrNull() ?: return false
        if (!isAcceptableWorkspacePath(canonical)) return false
        val target = File(workspace, FILE_NAME)
        if (targetExists(target)) return false
        val tmp = runCatching {
            File.createTempFile("$FILE_NAME.", ".tmp", workspace)
        }.getOrNull() ?: return false
        return runCatching {
            template.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (targetExists(target) || !tmp.renameTo(target)) {
                tmp.delete()
                false
            } else {
                true
            }
        }.onFailure { tmp.delete() }.getOrDefault(false)
    }

    /**
     * True when [target] exists in any form, including a dangling symlink:
     * [File.exists] follows the link and answers false for a dangling one,
     * and seeding over it would replace the user's link with a regular file
     * (audit-70 42-F2).
     */
    private fun targetExists(target: File): Boolean =
        target.exists() || Files.isSymbolicLink(target.toPath())
}
