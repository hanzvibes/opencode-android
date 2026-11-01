package dev.bidzz.opencode.workspace

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File

/**
 * Decides which directory the engine treats as the user's project.
 *
 * Two modes, per `plan.md` §09:
 *
 * - **Internal** — app-private storage. Always available, no permission, survives
 *   being denied all-files access. This is the default so the app is useful
 *   immediately instead of opening on a permission wall.
 * - **Shared** — a user-chosen directory on `/storage/emulated/0`, used in place.
 *   Requires all-files access, because the engine needs a real POSIX path and
 *   cannot be handed a `content://` URI.
 *
 * The choice is a path string in `SharedPreferences`. It is validated on read:
 * a directory can disappear (SD card removed, user deleted it, permission
 * revoked) and the app must fall back rather than fail to start.
 */
class WorkspaceStore(private val context: Context) {
    companion object {
        private const val PREFS = "workspace"
        private const val KEY_PATH = "path"
        private const val KEY_RECENT = "recent"

        /** Enough to cover active work without turning the picker into a list. */
        private const val MAX_RECENT = 8

        /** Default project directory when the user has not picked one. */
        fun internalWorkspace(context: Context): File =
            File(context.filesDir, "workspace").apply { mkdirs() }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Path the user selected, or null when using the internal workspace. */
    var selectedPath: String?
        get() = prefs.getString(KEY_PATH, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_PATH) else putString(KEY_PATH, value)
            }.apply()
            if (value != null) rememberRecent(value)
        }

    /**
     * Recently used project directories, newest first.
     *
     * Stored as a newline-separated list. Small enough that a database would be
     * overhead, and a corrupt list degrades to "no recents" rather than a crash.
     */
    fun recentPaths(): List<String> =
        prefs.getString(KEY_RECENT, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private fun rememberRecent(path: String) {
        val updated = (listOf(path) + recentPaths().filter { it != path }).take(MAX_RECENT)
        prefs.edit().putString(KEY_RECENT, updated.joinToString("\n")).apply()
    }

    /**
     * The directory the engine should use.
     *
     * Falls back to the internal workspace whenever the stored path is unusable,
     * and clears the stale selection so the UI does not keep showing a project
     * that is no longer reachable.
     */
    fun resolve(): File {
        val stored = selectedPath ?: return internalWorkspace(context)
        val dir = File(stored)
        val usable = dir.isDirectory && dir.canWrite() && hasAllFilesAccess()
        if (!usable) {
            selectedPath = null
            return internalWorkspace(context)
        }
        return dir
    }

    /** The app-private workspace path, which needs no permission. */
    fun internalPath(): String = internalWorkspace(context).absolutePath

    /**
     * True when the app may read and write shared storage directly.
     *
     * Below API 30 there is no all-files-access concept, so this reports the legacy runtime
     * permission. Returning `true` unconditionally here hid the grant button on Android 10 and
     * left the folder picker with nothing but `Downloads` to offer.
     */
    fun hasAllFilesAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Rejects workspaces that would hand the agent the whole filesystem.
     *
     * A session created with `directory: "/"` is accepted by the engine, and the
     * `shell` tool then writes anywhere (verified in `handoff.md` §2.11). The app
     * must never offer such a directory, so shallow roots are refused here rather
     * than trusted to the UI.
     */
    fun isAcceptableWorkspace(dir: File): Boolean {
        if (!dir.isAbsolute || !dir.isDirectory || !dir.canWrite()) return false
        val path = dir.canonicalPath.trimEnd(File.separatorChar)
        if (path.isEmpty()) return false
        // Depth 0 and 1 are the filesystem root and top-level volumes.
        val depth = path.split(File.separatorChar).count { it.isNotEmpty() }
        return depth >= 2
    }
}
