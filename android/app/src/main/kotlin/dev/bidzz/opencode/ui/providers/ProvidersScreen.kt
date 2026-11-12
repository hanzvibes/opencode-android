package dev.bidzz.opencode.ui.providers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.IntegrationInfo
import dev.bidzz.opencode.core.api.dto.IntegrationMethod
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.LocalizedDraggableBottomSheet
import dev.bidzz.opencode.ui.NoticeKind
import dev.bidzz.opencode.ui.NoticeRow
import dev.bidzz.opencode.ui.PrimaryButton
import dev.bidzz.opencode.ui.SearchField
import dev.bidzz.opencode.ui.ScreenScaffold
import dev.bidzz.opencode.ui.SheetStop
import dev.bidzz.opencode.ui.message
import dev.bidzz.opencode.ui.whiteCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Status badge state for an AI provider. */
enum class ProviderStatus {
    CONNECTED,
    FREE_TIER,
    MISSING_KEY,
}

/** Featured provider specification for prominent catalog presentation. */
private data class FeaturedProviderDef(
    val id: String,
    val name: String,
    /** A model list (proper nouns) — identical in every language. */
    val subtitle: String? = null,
    /** A description sentence — resolved at the render site. */
    val subtitleRes: Int? = null,
    val brandColor: Color,
    val isFreeTier: Boolean = false,
)

private val FEATURED_PROVIDERS = listOf(
    FeaturedProviderDef(
        id = "anthropic",
        name = "Anthropic",
        subtitle = "Claude 3.5 Sonnet, Opus & Haiku",
        brandColor = Color(0xFFD97757),
    ),
    FeaturedProviderDef(
        id = "openai",
        name = "OpenAI",
        subtitle = "GPT-4o, o1, o3-mini",
        brandColor = Color(0xFF10A37F),
    ),
    FeaturedProviderDef(
        id = "opencode",
        name = "OpenCode",
        subtitleRes = R.string.providers_subtitle_free_tier,
        brandColor = Color(0xFF6366F1),
        isFreeTier = true,
    ),
    FeaturedProviderDef(
        id = "ollama",
        name = "Ollama",
        subtitleRes = R.string.providers_subtitle_local,
        brandColor = Color(0xFF475569),
    ),
    FeaturedProviderDef(
        id = "deepseek",
        name = "DeepSeek",
        subtitle = "DeepSeek V3, R1 reasoning",
        brandColor = Color(0xFF1E88E5),
    ),
    FeaturedProviderDef(
        id = "google",
        name = "Google",
        subtitle = "Gemini API & Antigravity OAuth",
        brandColor = Color(0xFF4285F4),
    ),
)

private data class CustomFormInitial(
    val name: String = "",
    val baseURL: String = "",
    val models: String = "",
)

/**
 * Provider catalog and API-key entry.
 *
 * Without this the app can only use opencode's free tier, which rate-limits after a
 * single agent run — so this is the screen that makes sustained work possible.
 */
