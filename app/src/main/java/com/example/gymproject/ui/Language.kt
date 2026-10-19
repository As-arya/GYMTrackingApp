package com.example.gymproject.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Per-app language: "" follows the system, "in" is Indonesian, "en" is English. */
object AppLanguage {
    fun selected(): String {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return when {
            tag.isBlank() -> ""
            tag.startsWith("in") || tag.startsWith("id") -> "in"
            else -> "en"
        }
    }

    fun select(context: Context, tag: String) {
        // Android 13+ applies the app locale to every component; the timer service on older versions reads this copy.
        context.getSharedPreferences("settings", 0).edit().putString("language", tag).apply()
        AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
    }

    /** Gives a non-activity context (the timer service) the chosen language on Android 12 and older. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences("settings", 0).getString("language", "").orEmpty()
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }
}
