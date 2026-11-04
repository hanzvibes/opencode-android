package dev.bidzz.opencode

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * The language the app's own text is shown in.
 *
 * [System] follows the phone. The other two pin the app to one language whatever the phone
 * is set to, which is what a bilingual user who keeps the phone in English wants.
 */
enum class AppLanguage(val code: String, val tag: String?) {
    System("system", null),
    English("en", "en"),
    Indonesian("id", "id");

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: System
    }
}

/**
 * Remembers the chosen [AppLanguage] and builds a [Context] that shows it.
 *
 * Applied inside Compose (see `OpenCodeApp`) rather than by recreating the activity, so
 * picking a language in Settings changes the text at once and keeps the screen the user is on.
 * The platform's per-app language API needs API 33 and the AppCompat backport needs an
 * `AppCompatActivity`; this app targets API 29 and uses `ComponentActivity`.
 */
object LanguageStore {
    private const val PREFS = "app_settings"
    private const val KEY = "language"

    fun get(context: Context): AppLanguage =
        AppLanguage.fromCode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.code).apply()
    }

    /** [base] showing [language]; for [AppLanguage.System] the phone's own locales. */
    fun wrap(base: Context, language: AppLanguage): Context {
        val locales = language.tag?.let { LocaleList(Locale.forLanguageTag(it)) }
            ?: Resources.getSystem().configuration.locales
        val config = Configuration(base.resources.configuration).apply { setLocales(locales) }
        return base.createConfigurationContext(config)
    }
}
