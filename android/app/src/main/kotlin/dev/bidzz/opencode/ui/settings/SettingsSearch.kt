package dev.bidzz.opencode.ui.settings

/**
 * One searchable settings row: the texts the search field matches against.
 * Title is always present; subtitle and value are matched when present.
 */
data class SettingsSearchEntry(
    val title: String,
    val subtitle: String? = null,
    val value: String? = null,
)

/**
 * True when [query] is blank or matches any of [texts].
 *
 * The match is a case-insensitive substring match on the trimmed query, so
 * "port", "PORT" and " por " all find the engine port row. Null and blank
 * texts never match a non-blank query.
 */
fun matchesSettingsQuery(query: String, vararg texts: String?): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return texts.any { it?.contains(needle, ignoreCase = true) == true }
}

/** True when [query] matches [entry]'s title, subtitle or value. */
fun matchesSettingsQuery(query: String, entry: SettingsSearchEntry): Boolean =
    matchesSettingsQuery(query, entry.title, entry.subtitle, entry.value)

/**
 * The entries whose title, subtitle or value match [query].
 * A blank query returns every entry, in order.
 */
fun filterSettingsEntries(
    query: String,
    entries: List<SettingsSearchEntry>,
): List<SettingsSearchEntry> = entries.filter { matchesSettingsQuery(query, it) }
