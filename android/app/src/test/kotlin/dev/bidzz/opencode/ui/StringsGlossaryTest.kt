package dev.bidzz.opencode.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for the i18n glossary decisions (Audit-200 D1/D2): the two
 * `strings.xml` files must stay in parity, identical EN/ID values must be
 * exactly the documented allow-list (m-863), and the ID values must keep
 * the D2 glossary — "engine" not "mesin" (m-937), "agen" not "agent"
 * (m-936), "berkas" in running sentences with "File" only as a short
 * label (m-932), "ruang kerja"/"proyek" (m-1218), "Ubah" (m-1219),
 * "Gagal" for failure statuses (m-1221), the "Anda" register (m-1222) —
 * and the EN values must stay contraction-free (m-1222).
 *
 * Unit tests run with the module dir as the working directory, so the
 * resource files are readable here (same pattern as AboutLicensesTest).
 */
class StringsGlossaryTest {

    private fun readStrings(locale: String): Map<String, String> {
        val file = File("src/main/res/values$locale/strings.xml")
        assertTrue("$locale strings.xml is missing", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = mutableMapOf<String, String>()
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i)
            val name = el.attributes.getNamedItem("name").nodeValue
            out[name] = el.textContent
        }
        return out
    }

    // m-863: the only keys whose EN and ID values are intentionally
    // identical — proper nouns, technical loanwords, and format-only
    // strings. If this list changes, the change must be a deliberate
    // glossary decision, not drift.
    private val identicalAllowList = setOf(
        "about_github", "about_version", "chat_find_count", "chat_menu",
        "chat_mode_fallback", "chat_mode_label", "chat_model_fallback",
        "chat_model_label", "chat_status_dialog_branch", "cmd_skill_tag",
        "composer_working_progress", "config_field_model", "config_field_shell",
        "diag_cpu", "diag_engine_label", "diag_health_normal",
        "diag_log_level_error", "diag_log_level_info", "diag_ram",
        "diag_total", "empty_state_provider_model", "engine_notif_channel_name",
        "files_diff", "files_root", "files_search_group", "job_kind_shell",
        "language_english", "language_indonesian", "mcp_add_command_hint",
        "mcp_add_cwd_hint", "mcp_add_url", "mcp_add_url_hint",
        "mcp_resource_line", "message_fork", "model_tier_pro",
        "preview_image_size", "providers_antigravity_oauth_account",
        "providers_antigravity_title", "providers_featured_anthropic_subtitle",
        "providers_featured_deepseek_subtitle", "providers_featured_google_subtitle",
        "providers_featured_openai_subtitle", "schedule_prompt_label",
        "schedule_when_at_hint", "schedule_when_cron", "schedule_when_cron_hint",
        "settings_section_engine", "step_target", "tool_stderr", "tool_stdout",
        "usage_per_model", "usage_section_go",
    )

    // m-932: the only ID values still allowed to contain the word "file" —
    // OS permission names ("Akses semua file"), technical collocations
    // ("file konfigurasi", "jalur file"), and short labels ("File").
    private val fileAllowList = setOf(
        "settings_all_files", "mcp_remove_body", "onboarding_permissions_title",
        "onboarding_permissions_body", "onboarding_grant_access",
        "onboarding_access_granted", "revert_messages_and_files", "drawer_files",
        "project_grant_text", "project_grant_button", "fileview_reading",
        "conflict_title", "providers_key_body",
    )

    @Test
    fun `en and id string keys are in parity`() {
        val en = readStrings("")
        val id = readStrings("-in")
        assertEquals("keys missing from values-in", emptySet<String>(), en.keys - id.keys)
        assertEquals("keys missing from values", emptySet<String>(), id.keys - en.keys)
    }

    @Test
    fun `identical en-id values are exactly the allow-list`() {
        val en = readStrings("")
        val id = readStrings("-in")
        val identical = en.keys.filter { en[it] == id[it] }.toSet()
        assertEquals(
            "en==id drifted from the allow-list — a new identical value is " +
                "either a missing translation or a deliberate loanword that " +
                "belongs in the allow-list with a comment in values-in",
            identicalAllowList,
            identical,
        )
    }

    @Test
    fun `id values keep the d2 glossary`() {
        val id = readStrings("-in")
        val word = { w: String -> Regex("""\b$w\b""", RegexOption.IGNORE_CASE) }
        assertEquals("ID still uses 'mesin' (D2 m-937: 'engine' wins)",
            emptyList<String>(), id.filter { (_, v) -> word("mesin").containsMatchIn(v) }.keys.toList())
        assertEquals("ID still uses 'agent' (D2 m-936: 'agen' wins)",
            emptyList<String>(), id.filter { (_, v) -> word("agent").containsMatchIn(v) }.keys.toList())
        assertEquals("ID still uses 'kamu' (D2 m-1222: 'Anda' register)",
            emptyList<String>(), id.filter { (_, v) -> word("kamu").containsMatchIn(v) }.keys.toList())
        val muWords = id.flatMap { (_, v) ->
            Regex("""[A-Za-z]+mu\b""").findAll(v).map { it.value.lowercase() }
        }.toSet() - "ilmu"
        assertEquals("ID still uses an informal -mu possessive (D2 m-1222)",
            emptySet<String>(), muWords)
        val workspaceKeys = id.filter { (k, v) ->
            k != "mcp_add_cwd_hint" &&
                (word("workspace").containsMatchIn(v) || word("project").containsMatchIn(v) ||
                    v.contains("Projects"))
        }.keys.toList()
        assertEquals("ID still uses 'workspace'/'project' (D2 m-1218: 'ruang kerja'/'proyek')",
            emptyList<String>(), workspaceKeys)
        val fileKeys = id.filter { (_, v) -> word("file").containsMatchIn(v) }.keys.toSet()
        assertEquals(
            "ID 'file' occurrences drifted from the allow-list — running " +
                "sentences use 'berkas' (D2 m-932); only labels, OS permission " +
                "names and technical collocations may keep 'File'",
            fileAllowList,
            fileKeys,
        )
    }

    @Test
    fun `en values are contraction-free`() {
        val en = readStrings("")
        val contraction = Regex("""n['’]t|\b\w+['’](?:re|ll|ve|d|m)\b|\b(?:it|that|here|there|what|let|this)['’]s\b""")
        val hits = en.filter { (_, v) -> contraction.containsMatchIn(v) }.keys.toList()
        assertEquals("EN still has contractions (D2 m-1222: write them out)",
            emptyList<String>(), hits)
    }
}
