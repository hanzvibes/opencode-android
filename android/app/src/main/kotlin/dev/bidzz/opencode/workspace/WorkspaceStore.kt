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
     * The last stored path [resolve] found unusable, or null.
     *
     * In memory only: it is a note for the current launch ("your project
     * folder is unreachable for now"), not a second copy of the selection.
     * The selection itself is never cleared by a fallback (item 12) — a
     * transient failure (SD card unmounted, permission toggled off and on)
     * must not delete the project the user picked.
     */
    var lastUnavailablePath: String? = null
        private set

    /**
     * The directory the engine should use.
     *
     * Falls back to the internal workspace whenever the stored path is unusable,
     * and remembers the stored path in [lastUnavailablePath] so the UI can say
     * so. The selection is never cleared here (item 12): a transient failure
     * must not delete the project the user picked — it may be back on the
     * next launch, and re-picking it by hand is the user's call, not the
     * fallback's.
     */
    fun resolve(): File {
        val stored = selectedPath ?: run {
            lastUnavailablePath = null
            return internalWorkspace(context)
        }
        val dir = File(stored)
        // A stored storage root is refused the same way a fresh pick is
        // (item 30): it may have been saved before the guard existed.
        val usable = dir.isDirectory && dir.canWrite() && hasAllFilesAccess() && isAcceptableWorkspace(dir)
        if (!usable) {
            lastUnavailablePath = stored
            return internalWorkspace(context)
        }
        lastUnavailablePath = null
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
        return isAcceptableWorkspacePath(path)
    }
}

/**
 * The path-only half of [WorkspaceStore.isAcceptableWorkspace], kept as a
 * free function so it can be tested directly: the member only adds the
 * filesystem checks (exists, writable) and the canonicalisation.
 *
 * A storage root is not a project (item 30): the agent would see every
 * photo, download and other app's file. The roots themselves are refused;
 * the folders inside them stay allowed.
 */
internal fun isAcceptableWorkspacePath(canonicalPath: String): Boolean {
    if (canonicalPath.isEmpty()) return false
    if (isStorageRoot(canonicalPath)) return false
    // Depth 0 and 1 are the filesystem root and top-level volumes.
    val depth = canonicalPath.split(File.separatorChar).count { it.isNotEmpty() }
    return depth >= 2
}

/**
 * True for the shared-storage roots themselves — not for the folders
 * inside them. `/sdcard` canonicalises to `/storage/emulated/0`, so it
 * is covered without a special case; a volume root is exactly one
 * segment under `/storage` (an SD card's UUID).
 */
internal fun isStorageRoot(canonicalPath: String): Boolean {
    if (canonicalPath == "/storage/emulated" || canonicalPath == "/storage/emulated/0") return true
    val rest = canonicalPath.removePrefix("/storage/")
    return canonicalPath.startsWith("/storage/") && !rest.contains(File.separatorChar)
}
}