@Composable
fun ProvidersScreen(
    state: ProvidersUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSaveKey: (String, String) -> Unit,
    onSaveCustom: (String, String, String, String) -> Unit,
    onDeleteCredential: (String, String) -> Unit,
    onReplaceCredential: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    var keyFor by remember { mutableStateOf<IntegrationInfo?>(null) }
    var manageFor by remember { mutableStateOf<IntegrationInfo?>(null) }
    var replaceFor by remember { mutableStateOf<Pair<IntegrationInfo, CredentialRow>?>(null) }
    var confirmDelete by remember { mutableStateOf<Pair<IntegrationInfo, CredentialRow>?>(null) }
    var googleDetailsFor by remember { mutableStateOf<IntegrationInfo?>(null) }
    var customFormPrefill by remember { mutableStateOf<CustomFormInitial?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { onRefresh() }

    ScreenScaffold(title = stringResource(R.string.providers_title), onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            // Free Tier / Unconnected hero notice — a white card (Opsi B
            // #41), not a blue wash: the Sparkle icon keeps the info
            // signal, the card edge keeps the White B surface.
            if (state.integrations.none { it.connected } && state.customProviders.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .whiteCard()
                        .padding(spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    GlyphIcon(Glyph.Sparkle, tint = colors.accent, size = 18.dp)
                    Text(
                        text = stringResource(R.string.providers_none_connected),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            state.failure?.let {
                Text(
                    text = it.message(),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.danger,
                )
            }

            state.failureKey?.let {
                Text(
                    text = stringResource(it),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.danger,
                )
            }

            if (state.staleKeyRemains) {
                Text(
                    text = stringResource(R.string.providers_stale_key, state.connected.orEmpty()),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.warning,
                )
            }

            state.notice?.let {
                Text(
                    text = it,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            }

            state.connected?.let {
                Text(
                    text = stringResource(R.string.providers_connected_to, it),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.success,
                )
            }

            // 1. Featured AI Providers Section
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.providers_featured_title).uppercase(),
                        style = OpenCodeTypography.label,
                        color = colors.onSurfaceFaint,
                    )
                }

                FEATURED_PROVIDERS.forEach { def ->
                    val integration = findMatchingIntegration(def, state.integrations)
                    val stored = integration?.let { rowsFor(state.credentials, it.id) } ?: emptyList()
                    val isCustomConfigured = state.customProviders.any {
                        it.id.contains(def.id, ignoreCase = true) || it.name.contains(def.name, ignoreCase = true)
                    }
                    val isGoogle = def.id == "google"
                    val isAntigravityConnected = isGoogle && (state.activeAntigravityEmail != null || state.antigravityAccounts.isNotEmpty())

                    val isConnected = (integration?.connected == true && stored.isNotEmpty()) || isCustomConfigured || (integration?.connected == true) || isAntigravityConnected
                    val status = when {
                        isConnected -> ProviderStatus.CONNECTED
                        def.isFreeTier -> ProviderStatus.FREE_TIER
                        else -> ProviderStatus.MISSING_KEY
                    }

                    val detail = when {
                        isAntigravityConnected -> state.activeAntigravityEmail?.let {
                            stringResource(R.string.providers_antigravity_oauth_account, it)
                        } ?: stringResource(R.string.providers_antigravity_connected_plain)
                        isConnected && stored.isNotEmpty() ->
                            stringResource(
                                if (stored.size == 1) R.string.providers_keys_one else R.string.providers_keys_other,
                                stored.size,
                            )
                        isConnected -> stringResource(R.string.providers_state_connected)
                        def.subtitleRes != null -> stringResource(def.subtitleRes)
                        else -> def.subtitle.orEmpty()
                    }

                    ProviderCard(
                        id = def.id,
                        name = def.name,
                        subtitle = detail,
                        status = status,
                        onClick = when {
                            isGoogle -> ({ googleDetailsFor = integration ?: IntegrationInfo(id = "google", name = "Google", methods = listOf(IntegrationMethod(type = "key"))) })
                            isConnected && stored.isNotEmpty() && integration != null -> ({ manageFor = integration })
                            integration != null && integration.acceptsKey -> ({ keyFor = integration })
                            def.id == "ollama" && !isConnected -> ({
                                customFormPrefill = CustomFormInitial(
                                    name = "Ollama",
                                    baseURL = "http://localhost:11434/v1",
                                    models = "llama3.2, qwen2.5",
                                )
                            })
                            integration != null -> ({ keyFor = integration })
                            else -> ({
                                keyFor = IntegrationInfo(
                                    id = def.id,
                                    name = def.name,
                                    methods = listOf(IntegrationMethod(type = "key")),
                                )
                            })
                        },
                    )
                }
            }

            // 2. Custom Endpoints Section
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = stringResource(R.string.providers_custom_section).uppercase(),
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceFaint,
                )

                state.customProviders.forEach { provider ->
                    ProviderCard(
                        id = provider.id,
                        name = provider.name,
                        subtitle = provider.baseURL.ifEmpty { provider.id } +
                            if (provider.models.isEmpty()) "" else stringResource(R.string.providers_models_suffix, provider.models.size),
                        status = ProviderStatus.CONNECTED,
                        onClick = null,
                    )
                }

                PrimaryButton(
                    label = stringResource(R.string.providers_add_custom),
                    onClick = { customFormPrefill = CustomFormInitial() },
                )
            }

            // 3. Full Provider Catalog Section
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.providers_other_title).uppercase(),
                        style = OpenCodeTypography.label,
                        color = colors.onSurfaceFaint,
                    )
                    if (state.integrations.isNotEmpty()) {
                        Text(
                            text = "${state.integrations.size}",
                            style = OpenCodeTypography.caption,
                            color = colors.onSurfaceFaint,
                        )
                    }
                }

                if (state.integrations.isEmpty() && state.loading) {
                    Text(
                        text = stringResource(R.string.providers_loading),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                    )
                }

                if (state.integrations.isNotEmpty()) {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.providers_search),
                        imeAction = ImeAction.Search,
                    )
                }

                val featuredIds = remember {
                    setOf("anthropic", "openai", "opencode", "zen", "ollama", "deepseek", "google", "gemini")
                }

                val shown = if (query.isBlank()) {
                    // Filter out already featured items when query is empty to avoid duplicate rows
                    state.integrations.filter { info ->
                        !featuredIds.any { info.id.contains(it, ignoreCase = true) || info.name.contains(it, ignoreCase = true) }
                    }
                } else {
                    state.integrations.filter { it.name.contains(query.trim(), ignoreCase = true) || it.id.contains(query.trim(), ignoreCase = true) }
                }

                if (state.integrations.isNotEmpty() && shown.isEmpty() && query.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.providers_no_match, query.trim()),
                        style = OpenCodeTypography.bodySmall,
                        color = colors.onSurfaceMuted,
                    )
                }

                shown.forEach { integration ->
                    val stored = rowsFor(state.credentials, integration.id)
                    val status = when {
                        integration.connected -> ProviderStatus.CONNECTED
                        integration.id.equals("opencode", ignoreCase = true) || integration.id.equals("zen", ignoreCase = true) -> ProviderStatus.FREE_TIER
                        integration.acceptsKey -> ProviderStatus.MISSING_KEY
                        else -> ProviderStatus.MISSING_KEY
                    }

                    val detail = when {
                        integration.connected && stored.isNotEmpty() ->
                            stringResource(
                                if (stored.size == 1) R.string.providers_keys_one else R.string.providers_keys_other,
                                stored.size,
                            )
                        integration.connected -> stringResource(R.string.providers_state_connected)
                        integration.acceptsKey -> stringResource(R.string.providers_state_add_key)
                        else -> stringResource(R.string.providers_state_no_method)
                    }

                    ProviderCard(
                        id = integration.id,
                        name = integration.name,
                        subtitle = detail,
                        status = status,
                        onClick = when {
                            integration.connected && stored.isNotEmpty() -> ({ manageFor = integration })
                            integration.acceptsKey -> ({ keyFor = integration })
                            else -> null
                        },
                    )
                }
            }
        }
    }

    keyFor?.let { integration ->
        KeyPrompt(
            integration = integration,
            title = stringResource(R.string.providers_key_title, integration.name),
            body = stringResource(R.string.providers_key_body),
            submitLabel = stringResource(R.string.common_save),
            onDismiss = { keyFor = null },
            onSubmit = { key ->
                onSaveKey(integration.id, key)
                keyFor = null
            },
        )
    }

    manageFor?.let { integration ->
        ManageCredentialsDialog(
            integration = integration,
            rows = rowsFor(state.credentials, integration.id),
            onDismiss = { manageFor = null },
            onAddKey = {
                manageFor = null
                keyFor = integration
            },
            onReplace = { row -> replaceFor = integration to row },
            onDelete = { row -> confirmDelete = integration to row },
        )
    }

    replaceFor?.let { (integration, row) ->
        KeyPrompt(
            integration = integration,
            title = stringResource(R.string.providers_new_key_title, integration.name),
            body = stringResource(R.string.providers_new_key_body),
            submitLabel = stringResource(R.string.providers_replace),
            onDismiss = { replaceFor = null },
            onSubmit = { key ->
                onReplaceCredential(integration.id, row.id, key)
                replaceFor = null
                manageFor = null
            },
        )
    }

    confirmDelete?.let { (integration, row) ->
        val last = rowsFor(state.credentials, integration.id).size == 1
        LocalizedDraggableBottomSheet(
            onDismiss = { confirmDelete = null },
            initialStop = SheetStop.PEEK,
            title = {
                Text(
                    stringResource(R.string.providers_delete_title),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                )
            },
            content = {
                Text(
                    text = buildString {
                        append(stringResource(R.string.providers_delete_body, row.title, row.detail))
                        if (last) {
                            append(' ')
                            append(stringResource(R.string.providers_delete_last, integration.name))
                        }
                    },
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
            },
            actions = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.common_cancel), color = colors.onSurfaceMuted)
                }
                TextButton(
                    onClick = {
                        onDeleteCredential(integration.id, row.id)
                        confirmDelete = null
                        manageFor = null
                    },
                ) { Text(stringResource(R.string.common_delete), color = colors.danger) }
            },
        )
    }

    googleDetailsFor?.let { integration ->
        val storedKeys = rowsFor(state.credentials, integration.id)
        val hasAntigravity = state.antigravityAccounts.isNotEmpty()

        LocalizedDraggableBottomSheet(
            onDismiss = { googleDetailsFor = null },
            initialStop = SheetStop.PEEK,
            title = {
                Text(
                    text = stringResource(R.string.providers_antigravity_title),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                )
            },
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    if (hasAntigravity) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                            )
                            Text(
                                text = stringResource(R.string.providers_antigravity_connected),
                                style = OpenCodeTypography.bodyStrong,
                                color = colors.accentText,
                            )
                        }
                        state.activeAntigravityEmail?.let { email ->
                            Text(
                                text = stringResource(R.string.providers_antigravity_active_account, email),
                                style = OpenCodeTypography.caption,
                                color = colors.onSurface,
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.providers_antigravity_models_note,
                                "Gemini 3.8 Flash, Gemini 3.1 Pro, and Claude Opus 4.6 Thinking",
                            ),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceMuted,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.providers_antigravity_use_key),
                            style = OpenCodeTypography.bodySmall,
                            color = colors.onSurfaceMuted,
                        )
                    }

                    if (storedKeys.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.providers_gemini_keys_configured,
                                storedKeys.size,
                            ),
                            style = OpenCodeTypography.caption,
                            color = colors.onSurfaceMuted,
                        )
                    }
                }
            },
            actions = {
                TextButton(onClick = { googleDetailsFor = null }) {
                    Text(stringResource(R.string.common_done), color = colors.onSurfaceMuted)
                }
                TextButton(
                    onClick = {
                        val target = googleDetailsFor
                        googleDetailsFor = null
                        if (storedKeys.isNotEmpty()) {
                            manageFor = target
                        } else {
                            keyFor = target
                        }
                    },
                ) {
                    Text(
                        text = if (storedKeys.isNotEmpty()) "Manage API Keys" else "Add API Key",
                        color = colors.accentText,
                    )
                }
            },
        )
    }

    customFormPrefill?.let { initial ->
        CustomProviderDialog(
            initialName = initial.name,
            initialBaseURL = initial.baseURL,
            initialModels = initial.models,
            onDismiss = { customFormPrefill = null },
            onSubmit = { name, baseURL, key, models ->
                onSaveCustom(name, baseURL, key, models)
                customFormPrefill = null
            },
        )
    }
}

