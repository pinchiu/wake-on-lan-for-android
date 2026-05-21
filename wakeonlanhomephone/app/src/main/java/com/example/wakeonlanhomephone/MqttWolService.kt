package com.example.wakeonlanhomephone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import java.nio.charset.StandardCharsets
import java.util.UUID

class MqttWolService : Service() {

    private var mqttClient: Mqtt3AsyncClient? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var cachedConfig: MqttConfig? = null

    companion object {
        const val TAG = "MqttWolService"
        const val NOTIFICATION_CHANNEL_ID = "MqttWolServiceChannel"
        const val NOTIFICATION_ID = 2
        const val TOPIC = "home/pc/power"
        const val ACTION_PUBLISH = "com.example.wakeonlanhomephone.action.PUBLISH"
        const val EXTRA_TOPIC = "com.example.wakeonlanhomephone.extra.TOPIC"
        const val EXTRA_MESSAGE = "com.example.wakeonlanhomephone.extra.MESSAGE"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun getMqttConfig(): MqttConfig {
        var config = cachedConfig
        if (config == null) {
            config = MqttConfigManager(this).getConfig()
            cachedConfig = config
        }
        return config
    }

    private fun acquireWakeLock(timeout: Long) {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MqttWolService::WakeLock")
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(timeout)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Acquire WakeLock 失敗", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Release WakeLock 失敗", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing MQTT Service..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PUBLISH) {
            val topic = intent.getStringExtra(EXTRA_TOPIC) ?: ""
            val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
            if (topic.isNotEmpty()) {
                publishMessage(topic, message)
            }
            return START_STICKY
        }

        Log.d(TAG, "Service Started")
        AppLogger.log("MQTT Service Started")
        connectMqtt()
        return START_STICKY
    }

    private fun publishMessage(topic: String, message: String) {
         val client = mqttClient ?: return
         if (!client.state.isConnected) {
             AppLogger.log("Cannot publish: MQTT Disconnected")
             return
         }
         client.publishWith()
             .topic(topic)
             .payload(message.toByteArray(StandardCharsets.UTF_8))
             .send()
             .whenComplete { _, throwable ->
                 if (throwable != null) {
                     Log.e(TAG, "Publish failed", throwable)
                     AppLogger.log("Publish Failed: ${throwable.message}")
                 } else {
                     Log.d(TAG, "Published to $topic: $message")
                     AppLogger.log("Published to $topic: $message")
                 }
             }
    }
    private fun connectMqtt() {
        cachedConfig = null
        val currentClient = mqttClient
        if (currentClient != null && currentClient.state.isConnected) {
            Log.d(TAG, "Already connected")
            AppGlobalState.updateState(MqttConnectionState.CONNECTED)
            return
        }

        val config = getMqttConfig()
        Log.d(TAG, "Connecting to ${config.host}:${config.port} as ${config.username}")
        
        if (config.host.isEmpty()) {
            Log.e(TAG, "Host is empty, skipping connection")
            AppGlobalState.updateState(MqttConnectionState.FAILED, error = "Host not configured")
            return
        }

        AppLogger.log("Connecting to MQTT: ${config.host}")
        
        // Report Connecting State
        val protocolPrefix = if (config.useSsl) "ssl://" else "tcp://"
        AppGlobalState.updateState(MqttConnectionState.CONNECTING, url = "$protocolPrefix${config.host}:${config.port}")

        // Acquire WakeLock during connection attempt (30 seconds timeout)
        acquireWakeLock(30 * 1000L)

        val builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier(if (config.clientId.isNotEmpty()) config.clientId else UUID.randomUUID().toString())
            .serverHost(config.host)
            .serverPort(config.port)

        if (config.useSsl) {
            builder.sslWithDefaultConfig()
        }

        if (config.protocol.equals("WebSocket", ignoreCase = true)) {
            builder.webSocketWithDefaultConfig()
            Log.d(TAG, "Using WebSocket Protocol")
        } else {
             Log.d(TAG, "Using TCP Protocol")
        }
        
        if (config.autoConnect) {
            builder.automaticReconnectWithDefaultConfig()
        }

        mqttClient = builder.buildAsync()

        val client = mqttClient ?: return

        client.connectWith()
            .simpleAuth()
            .username(config.username)
            .password(config.password.toByteArray(StandardCharsets.UTF_8))
            .applySimpleAuth()
            .keepAlive(config.keepAlive)
            .send()
            .whenComplete { connAck: Mqtt3ConnAck?, throwable: Throwable? ->
                releaseWakeLock()
                if (throwable != null) {
                    Log.e(TAG, "Connection failed", throwable)
                    updateNotification("MQTT Connection Failed. Retrying...")
                    AppLogger.log("MQTT Connection Failed: ${throwable.message}")
                    AppGlobalState.updateState(MqttConnectionState.FAILED, error = throwable.message ?: "Unknown Error")
                } else {
                    Log.d(TAG, "Connected to MQTT")
                    updateNotification("Connected to MQTT. Listening on ${config.topic}")
                    AppLogger.log("MQTT Connected. Listening on ${config.topic}")
                    AppGlobalState.updateState(MqttConnectionState.CONNECTED)
                    subscribeToTopic(config.topic)
                    
                    // Subscribe to Devices
                    val deviceManager = DeviceManager(this)
                    val devices = deviceManager.getDevices()
                    AppGlobalState.updateDevices(devices) // Sync state
                    devices.forEach { device ->
                        subscribeToDevice(device)
                    }
                }
            }
    }

