package dev.bidzz.opencode.core.api

import dev.bidzz.opencode.core.api.dto.ArtifactContent
import dev.bidzz.opencode.core.api.dto.ArtifactCreateInput
import dev.bidzz.opencode.core.api.dto.ArtifactInfo
import dev.bidzz.opencode.core.api.dto.ArtifactProvidersResponse
import dev.bidzz.opencode.core.api.dto.ArtifactUpdateInput
import dev.bidzz.opencode.core.api.dto.ArtifactsResponse
import dev.bidzz.opencode.core.api.dto.JobInfo
import dev.bidzz.opencode.core.api.dto.JobsResponse
import dev.bidzz.opencode.core.api.dto.SingleResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Decoding/encoding of the artifact + job contracts (mockup group 3,
 * engine v2.2.7, fork pin fedc6f20). The raw payloads mirror the
 * engine's answers, including the `{location, data}` wrapper on the
 * artifact routes (the `location` key is ignored) and the tagged
 * `kind` union of `Job.Info`.
 */
class ArtifactJobDecodingTest {

    @Test
    fun `decodes artifact providers with capabilities and default`() {
        val parsed = ApiJson.decodeFromString<ArtifactProvidersResponse>(
            """{"location":{"directory":"/tmp"},"data":[
                {"id":"local","name":"Local","capabilities":["static"],"default":true},
                {"id":"cloud","name":"Cloud","capabilities":["static","server-code","sqlite"],"default":false}
            ]}""",
        )
        assertEquals(2, parsed.data.size)
        assertEquals("local", parsed.data[0].id)
        assertEquals(listOf("static"), parsed.data[0].capabilities)
        assertEquals(true, parsed.data[0].default)
        assertEquals("cloud", parsed.data[1].id)
    }

    @Test
    fun `decodes an artifact list with session and url`() {
        val parsed = ApiJson.decodeFromString<ArtifactsResponse>(
            """{"location":{"directory":"/tmp"},"data":[
                {"id":"art_01","provider":"local","sessionID":"ses_01","title":"Demo","entry":"index.html",
                 "url":"/api/experimental/artifacts/art_01/view/",
                 "time":{"created":1762900000000,"updated":1762900100000}},
                {"id":"art_02","provider":"local","title":"No session","entry":"app.html",
                 "url":"/api/experimental/artifacts/art_02/view/",
                 "time":{"created":1762900000000,"updated":1762900000000}}
            ]}""",
        )
        assertEquals(2, parsed.data.size)
        assertEquals("ses_01", parsed.data[0].sessionID)
        assertEquals("/api/experimental/artifacts/art_01/view/", parsed.data[0].url)
        assertEquals(1762900100000L, parsed.data[0].time.updated)
        assertNull(parsed.data[1].sessionID)
    }

    @Test
    fun `decodes artifact content with files`() {
        val parsed = ApiJson.decodeFromString<SingleResponse<ArtifactContent>>(
            """{"location":{"directory":"/tmp"},"data":{"info":
                {"id":"art_01","provider":"local","title":"Demo","entry":"index.html",
                 "url":"/api/experimental/artifacts/art_01/view/",
                 "time":{"created":1762900000000,"updated":1762900100000}},
                "files":{"index.html":"<h1>hi</h1>","style.css":"h1{color:red}"}}}""",
        )
        assertEquals("Demo", parsed.data.info.title)
        assertEquals("<h1>hi</h1>", parsed.data.files["index.html"])
        assertEquals(2, parsed.data.files.size)
    }

    @Test
    fun `create input omits absent optional fields`() {
        val encoded = ApiJson.encodeToString(
            ArtifactCreateInput(
                title = "Demo",
                files = mapOf("index.html" to "<h1>hi</h1>"),
            ),
        )
        assertEquals(
            """{"title":"Demo","files":{"index.html":"<h1>hi</h1>"}}""",
            encoded,
        )
    }

    @Test
    fun `update input encodes title and files`() {
        val encoded = ApiJson.encodeToString(
            ArtifactUpdateInput(
                title = "Demo v2",
                files = mapOf("index.html" to "<h1>v2</h1>"),
                sessionID = "ses_01",
            ),
        )
        assertEquals(
            """{"title":"Demo v2","files":{"index.html":"<h1>v2</h1>"},"sessionID":"ses_01"}""",
            encoded,
        )
    }

    @Test
    fun `decodes all three job kinds`() {
        val parsed = ApiJson.decodeFromString<JobsResponse>(
            """{"data":[
                {"id":"job_1","sessionID":"ses_01","status":"running","kind":"shell",
                 "shellID":"sh_1","command":"npm test",
                 "time":{"started":1762900000000}},
                {"id":"job_2","sessionID":"ses_01","status":"completed","title":"Research",
                 "kind":"subagent","childSessionID":"ses_02","agent":"explore",
                 "description":"Search the codebase",
                 "time":{"started":1762900000000,"completed":1762900060000}},
                {"id":"job_3","sessionID":"ses_01","status":"error","kind":"image",
                 "path":"/tmp/out.png","model":"gpt-image-1",
                 "time":{"started":1762900000000,"completed":1762900030000}}
            ]}""",
        )
        assertEquals(3, parsed.data.size)
        val shell = parsed.data[0]
        assertEquals("shell", shell.kind)
        assertEquals("npm test", shell.command)
        assertEquals("npm test", shell.detailLine())
        assertNull(shell.time.completed)
        val subagent = parsed.data[1]
        assertEquals("subagent", subagent.kind)
        assertEquals("ses_02", subagent.childSessionID)
        assertEquals("Research", subagent.detailLine())
        assertEquals(1762900060000L, subagent.time.completed)
        val image = parsed.data[2]
        assertEquals("image", image.kind)
        assertEquals("/tmp/out.png", image.path)
        assertEquals("gpt-image-1", image.model)
        assertEquals("/tmp/out.png", image.detailLine())
    }

    @Test
    fun `decodes an empty job list`() {
        val parsed = ApiJson.decodeFromString<JobsResponse>("""{"data":[]}""")
        assertEquals(0, parsed.data.size)
    }

    @Test
    fun `artifact info decodes without optional session`() {
        val parsed = ApiJson.decodeFromString<SingleResponse<ArtifactInfo>>(
            """{"location":{"directory":"/tmp"},"data":
                {"id":"art_01","provider":"local","title":"Demo","entry":"index.html",
                 "url":"/api/experimental/artifacts/art_01/view/",
                 "time":{"created":1762900000000,"updated":1762900100000}}}""",
        )
        assertNull(parsed.data.sessionID)
        assertEquals("art_01", parsed.data.id)
    }
}
