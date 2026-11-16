package dev.bidzz.opencode.ui.settings

import dev.bidzz.opencode.ui.sessions.matchesAllTokens

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
 * True when [query] is blank or every token of it matches one of [texts].
 *
 * The match is tokenised on whitespace (audit-200 D2, m-1119, the same
 * rule as the session search): "engine port" finds the engine port row,
 * and "port", "PORT" and " por " still find it. Null and blank texts
 * never match a non-blank query.
 */
fun matchesSettingsQuery(query: String, vararg texts: String?): Boolean =
    matchesAllTokens(query, texts.toList())

/** True when [query] matches [entry]'s title, subtitle or value. */
fun matchesSettingsQuery(query: String, entry: SettingsSearchEntry): Boolean =
    matchesSettingsQuery(query, entry.title, entry.subtitle, entry.value)

/**
 * Bilingual match (audit-200 D15, m-545): [query] matches when it matches
 * the row's texts in the display locale ([texts]), or the same texts
 * resolved in English ([englishTexts]) or Indonesian ([indonesianTexts]).
 * The caller resolves the translations — this function only proves the
 * matcher itself is bilingual, so a cross-locale query ("project folder"
 * in the ID locale, "folder proyek" in the EN locale) finds the row.
 */
fun matchesSettingsQueryBilingual(
    query: String,
    texts: List<String?>,
    englishTexts: List<String?>,
    indonesianTexts: List<String?>,
): Boolean = matchesSettingsQuery(query, *(texts + englishTexts + indonesianTexts).toTypedArray())

/**
 * The entries whose title, subtitle or value match [query].
 * A blank query returns every entry, in order.
 */
fun filterSettingsEntries(
    query: String,
    entries: List<SettingsSearchEntry>,
): List<SettingsSearchEntry> = entries.filter { matchesSettingsQuery(query, it) }
