package com.example.wakeonwanremotephone

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Target computer data model.
 * Supports storing multiple computers with name, MAC address, and local IPv4 address.
 *
 * [pcSecret] is optional and only needed when the `pc_onoff` daemon is reached
 * through a public port-forward. A blank value means "use the shared PC secret
 * from [SecuritySettings]", so older profiles keep working unchanged.
 */
data class DeviceProfile(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("name") val name: String,
    @SerializedName("mac") val mac: String,
    @SerializedName("ip") val ip: String = "",
    @SerializedName("pcSecret") val pcSecret: String = ""
)

/**
 * Device and profile manager for the Remote Phone client.
 */
class DeviceProfileManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val PREFS_NAME = "RemoteControlPrefs"
        const val KEY_DEVICES_JSON = "devices_profile_list"
        const val KEY_SELECTED_DEVICE_ID = "selected_device_id"
        const val KEY_IPV6 = "helperIpv6Address"
        const val KEY_MAC = "computerMacAddress"
        const val KEY_IPV4 = "computerLocalIpv4"
        const val KEY_LAN_MODE = "localLanMode"
        private const val KEY_LEGACY_MIGRATED = "legacy_migrated"

        /** Serialises all read-modify-write cycles on the shared preference list. */
        private val DEVICES_LOCK = Any()
    }

    init {
        synchronized(DEVICES_LOCK) {
            // Migrate the legacy single-computer preferences exactly once. Without the
            // flag, deleting the last device would resurrect it on the next launch.
            if (!prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) {
                if (getDevices().isEmpty()) {
                    val oldMac = prefs.getString(KEY_MAC, "")?.trim() ?: ""
                    val oldIp = prefs.getString(KEY_IPV4, "")?.trim() ?: ""
                    if (oldMac.isNotEmpty()) {
                        val initialDevice = DeviceProfile(
                            name = AppLocale.wrap(context).getString(R.string.default_device_primary),
                            mac = oldMac,
                            ip = oldIp
                        )
                        saveDevices(listOf(initialDevice))
                        setSelectedDeviceId(initialDevice.id)
                    }
                }
                prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
            }
        }
    }

    fun getDevices(): List<DeviceProfile> = synchronized(DEVICES_LOCK) {
        val json = prefs.getString(KEY_DEVICES_JSON, null) ?: return@synchronized emptyList()
        try {
            val type = object : TypeToken<List<DeviceProfile?>>() {}.type
            val loaded: List<DeviceProfile?>? = gson.fromJson(json, type)
            val localized = AppLocale.wrap(context)
            loaded.orEmpty().mapNotNull { raw ->
                if (raw == null) return@mapNotNull null
                // Gson bypasses Kotlin defaults and nullability, so sanitise every field.
                val id = nullableTrimmed(raw.id)
                val name = nullableTrimmed(raw.name)
                val mac = nullableTrimmed(raw.mac)
                val ip = nullableTrimmed(raw.ip)
                val pcSecret = nullableTrimmed(raw.pcSecret)
                DeviceProfile(
                    // A deterministic fallback keeps a legacy/profile-less entry selectable
                    // without a new random id on every read.
                    id = id.ifBlank {
                        UUID.nameUUIDFromBytes("$name|$mac|$ip".toByteArray()).toString()
                    },
                    name = name.ifBlank { localized.getString(R.string.default_device_new) },
                    mac = mac,
                    ip = ip,
                    pcSecret = pcSecret
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDevices(devices: List<DeviceProfile>) {
        synchronized(DEVICES_LOCK) {
            val json = gson.toJson(devices)
            prefs.edit().putString(KEY_DEVICES_JSON, json).apply()
            notifyWidgetUpdate()
        }
    }

    fun addDevice(name: String, mac: String, ip: String, pcSecret: String = ""): DeviceProfile {
        return synchronized(DEVICES_LOCK) {
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
    }

    fun updateDevice(updated: DeviceProfile) {
        synchronized(DEVICES_LOCK) {
            val current = getDevices().toMutableList()
            val index = current.indexOfFirst { it.id == updated.id }
            if (index != -1) {
                current[index] = updated
                saveDevices(current)
                if (getSelectedDeviceId() == updated.id) {
                    prefs.edit()
                        .putString(KEY_MAC, updated.mac)
                        .putString(KEY_IPV4, updated.ip)
                        .apply()
                }
            }
        }
    }

    fun deleteDevice(id: String) {
        synchronized(DEVICES_LOCK) {
            val current = getDevices().toMutableList()
            current.removeAll { it.id == id }
            saveDevices(current)
            if (getSelectedDeviceId() == id) {
                val next = current.firstOrNull()
                if (next != null) {
                    setSelectedDeviceId(next.id)
                } else {
                    setSelectedDeviceId("")
                }
            }
            if (current.isEmpty()) {
                // Never leave the legacy keys behind: they would be migrated back
                // into a device on the next launch.
                prefs.edit()
                    .putString(KEY_MAC, "")
                    .putString(KEY_IPV4, "")
                    .apply()
            }
        }
    }

    fun getSelectedDeviceId(): String {
        return prefs.getString(KEY_SELECTED_DEVICE_ID, "") ?: ""
    }

    fun setSelectedDeviceId(id: String) {
        synchronized(DEVICES_LOCK) {
            prefs.edit().putString(KEY_SELECTED_DEVICE_ID, id).apply()
            val device = getDevices().find { it.id == id }
            if (device != null) {
                prefs.edit()
                    .putString(KEY_MAC, device.mac)
                    .putString(KEY_IPV4, device.ip)
                    .apply()
            } else if (id.isBlank()) {
                prefs.edit()
                    .putString(KEY_MAC, "")
                    .putString(KEY_IPV4, "")
                    .apply()
            }
            notifyWidgetUpdate()
        }
    }

    fun getSelectedDevice(): DeviceProfile? {
        val selectedId = getSelectedDeviceId()
        val devices = getDevices()
        return devices.find { it.id == selectedId } ?: devices.firstOrNull()
    }

    fun getHelperIpv6(): String {
        return prefs.getString(KEY_IPV6, "") ?: ""
    }

    fun setHelperIpv6(ipv6: String) {
        prefs.edit().putString(KEY_IPV6, ipv6.trim()).apply()
        notifyWidgetUpdate()
    }

    fun isLocalLanMode(): Boolean {
        return prefs.getBoolean(KEY_LAN_MODE, false)
    }

    fun setLocalLanMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LAN_MODE, enabled).apply()
        notifyWidgetUpdate()
    }

    private fun notifyWidgetUpdate() {
        try {
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, RemoteWakeWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (widgetIds.isNotEmpty()) {
                val intent = android.content.Intent(context, RemoteWakeWidgetProvider::class.java).apply {
                    action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                }
                context.sendBroadcast(intent)
            }
        } catch (_: Exception) {
            // Widget update is best-effort
        }
    }

    private fun nullableTrimmed(value: String?): String = value?.trim().orEmpty()
}