    private fun subscribeToTopic(topic: String) {
        val client = mqttClient ?: return
        client.subscribeWith()
            .topicFilter(topic)
            .callback { publish ->
                val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8).trim()
                Log.d(TAG, "Received message: $payload")
                AppLogger.log("MQTT Received: $payload")
                

                val config = getMqttConfig()

                // 1. Check for "ACTION,MAC" format
                if (payload.contains(",")) {
                    val parts = payload.split(",")
                    if (parts.size >= 2) {
                        val action = parts[0].trim()
                        val macAddress = parts[1].trim()
                        
                        if (action.equals("WAKE", ignoreCase = true)) {
                            // Helper method to format MAC if needed or just pass strict check
                            if (macAddress.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$"))) {
                                val result = WolUtil.sendMagicPacket(macAddress)
                                Log.d(TAG, "WOL Result: $result")
                                updateNotification("WOL Sent to $macAddress")
                                AppLogger.log("MQTT Action: WOL Sent to $macAddress")
                            } else {
                                Log.e(TAG, "Invalid MAC in command: $macAddress")
                                AppLogger.log("Error: Invalid MAC in WAKE command")
                            }
                        }
                    }
                }
                // 2. Check for simple "WAKE" or "ON" (uses Target MAC from settings)
                else if (payload.equals("WAKE", ignoreCase = true) || payload.equals("ON", ignoreCase = true)) {
                    if (config.targetMac.isNotEmpty()) {
                         val result = WolUtil.sendMagicPacket(config.targetMac)
                         Log.d(TAG, "WOL Result: $result")
                         updateNotification("WOL Sent to ${config.targetMac}")
                         AppLogger.log("MQTT Action: WOL Sent to default MAC (${config.targetMac})")
                    } else {
                        Log.e(TAG, "WAKE command received but no Target MAC configured")
                        AppLogger.log("Error: WAKE received but Target MAC is empty")
                    }
                }
                // 3. Triggering WOL if it looks like a MAC directly
                 else if (payload.matches(Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$"))) {
                     val result = WolUtil.sendMagicPacket(payload)
                     Log.d(TAG, "WOL Result: $result")
                     updateNotification("WOL Sent to $payload")
                     AppLogger.log("MQTT Action: WOL Sent ($result)")
                 } else {
                     Log.d(TAG, "Ignored unknown payload: $payload")
                 }
            }
            .send()
            .whenComplete { subAck, throwable ->
                 if (throwable != null) {
                     Log.e(TAG, "Subscription failed", throwable)
                     AppLogger.log("MQTT Subscription failed")
                 } else {
                     Log.d(TAG, "Subscribed to $topic")
                 }
            }
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Mqtt WOL Service")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MQTT WOL Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }

    private fun subscribeToDevice(device: MqttDevice) {
        val client = mqttClient ?: return
        client.subscribeWith()
            .topicFilter(device.topic)
            .callback { publish ->
                val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8).trim()
                Log.d(TAG, "Device ${device.name} status: $payload")
                
                if (payload == device.onlinePayload) {
                    AppGlobalState.updateDeviceStatus(device.id, true)
                } else if (payload == device.offlinePayload) {
                    AppGlobalState.updateDeviceStatus(device.id, false)
                }
            }
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "Failed to subscribe to device: ${device.name}", throwable)
                } else {
                    Log.d(TAG, "Subscribed to device: ${device.name}")
                }
            }
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting MQTT", e)
        }
        releaseWakeLock()
        AppGlobalState.updateState(MqttConnectionState.DISCONNECTED)
        cachedConfig = null
        try {
            stopForeground(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }
        super.onDestroy()
    }
}
