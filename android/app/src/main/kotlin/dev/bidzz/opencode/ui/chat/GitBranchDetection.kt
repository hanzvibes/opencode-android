package dev.bidzz.opencode.ui.chat

import java.io.File

/**
 * Detects the active Git branch or commit hash for a workspace path.
 *
 * Reads `.git/HEAD` directly from disk so git state is resolved instantly without
 * running a process or relying on engine RPC. Supports regular repositories as well as
 * worktrees and submodules that point to a repository via a `gitdir: <path>` file.
 *
 * Returns the short branch name (e.g. `main`), short commit hash, or null if the
 * workspace is not a git repository or cannot be read.
 */
fun detectGitBranch(workspacePath: String?): String? {
    if (workspacePath.isNullOrBlank()) return null
    return try {
        val root = File(workspacePath)
        val git = File(root, ".git")
        when {
            git.isDirectory -> {
                val head = File(git, "HEAD")
                if (head.exists()) parseGitHead(head.readText()) else null
            }
            git.isFile -> {
                // Worktree or submodule pointer
                val content = git.readText().trim()
                if (content.startsWith("gitdir:")) {
                    val targetPath = content.removePrefix("gitdir:").trim()
                    val targetFile = File(targetPath)
                    val realGitDir = if (targetFile.isAbsolute) targetFile else File(root, targetPath)
                    val head = File(realGitDir, "HEAD")
                    if (head.exists()) parseGitHead(head.readText()) else null
                } else null
            }
            else -> null
        }
    } catch (_: Throwable) {
        null
    }
}

internal fun parseGitHead(raw: String): String {
    val line = raw.trim()
    return when {
        line.startsWith("ref: refs/heads/") -> line.removePrefix("ref: refs/heads/").trim()
        line.isNotEmpty() -> line.take(7)
        else -> "HEAD"
    }
}
