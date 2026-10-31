package dev.bidzz.opencode.core.engine

import java.io.File

/**
 * Locates the engine executable on the device.
 *
 * Since Android 10, executing a file from app-private storage fails with `EACCES`
 * even after `chmod +x` — the kernel refuses `execve` on files in writable
 * directories for apps. The only exec-allowed location is the directory the
 * package manager extracted native libraries into.
 *
 * Verified on Android 16: a binary in `filesDir` produced
 * `error=13, Permission denied`, while the same binary in `nativeLibraryDir`
 * ran normally. See `handoff.md` §1.4.
 *
 * The binary is therefore shipped as `jniLibs/<abi>/libopencode.so` and used from
 * `nativeLibraryDir` directly. No extraction, no chmod.
 */
class EngineBinary private constructor(
    val executable: File,
    val toolBinDir: File,
) {
    companion object {
        /** Package manager renames `libopencode.so` to `libopencode.so`; keep one constant. */
        const val LIBRARY_NAME = "libopencode.so"

        /**
         * Tool binaries are shipped as libraries too, then linked into a bin dir.
         *
         * Single source of truth: `linkTools` derives its work from this map, so a
         * tool cannot be listed here and silently skipped at link time. The engine
         * resolves both through `which(<name>)` against the PATH entry that
         * `EngineEnvironment` points at this directory.
         */
        val TOOL_LIBRARIES: Map<String, String> = mapOf(
            "librg.so" to "rg",
            "libgit.so" to "git",
        )

        fun resolve(nativeLibraryDir: File, filesDir: File): EngineBinary {
            val engine = File(nativeLibraryDir, LIBRARY_NAME)
            if (!engine.exists()) {
                throw EngineStartupException(
                    "Engine binary missing at ${engine.absolutePath}. " +
                        "Expected the build to package it as jniLibs/<abi>/$LIBRARY_NAME.",
                )
            }
            if (!engine.canExecute()) {
                // On a correct install this never happens; if it does, the file was
                // placed in the wrong directory or the APK was built without
                // useLegacyPackaging, so report it instead of trying chmod.
                throw EngineStartupException(
                    "Engine binary is not executable at ${engine.absolutePath}. " +
                        "This usually means it was not installed as a native library.",
                )
            }

            val toolBin = File(filesDir, "engine/tools/bin")
            linkTools(nativeLibraryDir, toolBin)

            return EngineBinary(engine, toolBin)
        }

        /**
         * Exposes `rg` and `git` under their real names.
         *
         * The engine invokes them as `rg` and `git`, but native libraries must be
         * named `lib*.so`, so the packaged files cannot carry those names.
         *
         * A symlink is used rather than a hard link. This is load-bearing: files
         * in `filesDir` carry the `app_data_file` SELinux label, and `execve` on
         * that label is denied for `untrusted_app` even when the mode bits are
         * `0755` — the same W^X rule that forces the engine itself into
         * `nativeLibraryDir` (handoff.md §2.4). A hard link inherits the label of
         * its source, so a hard-linked `git` fails with EACCES (exit 126). A
         * symlink keeps the label of the target (`apk_data_file`), which is
         * exec-allowed, and resolving through it works.
         *
         * Verified on the emulator: hard link -> `Permission denied`, symlink ->
         * `git version 2.56.0`.
         */
        private fun linkTools(nativeLibraryDir: File, binDir: File) {
            if (!binDir.exists() && !binDir.mkdirs()) {
                throw EngineStartupException("Cannot create tool bin dir: ${binDir.absolutePath}")
            }
            for ((libName, toolName) in TOOL_LIBRARIES) {
                val source = File(nativeLibraryDir, libName)
                if (!source.exists()) continue
                val target = File(binDir, toolName)
                if (isCurrent(source, target)) continue

                // Delete before creating: an existing entry makes both createSymbolicLink
                // and createLink fail with EEXIST, which would silently keep a stale
                // binary from a previous APK version in place.
                target.delete()
                val linked = try {
                    java.nio.file.Files.createSymbolicLink(target.toPath(), source.toPath())
                    true
                } catch (_: Throwable) {
                    false
                }
                if (!linked) {
                    // Filesystems that reject symlinks fall back to a copy. The copy
                    // lands in filesDir and therefore will NOT be executable by the
                    // engine; this is a last resort that at least keeps the file
                    // visible for diagnostics.
                    source.copyTo(target, overwrite = true)
                    target.setExecutable(true)
                }
            }
        }

        /**
         * True when `target` already points at `source` and can be executed.
         *
         * Compares the resolved target rather than size/mtime: for a symlink,
         * `length()` reports the link text, not the binary, so a size comparison
         * would always consider it stale and rewrite it on every start.
         */
        private fun isCurrent(source: File, target: File): Boolean {
            if (!target.exists() || !target.canExecute()) return false
            val resolved = try {
                target.toPath().toRealPath()
            } catch (_: Throwable) {
                return false
            }
            return resolved == source.toPath().toRealPath()
        }
    }
}
