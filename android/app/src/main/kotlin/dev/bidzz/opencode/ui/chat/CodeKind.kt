package dev.bidzz.opencode.ui.chat

/** What a fenced code block is, as far as the actions on it are concerned. */
private val EXTENSIONS = mapOf(
    "html" to "html", "htm" to "html", "svg" to "svg", "css" to "css",
    "js" to "js", "javascript" to "js", "ts" to "ts", "typescript" to "ts",
    "json" to "json", "xml" to "xml", "md" to "md", "markdown" to "md",
    "kotlin" to "kt", "kt" to "kt", "java" to "java",
    "python" to "py", "py" to "py", "sh" to "sh", "bash" to "sh", "shell" to "sh",
    "yaml" to "yml", "yml" to "yml", "sql" to "sql", "c" to "c", "cpp" to "cpp",
    "go" to "go", "rust" to "rs", "rs" to "rs", "php" to "php", "ruby" to "rb",
)

/** Suggested file name for saving [language] code; plain text when the language is unknown. */
fun suggestedFileName(language: String?): String {
    val ext = EXTENSIONS[language?.trim()?.lowercase()] ?: return "code.txt"
    return if (ext == "html") "index.html" else "code.$ext"
}
