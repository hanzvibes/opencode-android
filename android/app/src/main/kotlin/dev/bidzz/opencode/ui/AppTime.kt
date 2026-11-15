package dev.bidzz.opencode.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The locale of the wrapped app context (92-F10) — the pinned app language,
 * not the phone's. Same source as [dateDividerLabel] in ChatScreen: a user
 * who pinned the app to Bahasa Indonesia sees Indonesian month abbreviations
 * whatever the phone is set to.
 */
@Composable
fun appLocale(): Locale = LocalContext.current.resources.configuration.locales[0]

/**
 * "27 Oct 2026, 09:00" in the app language (Audit-200 D1: m-021/m-554).
 * The month abbreviation follows the pinned language, not the system locale.
 */
fun formatAppDateTime(millis: Long, locale: Locale): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale))

/** "27 Oct 09:00" in the app language (Audit-200 D1: m-554, schedule instants). */
fun formatAppDateShort(millis: Long, locale: Locale): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM HH:mm", locale))