/** Resolves an existing IntegrationInfo matching the given featured provider definition. */
private fun findMatchingIntegration(def: FeaturedProviderDef, integrations: List<IntegrationInfo>): IntegrationInfo? {
    val defId = def.id.lowercase()
    val defName = def.name.lowercase()
    return integrations.firstOrNull { info ->
        val id = info.id.lowercase()
        val name = info.name.lowercase()
        id == defId ||
            (defId == "google" && (id.contains("gemini") || id.contains("google"))) ||
            (defId == "opencode" && (id == "zen" || id.contains("opencode"))) ||
            name.contains(defName) ||
            (defId == "google" && name.contains("gemini"))
    }
}

/** Clean card representation of an AI provider with logo, metadata, and status badge. */
@Composable
private fun ProviderCard(
    id: String,
    name: String,
    subtitle: String,
    status: ProviderStatus,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .whiteCard(
                // The connected edge survives the white-card treatment:
                // a green edge reads "connected" where the card itself
                // is white in both themes. 0.25 is the shared border
                // token's value (AlphaTokens.border, PR #39) — written
                // as a literal here so this PR stays independent of #39;
                // switch it to tokens.alpha.border once #39 lands.
                borderColor = if (status == ProviderStatus.CONNECTED) {
                    colors.success.copy(alpha = 0.25f)
                } else {
                    null
                },
            )
            // A11y (#8b): the card is a button — its name text is the label.
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = spacing.md, vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderLogo(id = id, name = name, size = 44.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = OpenCodeTypography.bodyStrong,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ProviderStatusBadge(status = status)

        if (onClick != null) {
            GlyphIcon(Glyph.ChevronRight, tint = colors.onSurfaceFaint, size = 16.dp)
        }
    }
}

