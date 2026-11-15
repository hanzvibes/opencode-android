package dev.bidzz.opencode.ui.chat

/**
 * Save for a fenced code block (Fase 1, item 4).
 *
 * Save goes through the system picker (SAF `CreateDocument`, launched from
 * MainActivity like the attach picker's `OpenDocument` — a Compose launcher
 * in chat crashed for want of an ActivityResultRegistryOwner), so the user
 * picks where the file lands. The suggested filename is `snippet.<ext>`
 * from the fence language; the code itself is saved verbatim.
 *
 * There is deliberately no Run button here for non-shell code. The doc lists shell endpoints
 * (`POST /api/session/{id}/shell`, `POST /api/shell`), but it gives no
 * request-body schema for either and the client has no shell method, so
 * the request shape would be guessed — and running arbitrary
 * model-written code from a chat bubble, with no sandbox or allowlist,
 * is not a button to add on a guess. Save is the safe half; Run stays
 * unimplemented until a verified, gated execution path exists.
 *
 * That path now exists for shell fences (audit-200 C14, M-196): the
 * request body is verified against the engine fork source at pin
 * fedc6f20 (`Shell.CreateInput`: `{command, cwd?, timeout?,
 * metadata?}`), and Run is allowlisted to shell fences only
 * ([isRunnableCodeBlock]) behind a confirmation dialog that shows the
 * exact command. Anything else stays Save-only.
 */
fun codeBlockFileName(language: String?): String {
    val raw = language?.trim()?.substringBefore(' ')?.substringBefore(':')?.lowercase()
        .orEmpty()
    // Audit-200 D4 (m-076): "docker" is a Dockerfile by convention, like
    // "dockerfile" above — it used to fall through to snippet.txt.
    if (raw == "dockerfile" || raw == "docker") return "Dockerfile"
    val ext = when (raw) {
        "kt", "kts", "kotlin" -> "kt"
        "py", "python" -> "py"
        "js", "javascript", "mjs" -> "js"
        "ts", "typescript" -> "ts"
        "tsx" -> "tsx"
        "jsx" -> "jsx"
        "rs", "rust" -> "rs"
        "go", "golang" -> "go"
        "java" -> "java"
        "c" -> "c"
        "h" -> "h"
        "cpp", "cc", "cxx", "c++" -> "cpp"
        "hpp" -> "hpp"
        "cs", "csharp", "c#" -> "cs"
        "dart" -> "dart"
        "scala" -> "scala"
        "swift" -> "swift"
        "rb", "ruby" -> "rb"
        "php" -> "php"
        "html", "htm" -> "html"
        "css" -> "css"
        "scss" -> "scss"
        "json", "jsonc" -> "json"
        "yaml", "yml" -> "yaml"
        "toml" -> "toml"
        "xml" -> "xml"
        "sql" -> "sql"
        // Audit-200 D4 (m-068/m-076): console/terminal are shell fences —
        // they save as .sh, like sh/bash/zsh. They stay Save-only:
        // isRunnableCodeBlock below deliberately does NOT gain them.
        "sh", "bash", "zsh", "shell", "console", "terminal" -> "sh"
        "md", "markdown" -> "md"
        "diff" -> "diff"
        else -> "txt"
    }
    return "snippet.$ext"
}

/**
 * Whether a fenced code block may be run from the chat (audit-200 C14,
 * M-196): shell fences only. The command runs in the project directory
 * through the engine's shell API, after a confirmation dialog showing
 * the exact command — anything else (python, JS, SQL, …) stays
 * Save-only, because executing it as a shell command would be wrong
 * and running it through an interpreter is a different feature.
 */
fun isRunnableCodeBlock(language: String?): Boolean {
    val raw = language?.trim()?.substringBefore(' ')?.substringBefore(':')?.lowercase().orEmpty()
    return raw in setOf("sh", "bash", "zsh", "shell")
}
