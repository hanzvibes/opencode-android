package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.GenerateImageInput
import dev.bidzz.opencode.core.api.dto.GenerateImageResponse
import dev.bidzz.opencode.core.api.dto.GenerateTextInput
import dev.bidzz.opencode.core.api.dto.GenerateTextResponse
import dev.bidzz.opencode.core.api.dto.ModelRef
import dev.bidzz.opencode.core.api.dto.SessionGenerateResponse
import dev.bidzz.opencode.core.api.dto.SingleResponse
import dev.bidzz.opencode.core.api.dto.WebSearchProvidersResponse
import dev.bidzz.opencode.core.api.dto.WebSearchResponse
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Decoding/encoding of the generate + web search contracts (mockup
 * group 5, engine v2.2.7, fork pin fedc6f20). The raw payloads mirror
 * the engine's answers, including the `{location, data}` wrapper on
 * the web search routes and the base64 image payloads.
 */
class GenerateWebSearchDecodingTest {

    @Test
    fun `decodes web search providers`() {
        val parsed = ApiJson.decodeFromString<WebSearchProvidersResponse>(
            """{"location":{"directory":"/tmp"},"data":[
                {"id":"tavily","name":"Tavily"},
                {"id":"exa","name":"Exa"}
            ]}""",
        )
        assertEquals(2, parsed.data.size)
        assertEquals("tavily", parsed.data[0].id)
        assertEquals("Exa", parsed.data[1].name)
    }

    @Test
    fun `decodes web search results with optional fields`() {
        val parsed = ApiJson.decodeFromString<SingleResponse<WebSearchResponse>>(
            """{"location":{"directory":"/tmp"},"data":{"providerID":"tavily","results":[
                {"url":"https://example.com/a","title":"A","content":"First result","time":{"published":1762900000000}},
                {"url":"https://example.com/b"}
            ]}}""",
        )
        assertEquals("tavily", parsed.data.providerID)
        assertEquals(2, parsed.data.results.size)
        assertEquals("First result", parsed.data.results[0].content)
        assertEquals(1762900000000L, parsed.data.results[0].time?.published)
        assertNull(parsed.data.results[1].title)
        assertNull(parsed.data.results[1].content)
        assertNull(parsed.data.results[1].time)
    }

    @Test
    fun `generate text input omits a missing model`() {
        val encoded = ApiJson.encodeToString(GenerateTextInput(prompt = "Hello"))
        assertEquals("""{"prompt":"Hello"}""", encoded)
    }

    @Test
    fun `generate text input encodes a model ref`() {
        val encoded = ApiJson.encodeToString(
            GenerateTextInput(
                prompt = "Hello",
                model = ModelRef(id = "gpt-5", providerID = "openai"),
            ),
        )
        assertEquals(
            """{"prompt":"Hello","model":{"id":"gpt-5","providerID":"openai"}}""",
            encoded,
        )
    }

    @Test
    fun `decodes a generate text answer`() {
        val parsed = ApiJson.decodeFromString<GenerateTextResponse>(
            """{"data":{"text":"Hello back"}}""",
        )
        assertEquals("Hello back", parsed.data.text)
    }

    @Test
    fun `generate image input encodes model prompt and options`() {
        val encoded = ApiJson.encodeToString(
            GenerateImageInput(
                model = ModelRef(id = "gpt-image-1", providerID = "openai"),
                prompt = "A cat in a hat",
                n = 2,
                aspect = "16:9",
                resolution = "2K",
            ),
        )
        assertEquals(
            """{"model":{"id":"gpt-image-1","providerID":"openai"},"prompt":"A cat in a hat","n":2,"aspect":"16:9","resolution":"2K"}""",
            encoded,
        )
    }

    @Test
    fun `decodes a generate image answer with cost and tokens`() {
        val parsed = ApiJson.decodeFromString<GenerateImageResponse>(
            """{"data":{"images":[
                {"type":"image","data":"iVBORw0KGgo=","mime":"image/png","name":"cat.png"}
            ],"cost":0.04,"tokens":{"input":120,"output":800}}}""",
        )
        assertEquals(1, parsed.data.images.size)
        assertEquals("image", parsed.data.images[0].type)
        assertEquals("iVBORw0KGgo=", parsed.data.images[0].data)
        assertEquals("image/png", parsed.data.images[0].mime)
        assertEquals(0.04, parsed.data.cost!!, 0.0001)
        assertEquals(120L, parsed.data.tokens?.input)
        assertEquals(800L, parsed.data.tokens?.output)
    }

    @Test
    fun `decodes a session generate answer with tokens and cost`() {
        val parsed = ApiJson.decodeFromString<SessionGenerateResponse>(
            """{"data":{"text":"Summary here","tokens":{"input":500,"output":120,"reasoning":0,"cache":{"read":0,"write":0}},"cost":0.003}}""",
        )
        assertEquals("Summary here", parsed.data.text)
        assertEquals(500L, parsed.data.tokens.input)
        assertEquals(120L, parsed.data.tokens.output)
        assertEquals(0.003, parsed.data.cost, 0.0001)
    }
}
