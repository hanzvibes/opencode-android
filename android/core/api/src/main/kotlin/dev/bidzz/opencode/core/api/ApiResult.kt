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
         * Short, user-facing description. Never leaks a stack trace.
         *
         * The tag is checked before the status code because both
         * `SessionNotFoundError` and `LocationNotFoundError` arrive as 404: routing
         * on the code alone would tell the user their session vanished when in fact
         * their project folder moved.
         */
        val display: String
            get() = when {
                isUnauthorized -> "Engine rejected the credentials"
                isLocationMissing -> "Project folder is no longer available"
                isSessionMissing -> "Session no longer exists"
                isNetwork -> "Cannot reach the engine"
                message != null -> message!!
                else -> "Request failed ($code)"
            }
    }
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
