package org.jls.makeitrun.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.jls.makeitrun.R

enum class AppLanguage(
    val tag: String?,
    @param:StringRes val labelResId: Int,
) {
    SYSTEM(null, R.string.settings_language_system),
    ENGLISH("en", R.string.settings_language_english),
    FRENCH("fr", R.string.settings_language_french),
    ;

    companion object {

        fun current(): AppLanguage {
            val selected = AppCompatDelegate.getApplicationLocales()
                .toLanguageTags()
                .substringBefore(',')

            return entries.firstOrNull { language ->
                language.tag != null && selected.startsWith(language.tag)
            } ?: SYSTEM
        }

        fun apply(language: AppLanguage) {
            val locales = language.tag
                ?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()

            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
