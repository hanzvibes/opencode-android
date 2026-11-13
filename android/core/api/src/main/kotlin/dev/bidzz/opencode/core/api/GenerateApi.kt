package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.GenerateImageInput
import dev.bidzz.opencode.core.api.dto.GenerateImageResponse
import dev.bidzz.opencode.core.api.dto.GenerateTextInput
import dev.bidzz.opencode.core.api.dto.GenerateTextResponse
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.api.dto.SessionGenerateInput
import dev.bidzz.opencode.core.api.dto.SessionGenerateResponse
import kotlinx.serialization.encodeToString
import java.net.URLEncoder

/**
 * One-shot generation (mockup group 5, engine v2.2.7).
 *
 * All three routes are experimental and stateless — nothing they
 * produce is stored on the engine. Shapes verified against the fork
 * source at pin fedc6f20 (`packages/protocol/src/groups/generate.ts`
 * + `session.ts`, `packages/schema/src/generate.ts`):
 *
 * - Text: `POST /api/experimental/generate/text` with `{prompt,
 *   model?}` → `{data: {text}}`. The model is optional; without it
 *   the engine uses the base configuration's default model.
 * - Image: `POST /api/experimental/generate/image` with `{model
 *   (required), prompt, images?, n?, aspect?, resolution?}` → `{data:
 *   {images: [{type: "image", data (base64), mime, name?}], cost?,
 *   tokens?}}`. `images` are edit sources or style references.
 * - Session: `POST /api/session/{id}/generate` with `{prompt}` →
 *   `{data: {text, tokens, cost}}`. Transient (no history mutation),
 *   but the tokens and cost count toward the session's totals.
 */
class GenerateApi(private val client: EngineClient) {

    suspend fun generateText(prompt: String, model: ModelRef? = null): ApiResult<String> {
        val body = ApiJson.encodeToString(GenerateTextInput(prompt = prompt, model = model))
        val request = client.request("/api/experimental/generate/text")
            .post(client.jsonBody(body)).build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<GenerateTextResponse>(raw).data.text
        }
    }

    suspend fun generateImage(input: GenerateImageInput): ApiResult<dev.bidzz.opencode.core.api.dto.GenerateImageResult> {
        val body = ApiJson.encodeToString(input)
        val request = client.request("/api/experimental/generate/image")
            .post(client.jsonBody(body)).build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<GenerateImageResponse>(raw).data
        }
    }

    suspend fun sessionGenerate(sessionID: String, prompt: String): ApiResult<dev.bidzz.opencode.core.api.dto.SessionGenerateResult> {
        val body = ApiJson.encodeToString(SessionGenerateInput(prompt = prompt))
        val request = client.request("/api/session/${encode(sessionID)}/generate")
            .post(client.jsonBody(body)).build()
        return client.execute(request) { raw ->
            ApiJson.decodeFromString<SessionGenerateResponse>(raw).data
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
