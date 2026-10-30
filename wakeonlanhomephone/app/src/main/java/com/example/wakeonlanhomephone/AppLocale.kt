package com.example.wakeonlanhomephone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * 應用程式語言偏好管理。
 *
 * 偏好設定為唯一真實來源（不依賴 AppCompat 或 LocaleManager）：
 * - ""      跟隨系統
 * - "zh-TW" 繁體中文
 * - "en"    英文
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
     * 依使用者偏好包裝 Context；跟隨系統時直接回傳原始 Context。
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
     * 儲存語言偏好並重建目前 Activity，讓所有畫面立即套用新語言。
     */
    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LANGUAGE, tag)
            .apply()

        // Compose 的 LocalContext 可能是 ContextThemeWrapper，先往上找出 Activity。
        var candidate: Context? = context
        while (candidate !is Activity && candidate is ContextWrapper) {
            candidate = candidate.baseContext
        }
        (candidate as? Activity)?.recreate()
    }
}
