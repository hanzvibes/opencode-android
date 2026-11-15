package dev.bidzz.opencode.ui.files

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Content search across the workspace (audit-200 C16, M-238).
 *
 * Quick-open only searches file NAMES; there was no way to find a string
 * inside a file at all. The engine has no content-search endpoint
 * (`GET /api/fs/find` is name-only), so the search runs the `rg` binary
 * the app already bundles for the agent's grep tool
 * (`EngineBinary.TOOL_LIBRARIES`: `librg.so` → `rg`) directly against
 * the workspace directory. `rg` respects `.gitignore` and skips hidden
 * and binary files by default, so a search stays inside the project.
 *
 * The query is literal text (`--fixed-strings`): a stray `(` in what the
 * user typed must find that parenthesis, not fail as a broken regex.
 */

/** One matching line, with its file (relative to the workspace root). */
data class ContentMatch(
    val path: String,
    val lineNumber: Int,
    val lineText: String,
)

/** What a search returned. `truncated` means more matches exist than [matches] holds. */
data class ContentSearchResult(
    val matches: List<ContentMatch>,
    val truncated: Boolean,
)

/** How many matches a search returns at most (per file: [MaxMatchesPerFile]). */
const val MaxContentMatches = 100

/** How many matches a search returns from one file at most. */
const val MaxMatchesPerFile = 10

/** How long a search may run before it is killed and its partial results returned. */
const val ContentSearchTimeoutMs = 30_000L

@Serializable
private data class RgLine(val type: String, val data: RgData? = null)

@Serializable
private data class RgData(
    val path: RgPath? = null,
    val lines: RgLines? = null,
    val line_number: Int? = null,
)

@Serializable
private data class RgPath(val text: String? = null)

@Serializable
private data class RgLines(val text: String? = null)

private val rgJson = Json { ignoreUnknownKeys = true }

/**
 * Parses one `--json` output line from `rg` into a [ContentMatch], or null
 * when the line is not a text match. Binary matches carry their bytes
 * (`lines.bytes`, base64) instead of `lines.text` and are skipped — the
 * viewer cannot show them as text anyway.
 */
internal fun parseRgJsonLine(line: String, workspaceDir: File): ContentMatch? {
    val parsed = runCatching { rgJson.decodeFromString<RgLine>(line) }.getOrNull() ?: return null
    if (parsed.type != "match") return null
    val data = parsed.data ?: return null
    val absolutePath = data.path?.text ?: return null
    val text = data.lines?.text?.trimEnd('\n', '\r') ?: return null
    val lineNumber = data.line_number ?: return null
    // The prefix carries its trailing separator: without it a path like
    // "/tmp/ws-evil/a.kt" would strip the "/tmp/ws" prefix of workspace
    // "/tmp/ws" and read as a hit inside it. Compare before trimming —
    // trimming first turned "/elsewhere/a.kt" into "elsewhere/a.kt",
    // which no longer equalled the absolute path and slipped through.
    val prefix = workspaceDir.absolutePath.trimEnd(File.separatorChar) + File.separatorChar
    val relative = absolutePath.removePrefix(prefix)
    if (relative.isEmpty() || relative == absolutePath) return null
    return ContentMatch(path = relative, lineNumber = lineNumber, lineText = text)
}

/**
 * Runs the bundled `rg` over [workspaceDir] for [query] and returns the
 * matches in rg's own order (directory walk, then line). Stops reading
 * at [maxMatches] and kills the process; a search that runs longer than
 * [ContentSearchTimeoutMs] is killed too and its partial matches are
 * returned with `truncated = true`. Never throws: a missing binary, a
 * dead workspace or a broken pipe all answer with an empty result.
 */
suspend fun runContentSearch(
    rgBinary: File,
    workspaceDir: File,
    query: String,
    maxMatches: Int = MaxContentMatches,
): ContentSearchResult {
    if (!rgBinary.canExecute() || !workspaceDir.isDirectory || query.isBlank()) {
        return ContentSearchResult(emptyList(), truncated = false)
    }
    val process = runCatching {
        ProcessBuilder(
            rgBinary.absolutePath,
            "--json",
            "--fixed-strings",
            "--max-count=$MaxMatchesPerFile",
            "--no-heading",
            query,
            workspaceDir.absolutePath,
        )
            .directory(workspaceDir)
            .redirectErrorStream(false)
            .start()
    }.getOrNull() ?: return ContentSearchResult(emptyList(), truncated = false)
    val matches = mutableListOf<ContentMatch>()
    var timedOut = false
    var hitCap = false
    try {
        withTimeoutOrNull(ContentSearchTimeoutMs) {
            process.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (matches.size >= maxMatches) {
                        hitCap = true
                        break
                    }
                    parseRgJsonLine(line, workspaceDir)?.let { matches += it }
                }
            }
            // A full scan that finished on its own: wait for the exit code
            // so a crashed rg does not read as a clean empty result. Skipped
            // when the cap cut the stream short — the process is killed
            // below instead of waiting for a scan nobody needs.
            if (!hitCap) process.waitFor(5, TimeUnit.SECONDS)
        } ?: run { timedOut = true }
    } catch (e: kotlinx.coroutines.CancellationException) {
        // A cancelled search must stay cancelled: swallowing it here
        // would let the launch block publish stale results over the
        // newer search that cancelled this one.
        throw e
    } catch (_: Exception) {
        // A read that fails mid-stream (killed process, closed pipe) keeps
        // whatever matched so far; the search is reported truncated.
        timedOut = true
    } finally {
        runCatching { process.destroyForcibly() }
    }
    val truncated = timedOut || hitCap
    return ContentSearchResult(matches, truncated)
}
