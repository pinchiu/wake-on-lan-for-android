package com.example.wakeonwanremotephone

import android.content.Context
import android.content.SharedPreferences

/**
 * Small SharedPreferences helper for the two shared command secrets.
 *
 * Both start empty. The helper secret is shared with the Home Phone (TCP 9876
 * and MQTT command topic); the PC secret must match the `pc_onoff` daemon and
 * protects direct UDP 9877 power commands.
 */
class SecuritySettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "security_settings"
        const val KEY_HELPER_SECRET = "helper_secret"
        const val KEY_PC_SECRET = "pc_secret"
    }

    fun getHelperSecret(): String = prefs.getString(KEY_HELPER_SECRET, "").orEmpty()

    fun setHelperSecret(secret: String) {
        prefs.edit().putString(KEY_HELPER_SECRET, secret.trim()).apply()
    }

    /** Returns a fresh secret without persisting it; the dialog saves on Save only. */
    fun generateHelperSecret(): String = CommandAuth.generateSecret()

    fun getPcSecret(): String = prefs.getString(KEY_PC_SECRET, "").orEmpty()

    fun setPcSecret(secret: String) {
        prefs.edit().putString(KEY_PC_SECRET, secret.trim()).apply()
    }
}
