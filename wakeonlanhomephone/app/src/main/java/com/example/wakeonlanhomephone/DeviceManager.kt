package com.example.wakeonlanhomephone

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class MqttDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val topic: String,
    val onlinePayload: String = "online",
    val offlinePayload: String = "offline"
)

class DeviceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mqtt_devices", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_DEVICES = "devices_list"
    }

    fun getDevices(): List<MqttDevice> {
        val json = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        val type = object : TypeToken<List<MqttDevice>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveDevices(devices: List<MqttDevice>) {
        val json = gson.toJson(devices)
        prefs.edit().putString(KEY_DEVICES, json).apply()
        // Update global state immediately
        AppGlobalState.updateDevices(devices)
    }

    fun addDevice(device: MqttDevice) {
        val current = getDevices().toMutableList()
        current.add(device)
        saveDevices(current)
    }

    fun removeDevice(deviceId: String) {
        val current = getDevices().toMutableList()
        current.removeAll { it.id == deviceId }
        saveDevices(current)
    }

    fun updateDevice(device: MqttDevice) {
        val current = getDevices().toMutableList()
        val index = current.indexOfFirst { it.id == device.id }
        if (index != -1) {
            current[index] = device
            saveDevices(current)
        }
    }
}
