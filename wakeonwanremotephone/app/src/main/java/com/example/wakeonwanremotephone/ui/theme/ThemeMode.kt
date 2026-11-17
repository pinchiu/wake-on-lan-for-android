package com.example.wakeonwanremotephone.ui.theme

import android.content.Context

/**
 * User-selectable appearance mode. [SYSTEM] follows the Android ui-mode
 * setting; the other two force the Frost light or dark palette.
 */
enum class AppThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            values().firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

/**
 * Persists the appearance preference in its own SharedPreferences file so it
 * is independent from the language and device-profile preferences.
 */
class ThemePreference(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode: AppThemeMode
        get() = AppThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, null))
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.storageValue).apply()
        }

    companion object {
        const val PREFS_NAME = "appearance"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
