package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.FileDiffInfo
import dev.bidzz.opencode.core.api.dto.FsEntry
import dev.bidzz.opencode.core.api.dto.FsListResponse
import dev.bidzz.opencode.core.api.dto.VcsDiffResponse
import dev.bidzz.opencode.core.api.dto.VcsFileStatus
import dev.bidzz.opencode.core.api.dto.VcsStatusResponse
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Filesystem access through the engine.
 *
 * Everything is relative to a `location` (the project directory), which the engine
 * resolves server-side. Passing the directory on every call rather than relying on
 * the engine's working directory means a stale client cannot read the wrong project.
 */
class FileApi(private val client: EngineClient) {

    /**
     * Lists one directory.
     *
     * [path] is relative to [directory]; empty means the directory itself.
     */
    suspend fun list(directory: String, path: String = ""): ApiResult<List<FsEntry>> {
        val query = buildString {
            append("/api/fs/list?")
            append(locationParam(directory))
            if (path.isNotEmpty()) append("&path=").append(enc(path))
        }
        val request = client.request(query).get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<FsListResponse>(raw).data
        }
    }

    /**
     * Reads a file as text.
     *
     * The endpoint returns `application/octet-stream`, so the body is decoded
     * explicitly rather than through the JSON path. Binary files are rejected
     * before decoding: a NUL byte in the first block means the content is not text,
     * and showing mojibake in a code viewer is worse than saying "binary file".
     */
    suspend fun readText(directory: String, path: String): ApiResult<String> = withContext(Dispatchers.IO) {
        val url = "/api/fs/read/${path.split('/').joinToString("/") { enc(it) }}?${locationParam(directory)}"
        val request = client.request(url).get().build()
        try {
            client.streaming().newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val parsed = runCatching { ApiJson.decodeFromString<ErrorBody>(body) }.getOrNull()
                    return@withContext ApiResult.Failure(
                        code = response.code,
                        tag = parsed?.tag,
                        message = parsed?.message ?: body.take(200).ifBlank { null },
                    )
                }
                val bytes = response.body?.bytes() ?: ByteArray(0)
                if (looksBinary(bytes)) {
                    return@withContext ApiResult.Failure(
                        code = -2,
                        message = "Binary file (${bytes.size} bytes)",
                    )
                }
                ApiResult.Success(bytes.toString(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            ApiResult.Failure(code = 0, message = e.message, cause = e)
        }
    }

    /**
     * A NUL byte in the first 8 KB means the file is not text.
     *
     * This is the same heuristic git uses. Checking only the start keeps a large
     * file from being scanned in full before the answer is known.
     */
    private fun looksBinary(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, 8192)
        for (i in 0 until limit) if (bytes[i] == 0.toByte()) return true
        return false
    }

    /**
     * Writes a file, creating missing parent directories.
     *
     * The target is carried in the query exactly like [readText] carries it, and the
     * content is the whole request body as `application/octet-stream` — the endpoint
     * declares binary, so a JSON envelope here would be rejected rather than parsed.
     */
    suspend fun writeText(directory: String, path: String, content: String): ApiResult<Unit> {
        val request = client.request(writeUrl(directory, path)).post(client.bytesBody(content)).build()
        return client.executeUnit(request)
    }

    /** Kept separate so the query encoding can be asserted without a running engine. */
    internal fun writeUrl(directory: String, path: String): String =
        "/api/experimental/fs/write?${locationParam(directory)}&path=${enc(path)}"

    internal fun locationParam(directory: String): String =
        "location%5Bdirectory%5D=${enc(directory)}"

    /**
     * Whether the directory is a git repository.
     *
     * Verified behaviour: `GET /api/vcs/status` returns **200 with an empty array**
     * both when the tree is clean and when the directory is not a repository at all
     * — it never returns 404 for this. The status code therefore cannot answer the
     * question.
     *
     * A `.git` entry in the listing is what actually distinguishes the two. That
     * still does not prove the engine can *run* git — on Android the bundled git
     * binary may be missing — which the UI reports separately rather than folding
     * into "no changes".
     */
    suspend fun hasRepository(directory: String): Boolean = when (val entries = list(directory)) {
        is ApiResult.Success -> entries.value.any { it.path.trimEnd('/') == ".git" && it.isDirectory }
        is ApiResult.Failure -> false
    }

    /**
     * Percent-encoding for query values.
     *
     * `URLEncoder` is form-encoding, which writes a space as `+`; the engine reads
     * query values as percent-encoded, where `+` is a literal plus. Verified on a live
     * engine: reading `with space.md` through `+` answers
     * `File not found: with+space.md [HTTP 404] [FileNotFoundError]`.
     */
    private fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/**
 * Version control through the engine.
 *
 * The engine returns an **empty array** rather than an error when the directory is
 * not a repository, so callers cannot distinguish "clean tree" from "no repo" by
 * the result alone. [hasRepository] exists to tell those apart, because the UI must
 * not claim a project is clean when it was never initialised.
 */
class VcsApi(private val client: EngineClient) {

    suspend fun status(directory: String): ApiResult<List<VcsFileStatus>> {
        val request = client.request("/api/vcs/status?${FileApi(client).locationParam(directory)}")
            .get()
            .build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<VcsStatusResponse>(raw).data
        }
    }

    /**
     * Unified diffs for the working tree.
     *
     * `mode=working` compares against HEAD, which is what "what did the agent
     * change" means. The other modes compare branches or commits and are not used
     * by the diff viewer.
     */
    suspend fun diff(directory: String): ApiResult<List<FileDiffInfo>> {
        val query = "/api/vcs/diff?${FileApi(client).locationParam(directory)}&mode=working"
        val request = client.request(query).get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<VcsDiffResponse>(raw).data
        }
    }

    /**
     * Whether the directory is a git repository.
     *
     * Delegates to [FileApi] because the answer comes from the directory listing,
     * not from the status endpoint: status returns 200 with an empty array for both
     * "clean tree" and "not a repository".
     */
    suspend fun hasRepository(directory: String): Boolean = FileApi(client).hasRepository(directory)
}