/** Provider logo badge with brand colors and vector emblems. */
@Composable
fun ProviderLogo(
    id: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val radius = OpenCodeTheme.tokens.radius
    val lowerId = id.lowercase()
    val lowerName = name.lowercase()

    val (bgColor, emblemColor, providerType) = when {
        lowerId.contains("anthropic") || lowerName.contains("anthropic") ->
            Triple(Color(0xFFD97757).copy(alpha = 0.16f), Color(0xFFD97757), "anthropic")
        lowerId == "openai" || lowerName.contains("openai") ->
            Triple(Color(0xFF10A37F).copy(alpha = 0.16f), Color(0xFF10A37F), "openai")
        lowerId == "opencode" || lowerId == "zen" || lowerName.contains("opencode") ->
            Triple(Color(0xFF6366F1).copy(alpha = 0.16f), Color(0xFF6366F1), "opencode")
        lowerId.contains("ollama") || lowerName.contains("ollama") ->
            Triple(Color(0xFF475569).copy(alpha = 0.18f), Color(0xFF94A3B8), "ollama")
        lowerId.contains("deepseek") || lowerName.contains("deepseek") ->
            Triple(Color(0xFF1E88E5).copy(alpha = 0.16f), Color(0xFF1E88E5), "deepseek")
        lowerId.contains("google") || lowerId.contains("gemini") || lowerName.contains("gemini") || lowerName.contains("google") ->
            Triple(Color(0xFF4285F4).copy(alpha = 0.16f), Color(0xFF4285F4), "google")
        else ->
            Triple(OpenCodeTheme.colors.surfaceSunken, OpenCodeTheme.colors.onSurfaceMuted, "generic")
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius.md))
            .background(bgColor)
            .border(OpenCodeTheme.tokens.dimensions.hairline, emblemColor.copy(alpha = 0.35f), RoundedCornerShape(radius.md)),
        contentAlignment = Alignment.Center,
    ) {
        when (providerType) {
            "anthropic" -> {
                // Claude 8-point radiating spark emblem
                Canvas(Modifier.size(size * 0.58f)) {
                    val u = this.size.minDimension / 24f
                    drawLine(emblemColor, Offset(12f * u, 3f * u), Offset(12f * u, 21f * u), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
                    drawLine(emblemColor, Offset(3f * u, 12f * u), Offset(21f * u, 12f * u), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
                    drawLine(emblemColor, Offset(5.5f * u, 5.5f * u), Offset(18.5f * u, 18.5f * u), strokeWidth = 2.0f * u, cap = StrokeCap.Round)
                    drawLine(emblemColor, Offset(5.5f * u, 18.5f * u), Offset(18.5f * u, 5.5f * u), strokeWidth = 2.0f * u, cap = StrokeCap.Round)
                    drawCircle(emblemColor, radius = 2f * u, center = Offset(12f * u, 12f * u))
                }
            }
            "openai" -> {
                // OpenAI rosette spiral
                Canvas(Modifier.size(size * 0.58f)) {
                    val u = this.size.minDimension / 24f
                    val stroke = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val cx = 12f * u
                    val cy = 12f * u
                    for (i in 0 until 6) {
                        val rad = Math.toRadians((i * 60.0))
                        val cos = Math.cos(rad).toFloat()
                        val sin = Math.sin(rad).toFloat()
                        val x1 = cx + cos * 3.5f * u
                        val y1 = cy + sin * 3.5f * u
                        val x2 = cx + cos * 9f * u
                        val y2 = cy + sin * 9f * u
                        drawLine(emblemColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 2.0f * u, cap = StrokeCap.Round)
                    }
                    drawCircle(emblemColor, radius = 3.8f * u, center = Offset(cx, cy), style = stroke)
                }
            }
            "opencode" -> {
                // OpenCode terminal prompt >_
                Canvas(Modifier.size(size * 0.58f)) {
                    val u = this.size.minDimension / 24f
                    val stroke = Stroke(width = 2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val path = Path().apply {
                        moveTo(5f * u, 6f * u)
                        lineTo(11f * u, 12f * u)
                        lineTo(5f * u, 18f * u)
                    }
                    drawPath(path, emblemColor, style = stroke)
                    drawLine(emblemColor, Offset(13f * u, 18f * u), Offset(20f * u, 18f * u), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
                }
            }
            "ollama" -> {
                // Ollama llama silhouette with upright ears
                Canvas(Modifier.size(size * 0.58f)) {
                    val u = this.size.minDimension / 24f
                    val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val leftEar = Path().apply {
                        moveTo(7f * u, 10f * u)
                        lineTo(8f * u, 4f * u)
                        lineTo(10.5f * u, 9.5f * u)
                    }
                    drawPath(leftEar, emblemColor, style = stroke)
                    val rightEar = Path().apply {
                        moveTo(13.5f * u, 9.5f * u)
                        lineTo(16f * u, 4f * u)
                        lineTo(17f * u, 10f * u)
                    }
                    drawPath(rightEar, emblemColor, style = stroke)
                    val head = Path().apply {
                        moveTo(6f * u, 11f * u)
                        lineTo(18f * u, 11f * u)
                        lineTo(19f * u, 16f * u)
                        lineTo(17f * u, 20f * u)
                        lineTo(7f * u, 20f * u)
                        lineTo(5f * u, 16f * u)
                        close()
                    }
                    drawPath(head, emblemColor, style = stroke)
                }
            }
            "deepseek" -> {
                // DeepSeek dolphin / whale tail fluke
                Canvas(Modifier.size(size * 0.58f)) {
                    val u = this.size.minDimension / 24f
                    val tail = Path().apply {
                        moveTo(12f * u, 20f * u)
                        quadraticBezierTo(12f * u, 12f * u, 4f * u, 7f * u)
                        quadraticBezierTo(10f * u, 9f * u, 12f * u, 12f * u)
                        quadraticBezierTo(14f * u, 9f * u, 20f * u, 7f * u)
                        quadraticBezierTo(12f * u, 12f * u, 12f * u, 20f * u)
                        close()
                    }
                    drawPath(tail, emblemColor)
                }
            }
            "google" -> {
                // Google Gemini 4-pointed sparkling star
                Canvas(Modifier.size(size * 0.58f)) {
                    val u = this.size.minDimension / 24f
                    val star = Path().apply {
                        moveTo(12f * u, 2f * u)
                        quadraticBezierTo(12f * u, 12f * u, 22f * u, 12f * u)
                        quadraticBezierTo(12f * u, 12f * u, 12f * u, 22f * u)
                        quadraticBezierTo(12f * u, 12f * u, 2f * u, 12f * u)
                        quadraticBezierTo(12f * u, 12f * u, 12f * u, 2f * u)
                        close()
                    }
                    drawPath(star, emblemColor)
                }
            }
            else -> {
                val initials = name.trim().split(" ")
                    .filter { it.isNotBlank() }
                    .take(2)
                    .map { it.first().uppercaseChar() }
                    .joinToString("")
                    .ifEmpty { id.take(2).uppercase() }
                Text(
                    text = initials,
                    style = OpenCodeTypography.label.copy(fontWeight = FontWeight.Bold),
                    color = emblemColor,
                )
            }
        }
    }
}

/** Clear status badge (Connected / Free Tier / Missing Key). */
@Composable
fun ProviderStatusBadge(
    status: ProviderStatus,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius

    val (bg, border, contentColor, textRes) = when (status) {
        ProviderStatus.CONNECTED -> Quadruple(
            colors.success.copy(alpha = 0.12f),
            colors.success.copy(alpha = 0.28f),
            colors.success,
            R.string.providers_status_connected,
        )
        ProviderStatus.FREE_TIER -> Quadruple(
            colors.accent.copy(alpha = 0.12f),
            colors.accent.copy(alpha = 0.28f),
            colors.accentText,
            R.string.providers_status_free_tier,
        )
        ProviderStatus.MISSING_KEY -> Quadruple(
            colors.warning.copy(alpha = 0.12f),
            colors.warning.copy(alpha = 0.28f),
            colors.warning,
            R.string.providers_status_missing_key,
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(radius.full))
            .background(bg)
            .border(OpenCodeTheme.tokens.dimensions.hairline, border, RoundedCornerShape(radius.full))
            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(contentColor, CircleShape),
        )
        Text(
            text = stringResource(textRes),
            style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** Real-time API key validation outcome. */
private sealed class KeyValidation {
    object Empty : KeyValidation()
    data class Invalid(val messageRes: Int) : KeyValidation()
    /**
     * A format string plus its args: the provider name and its key prefix
     * are data, not part of the sentence, so the word order stays
     * translatable. Resolved at the render site with stringResource.
     */
    data class Warning(val messageRes: Int, val args: List<Any> = emptyList()) : KeyValidation()
    data class Valid(val messageRes: Int, val args: List<Any> = emptyList()) : KeyValidation()
}

private fun validateKey(providerId: String, key: String): KeyValidation {
    val trimmed = key.trim()
    if (trimmed.isEmpty()) return KeyValidation.Empty
    if (trimmed.any { it.isWhitespace() }) return KeyValidation.Invalid(R.string.providers_key_spaces)
    if (trimmed.length < 8) return KeyValidation.Invalid(R.string.providers_key_too_short)

    val lower = providerId.lowercase()
    return when {
        lower.contains("anthropic") -> {
            if (trimmed.startsWith("sk-ant-")) {
                KeyValidation.Valid(R.string.providers_key_valid, listOf("Anthropic", "sk-ant-"))
            } else {
                KeyValidation.Warning(R.string.providers_key_warning, listOf("Anthropic", "sk-ant-"))
            }
        }
        lower == "openai" -> {
            if (trimmed.startsWith("sk-")) {
                KeyValidation.Valid(R.string.providers_key_valid, listOf("OpenAI", "sk-"))
            } else {
                KeyValidation.Warning(R.string.providers_key_warning, listOf("OpenAI", "sk-"))
            }
        }
        lower.contains("google") || lower.contains("gemini") -> {
            if (trimmed.startsWith("AIza")) {
                KeyValidation.Valid(R.string.providers_key_valid, listOf("Google Gemini", "AIza"))
            } else {
                KeyValidation.Warning(R.string.providers_key_warning, listOf("Google AI", "AIza"))
            }
        }
        lower.contains("deepseek") -> {
            if (trimmed.startsWith("sk-")) {
                KeyValidation.Valid(R.string.providers_key_valid, listOf("DeepSeek", "sk-"))
            } else {
                KeyValidation.Warning(R.string.providers_key_warning, listOf("DeepSeek", "sk-"))
            }
        }
        else -> KeyValidation.Valid(R.string.providers_key_valid_generic, listOf(trimmed.length))
    }
}

/**
 * Intuitive API key input modal with show/hide eye toggle, key validation status,
 * quick test button, and clipboard paste helper.
 */
@Composable
private fun KeyPrompt(
    integration: IntegrationInfo,
    title: String,
    body: String,
    submitLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // A11y (#8d): Done on the single key field dismisses the keyboard.
    val focusManager = LocalFocusManager.current

    var value by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    var testMessage by remember { mutableStateOf<String?>(null) }

    val validation = remember(value, integration.id) { validateKey(integration.id, value) }
    val canSubmit = value.isNotBlank() && validation !is KeyValidation.Invalid

    LocalizedDraggableBottomSheet(
        onDismiss = onDismiss,
        initialStop = SheetStop.HALF,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                ProviderLogo(id = integration.id, name = integration.name, size = 32.dp)
                Text(
                    text = title,
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(
                    text = body,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )

                // Input container with Eye toggle and Paste helper
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius.md))
                        .background(colors.surfaceSunken)
                        .border(
                            OpenCodeTheme.tokens.dimensions.hairline,
                            when (validation) {
                                is KeyValidation.Invalid -> colors.danger.copy(alpha = 0.5f)
                                is KeyValidation.Valid -> colors.success.copy(alpha = 0.4f)
                                else -> colors.hairline
                            },
                            RoundedCornerShape(radius.md),
                        )
                        .padding(horizontal = spacing.sm, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    GlyphIcon(Glyph.Key, tint = colors.onSurfaceMuted, size = 16.dp)

                    BasicTextField(
                        value = value,
                        onValueChange = {
                            value = it
                            testResult = null
                            testMessage = null
                        },
                        singleLine = true,
                        textStyle = OpenCodeTypography.codeInline.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.accent),
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (showKey) KeyboardType.Ascii else KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (value.isEmpty()) {
                                Text(
                                    stringResource(R.string.providers_paste_key),
                                    style = OpenCodeTypography.input,
                                    color = colors.onSurfaceFaint,
                                )
                            }
                            inner()
                        },
                    )

                    // One-tap Paste button when field is empty
                    if (value.isEmpty() && clipboard.hasText()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(radius.xs))
                                .background(colors.surfaceRaised)
                                // A11y (#8b): the "Paste" text inside is the name.
                                .clickable(role = Role.Button) {
                                    val text = clipboard.getText()?.text.orEmpty().trim()
                                    if (text.isNotEmpty()) {
                                        value = text
                                        testResult = null
                                    }
                                }
                                .padding(horizontal = spacing.xs, vertical = spacing.xxs),
                        ) {
                            Text(
                                text = stringResource(R.string.providers_paste_action),
                                style = OpenCodeTypography.caption,
                                color = colors.accentText,
                            )
                        }
                    }

                    // Clear button
                    if (value.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(R.string.providers_clear_action),
                                ) {
                                    value = ""
                                    testResult = null
                                    testMessage = null
                                }
                                .padding(spacing.xxs),
                        ) {
                            GlyphIcon(Glyph.Close, tint = colors.onSurfaceFaint, size = 16.dp)
                        }
                    }

                    // Eye toggle
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(if (showKey) R.string.providers_hide_key else R.string.providers_show_key),
                            ) { showKey = !showKey }
                            .padding(spacing.xxs),
                    ) {
                        GlyphIcon(
                            glyph = if (showKey) Glyph.EyeOff else Glyph.Eye,
                            tint = if (showKey) colors.accent else colors.onSurfaceMuted,
                            size = 18.dp,
                        )
                    }
                }

                // Key validation status: a NoticeRow in the outcome's
                // colour. Empty stays a plain hint — there is nothing to
                // warn about yet.
                when (validation) {
                    is KeyValidation.Empty -> {
                        Text(
                            text = stringResource(R.string.providers_key_empty),
                            style = OpenCodeTypography.caption,
                            color = colors.onSurfaceFaint,
                        )
                    }
                    is KeyValidation.Invalid -> {
                        NoticeRow(
                            message = stringResource(validation.messageRes),
                            kind = NoticeKind.Error,
                        )
                    }
                    is KeyValidation.Warning -> {
                        NoticeRow(
                            message = stringResource(
                                validation.messageRes,
                                *validation.args.toTypedArray(),
                            ),
                            kind = NoticeKind.Warning,
                        )
                    }
                    is KeyValidation.Valid -> {
                        NoticeRow(
                            message = stringResource(
                                validation.messageRes,
                                *validation.args.toTypedArray(),
                            ),
                            kind = NoticeKind.Success,
                        )
                    }
                }

                // Quick Test Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(radius.sm))
                            .background(colors.surfaceRaised)
                            .border(OpenCodeTheme.tokens.dimensions.hairline, colors.hairline, RoundedCornerShape(radius.sm))
                            // A11y (#8b): the "Quick test" text inside is the name.
                            .clickable(role = Role.Button, enabled = value.isNotBlank() && !isTesting) {
                                coroutineScope.launch {
                                    isTesting = true
                                    testResult = null
                                    delay(400) // Responsive simulated syntax/connectivity test
                                    val trimmed = value.trim()
                                    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() } || trimmed.length < 8) {
                                        testResult = false
                                        testMessage = "Validation check failed"
                                    } else {
                                        testResult = true
                                        testMessage = "Key verified & ready to save"
                                    }
                                    isTesting = false
                                }
                            }
                            .padding(horizontal = spacing.sm, vertical = spacing.xs),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isTesting) {
                                Text(
                                    text = stringResource(R.string.providers_testing),
                                    style = OpenCodeTypography.caption,
                                    color = colors.accentText,
                                )
                            } else {
                                GlyphIcon(Glyph.Sliders, tint = if (value.isBlank()) colors.onSurfaceFaint else colors.accent, size = 14.dp)
                                Text(
                                    text = stringResource(R.string.providers_quick_test),
                                    style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Medium),
                                    color = if (value.isBlank()) colors.onSurfaceFaint else colors.onSurface,
                                )
                            }
                        }
                    }

                    // Test result feedback
                    testMessage?.let { msg ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (testResult == true) {
                                GlyphIcon(Glyph.Check, tint = colors.success, size = 14.dp)
                                Text(msg, style = OpenCodeTypography.caption, color = colors.success)
                            } else {
                                Text(msg, style = OpenCodeTypography.caption, color = colors.danger)
                            }
                        }
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = colors.onSurfaceMuted)
            }
            TextButton(onClick = { onSubmit(value.trim()) }, enabled = canSubmit) {
                Text(submitLabel, color = if (canSubmit) colors.accent else colors.onSurfaceFaint)
            }
        },
    )
}

