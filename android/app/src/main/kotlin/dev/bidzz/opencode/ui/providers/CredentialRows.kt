package dev.bidzz.opencode.ui.providers

import androidx.annotation.StringRes
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.CredentialEntry

/**
 * One stored credential as the manage screen renders it.
 *
 * The mapping lives here rather than inside the composable because both steps can be
 * wrong in ways that still look like a working screen: credentials matched on the
 * wrong key list another provider's keys back, and a blank title makes a row
 * undeletable-by-mistake only by luck.
 */
data class CredentialRow(
    val id: String,
    val title: String,
    /** Discriminator shown under the title. */
    val detail: String,
    val inUse: Boolean,
)

/** The key the engine uses to group credentials is its integration id. */
fun rowsFor(entries: List<CredentialEntry>, integrationID: String): List<CredentialRow> =
    entries.asSequence()
        .filter { it.integrationID == integrationID }
        .sortedWith(compareByDescending<CredentialEntry> { it.active }.thenBy { credentialTitle(it) })
        .map { entry ->
            CredentialRow(
                id = entry.id,
                title = credentialTitle(entry),
                detail = shortId(entry.id),
                inUse = entry.active,
            )
        }
        .toList()

/**
 * Engine credentials can carry an empty label — every key this app stores comes
 * without one — so the row needs a fallback that still reads as a key.
 */
fun credentialTitle(entry: CredentialEntry): String = entry.label.ifBlank { "API key" }

/**
 * Truncated credential id, for telling two identically-labelled keys apart.
 *
 * The secret itself is never available here by design: `Credential.Entry.value` is
 * not declared on the DTO, so the only stable text the engine gives is the id.
 */
fun shortId(id: String): String {
    val prefix = id.take(6)
    val suffix = id.takeLast(4)
    // At this length the two ends already cover the whole id; slicing it anyway would
    // print an ellipsis over characters that are already in the prefix.
    return if (id.length <= prefix.length + suffix.length) id else "$prefix…$suffix"
}

/**
 * What the screen says after a delete, which depends on whether the provider survives it.
 *
 * A string resource plus its format args (92-F4), resolved at the render site with
 * `stringResource` — the same shape as `KeyValidation.Valid/Warning`. A plain
 * `String` here would force the sentence into English, because this function has no
 * `Context` and the view model that calls it has none either.
 */
data class DeleteNotice(
    @StringRes val messageRes: Int,
    val args: List<Any> = emptyList(),
)

fun afterDeleteNotice(remaining: Int, providerName: String): DeleteNotice =
    if (remaining == 0) {
        DeleteNotice(R.string.providers_no_keys_left, listOf(providerName))
    } else {
        DeleteNotice(R.string.providers_keys_left, listOf(remaining, providerName))
    }
