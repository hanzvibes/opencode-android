package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.ArtifactInfo
import dev.bidzz.opencode.core.api.dto.CredentialEntry
import dev.bidzz.opencode.core.api.dto.IntegrationInfo
import dev.bidzz.opencode.core.api.dto.JobInfo
import dev.bidzz.opencode.core.api.dto.ProviderInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    // Audit-200 B1 (M-005): a total decode failure is a contract
    // mismatch, not an empty list --------------------------------------

    @Test
    fun `an empty data array still decodes as empty`() {
        assertEquals(
            emptyList<IntegrationInfo>(),
            client.decodeDataList<IntegrationInfo>("""{"data":[]}"""),
        )
    }

    @Test
    fun `every entry failing to decode throws instead of returning empty`() {
        assertThrows(IllegalStateException::class.java) {
            client.decodeDataList<IntegrationInfo>(
                """{"data":[{"name":"No id"},{"name":"No id either"}]}""",
            )
        }
    }

    // Audit-200 B1 (M-314): artifacts and jobs decode per entry, so one
    // bad entry skips instead of hiding the whole list ------------------

    @Test
    fun `an artifact list skips a malformed entry`() {
        val raw = """{"data":[
            {"id":"art_01","provider":"local","title":"Demo","entry":"index.html",
             "url":"/api/experimental/artifacts/art_01/view/",
             "time":{"created":1762900000000,"updated":1762900100000}},
            {"provider":"local","title":"No id at all"},
            {"id":"art_02","provider":"local","title":"Second","entry":"app.html",
             "url":"/api/experimental/artifacts/art_02/view/",
             "time":{"created":1762900000000,"updated":1762900000000}}
        ]}"""
        assertEquals(
            listOf("art_01", "art_02"),
            client.decodeDataList<ArtifactInfo>(raw).map { it.id },
        )
    }

    @Test
    fun `a job list skips a malformed entry`() {
        val raw = """{"data":[
            {"id":"job_01","sessionID":"ses_01","status":"running",
             "time":{"started":1762900000000},"kind":"shell","command":"sleep 10"},
            {"sessionID":"ses_01","status":"completed"},
            {"id":"job_02","sessionID":"ses_01","status":"completed",
             "time":{"started":1762900000000,"completed":1762900010000},"kind":"subagent"}
        ]}"""
        assertEquals(
            listOf("job_01", "job_02"),
            client.decodeDataList<JobInfo>(raw).map { it.id },
        )
    }
}
