package dev.bidzz.opencode

import android.content.res.AssetManager
import android.util.Log
import java.io.File

/**
 * Extracts the bundled Node.js runtime assets (`assets/node/`, staged by
 * `script/android/stage-node.sh`) into `files/engine/node/` at first launch.
 *
 * Assets stay inside the APK; the engine's shell cannot read them from there,
 * and npm computes its global prefix from the real path of `npm-cli.js`, so
 * the tree must live on the writable filesystem. The version marker makes the
 * copy idempotent: an update ships a new [VERSION], which re-extracts once.
 *
 * This never throws and never blocks engine startup: if the assets are
 * missing (a build without the staging step) or the copy fails, Node's
 * binary still works — only `npm` is unavailable — and the failure is logged
 * for Diagnostics instead of taking the engine down with it.
 */
object NodeAssets {
    private const val TAG = "NodeAssets"

    /** Bump when the bundled Node/npm versions change. */
    const val VERSION = "node-24.22.2-npm-12.2.2"

    private const val ASSET_ROOT = "node"
    private const val MARKER_NAME = ".node-assets-version"

    /**
     * Copies `assets/node/` to `files/engine/node/` unless the version marker
     * already matches. Returns true when files were (re)written.
     *
     * The marker is only written when `npm-cli.js` landed: on a build without
     * the staging step the copy is a no-op, and the next launch (with staged
     * assets) must retry instead of believing an empty directory is current.
     */
    fun sync(assets: AssetManager, filesDir: File): Boolean {
        val nodeDir = File(filesDir, "engine/node")
        val marker = File(nodeDir, MARKER_NAME)
        if (marker.isFile && runCatching { marker.readText() }.getOrNull() == VERSION) {
            return false
        }
        return try {
            nodeDir.deleteRecursively()
            nodeDir.mkdirs()
            copyDir(assets, ASSET_ROOT, nodeDir)
            val npmCli = File(nodeDir, "lib/node_modules/npm/bin/npm-cli.js")
            if (!npmCli.isFile) {
                Log.w(TAG, "node assets missing npm-cli.js after copy; not marking synced")
                nodeDir.deleteRecursively()
                return false
            }
            marker.writeText(VERSION)
            Log.i(TAG, "node assets synced ($VERSION) to ${nodeDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "node assets sync failed: ${e.message}")
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