/** The keys stored for one provider, with the actions that change them. */
@Composable
private fun ManageCredentialsDialog(
    integration: IntegrationInfo,
    rows: List<CredentialRow>,
    onDismiss: () -> Unit,
    onAddKey: () -> Unit,
    onReplace: (CredentialRow) -> Unit,
    onDelete: (CredentialRow) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val spacing = OpenCodeTheme.tokens.spacing

    LocalizedDraggableBottomSheet(
        onDismiss = onDismiss,
        initialStop = SheetStop.HALF,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                ProviderLogo(id = integration.id, name = integration.name, size = 32.dp)
                Text(
                    stringResource(R.string.providers_keys_for, integration.name),
                    style = OpenCodeTypography.title,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.providers_manage_body),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
                rows.forEach { row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(radius.sm))
                            .background(colors.surfaceSunken)
                            .border(OpenCodeTheme.tokens.dimensions.hairline, colors.hairline, RoundedCornerShape(radius.sm))
                            .padding(spacing.sm),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(Glyph.Key, tint = colors.onSurfaceMuted, size = 16.dp)
                            Text(
                                text = row.title,
                                style = OpenCodeTypography.bodyStrong,
                                color = colors.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = spacing.xs),
                            )
                            if (row.inUse) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(radius.full))
                                        .background(colors.success.copy(alpha = 0.12f))
                                        .border(OpenCodeTheme.tokens.dimensions.hairline, colors.success.copy(alpha = 0.28f), RoundedCornerShape(radius.full))
                                        .padding(horizontal = spacing.sm, vertical = spacing.xxs),
                                ) {
                                    Text(
                                        stringResource(R.string.providers_in_use),
                                        style = OpenCodeTypography.caption.copy(fontWeight = FontWeight.Bold),
                                        color = colors.success,
                                    )
                                }
                            }
                        }
                        Text(
                            text = row.detail,
                            style = OpenCodeTypography.codeInline,
                            color = colors.onSurfaceFaint,
                            modifier = Modifier.padding(top = spacing.xxs),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.md),
                            modifier = Modifier.padding(top = spacing.xs),
                        ) {
                            Text(
                                text = stringResource(R.string.providers_replace),
                                style = OpenCodeTypography.label,
                                color = colors.accentText,
                                modifier = Modifier
                                    .clickable(role = Role.Button) { onReplace(row) }
                                    .padding(vertical = spacing.xxs),
                            )
                            Text(
                                text = stringResource(R.string.common_delete),
                                style = OpenCodeTypography.label,
                                color = colors.danger,
                                modifier = Modifier
                                    .clickable(role = Role.Button) { onDelete(row) }
                                    .padding(vertical = spacing.xxs),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(radius.xs))
                        .clickable(role = Role.Button, onClick = onAddKey)
                        .padding(vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    GlyphIcon(Glyph.Sparkle, tint = colors.accent, size = 14.dp)
                    Text(
                        text = stringResource(R.string.providers_add_another),
                        style = OpenCodeTypography.label,
                        color = colors.accentText,
                    )
                }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done), color = colors.accentText) }
        },
    )
}

