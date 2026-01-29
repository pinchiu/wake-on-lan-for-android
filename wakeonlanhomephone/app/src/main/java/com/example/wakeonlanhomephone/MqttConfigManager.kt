package com.example.wakeonlanhomephone

import android.content.Context
import android.content.SharedPreferences

data class MqttConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val useSsl: Boolean,
    val topic: String,
    val targetMac: String
)

class MqttConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mqtt_config", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SSL = "ssl"
        private const val KEY_TOPIC = "topic"
        private const val KEY_TARGET_MAC = "target_mac"
        
        // Defaults (Adafruit IO)
        const val DEFAULT_HOST = "io.adafruit.com"
        const val DEFAULT_PORT = 8883
        const val DEFAULT_USER = "poochen"
        const val DEFAULT_PASS = "YOUR_ADAFRUIT_KEY" // Placeholder
        const val DEFAULT_SSL = true
        const val DEFAULT_TOPIC = "poochen/feeds/pc-command"
        const val DEFAULT_TARGET_MAC = ""
    }

    fun getConfig(): MqttConfig {
        return MqttConfig(
            host = prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST,
            port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
            username = prefs.getString(KEY_USERNAME, DEFAULT_USER) ?: DEFAULT_USER,
            password = prefs.getString(KEY_PASSWORD, DEFAULT_PASS) ?: DEFAULT_PASS,
            useSsl = prefs.getBoolean(KEY_SSL, DEFAULT_SSL),
            topic = prefs.getString(KEY_TOPIC, DEFAULT_TOPIC) ?: DEFAULT_TOPIC,
            targetMac = prefs.getString(KEY_TARGET_MAC, DEFAULT_TARGET_MAC) ?: DEFAULT_TARGET_MAC
        )
    }

    fun saveConfig(config: MqttConfig) {
        prefs.edit().apply {
            putString(KEY_HOST, config.host)
            putInt(KEY_PORT, config.port)
            putString(KEY_USERNAME, config.username)
            putString(KEY_PASSWORD, config.password)
            putBoolean(KEY_SSL, config.useSsl)
            putString(KEY_TOPIC, config.topic)
            putString(KEY_TARGET_MAC, config.targetMac)
            apply()
        }
    }
}
