package com.example.wakeonlanhomephone.ui.theme

import android.content.Context

/**
 * User-selectable appearance mode.
 *
 * [storageValue] is the stable string written to SharedPreferences; [fromStorage]
 * tolerates missing or unknown values by falling back to [SYSTEM].
 */
enum class AppThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: SYSTEM
        }
    }
}

/**
 * Persists the appearance choice in its own SharedPreferences file so it is
 * independent of device profiles and language settings.
 */
class ThemePreference(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode: AppThemeMode
        get() = AppThemeMode.fromStorage(preferences.getString(KEY_THEME_MODE, null))
        set(value) {
            preferences.edit()
                .putString(KEY_THEME_MODE, value.storageValue)
                .apply()
        }

    companion object {
        const val PREFS_NAME = "appearance"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
