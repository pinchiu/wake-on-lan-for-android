package com.example.wakeonwanremotephone

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * 目標電腦資料模型。
 * 支援使用者儲存多台電腦之名稱、網卡 MAC 地址與內網 IPv4 位址。
 */
data class DeviceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mac: String,
    val ip: String = ""
)

/**
 * 遠端遙控端之裝置與設定檔管理員。
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
    }

    init {
        // 自動遷移舊版單一電腦設定
        if (getDevices().isEmpty()) {
            val oldMac = prefs.getString(KEY_MAC, "")?.trim() ?: ""
            val oldIp = prefs.getString(KEY_IPV4, "")?.trim() ?: ""
            if (oldMac.isNotEmpty()) {
                val initialDevice = DeviceProfile(
                    name = "主要電腦",
                    mac = oldMac,
                    ip = oldIp
                )
                saveDevices(listOf(initialDevice))
                setSelectedDeviceId(initialDevice.id)
            }
        }
    }

    fun getDevices(): List<DeviceProfile> {
        val json = prefs.getString(KEY_DEVICES_JSON, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DeviceProfile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDevices(devices: List<DeviceProfile>) {
        val json = gson.toJson(devices)
        prefs.edit().putString(KEY_DEVICES_JSON, json).apply()
        notifyWidgetUpdate()
    }

    fun addDevice(name: String, mac: String, ip: String): DeviceProfile {
        val newDevice = DeviceProfile(
            name = name.trim().ifEmpty { "新電腦" },
            mac = mac.trim(),
            ip = ip.trim()
        )
        val current = getDevices().toMutableList()
        current.add(newDevice)
        saveDevices(current)
        if (getSelectedDeviceId().isEmpty()) {
            setSelectedDeviceId(newDevice.id)
        }
        return newDevice
    }

    fun updateDevice(updated: DeviceProfile) {
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

    fun deleteDevice(id: String) {
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
    }

    fun getSelectedDeviceId(): String {
        return prefs.getString(KEY_SELECTED_DEVICE_ID, "") ?: ""
    }

    fun setSelectedDeviceId(id: String) {
        prefs.edit().putString(KEY_SELECTED_DEVICE_ID, id).apply()
        val device = getDevices().find { it.id == id }
        if (device != null) {
            prefs.edit()
                .putString(KEY_MAC, device.mac)
                .putString(KEY_IPV4, device.ip)
                .apply()
        }
        notifyWidgetUpdate()
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
}
