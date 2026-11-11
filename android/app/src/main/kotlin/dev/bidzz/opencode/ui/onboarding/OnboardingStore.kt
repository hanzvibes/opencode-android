package dev.bidzz.opencode.ui.onboarding

import android.content.Context
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
        return dir.listFiles()?.isEmpty() ?: true
    }
}
