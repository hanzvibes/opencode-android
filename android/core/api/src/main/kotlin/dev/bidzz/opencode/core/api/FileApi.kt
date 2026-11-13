package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.FileDiffInfo
import dev.bidzz.opencode.core.api.dto.FsEntry
import dev.bidzz.opencode.core.api.dto.FsListResponse
import dev.bidzz.opencode.core.api.dto.VcsBase
import dev.bidzz.opencode.core.api.dto.VcsBaseResponse
import dev.bidzz.opencode.core.api.dto.VcsBranchesResponse
import dev.bidzz.opencode.core.api.dto.VcsDiffResponse
import dev.bidzz.opencode.core.api.dto.VcsFileStatus
import dev.bidzz.opencode.core.api.dto.VcsInfo
import dev.bidzz.opencode.core.api.dto.VcsInfoResponse
import dev.bidzz.opencode.core.api.dto.VcsStatusResponse
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * What [FileApi.readText] read, without holding a whole huge file in RAM (59-F5).
 *
 * The body is streamed with a cap on what is kept: a 200 MB log no longer
 * becomes bytes + a UTF-16 string + a lines list (~3x its size) before the
 * [LargeFileGuard] preview can cut it down. The preview only ever needs
 * the first 2,000 lines, so [text] holds at most [FILE_READ_CAP_BYTES]
 * bytes — but [totalBytes] and [totalLines] still describe the whole file,
 * so the preview banner never understates how much was held back.
 */
data class FileTextResult(
    /** The text kept: the whole file, or its first [FILE_READ_CAP_BYTES] bytes when capped. */
    val text: String,
    /** True bytes in the body, kept or not. */
    val totalBytes: Long,
    /** Line count of the whole body, in [String.lines] terms. */
    val totalLines: Int,
    /** True when [text] stops before the end of the body. */
    val truncated: Boolean,
)

/** Bytes [FileApi.readText] keeps before it stops buffering (59-F5): 8 MB. */
internal const val FILE_READ_CAP_BYTES = 8_388_608

/**
 * Line count of [bytes] in [String.lines] terms (59-F5): separators are
 * "\r\n", "\n" and "\r" (a split "\r\n" counts once); an empty input is
 * one (empty) line, matching `"" .lines().size`.
 */
