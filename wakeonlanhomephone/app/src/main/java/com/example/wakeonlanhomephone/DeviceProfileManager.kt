package com.example.wakeonlanhomephone

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * 家用助手端目標電腦資料模型。
 * 支援使用者儲存多台電腦之名稱、網卡 MAC 地址與區域網路 IPv4 位址。
 */
data class DeviceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mac: String,
    val ip: String = ""
)

/**
 * 家用助手端電腦設定檔管理員。
 * 管理目標電腦清單、當前選定目標，並自動與 MqttConfigManager 及桌面小工具 (WakePcWidgetProvider) 同步。
 */
class DeviceProfileManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val PREFS_NAME = "HomePhoneDevicePrefs"
        const val KEY_DEVICES_JSON = "devices_profile_list"
        const val KEY_SELECTED_DEVICE_ID = "selected_device_id"
    }

    init {
        // 向下相容：初次啟動時自動遷移 MqttConfigManager 的舊版單一 MAC 設定
        if (getDevices().isEmpty()) {
            val configManager = MqttConfigManager(context)
            val oldMac = configManager.getConfig().targetMac.trim()
            if (oldMac.isNotEmpty()) {
                val initialDevice = DeviceProfile(
                    name = "主要電腦",
                    mac = oldMac,
                    ip = ""
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
                syncToMqttConfig(updated.mac)
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
            syncToMqttConfig(device.mac)
        }
        notifyWidgetUpdate()
    }

    fun getSelectedDevice(): DeviceProfile? {
        val selectedId = getSelectedDeviceId()
        val devices = getDevices()
        return devices.find { it.id == selectedId } ?: devices.firstOrNull()
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
