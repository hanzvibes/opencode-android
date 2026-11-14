package dev.bidzz.opencode.ui.onboarding

import android.content.Context
import dev.bidzz.opencode.WorkspaceAgentsMd
import dev.bidzz.opencode.workspace.WorkspaceStore

/**
 * First-run detection for the onboarding flow.
 *
 * Same pattern as `LanguageStore` / `ThemeStore`: one boolean in the shared
 * `app_settings` preferences, no new storage mechanism. The flow is shown
 * once — when the workspace is still empty — and never again after it is
 * dismissed or completed, whichever step the user leaves from. Skipping is
 * completing: a skipped flow is not forced on the next launch.
 */
object OnboardingStore {
    private const val PREFS = "app_settings"
    private const val KEY_COMPLETED = "onboarding_completed"

    fun isCompleted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPLETED, false)

    fun markCompleted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    /**
     * True on a genuine first run: onboarding was never dismissed and the
     * workspace has nothing in it yet. A workspace with files is not a
     * first run even if onboarding was never seen (e.g. the user picked a
     * folder before this flow existed), and a dismissed flow never
     * reappears just because the workspace is empty again.
     */
    fun shouldShow(context: Context, workspaceStore: WorkspaceStore): Boolean {
        if (isCompleted(context)) return false
        val dir = workspaceStore.resolve()
        return workspaceLooksFirstRun(dir.listFiles()?.map { it.name })
    }
}

/**
 * Whether a workspace counts as untouched (audit-200 C10, M-015):
 * empty, or holding only the `AGENTS.md` the app itself seeds on
 * startup ([WorkspaceAgentsMd.ensure]). The seed runs before the UI
 * appears, so a fresh install's workspace is never literally empty
 * and onboarding never showed. A workspace with any other file is a
 * real workspace, even if it also holds AGENTS.md.
 */
internal fun workspaceLooksFirstRun(fileNames: List<String>?): Boolean {
    if (fileNames == null) return true
    return fileNames.all { it == WorkspaceAgentsMd.FILE_NAME }
}