internal fun countLines(bytes: ByteArray): Int {
    if (bytes.isEmpty()) return 1
    var breaks = 0
    var prev = -1
    for (b in bytes) {
        val v = b.toInt()
        when (v) {
            '\n'.code -> if (prev != '\r'.code) breaks++
            '\r'.code -> breaks++
        }
        prev = v
    }
    return breaks + 1
}

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
     * The decode itself is strict (item 11): `String(bytes, UTF_8)` silently
     * replaces malformed sequences with U+FFFD, so editing and saving such a
     * file would write the replacement characters back over the original
     * bytes. A file that is not valid UTF-8 is refused instead.
     *
     * The body is streamed with a cap (59-F5): only the first
     * [FILE_READ_CAP_BYTES] bytes are kept, while the true size and line
     * count are still measured over the whole body — the old `bytes()`
     * call loaded the entire file into RAM before the LargeFileGuard
     * preview could cut it down, so opening one large log could OOM the
     * app. A capped result must never be edited as if whole: the caller
     * refuses a full load when [FileTextResult.truncated] is true.
     */
    suspend fun readText(directory: String, path: String): ApiResult<FileTextResult> = withContext(Dispatchers.IO) {
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
                val stream = response.body?.byteStream()
                    ?: return@withContext ApiResult.Success(FileTextResult("", 0, 1, false))
                val read = stream.use { readCapped(it) }
                if (looksBinary(read.kept)) {
                    // Tagged rather than worded: the screen knows the language, and the byte
                    // count still reaches it through `code`'s sibling field.
                    return@withContext ApiResult.Failure(
                        code = -2,
                        tag = "BinaryFile",
                        message = read.totalBytes.toString(),
                    )
                }
                val text = decodeCapped(read.kept, read.truncated) ?: return@withContext ApiResult.Failure(
                    code = -3,
                    tag = "NotUtf8",
                    message = read.totalBytes.toString(),
                )
                ApiResult.Success(FileTextResult(text, read.totalBytes, read.totalLines, read.truncated))
            }
        } catch (e: CancellationException) {
            // Cancellation is not a failure (Fase 2, 59-F4): the same
            // swallow as in EngineClient.execute — a cancelled read came
            // back as a failed one, so the job never actually stopped.
            throw e
        } catch (e: Exception) {
            ApiResult.Failure(code = 0, message = e.message, cause = e)
        }
    }

    /**
     * Streams [stream] to the end, keeping at most [FILE_READ_CAP_BYTES]
     * bytes (59-F5). The true byte count and line count are measured over
     * the whole body, so a capped read still reports the file's real size.
     * Line breaks match [String.lines]: "\r\n" counts once even when the
     * "\r" and "\n" land in different chunks.
     */
    private fun readCapped(stream: InputStream): CappedRead {
        val kept = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var totalBytes = 0L
        var breaks = 0
        var prev = -1
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            for (i in 0 until n) {
                val v = buf[i].toInt()
                when (v) {
                    '\n'.code -> if (prev != '\r'.code) breaks++
                    '\r'.code -> breaks++
                }
                prev = v
            }
            if (kept.size() < FILE_READ_CAP_BYTES) {
                kept.write(buf, 0, minOf(n, FILE_READ_CAP_BYTES - kept.size()))
            }
            totalBytes += n
        }
        val totalLines = if (totalBytes == 0L) 1 else breaks + 1
        return CappedRead(kept.toByteArray(), totalBytes, totalLines, totalBytes > FILE_READ_CAP_BYTES)
    }

    /**
     * Strict UTF-8 decode of the kept bytes (item 11). A cap can cut a
     * multi-byte sequence in half, which is not a malformed file: when
     * truncated, retry after dropping up to 3 trailing bytes (a UTF-8
     * sequence is at most 4 bytes). A genuine malformation anywhere else
     * still fails all attempts and returns null.
     */
    private fun decodeCapped(kept: ByteArray, truncated: Boolean): String? {
        var end = kept.size
        while (true) {
            val decoded = runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(kept, 0, end))
                    .toString()
            }.getOrNull()
            if (decoded != null) return decoded
            if (!truncated || kept.size - end >= 3) return null
            end--
        }
    }

    /** One streamed read: the kept prefix plus the whole body's measurements. */
    private data class CappedRead(
        val kept: ByteArray,
        val totalBytes: Long,
        val totalLines: Int,
        val truncated: Boolean,
    )

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
     * Branch names for a project.
     *
     * `search` filters server-side, `limit` caps the list (engine default 50,
     * max 100). An empty list means no branches matched — or no repository;
     * like status/diff the engine answers 200 with an empty array, so this
     * alone cannot tell those apart.
     */
    suspend fun branches(directory: String, search: String? = null, limit: Int? = null): ApiResult<List<String>> {
        val query = buildString {
            append("/api/vcs/branch?").append(FileApi(client).locationParam(directory))
            if (!search.isNullOrBlank()) {
                append("&search=").append(java.net.URLEncoder.encode(search, "UTF-8"))
            }
            if (limit != null) append("&limit=").append(limit)
        }
        val request = client.request(query).get().build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<VcsBranchesResponse>(raw).data
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

    /**
     * Current and default branch for a project.
     *
     * The branch list alone cannot say which branch is current or default
     * — it is a plain array of names — so the status dialog reads them
     * from here instead of guessing.
     */
    suspend fun info(directory: String): ApiResult<VcsInfo> {
        val request = client.request("/api/vcs?${FileApi(client).locationParam(directory)}")
            .get()
            .build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<VcsInfoResponse>(raw).data
        }
    }

    /**
     * The review base for a project, or null when the engine has none.
     *
     * Null before the first commit, or when the provider has no base
     * metadata — so a null answer is a fact about the repository, not a
     * failure, and the caller must not report it as one.
     */
    suspend fun base(directory: String): ApiResult<VcsBase?> {
        val request = client.request("/api/vcs/base?${FileApi(client).locationParam(directory)}")
            .get()
            .build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<VcsBaseResponse>(raw).data
        }
    }
}
