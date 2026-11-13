package dev.bidzz.opencode.core.api.dto

import kotlinx.serialization.Serializable

/**
 * One-shot generation + direct web search (mockup group 5, engine v2.2.7).
 *
 * Shapes verified against the engine fork source at pin fedc6f20
 * (`packages/protocol/src/groups/{websearch,generate,session}.ts`,
 * `packages/schema/src/{websearch,generate,model,token-usage,money}.ts`):
 *
 * - Web search: providers list and one query at a time. The query
 *   answers `{location, data}` (the `location` key is ignored, like
 *   every other `{data}` envelope here).
 * - Generate text is stateless and uses the base configuration's
 *   default model when none is given. Generate image needs a model
 *   (image models only — the engine rejects others with
 *   `UnsupportedModelError`).
 * - The deprecated alias `POST /api/experimental/generate` is not
 *   used; the canonical paths are `/generate/text` and
 *   `/generate/image`.
 * - Session generate is transient: it answers from the current
 *   session context without mutating session history, but its tokens
 *   and cost count toward the session's totals.
 * - `Model.Ref` is the existing [ModelRef] (`{id, providerID,
 *   variant?}`); [ModelInfo.ref] builds one from a listed model.
 *   `Money.USD` is a plain number → [Double]; `TokenUsage.Info`
 *   is the existing [TokenUsage].
 */
@Serializable
data class WebSearchProvider(
    val id: String,
    val name: String,
)

@Serializable
data class WebSearchProvidersResponse(
    val data: List<WebSearchProvider> = emptyList(),
)

@Serializable
data class WebSearchInput(
    val query: String,
    val providerID: String? = null,
)

@Serializable
data class WebSearchResult(
    val url: String,
    val title: String? = null,
    val content: String? = null,
    val time: WebSearchResultTime? = null,
)

@Serializable
data class WebSearchResultTime(
    val published: Long? = null,
)

@Serializable
data class WebSearchResponse(
    val providerID: String,
    val results: List<WebSearchResult> = emptyList(),
)

@Serializable
data class GenerateTextInput(
    val prompt: String,
    val model: ModelRef? = null,
)

@Serializable
data class GenerateTextResponse(
    val data: GenerateTextResult,
)

@Serializable
data class GenerateTextResult(
    val text: String,
)

/**
 * One image file, base64-encoded. `data` is the raw base64 payload
 * (no `data:` prefix); `mime` says how to render it.
 */
@Serializable
data class GenerateImageFile(
    val data: String,
    val mime: String,
    val name: String? = null,
)

@Serializable
data class GenerateImageInput(
    val model: ModelRef,
    val prompt: String,
    val images: List<GenerateImageFile>? = null,
    val n: Int? = null,
    val aspect: String? = null,
    val resolution: String? = null,
)

@Serializable
data class GenerateImageOutput(
    val type: String,
    val data: String,
    val mime: String,
    val name: String? = null,
)

@Serializable
data class GenerateImageUsage(
    val input: Long? = null,
    val output: Long? = null,
)

@Serializable
data class GenerateImageResult(
    val images: List<GenerateImageOutput> = emptyList(),
    val cost: Double? = null,
    val tokens: GenerateImageUsage? = null,
)

@Serializable
data class GenerateImageResponse(
    val data: GenerateImageResult,
)

@Serializable
data class SessionGenerateInput(
    val prompt: String,
)

@Serializable
data class SessionGenerateResult(
    val text: String,
    val tokens: TokenUsage,
    val cost: Double,
)

@Serializable
data class SessionGenerateResponse(
    val data: SessionGenerateResult,
)
