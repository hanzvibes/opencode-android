package dev.bidzz.opencode.ui.chat

/**
 * The header title: which project the agent is working in.
 *
 * A full path does not fit a header and does not need to — the last segment is what
 * tells two projects apart, and it is the same string the file explorer shows.
 *
 * Audit-200 D16 (m-199): the last segment alone is ambiguous when two
 * known workspaces share a folder name ("/…/client-a/app" vs
 * "/…/client-b/app" both read "app"). When [knownWorkspaces] contains
 * another workspace with the same last segment, the parent segment is
 * shown too ("client-a/app"); otherwise the plain last segment is kept,
 * so the common case does not change.
 *
 * Falls back to the product name when there is nothing to name: a header that renders
 * empty reads as a broken screen, not as "no project yet".
 */
fun headerTitle(workspacePath: String?, knownWorkspaces: Set<String> = emptySet()): String {
    val trimmed = workspacePath?.trimEnd('/', ' ')?.trim().orEmpty()
    if (trimmed.isEmpty()) return DEFAULT_TITLE
    val segments = trimmed.split('/').filter { it.isNotEmpty() }
    val name = segments.lastOrNull().orEmpty()
    if (name.isEmpty()) return DEFAULT_TITLE
    val collides = knownWorkspaces.any { other ->
        val otherTrimmed = other.trimEnd('/', ' ').trim()
        otherTrimmed != trimmed &&
            otherTrimmed.substringAfterLast('/').trim() == name
    }
    if (!collides) return name
    val parent = segments.dropLast(1).lastOrNull().orEmpty()
    return if (parent.isEmpty()) name else "$parent/$name"
}

private const val DEFAULT_TITLE = "OpenCode"
