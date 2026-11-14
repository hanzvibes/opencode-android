package dev.bidzz.opencode

import android.content.res.AssetManager
import java.io.File

/**
 * Shared helpers for the runtime asset syncs ([NodeAssets], [LspAssets],
 * [PythonAssets]) — audit-200 A9-9, DL-038.
 *
 * The syncs used to `deleteRecursively()` the whole tree before
 * re-copying on a `VERSION` bump. But the trees are also where the user
 * installs things: npm computes its global prefix from the real path of
 * `npm-cli.js`, so `npm install -g <paket>` lands inside
 * `files/engine/node/` (and `pip install` inside
 * `files/engine/python/`). Every version bump wiped those packages with
 * no backup and no log. The syncs now delete only the paths the bundle
 * itself ships ([deleteOwnedPaths]) plus the version marker, and leave
 * everything else in place. Files the new bundle no longer ships linger
 * instead — they are inert (nothing references them) and the marker
 * still says which version the tree is.
 */

/**
 * The bundle's file paths under [assetPath], relative to it, in the same
 * traversal order as the syncs' `copyDir`. Uses the same heuristic:
 * `assets.list()` returns an empty array for files, so an empty result
 * means a file. An empty directory reports empty too and lands on the
 * list — deleting it is harmless (see [deleteOwnedPaths]) and the copy
 * recreates it.
 */
internal fun listAssetFiles(assets: AssetManager, assetPath: String): List<String> {
    val out = mutableListOf<String>()
    collectAssetFiles(assets, assetPath, "", out)
    return out
}

private fun collectAssetFiles(
    assets: AssetManager,
    assetPath: String,
    prefix: String,
    out: MutableList<String>,
) {
    val children = assets.list(assetPath) ?: return
    for (child in children) {
        val childAssetPath = "$assetPath/$child"
        val rel = if (prefix.isEmpty()) child else "$prefix/$child"
        if (assets.list(childAssetPath).isNullOrEmpty()) {
            out += rel
        } else {
            collectAssetFiles(assets, childAssetPath, rel, out)
        }
    }
}

/**
 * Deletes exactly the files [relativePaths] names under [root], and
 * returns the ones that could not be deleted. `File.delete()` removes a
 * file, or an empty directory; a non-empty directory is left in place
 * and reported, so a path the bundle ships as a file but the user filled
 * with their own files is never wiped — the caller's sentinel check then
 * fails and the copy retries on the next launch instead of deleting
 * them. A path that does not exist is a no-op.
 */
internal fun deleteOwnedPaths(root: File, relativePaths: Iterable<String>): List<File> {
    val refused = mutableListOf<File>()
    for (rel in relativePaths) {
        val target = File(root, rel)
        if (target.exists() && !target.delete()) refused += target
    }
    return refused
}
