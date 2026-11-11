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
 * There is deliberately no Run button here. The doc lists shell endpoints
 * (`POST /api/session/{id}/shell`, `POST /api/shell`), but it gives no
 * request-body schema for either and the client has no shell method, so
 * the request shape would be guessed — and running arbitrary
 * model-written code from a chat bubble, with no sandbox or allowlist,
 * is not a button to add on a guess. Save is the safe half; Run stays
 * unimplemented until a verified, gated execution path exists.
 */
fun codeBlockFileName(language: String?): String {
    val raw = language?.trim()?.substringBefore(' ')?.substringBefore(':')?.lowercase()
        .orEmpty()
    if (raw == "dockerfile") return "Dockerfile"
    val ext = when (raw) {
        "kt", "kotlin" -> "kt"
        "py", "python" -> "py"
        "js", "javascript" -> "js"
        "ts", "typescript" -> "ts"
        "tsx" -> "tsx"
        "jsx" -> "jsx"
        "rs", "rust" -> "rs"
        "go", "golang" -> "go"
        "java" -> "java"
        "c" -> "c"
        "cpp", "cc", "cxx" -> "cpp"
        "cs", "csharp" -> "cs"
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
        "sh", "bash", "zsh", "shell" -> "sh"
        "md", "markdown" -> "md"
        "diff" -> "diff"
        else -> "txt"
    }
    return "snippet.$ext"
}
