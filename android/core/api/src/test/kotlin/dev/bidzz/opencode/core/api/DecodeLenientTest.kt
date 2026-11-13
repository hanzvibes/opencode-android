package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.CredentialEntry
import dev.bidzz.opencode.core.api.dto.IntegrationInfo
import dev.bidzz.opencode.core.api.dto.ProviderInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * List decoding skips malformed entries instead of failing the whole list
 * (audit-100 38-F1): one entry without its required `id` — e.g. after an
 * engine upgrade, or a third-party integration with a deviating shape — used
 * to hide every provider and every credential, although the engine's
 * database was intact.
 */
class DecodeLenientTest {

    private val client = EngineClient("http://127.0.0.1:1", "")

    @Test
    fun `an entry without an id is skipped, the rest are returned`() {
        val raw = """{"data":[
            {"id":"openai","name":"OpenAI"},
            {"name":"No id at all"},
            {"id":"anthropic","name":"Anthropic"}
        ]}"""
        assertEquals(
            listOf("openai", "anthropic"),
            client.decodeDataList<IntegrationInfo>(raw).map { it.id },
        )
    }

    @Test
    fun `a null id and a wrongly typed field are skipped too`() {
        val raw = """{"data":[
            {"id":null,"name":"Null id"},
            {"id":"ok","name":"OK"},
            {"id":"bad","methods":"not-an-array"}
        ]}"""
        assertEquals(listOf("ok"), client.decodeDataList<IntegrationInfo>(raw).map { it.id })
    }

    @Test
    fun `credentials and providers skip bad entries the same way`() {
        val creds = """{"data":[
            {"id":"c1","integrationID":"openai"},
            {"integrationID":"openai"},
            {"id":"c2","integrationID":"anthropic"}
        ]}"""
        assertEquals(listOf("c1", "c2"), client.decodeDataList<CredentialEntry>(creds).map { it.id })

        val providers = """{"data":[
            {"id":"p1"},
            {"name":"no id"},
            {"id":"p2","name":"Two"}
        ]}"""
        assertEquals(listOf("p1", "p2"), client.decodeDataList<ProviderInfo>(providers).map { it.id })
    }

    @Test
    fun `a missing data array decodes as empty`() {
        assertEquals(
            emptyList<IntegrationInfo>(),
            client.decodeDataList<IntegrationInfo>("""{"location":null}"""),
        )
    }
}