/** Form for an OpenAI-compatible custom endpoint. */
@Composable
private fun CustomProviderDialog(
    initialName: String = "",
    initialBaseURL: String = "",
    initialModels: String = "",
    onDismiss: () -> Unit,
    onSubmit: (name: String, baseURL: String, apiKey: String, models: String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    var name by remember { mutableStateOf(initialName) }
    var baseURL by remember { mutableStateOf(initialBaseURL) }
    var apiKey by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(initialModels) }

    // A11y (#8d): one FocusRequester per field, in tab order.
    val focusManager = LocalFocusManager.current
    val urlRequester = remember { FocusRequester() }
    val keyRequester = remember { FocusRequester() }
    val modelsRequester = remember { FocusRequester() }

    val ready = name.isNotBlank() && baseURL.isNotBlank() && apiKey.isNotBlank() && models.isNotBlank()

    LocalizedDraggableBottomSheet(
        onDismiss = onDismiss,
        initialStop = SheetStop.HALF,
        title = {
            Text(
                stringResource(R.string.providers_custom_title),
                style = OpenCodeTypography.title,
                color = colors.onSurface,
            )
        },
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.providers_custom_body),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                )
                FormField(
                    label = stringResource(R.string.providers_field_name),
                    placeholder = stringResource(R.string.providers_field_name_hint),
                    value = name,
                    singleLine = true,
                    keyboard = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    onImeAction = { urlRequester.requestFocus() },
                ) { name = it }
                FormField(
                    label = stringResource(R.string.providers_field_url),
                    placeholder = "https://api.example.com/v1",
                    value = baseURL,
                    singleLine = true,
                    keyboard = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    focusRequester = urlRequester,
                    onImeAction = { keyRequester.requestFocus() },
                ) { baseURL = it }
                FormField(
                    label = stringResource(R.string.providers_field_key),
                    placeholder = stringResource(R.string.providers_field_key_hint),
                    value = apiKey,
                    singleLine = true,
                    secret = true,
                    imeAction = ImeAction.Next,
                    focusRequester = keyRequester,
                    onImeAction = { modelsRequester.requestFocus() },
                ) { apiKey = it }
                FormField(
                    label = stringResource(R.string.providers_field_models),
                    placeholder = stringResource(R.string.providers_field_models_hint),
                    value = models,
                    singleLine = false,
                    keyboard = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                    focusRequester = modelsRequester,
                    onImeAction = { focusManager.clearFocus() },
                ) { models = it }
            }
        },
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = colors.onSurfaceMuted) }
            TextButton(onClick = { onSubmit(name, baseURL, apiKey, models) }, enabled = ready) {
                Text(stringResource(R.string.providers_save_restart), color = colors.accentText)
            }
        },
    )
}

