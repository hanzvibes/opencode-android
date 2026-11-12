package dev.bidzz.opencode

import android.content.res.AssetManager
import android.util.Log
import java.io.File

/**
 * Extracts the bundled language servers (`assets/lsp/`, staged by
 * `script/android/stage-lsp.sh`) into `files/engine/lsp/` at first launch.
 *
 * Assets stay inside the APK; the engine's `lsp` config points at absolute
 * paths under `files/engine/lsp/`, so the tree must live on the writable
 * filesystem. The version marker makes the copy idempotent: an update ships
 * a new [VERSION], which re-extracts once.
 *
 * This never throws and never blocks engine startup: if the assets are
 * missing (a build without the staging step) or the copy fails, the engine
 * still runs -- only the bundled servers are unavailable -- and the failure
 * is logged for Diagnostics instead of taking the engine down with it.
 *
 * The tree contains `lib/node_modules/` (npm layout):
 * `typescript-language-server` (+ `typescript`, which it resolves from the
 * same tree) and `pyright` (the Node implementation; it does not need the
 * bundled python3). The servers run under the bundled Node, invoked as
 * `node <files>/engine/lsp/lib/node_modules/... --stdio` (see
 * engine/README.md for the opencode.json snippet).
 */
object LspAssets {
    private const val TAG = "LspAssets"

    /** Bump when any bundled language server version changes. */
    const val VERSION = "lsp-tsserver-6.0.3-ts-5.9.3-pyright-1.1.414"

    private const val ASSET_ROOT = "lsp"
    private const val MARKER_NAME = ".lsp-assets-version"

    /**
     * Copies `assets/lsp/` to `files/engine/lsp/` unless the version marker
     * already matches. Returns true when files were (re)written.
     *
     * The marker is only written when the TypeScript server entry point
     * landed: on a build without the staging step the copy is a no-op, and
     * the next launch (with staged assets) must retry instead of believing
     * an empty directory is current.
     */
    fun sync(assets: AssetManager, filesDir: File): Boolean {
        val lspDir = File(filesDir, "engine/lsp")
        val marker = File(lspDir, MARKER_NAME)
        if (marker.isFile && runCatching { marker.readText() }.getOrNull() == VERSION) {
            return false
        }
        return try {
            lspDir.deleteRecursively()
            lspDir.mkdirs()
            copyDir(assets, ASSET_ROOT, lspDir)
            val sentinel = File(lspDir, "lib/node_modules/typescript-language-server/lib/cli.mjs")
            if (!sentinel.isFile) {
                Log.w(TAG, "lsp assets missing typescript-language-server cli.mjs after copy; not marking synced")
                lspDir.deleteRecursively()
                return false
            }
            marker.writeText(VERSION)
            Log.i(TAG, "lsp assets synced ($VERSION) to ${lspDir.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "lsp assets sync failed: ${e.message}")
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
