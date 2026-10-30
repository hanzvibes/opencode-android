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

        /** Tool binaries are shipped as libraries too, then linked into a bin dir. */
        val TOOL_LIBRARIES = listOf("librg.so", "libgit.so")

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
         * The engine invokes them as `rg` and `git`. Native libraries must be named
         * `lib*.so`, so the packaged files cannot be called `rg` directly. Instead
         * of copying the binaries (which costs tens of MB and a startup delay), we
         * create hard links in a bin directory on PATH. Hard links are unavailable
         * on FUSE but app-private storage is ext4, so this works; if it fails we
         * fall back to a copy.
         */
        private fun linkTools(nativeLibraryDir: File, binDir: File) {
            if (!binDir.exists() && !binDir.mkdirs()) {
                throw EngineStartupException("Cannot create tool bin dir: ${binDir.absolutePath}")
            }
            val mapping = mapOf(
                "librg.so" to "rg",
                "libgit.so" to "git",
            )
            for ((libName, toolName) in mapping) {
                val source = File(nativeLibraryDir, libName)
                if (!source.exists()) continue
                val target = File(binDir, toolName)
                if (target.exists()) continue
                val linked = try {
                    java.nio.file.Files.createLink(target.toPath(), source.toPath())
                    true
                } catch (_: Throwable) {
                    false
                }
                if (!linked) {
                    source.copyTo(target, overwrite = true)
                    target.setExecutable(true)
                }
            }
        }
    }
}
