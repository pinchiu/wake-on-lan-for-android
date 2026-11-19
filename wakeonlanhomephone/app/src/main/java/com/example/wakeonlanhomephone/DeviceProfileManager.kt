package com.example.wakeonlanhomephone

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Target computer data model for Home Phone helper.
 * Supports saving computer name, network adapter MAC address, local IPv4 address, and an
 * optional per-computer PC secret. A blank [pcSecret] falls back to the shared value in
 * [MqttConfigManager].
 */
data class DeviceProfile(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("name") val name: String,
    @SerializedName("mac") val mac: String,
    @SerializedName("ip") val ip: String = "",
    @SerializedName("pcSecret") val pcSecret: String = ""
)

/**
 * Computer device profile manager for Home Phone helper.
 * Manages the list of target computers, currently selected device, and syncs with MqttConfigManager and WakePcWidgetProvider.
 */
class DeviceProfileManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val PREFS_NAME = "HomePhoneDevicePrefs"
        const val KEY_DEVICES_JSON = "devices_profile_list"
        const val KEY_SELECTED_DEVICE_ID = "selected_device_id"
        private const val KEY_LEGACY_MIGRATED = "legacy_migrated"

        /** Serializes read-modify-write access to the profile list and selection. */
        private val profileLock = Any()
    }

    init {
        migrateLegacyDeviceIfNeeded()
    }

    /**
     * Backwards compatibility: migrate the legacy single MAC setting from MqttConfigManager
     * exactly once. Without the one-shot flag, deleting every profile would resurrect the
     * legacy MAC on the next launch.
     */
    private fun migrateLegacyDeviceIfNeeded() = synchronized(profileLock) {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return@synchronized

        if (getDevices().isEmpty()) {
            val configManager = MqttConfigManager(context)
            val oldMac = configManager.getConfig().targetMac.trim()
            if (oldMac.isNotEmpty()) {
                val initialDevice = DeviceProfile(
                    name = AppLocale.wrap(context).getString(R.string.default_profile_primary),
                    mac = oldMac,
                    ip = ""
                )
                saveDevices(listOf(initialDevice))
                setSelectedDeviceId(initialDevice.id)
            }
        }
        prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
    }

    fun getDevices(): List<DeviceProfile> = synchronized(profileLock) {
        val json = prefs.getString(KEY_DEVICES_JSON, null) ?: return@synchronized emptyList()
        try {
            val type = object : TypeToken<List<DeviceProfile?>>() {}.type
            gson.fromJson<List<DeviceProfile?>>(withProfileDefaults(json), type)
                .orEmpty()
                .filterNotNull()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Gson bypasses Kotlin constructor defaults, so profiles written before a field existed
     * would deserialize it as null. Fill every string default in the stored JSON before
     * parsing so the backward-compatible values apply.
     */
    private fun withProfileDefaults(json: String): String {
        return try {
            val root = JsonParser().parse(json)
            if (!root.isJsonArray) return json
            root.asJsonArray.forEach { element ->
                if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    ensureString(obj, "id") { UUID.randomUUID().toString() }
                    ensureString(obj, "name") { "" }
                    ensureString(obj, "mac") { "" }
                    ensureString(obj, "ip") { "" }
                    ensureString(obj, "pcSecret") { "" }
                }
            }
            root.toString()
        } catch (_: Exception) {
            json
        }
    }

    private fun ensureString(obj: JsonObject, key: String, fallback: () -> String) {
        val value = obj.get(key)
        val usable = value != null && !value.isJsonNull && value.isJsonPrimitive && value.asString.isNotBlank()
        if (!usable) obj.addProperty(key, fallback())
    }

    fun saveDevices(devices: List<DeviceProfile>) = synchronized(profileLock) {
        val json = gson.toJson(devices)
        prefs.edit().putString(KEY_DEVICES_JSON, json).apply()
        notifyWidgetUpdate()
    }

    fun addDevice(name: String, mac: String, ip: String, pcSecret: String = ""): DeviceProfile =
        synchronized(profileLock) {
            val newDevice = DeviceProfile(
                name = name.trim().ifEmpty { AppLocale.wrap(context).getString(R.string.default_device_new) },
                mac = mac.trim(),
                ip = ip.trim(),
                pcSecret = pcSecret.trim()
            )
            val current = getDevices().toMutableList()
            current.add(newDevice)
            saveDevices(current)
            if (getSelectedDeviceId().isEmpty()) {
                setSelectedDeviceId(newDevice.id)
            }
            newDevice
        }

    fun updateDevice(updated: DeviceProfile, preservePcSecret: Boolean = true) =
        synchronized(profileLock) {
            val current = getDevices().toMutableList()
            val index = current.indexOfFirst { it.id == updated.id }
            if (index != -1) {
                // Legacy callers that only know name/MAC/IP keep the existing per-PC secret. The edit
                // dialog opts out so clearing the field switches the profile back to the shared secret.
                val pcSecret = if (preservePcSecret && updated.pcSecret.isBlank()) {
                    current[index].pcSecret.orEmpty()
                } else {
                    updated.pcSecret.trim()
                }
                val merged = updated.copy(pcSecret = pcSecret)
                current[index] = merged
                saveDevices(current)
                if (getSelectedDeviceId() == updated.id) {
                    syncToMqttConfig(merged.mac)
                }
            }
        }

    fun deleteDevice(id: String) = synchronized(profileLock) {
        val current = getDevices().toMutableList()
        current.removeAll { it.id == id }
        saveDevices(current)

        if (current.isEmpty()) {
            // Do not leave the MQTT target MAC or the widget pointing at a deleted profile.
            setSelectedDeviceId("")
            syncToMqttConfig("")
        } else if (getSelectedDeviceId() == id) {
            setSelectedDeviceId(current.first().id)
        }
    }

    fun getSelectedDeviceId(): String = synchronized(profileLock) {
        prefs.getString(KEY_SELECTED_DEVICE_ID, "") ?: ""
    }

    fun setSelectedDeviceId(id: String) = synchronized(profileLock) {
        prefs.edit().putString(KEY_SELECTED_DEVICE_ID, id).apply()
        val device = getDevices().find { it.id == id }
        if (device != null) {
            syncToMqttConfig(device.mac)
        } else if (id.isEmpty()) {
            syncToMqttConfig("")
        }
        notifyWidgetUpdate()
    }

    fun getSelectedDevice(): DeviceProfile? = synchronized(profileLock) {
        val selectedId = getSelectedDeviceId()
        val devices = getDevices()
        devices.find { it.id == selectedId } ?: devices.firstOrNull()
    }

    private fun syncToMqttConfig(mac: String) {
        try {
            val configManager = MqttConfigManager(context)
            val config = configManager.getConfig()
            if (config.targetMac != mac) {
                configManager.saveConfig(config.copy(targetMac = mac))
            }
        } catch (_: Exception) {
            // Best effort sync
        }
    }

    fun notifyWidgetUpdate() {
        try {
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, WakePcWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isNotEmpty()) {
                val intent = android.content.Intent(context, WakePcWidgetProvider::class.java).apply {
                    action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        } catch (_: Exception) {
            // Widget update is best-effort
        }
    }
}
