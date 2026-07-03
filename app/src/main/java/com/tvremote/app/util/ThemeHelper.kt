package com.tvremote.app.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.tvremote.app.data.session.AppPreferences
import java.util.Locale

object ThemeHelper {
    fun applySavedTheme(context: Context) {
        val prefs = AppPreferences(context)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
    }

    fun applyLanguage(context: Context) {
        val prefs = AppPreferences(context)
        val locale = Locale.forLanguageTag(prefs.selectedLanguage)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.createConfigurationContext(config)
    }

    fun setThemeMode(context: Context, mode: Int) {
        AppPreferences(context).themeMode = mode
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun isDarkMode(context: Context): Boolean {
        val mode = AppPreferences(context).themeMode
        return when (mode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val nightMask = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
}
