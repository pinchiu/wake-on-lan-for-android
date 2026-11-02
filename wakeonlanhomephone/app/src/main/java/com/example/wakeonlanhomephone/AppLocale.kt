package com.example.wakeonlanhomephone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Application locale preference management.
 *
 * SharedPreferences serves as the single source of truth:
 * - ""      Follow system
 * - "zh-TW" Traditional Chinese
 * - "en"    English
 */
object AppLocale {

    const val PREFS_NAME = DeviceProfileManager.PREFS_NAME
    const val KEY_APP_LANGUAGE = "app_language"

    const val TAG_SYSTEM = ""
    const val TAG_TRADITIONAL_CHINESE = "zh-TW"
    const val TAG_ENGLISH = "en"

    fun currentTag(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_LANGUAGE, TAG_SYSTEM)
            ?: TAG_SYSTEM
    }

    /**
     * Wrap Context based on user locale preference; returns original Context when following system.
     */
    fun wrap(base: Context): Context {
        val tag = currentTag(base)
        if (tag.isBlank()) return base

        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }

    /**
     * Persist language preference and recreate current Activity so all UI elements update immediately.
     */
    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LANGUAGE, tag)
            .apply()

        // Compose LocalContext may be ContextThemeWrapper; traverse up to find Activity.
        var candidate: Context? = context
        while (candidate !is Activity && candidate is ContextWrapper) {
            candidate = candidate.baseContext
        }
        (candidate as? Activity)?.recreate()
    }
}
