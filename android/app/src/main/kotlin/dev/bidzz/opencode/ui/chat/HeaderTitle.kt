package dev.bidzz.opencode.ui.chat

/**
 * The header title: which project the agent is working in.
 *
 * A full path does not fit a header and does not need to — the last segment is what
 * tells two projects apart, and it is the same string the file explorer shows.
 *
 * Falls back to the product name when there is nothing to name: a header that renders
 * empty reads as a broken screen, not as "no project yet".
 */
fun headerTitle(workspacePath: String?): String {
    val name = workspacePath
        ?.trimEnd('/', ' ')
        ?.substringAfterLast('/')
        ?.trim()
    return if (name.isNullOrEmpty()) DEFAULT_TITLE else name
}

private const val DEFAULT_TITLE = "OpenCode"
