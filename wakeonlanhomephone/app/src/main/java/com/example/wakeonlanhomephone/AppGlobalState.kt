package com.example.wakeonlanhomephone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MqttConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

object AppGlobalState {
    private val _connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage = _lastErrorMessage.asStateFlow()

    private val _brokerUrl = MutableStateFlow("")
    val brokerUrl = _brokerUrl.asStateFlow()

    private val _devices = MutableStateFlow<List<MqttDevice>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _deviceStatuses = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val deviceStatuses = _deviceStatuses.asStateFlow()

    fun updateState(state: MqttConnectionState, error: String? = null, url: String? = null) {
        _connectionState.value = state
        if (error != null) _lastErrorMessage.value = error
        if (url != null) _brokerUrl.value = url
    }
    
    fun updateDevices(newDevices: List<MqttDevice>) {
        _devices.value = newDevices
    }

    fun updateDeviceStatus(deviceId: String, isOnline: Boolean) {
        // Called from HiveMQ/Netty threads; atomic update prevents concurrent status loss.
        _deviceStatuses.update { current -> current + (deviceId to isOnline) }
    }

    fun resetError() {
        _lastErrorMessage.value = null
    }
}