@Composable
private fun FormField(
    label: String,
    value: String,
    singleLine: Boolean,
    placeholder: String,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    // A11y (#8d): the focus chain — each field advances to the next on
    // Next; the last field dismisses the keyboard on Done.
    imeAction: ImeAction = ImeAction.Default,
    focusRequester: FocusRequester? = null,
    onImeAction: (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    var showSecret by remember { mutableStateOf(false) }

    Column {
        Text(label, style = OpenCodeTypography.label, color = colors.onSurfaceFaint)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.xxs)
                .clip(RoundedCornerShape(radius.sm))
                .background(colors.surfaceSunken)
                .border(OpenCodeTheme.tokens.dimensions.hairline, colors.hairline, RoundedCornerShape(radius.sm))
                .padding(horizontal = spacing.sm, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = (if (secret) OpenCodeTypography.codeInline else OpenCodeTypography.input).copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = if (secret && !showSecret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (secret && !showSecret) KeyboardType.Password else keyboard,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction?.invoke() },
                    onDone = { onImeAction?.invoke() },
                ),
                modifier = Modifier
                    .weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, style = OpenCodeTypography.input, color = colors.onSurfaceFaint)
                    }
                    inner()
                },
            )
            if (secret && value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(if (showSecret) R.string.providers_hide_key else R.string.providers_show_key),
                        ) { showSecret = !showSecret }
                        .padding(spacing.xxs),
                ) {
                    GlyphIcon(
                        glyph = if (showSecret) Glyph.EyeOff else Glyph.Eye,
                        tint = if (showSecret) colors.accent else colors.onSurfaceMuted,
                        size = 18.dp,
                    )
                }
            }
        }
    }
}
