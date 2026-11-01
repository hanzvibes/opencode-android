package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.Serializable

/**
 * Provider catalog and credentials.
 *
 * Deliberately narrow: `Credential.Entry` as the engine returns it carries a `value`
 * holding the secret itself. That field is **not declared here**, so the JSON is
 * dropped at decode time instead of landing in memory where it could reach a log,
 * a crash report, or the diagnostics screen.
 */

/** One authentication method a provider offers (`key`, `oauth`, `command`, ...). */
@Serializable
data class IntegrationMethod(
    val id: String = "",
    val type: String? = null,
    val label: String? = null,
)

/**
 * A stored connection to a provider.
 *
 * Every field is optional on purpose — presence in the list is the fact the UI needs
 * ("this provider is connected"), and naming fields the engine might rename would
 * make this brittle for no gain.
 */
@Serializable
data class ProviderConnection(
    val id: String? = null,
    val label: String? = null,
)

@Serializable
data class IntegrationInfo(
    val id: String,
    val name: String = "",
    val methods: List<IntegrationMethod> = emptyList(),
    val connections: List<ProviderConnection> = emptyList(),
) {
    val connected: Boolean get() = connections.isNotEmpty()

    /** True when this provider accepts a raw API key, which is the only flow the app drives. */
    val acceptsKey: Boolean
        get() = methods.any { it.type == "key" || it.id == "key" }
}

@Serializable
data class IntegrationsResponse(
    val data: List<IntegrationInfo> = emptyList(),
    val location: LocationRef? = null,
)

@Serializable
data class CredentialEntry(
    val id: String,
    val integrationID: String = "",
    val label: String = "",
    val active: Boolean = false,
)

@Serializable
data class CredentialsResponse(
    val data: List<CredentialEntry> = emptyList(),
)

/**
 * The `key` member of the engine's tagged `Credential.Value` union.
 *
 * `type` deliberately has **no default**: `ApiJson` sets `encodeDefaults = false`, so a
 * property left at its default is omitted from the request body — and without the
 * discriminator the engine rejects the whole payload with
 * `Expected Credential.Value`. That is a 400, not a silent no-op, but only if you
 * actually send it.
 */
@Serializable
data class KeyValue(val type: String, val key: String)

@Serializable
data class CredentialCreateRequest(
    val integrationID: String,
    val value: KeyValue,
    val label: String? = null,
    val activate: Boolean? = null,
)
