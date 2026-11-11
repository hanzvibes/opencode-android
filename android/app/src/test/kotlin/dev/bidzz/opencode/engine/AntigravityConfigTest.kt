package dev.bidzz.opencode.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AntigravityConfigTest {

    @JvmField
    @Rule
    val temp: TemporaryFolder = TemporaryFolder()

    @Test
    fun `antigravity accounts are parsed correctly`() {
        val configHome = temp.newFolder("config-home")
        val ocDir = File(configHome, "opencode").apply { mkdirs() }
        val accountsFile = File(ocDir, "antigravity-accounts.json")

        accountsFile.writeText(
            """
            {
              "version": 1,
              "activeIndex": 0,
              "accounts": [
                {
                  "id": "acc-1",
                  "email": "dev@example.com",
                  "enabled": true,
                  "refreshToken": "sample-refresh-token",
                  "projectId": "my-project"
                },
                {
                  "id": "acc-2",
                  "email": "disabled@example.com",
                  "enabled": false,
                  "refreshToken": "sample-token-2"
                }
              ]
            }
            """.trimIndent()
        )

        val store = EngineConfigStore(configHome)
        val accounts = store.antigravityAccounts()

        assertEquals(2, accounts.size)
        assertEquals("dev@example.com", accounts[0].email)
        assertTrue(accounts[0].enabled)
        assertTrue(accounts[0].hasRefreshToken)
        assertEquals("my-project", accounts[0].projectId)

        val active = store.activeAntigravityAccount()
        assertNotNull(active)
        assertEquals("dev@example.com", active?.email)
        assertTrue(store.hasActiveAntigravity())
    }

    @Test
    fun `plugins list parses from opencode json`() {
        val configHome = temp.newFolder("config-home")
        val ocDir = File(configHome, "opencode").apply { mkdirs() }
        val configFile = File(ocDir, "opencode.json")

        configFile.writeText(
            """
            {
              "plugins": ["opencode-antigravity-auth@2.1.2"]
            }
            """.trimIndent()
        )

        val store = EngineConfigStore(configHome)
        val plugins = store.plugins()

        assertEquals(listOf("opencode-antigravity-auth@2.1.2"), plugins)
    }
}
