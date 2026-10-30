package com.example.wakeonwanremotephone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * App language preference. The stored preference is the single source of truth:
 * "" follows the system locale, "zh-TW" forces Traditional Chinese and "en"
 * forces English. The locale is applied at the activity boundary via
 * [wrap], so no AppCompat or platform LocaleManager is required.
 */
object AppLocale {
    const val PREFS_NAME = "RemoteControlPrefs"
    const val KEY_LANGUAGE = "app_language"

    const val TAG_SYSTEM = ""
    const val TAG_ZH_TW = "zh-TW"
    const val TAG_EN = "en"

    fun currentTag(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, TAG_SYSTEM) ?: TAG_SYSTEM
    }

    fun wrap(base: Context): Context {
        val tag = currentTag(base)
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, tag)
            .apply()
        var candidate: Context? = context
        while (candidate !is Activity && candidate is ContextWrapper) {
            candidate = candidate.baseContext
        }
        (candidate as? Activity)?.recreate()
    }
}
