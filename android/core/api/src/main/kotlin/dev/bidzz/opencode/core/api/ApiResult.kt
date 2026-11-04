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
    ) : ApiResult<Nothing> {
        val isUnauthorized: Boolean get() = tag == "UnauthorizedError" || code == 401
        val isSessionMissing: Boolean get() = tag == "SessionNotFoundError" || (code == 404 && tag == null)
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
                isNoSession -> FailureReason.NO_SESSION
                isNoPendingPermission -> FailureReason.NO_PENDING_PERMISSION
                isUnauthorized -> FailureReason.UNAUTHORIZED
                isLocationMissing -> FailureReason.LOCATION_MISSING
                isSessionMissing -> FailureReason.SESSION_MISSING
                isNetwork -> FailureReason.UNREACHABLE
                !message.isNullOrBlank() -> FailureReason.SERVER_MESSAGE
                else -> FailureReason.REQUEST_FAILED
            }

        /** The event stream rejected us mid-session; the socket is gone until it is reopened. */
        val isLostConnection: Boolean get() = tag == "LostConnection"

        /** Called before a session existed. The caller's bug, but the user sees it. */
        val isNoSession: Boolean get() = tag == "NoSession"

        /** A permission reply arrived with no request outstanding. */
        val isNoPendingPermission: Boolean get() = tag == "NoPendingPermission"

        /** The file is not text; [message] carries its size in bytes. */
        val isBinaryFile: Boolean get() = tag == "BinaryFile"
    }
}

/** Why a call failed, without the words. The screen turns this into a sentence. */
enum class FailureReason {
    UNAUTHORIZED,
    LOCATION_MISSING,
    SESSION_MISSING,
    UNREACHABLE,
    /** The engine sent its own message; show it verbatim. */
    SERVER_MESSAGE,
    REQUEST_FAILED,
    /** The event stream rejected us; the session has to be reopened. */
    LOST_CONNECTION,
    /** Nothing was open to act on. */
    NO_SESSION,
    NO_PENDING_PERMISSION,
    BINARY_FILE,
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
