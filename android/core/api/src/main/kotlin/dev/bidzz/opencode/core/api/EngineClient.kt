package dev.bidzz.opencode.core.api

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * HTTP access to the embedded engine.
 *
 * Every call is authenticated with the password the engine prints on startup:
 * `Authorization: Basic base64("opencode:<password>")`. That scheme is not in
 * `openapi.json` — the spec has an empty `securitySchemes` and `security: []` on
 * every operation — so it is implemented here from the observed behaviour.
 *
 * `POST /api/pair` exists but is not used: it requires the same password to obtain
 * a code, and the token it yields is rejected after the engine restarts (verified),
 * so it adds a round trip and no persistence.
 */
class EngineClient(
    /** Base URL including port, e.g. `http://127.0.0.1:43161`. */
    private val baseUrl: String,
    /** Value for the `Authorization` header, from `EngineCredentials.basicAuthHeader`. */
    private val authHeader: String,
    /** Generous by default: a prompt can take a minute to return. */
    private val callTimeoutSeconds: Long = 120,
) {
    companion object {
        private const val TAG = "EngineClient"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val OCTET_STREAM = "application/octet-stream".toMediaType()
    }

    /**
     * Separate client for the event stream: no call timeout, because the stream is
     * meant to stay open indefinitely. A read timeout still applies so a silently
     * dead socket is detected rather than hanging forever.
     */
    private val streamingClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val client: OkHttpClient = streamingClient.newBuilder()
        .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
        // No silent resend for REST (Fase 2, 43-F4): OkHttp retries a POST
        // whose connection failed before a response, and a prompt admitted
        // by the engine but lost on the wire would then be sent twice — the
        // prompt endpoint takes no idempotency key, so the duplicate is
        // indistinguishable from a second message. The streaming client
        // keeps the retry: a GET stream never mutates anything, and a
        // dropped connection there is recovered by the app-level reconnect.
        .retryOnConnectionFailure(false)
        .build()

    /** Raw client for SSE; the stream owns its own lifecycle. */
    internal fun streaming(): OkHttpClient = streamingClient

    internal fun request(path: String): Request.Builder =
        Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", authHeader)

    /** Executes [request] and maps the outcome, decoding a 2xx body into [T]. */
    suspend fun <T> execute(
        request: Request,
        decode: (String) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).await().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext response.toFailure(body)
                }
                // 204 has no body and no type to decode.
                if (response.code == 204 || body.isBlank()) {
                    @Suppress("UNCHECKED_CAST")
                    return@withContext ApiResult.Success(Unit as T)
                }
                ApiResult.Success(decode(body))
            }
        } catch (e: IOException) {
            Log.w(TAG, "${request.method} ${request.url.encodedPath} failed: ${e.message}")
            ApiResult.Failure(code = 0, message = e.message, cause = e)
        } catch (e: CancellationException) {
            // Cancellation is not a failure (Fase 2, 58-F4): swallowing it
            // turned a cancelled job into a failed one, so callers treated
            // it as a real error and the job never actually stopped. The
            // two named victims (SessionDiffViewModel.load, loadBranches)
            // both cancel the previous job before starting a new one, so
            // the rethrow is what keeps a stale write from landing — no
            // separate generation guard is needed there.
            throw e
        } catch (e: Exception) {
            // A decode failure is a contract mismatch, not a network problem, and
            // must be visible rather than silently retried.
            Log.e(TAG, "decoding ${request.url.encodedPath} failed", e)
            ApiResult.Failure(code = -1, message = e.message, cause = e)
        }
    }

    /** Same as [execute] but discards the body; for endpoints returning 204. */
    suspend fun executeUnit(request: Request): ApiResult<Unit> =
        execute(request) { }

    /** Body helpers. `null` body is legal for POSTs whose fields are all optional. */
    internal fun jsonBody(payload: String): okhttp3.RequestBody = payload.toRequestBody(JSON)

    internal fun emptyBody(): okhttp3.RequestBody = ByteArray(0).toRequestBody(JSON)

    /** Raw UTF-8 bytes: `fs/write` takes the file content as the body, not as JSON. */
    internal fun bytesBody(payload: String): okhttp3.RequestBody =
        payload.toByteArray(Charsets.UTF_8).toRequestBody(OCTET_STREAM)

    private fun Response.toFailure(body: String): ApiResult.Failure {
        val parsed = runCatching {
            ApiJson.decodeFromString<ErrorBody>(body)
        }.getOrNull()
        if (parsed == null) Log.w(TAG, "HTTP $code with unparseable body: ${body.take(200)}")
        return ApiResult.Failure(
            code = code,
            tag = parsed?.tag,
            message = parsed?.message ?: body.take(200).ifBlank { null },
        )
    }

    /** Parses a JSON element to [T], returning null instead of throwing. */
    internal inline fun <reified T> JsonElement?.decodeOrNull(): T? =
        this?.let { runCatching { ApiJson.decodeFromJsonElement<T>(kotlinx.serialization.serializer(), it) }.getOrNull() }

    /**
     * Decodes the `data` array of a list response, skipping malformed entries
     * instead of failing the whole list (audit-100 38-F1): one entry without
     * its required `id` — e.g. after an engine upgrade or a third-party
     * integration with a deviating shape — used to hide every provider and
     * every credential, although the engine's database was intact. The
     * skipped count is logged, not silent. A body that is not a list
     * response at all still throws, so the caller reports it as a failure.
     */
    internal inline fun <reified T> decodeDataList(raw: String): List<T> {
        val array = ApiJson.parseToJsonElement(raw).jsonObject["data"]?.jsonArray
            ?: return emptyList()
        var skipped = 0
        val items = ArrayList<T>(array.size)
        for (element in array) {
            val item = element.decodeOrNull<T>()
            if (item == null) skipped++ else items += item
        }
        if (skipped > 0) Log.w(TAG, "skipped $skipped malformed entries in list response")
        return items
    }
}

/**
 * Bridges OkHttp's callback API to a coroutine, cancelling the call if the
 * coroutine is cancelled. OkHttp's `execute()` blocks a thread, which would waste
 * a dispatcher slot per in-flight request on a phone.
 */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        },
    )
}
