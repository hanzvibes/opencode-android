package dev.bidzz.opencode.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.FailureReason

/**
 * A failure as a sentence in the app's language.
 *
 * The API layer reports [FailureReason] rather than words, because it has no Context and
 * therefore no way to know which language the user picked. This is the one place that turns
 * that value back into text, so every screen that shows an error words it the same way.
 */
@Composable
fun ApiResult.Failure.message(): String = when (reason) {
    FailureReason.UNAUTHORIZED -> stringResource(R.string.error_unauthorized)
    FailureReason.LOCATION_MISSING -> stringResource(R.string.error_location_missing)
    FailureReason.SESSION_MISSING -> stringResource(R.string.error_session_missing)
    FailureReason.UNREACHABLE -> stringResource(R.string.error_unreachable)
    FailureReason.REQUEST_FAILED -> stringResource(R.string.error_request_failed, code)
    FailureReason.LOST_CONNECTION -> stringResource(R.string.error_lost_connection, code)
    FailureReason.NO_SESSION -> stringResource(R.string.error_no_session)
    FailureReason.NO_PENDING_PERMISSION -> stringResource(R.string.error_no_pending_permission)
    FailureReason.BINARY_FILE -> stringResource(R.string.error_binary_file, message?.toIntOrNull() ?: 0)
    // The engine's own words. It is the one message this app cannot translate, and
    // substituting a generic sentence would throw away the only detail available.
    FailureReason.SERVER_MESSAGE -> message.orEmpty()
}
