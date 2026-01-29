package com.example.wakeonlanhomephone

import android.content.Context
import android.content.SharedPreferences

data class MqttConfig(
    val brokerName: String,
    val host: String,
    val port: Int,
    val clientId: String,
    val username: String,
    val password: String,
    val useSsl: Boolean,
    val topic: String,
    val targetMac: String,
    val protocol: String,
    val timeout: Int,
    val keepAlive: Int,
    val autoConnect: Boolean
)

class MqttConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mqtt_config", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BROKER_NAME = "broker_name"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SSL = "ssl"
        private const val KEY_TOPIC = "topic"
        private const val KEY_TARGET_MAC = "target_mac"
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_TIMEOUT = "timeout"
        private const val KEY_KEEP_ALIVE = "keep_alive"
        private const val KEY_AUTO_CONNECT = "auto_connect"

        // Defaults
        const val DEFAULT_BROKER_NAME = "My Broker"
        const val DEFAULT_HOST = ""
        const val DEFAULT_PORT = 8883
        const val DEFAULT_CLIENT_ID = "" // Empty = generate random
        const val DEFAULT_USER = ""
        const val DEFAULT_PASS = ""
        const val DEFAULT_SSL = true
        const val DEFAULT_TOPIC = ""
        const val DEFAULT_TARGET_MAC = ""
        const val DEFAULT_PROTOCOL = "TCP"
        const val DEFAULT_TIMEOUT = 30
        const val DEFAULT_KEEP_ALIVE = 60
        const val DEFAULT_AUTO_CONNECT = true
    }

    fun getConfig(): MqttConfig {
        return MqttConfig(
            brokerName = prefs.getString(KEY_BROKER_NAME, DEFAULT_BROKER_NAME) ?: DEFAULT_BROKER_NAME,
            host = prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST,
            port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
            clientId = prefs.getString(KEY_CLIENT_ID, DEFAULT_CLIENT_ID) ?: DEFAULT_CLIENT_ID,
            username = prefs.getString(KEY_USERNAME, DEFAULT_USER) ?: DEFAULT_USER,
            password = prefs.getString(KEY_PASSWORD, DEFAULT_PASS) ?: DEFAULT_PASS,
            useSsl = prefs.getBoolean(KEY_SSL, DEFAULT_SSL),
            topic = prefs.getString(KEY_TOPIC, DEFAULT_TOPIC) ?: DEFAULT_TOPIC,
            targetMac = prefs.getString(KEY_TARGET_MAC, DEFAULT_TARGET_MAC) ?: DEFAULT_TARGET_MAC,
            protocol = prefs.getString(KEY_PROTOCOL, DEFAULT_PROTOCOL) ?: DEFAULT_PROTOCOL,
            timeout = prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT),
            keepAlive = prefs.getInt(KEY_KEEP_ALIVE, DEFAULT_KEEP_ALIVE),
            autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, DEFAULT_AUTO_CONNECT)
        )
    }

    fun saveConfig(config: MqttConfig) {
        prefs.edit().apply {
            putString(KEY_BROKER_NAME, config.brokerName)
            putString(KEY_HOST, config.host)
            putInt(KEY_PORT, config.port)
            putString(KEY_CLIENT_ID, config.clientId)
            putString(KEY_USERNAME, config.username)
            putString(KEY_PASSWORD, config.password)
            putBoolean(KEY_SSL, config.useSsl)
            putString(KEY_TOPIC, config.topic)
            putString(KEY_TARGET_MAC, config.targetMac)
            putString(KEY_PROTOCOL, config.protocol)
            putInt(KEY_TIMEOUT, config.timeout)
            putInt(KEY_KEEP_ALIVE, config.keepAlive)
            putBoolean(KEY_AUTO_CONNECT, config.autoConnect)
            apply()
        }
    }
}
