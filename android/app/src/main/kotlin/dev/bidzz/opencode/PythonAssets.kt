package dev.bidzz.opencode

import android.content.res.AssetManager
import android.util.Log
import java.io.File

/**
 * Extracts the bundled Python runtime assets (`assets/python/`, staged by
 * `script/android/stage-python.sh`) into `files/engine/python/` at first launch.
 *
 * Assets stay inside the APK; the engine's shell cannot read them from there,
 * and CPython computes its stdlib location from PYTHONHOME (see
 * EngineEnvironment), so the tree must live on the writable filesystem. The
 * version marker makes the copy idempotent: an update ships a new [VERSION],
 * which re-extracts once.
 *
 * This never throws and never blocks engine startup: if the assets are
 * missing (a build without the staging step) or the copy fails, the engine
 * still runs -- only `python3` is unavailable -- and the failure is logged
 * for Diagnostics instead of taking the engine down with it.
 *
 * The tree contains `lib/python3.14/` (stdlib + pip in site-packages +
 * lib-dynload extension modules). The `libpython3.14.so` interpreter library
 * itself ships as a native library (jniLibs), not here.
 */
object PythonAssets {
    private const val TAG = "PythonAssets"

    /** Bump when the bundled Python/pip versions change. */
    const val VERSION = "python-3.14.10-pip-26.3"

    private const val ASSET_ROOT = "python"
    private const val MARKER_NAME = ".python-assets-version"

    /**
     * Copies `assets/python/` to `files/engine/python/` unless the version
     * marker already matches. Returns true when files were (re)written.
     *
     * The marker is only written when the stdlib sentinel (`os.py`) landed:
     * on a build without the staging step the copy is a no-op, and the next
     * launch (with staged assets) must retry instead of believing an empty
     * directory is current.
     */
    fun sync(assets: AssetManager, filesDir: File): Boolean {
        val pythonDir = File(filesDir, "engine/python")
        val marker = File(pythonDir, MARKER_NAME)
        if (marker.isFile && runCatching { marker.readText() }.getOrNull() == VERSION) {
            return false
        }
        return try {
            pythonDir.deleteRecursively()
            pythonDir.mkdirs()
            copyDir(assets, ASSET_ROOT, pythonDir)
            val sentinel = File(pythonDir, "lib/python3.14/os.py")
            if (!sentinel.isFile) {
                Log.w(TAG, "python assets missing lib/python3.14/os.py after copy; not marking synced")
                pythonDir.deleteRecursively()
                return false
            }
            marker.writeText(VERSION)
            Log.i(TAG, "python assets synced ($VERSION) to ${pythonDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "python assets sync failed: ${e.message}")
            false
        }
    }

    private fun copyDir(assets: AssetManager, assetPath: String, destDir: File) {
        val children = assets.list(assetPath) ?: return
        for (child in children) {
            val childAssetPath = "$assetPath/$child"
            val childDest = File(destDir, child)
            // assets.list() returns an empty array for files, so an empty
            // result means a file to copy; a non-empty result means a dir.
            // An empty directory also reports empty, so a failed open falls
            // back to treating it as a directory.
            val grandchildren = assets.list(childAssetPath)
            if (grandchildren.isNullOrEmpty()) {
                try {
                    assets.open(childAssetPath).use { input ->
                        childDest.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (_: java.io.FileNotFoundException) {
                    childDest.mkdirs()
                    copyDir(assets, childAssetPath, childDest)
                }
            } else {
                childDest.mkdirs()
                copyDir(assets, childAssetPath, childDest)
            }
        }
    }
}
