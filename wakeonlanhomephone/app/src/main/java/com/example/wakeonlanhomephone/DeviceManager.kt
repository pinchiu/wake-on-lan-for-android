package com.example.wakeonlanhomephone

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class MqttDevice(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("name") val name: String,
    @SerializedName("topic") val topic: String,
    @SerializedName("onlinePayload") val onlinePayload: String = "online",
    @SerializedName("offlinePayload") val offlinePayload: String = "offline"
)

class DeviceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mqtt_devices", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_DEVICES = "devices_list"

        /** Serializes the read-modify-write mutations of the stored device list. */
        private val deviceLock = Any()
    }

    fun getDevices(): List<MqttDevice> = synchronized(deviceLock) {
        val json = prefs.getString(KEY_DEVICES, null) ?: return@synchronized emptyList()
        try {
            val type = object : TypeToken<List<MqttDevice?>>() {}.type
            gson.fromJson<List<MqttDevice?>>(withDeviceDefaults(json), type)
                .orEmpty()
                .filterNotNull()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveDevices(devices: List<MqttDevice>) = synchronized(deviceLock) {
        val json = gson.toJson(devices)
        prefs.edit().putString(KEY_DEVICES, json).apply()
        // Update global state immediately
        AppGlobalState.updateDevices(devices)
    }

    fun addDevice(device: MqttDevice) = synchronized(deviceLock) {
        val current = getDevices().toMutableList()
        current.add(device)
        saveDevices(current)
    }

    fun removeDevice(deviceId: String) = synchronized(deviceLock) {
        val current = getDevices().toMutableList()
        current.removeAll { it.id == deviceId }
        saveDevices(current)
    }

    fun updateDevice(device: MqttDevice) = synchronized(deviceLock) {
        val current = getDevices().toMutableList()
        val index = current.indexOfFirst { it.id == device.id }
        if (index != -1) {
            current[index] = device
            saveDevices(current)
        }
    }

    /**
     * Gson bypasses Kotlin constructor defaults, so a legacy entry missing (or nulling) a
     * field would inject `null` into a non-null property. Fill the defaults in the JSON
     * before parsing instead.
     */
    private fun withDeviceDefaults(json: String): String = try {
        val root = JsonParser().parse(json)
        if (!root.isJsonArray) {
            json
        } else {
            root.asJsonArray.forEach { element ->
                if (element.isJsonObject) {
                    val obj = element.asJsonObject
                    ensureString(obj, "id") { UUID.randomUUID().toString() }
                    ensureString(obj, "name") { "" }
                    ensureString(obj, "topic") { "" }
                    ensureString(obj, "onlinePayload") { "online" }
                    ensureString(obj, "offlinePayload") { "offline" }
                }
            }
            root.toString()
        }
    } catch (_: Exception) {
        json
    }

    private fun ensureString(obj: JsonObject, key: String, fallback: () -> String) {
        val value = obj.get(key)
        val usable = value != null && !value.isJsonNull && value.isJsonPrimitive && value.asString.isNotBlank()
        if (!usable) obj.addProperty(key, fallback())
    }
}
