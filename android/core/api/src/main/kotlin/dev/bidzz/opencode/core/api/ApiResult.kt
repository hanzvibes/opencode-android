package dev.bidzz.opencode.core.api

import kotlinx.serialization.json.Json

/**
 * Outcome of an API call.
 *
 * Errors carry the server's `_tag` rather than just an HTTP code, because the
 * three failures that matter to the UI are distinguishable only by tag:
 * `UnauthorizedError` means the password rotated (the engine restarted),
 * `SessionNotFoundError` means the session is gone, and `LocationNotFoundError`
 * means the project directory moved or was deleted. All three can arrive with
 * different status codes over the engine's lifetime, so the tag is the contract.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(
        val code: Int,
        /** Server-side discriminator, e.g. "UnauthorizedError"; null if absent. */
        val tag: String? = null,
        val message: String? = null,
        val cause: Throwable? = null,
        /**
         * Seconds the server asked us to wait before retrying (audit-200
         * C10, M-152): the `Retry-After` header on a 429, in seconds, or
         * null when the server sent none or an unparseable value. The
         * banner shows the wait instead of offering an immediate re-send
         * that would fail again.
         */
        val retryAfterSeconds: Int? = null,
    ) : ApiResult<Nothing> {
        val isUnauthorized: Boolean get() = tag == "UnauthorizedError" || code == 401
        // Audit-200 D13 (m-984): every 404 on GET /api/session/{id} is a
        // missing session, whatever tag it carries — the engine only ever
        // 404s that endpoint for a session that is gone. The old rule
        // (tag == null only) let a 404 with an unfamiliar tag slip past
        // the refusal into a transient defer with the wrong banner
        // ("could not verify") and a queue entry that never retried.
        // The one exception is LocationNotFoundError: the session exists
        // but its project folder moved, which is a different problem.
        val isSessionMissing: Boolean get() =
            tag == "SessionNotFoundError" || (code == 404 && tag != "LocationNotFoundError")
        val isLocationMissing: Boolean get() = tag == "LocationNotFoundError"
        val isNetwork: Boolean get() = code == 0

        /**
         * What went wrong, as a value the screen can word in the app's language.
         *
         * This used to be an English sentence, which forced every caller to show English no
         * matter what language the app was set to. The tag is checked before the status code
         * because both `SessionNotFoundError` and `LocationNotFoundError` arrive as 404:
         * routing on the code alone would tell the user their session vanished when in fact
         * their project folder moved.
         */
        val reason: FailureReason
            get() = when {
                // An explicit tag is more specific than the status code: a stream that was
                // rejected mid-session is a lost connection, not a stale password.
                isLostConnection -> FailureReason.LOST_CONNECTION
                isBinaryFile -> FailureReason.BINARY_FILE
                isNotUtf8 -> FailureReason.NOT_UTF8
                isNoImageModel -> FailureReason.NO_IMAGE_MODEL
                isNoSession -> FailureReason.NO_SESSION
                isNoPendingPermission -> FailureReason.NO_PENDING_PERMISSION
                isUnauthorized -> FailureReason.UNAUTHORIZED
                isLocationMissing -> FailureReason.LOCATION_MISSING
                isSessionMissing -> FailureReason.SESSION_MISSING
                isNetwork -> FailureReason.UNREACHABLE
                // Audit-200 C10 (M-152): a 429 with a server message used
                // to surface as raw English SERVER_MESSAGE. It is a rate
                // limit, and the screen words it in the app's language.
                code == 429 -> FailureReason.RATE_LIMITED
                // Audit-200 D1 (m-664): the same treatment for the two
                // provider/engine tags WebSearch promises, and for a
                // decode failure (code -1) — the raw exception text is
                // English, and it can carry a path or a token.
                isServiceUnavailable -> FailureReason.SERVICE_UNAVAILABLE
                isInvalidRequest -> FailureReason.INVALID_REQUEST
                code == -1 -> FailureReason.DECODE_FAILED
                !message.isNullOrBlank() -> FailureReason.SERVER_MESSAGE
                else -> FailureReason.REQUEST_FAILED
            }

        /** The event stream rejected us mid-session; the socket is gone until it is reopened. */
        val isLostConnection: Boolean get() = tag == "LostConnection"

        /** No image model is selected for an image generation (see NO_IMAGE_MODEL). */
        val isNoImageModel: Boolean get() = tag == "NoImageModel"

        /** Called before a session existed. The caller's bug, but the user sees it. */
        val isNoSession: Boolean get() = tag == "NoSession"

        /** A permission reply arrived with no request outstanding. */
        val isNoPendingPermission: Boolean get() = tag == "NoPendingPermission"

        /** The file is not text; [message] carries its size in bytes. */
        val isBinaryFile: Boolean get() = tag == "BinaryFile"

        /** The file is text but not valid UTF-8; [message] carries its size in bytes. */
        val isNotUtf8: Boolean get() = tag == "NotUtf8"

        /** The provider/engine is down (e.g. a search provider outage). */
        val isServiceUnavailable: Boolean get() = tag == "ServiceUnavailableError"

        /** The engine refused the request itself (bad query, bad parameters). */
        val isInvalidRequest: Boolean get() = tag == "InvalidRequestError"
    }
}

/** Why a call failed, without the words. The screen turns this into a sentence. */
enum class FailureReason {
    UNAUTHORIZED,
    LOCATION_MISSING,
    SESSION_MISSING,
    UNREACHABLE,
    /** HTTP 429: the model is busy; re-sending on the same model fails again. */
    RATE_LIMITED,
    /** The provider/engine is down; the user should try another provider or wait. */
    SERVICE_UNAVAILABLE,
    /** The engine refused the request itself. */
    INVALID_REQUEST,
    /** The engine answered, but the response could not be decoded. */
    DECODE_FAILED,
    /** The engine sent its own message; show it verbatim. */
    SERVER_MESSAGE,
    REQUEST_FAILED,
    /** The event stream rejected us; the session has to be reopened. */
    LOST_CONNECTION,
    /** Nothing was open to act on. */
    NO_SESSION,
    NO_PENDING_PERMISSION,
    BINARY_FILE,
    /** Text that is not valid UTF-8; editing it would corrupt the file on save. */
    NOT_UTF8,
    /**
     * No image model is selected (audit-200 D2, m-1217): the selected id
     * went stale — restored from disk after the model list changed — so
     * the generation cannot start. Not an engine outage, so it must not
     * read as "Cannot reach the engine".
     */
    NO_IMAGE_MODEL,
}

/** Server error body: `{"_tag":"...","message":"..."}`. */
@kotlinx.serialization.Serializable
internal data class ErrorBody(
    @kotlinx.serialization.SerialName("_tag") val tag: String? = null,
    val message: String? = null,
)

/**
 * JSON settings shared by every decode in this module.
 *
 * `ignoreUnknownKeys` is not laziness: the engine gains fields between releases,
 * and a client that crashes on an unrecognised field would break on every upgrade.
 * `explicitNulls = false` keeps request bodies free of nulls the server's
 * `additionalProperties: false` schemas might reject.
 */
internal val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = false
}
