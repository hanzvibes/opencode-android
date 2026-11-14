package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * MCP server configuration and resource catalog, plus plugin check/update
 * requests (mockup group 4).
 *
 * Shapes verified against the engine fork source at pin
 * 92b2ca0dd727942e00aba3523ed141d53e78ea28:
 * `packages/schema/src/mcp.ts` (`ServerConfig`, `Resource`, `ResourceTemplate`,
 * `ResourceCatalog`), `packages/protocol/src/groups/mcp.ts` (endpoint paths
 * and payloads) and `packages/protocol/src/groups/plugin.ts` (check/update).
 *
 * The union rule from [Models.kt] applies here too: the engine's tagged
 * unions are modelled as one class with nullable fields, not a sealed
 * hierarchy, so a new config kind or field cannot crash decoding. The
 * `type` discriminator (`"local"` | `"remote"`) is what the form and the
 * engine agree on; everything else is optional and, with
 * `explicitNulls = false` + `encodeDefaults = false`, simply omitted when
 * absent — a local config encodes only `type` + `command` (+ `cwd` /
 * `environment` when set), a remote one only `type` + `url` (+ `headers` /
 * `oauth` when set).
 */

/**
 * Per-phase timeouts for one MCP server, in milliseconds.
 * The engine requires positive integers (`PositiveInt`); the app's add
 * form never sets timeouts, so no validation lives here.
 */
@Serializable
data class McpTimeoutConfig(
    val startup: Int? = null,
    val catalog: Int? = null,
    val execution: Int? = null,
)

/**
 * OAuth settings for a remote MCP server.
 *
 * The engine also accepts the literal `false` (OAuth explicitly off);
 * that value has no Kotlin representation here, so it is never sent —
 * omitting the field leaves the engine default in place, which is the
 * same outcome for a server that does not need OAuth.
 */
@Serializable
data class McpOAuthConfig(
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("client_secret") val clientSecret: String? = null,
    val scope: String? = null,
    @SerialName("callback_port") val callbackPort: Int? = null,
    @SerialName("redirect_uri") val redirectUri: String? = null,
    @SerialName("auth_server_metadata_url") val authServerMetadataUrl: String? = null,
)

/**
 * One MCP server's configuration, as sent to
 * `PUT /api/experimental/mcp/{server}` inside `{ "config": ... }`.
 *
 * `command` is a **string array**, not one string: `["npx", "-y", "pkg"]`.
 * The app's add form splits its command field on whitespace, so quoted
 * arguments are not supported yet — the field's hint says so.
 *
 * Adding is **runtime-only**: the endpoint description says it adds "at
 * runtime or replaces an existing one, connecting it immediately", and
 * removing stops the server "until restart". Nothing here is written to
 * the engine's config file, so a server added from the app is gone after
 * the next engine restart — the add form says so, and the remove confirm
 * repeats it.
 */
@Serializable
data class McpServerConfig(
    /** `"local"` or `"remote"`. */
    val type: String,
    /** Local only: the command and its arguments, as separate entries. */
    val command: List<String>? = null,
    /** Remote only: the server URL. */
    val url: String? = null,
    /** Local only: working directory; relative paths resolve from the workspace. */
    val cwd: String? = null,
    /** Local only: environment variables for the server process. */
    val environment: Map<String, String>? = null,
    /** Remote only: request headers. */
    val headers: Map<String, String>? = null,
    /** Remote only: OAuth settings (see [McpOAuthConfig]). */
    val oauth: McpOAuthConfig? = null,
    val disabled: Boolean? = null,
    /** Expose this server's tools through Code Mode. Defaults to true on the engine. */
    val codemode: Boolean? = null,
    val timeout: McpTimeoutConfig? = null,
    /** `"legacy"` (default) | `"auto"` | `"2026-07-28"`. */
    val protocol: String? = null,
    /**
     * Pool this server's connections (v2.2.7+): reuse one connection for
     * the server instead of starting a process per call. Optional;
     * absent means the engine default. Verified against the fork source
     * at pin fedc6f20 (`packages/schema/src/mcp.ts`: `pool:
     * Schema.Boolean.pipe(optional)` on both the local and remote
     * config).
     */
    val pool: Boolean? = null,
) {
    companion object {
        /** A local (stdio) server: `command` is already split into entries. */
        fun local(
            command: List<String>,
            cwd: String? = null,
            // Audit-200 B2 (M-291): the Add form now covers environment
            // and disabled too — they were config fields the app could
            // never set.
            environment: Map<String, String>? = null,
            disabled: Boolean? = null,
        ): McpServerConfig =
            McpServerConfig(
                type = "local",
                command = command,
                cwd = cwd,
                environment = environment,
                disabled = disabled,
            )

        /** A remote (HTTP) server. */
        fun remote(
            url: String,
            // Audit-200 B2 (M-291): same as local — headers and
            // disabled were config fields the app could never set.
            headers: Map<String, String>? = null,
            disabled: Boolean? = null,
        ): McpServerConfig =
            McpServerConfig(type = "remote", url = url, headers = headers, disabled = disabled)
    }
}

/** Body of `PUT /api/experimental/mcp/{server}`. `config` is required. */
@Serializable
data class McpAddRequest(
    val config: McpServerConfig,
)

/**
 * One resource exposed by a connected MCP server
 * (`GET /api/mcp/resource`).
 */
@Serializable
data class McpResource(
    val server: String,
    val name: String,
    val uri: String,
    val description: String? = null,
    val mimeType: String? = null,
)

/** One resource template exposed by a connected MCP server. */
@Serializable
data class McpResourceTemplate(
    val server: String,
    val name: String,
    val uriTemplate: String,
    val description: String? = null,
    val mimeType: String? = null,
)

/** The whole resource catalog: resources plus their templates. */
@Serializable
data class McpResourceCatalog(
    val resources: List<McpResource> = emptyList(),
    val templates: List<McpResourceTemplate> = emptyList(),
)

@Serializable
data class McpResourceCatalogResponse(
    val data: McpResourceCatalog = McpResourceCatalog(),
    val location: LocationRef? = null,
)

/**
 * Body of `POST /api/plugin/check`. `target` is one package target
 * (e.g. `opencode-anthropic-auth@1.2.3`); omitted it checks every
 * package plugin. The answer is the full inventory with `outdated: true`
 * stamped onto the package sources that have an update — the app replaces
 * its plugin list with it, it does not merge.
 */
@Serializable
data class PluginCheckRequest(
    val target: String? = null,
)

/**
 * Body of `POST /api/plugin/update`. `targets` are package targets from
 * the inventory; an unknown target fails the whole call with
 * `InvalidRequestError`, and any update failure fails it with
 * `ServiceUnavailableError` after the rest have finished.
 */
@Serializable
data class PluginUpdateRequest(
    val targets: List<String>,
)
