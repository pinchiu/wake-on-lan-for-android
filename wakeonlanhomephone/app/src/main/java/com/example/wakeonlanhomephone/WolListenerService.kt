package com.example.wakeonlanhomephone

import android.app.*
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.*
import java.text.SimpleDateFormat
import java.util.*

class WolListenerService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): WolListenerService = this@WolListenerService
    }
    private val binder = LocalBinder()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private lateinit var serverThread: Thread
    private var wakeLock: PowerManager.WakeLock? = null
    private var socket: DatagramSocket? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "WolListenerChannel"
        const val NOTIFICATION_ID = 1
        const val LISTENING_PORT = 9876
        private const val SOCKET_TIMEOUT_MS = 1000
        private const val PC_COMMAND_PORT = 9877
        const val TAG = "WolListenerService"
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!_isRunning.value) {
            _isRunning.value = true
            _logs.value = emptyList()
            Log.d(TAG, "Service started")

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("Listening for wake-up commands..."))

            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WoLHelper::CpuWakeLock")
            wakeLock?.acquire(10 * 60 * 1000L)

            serverThread = Thread { listener() }
            serverThread.start()
        }
        return START_STICKY
    }

    private fun listener() {
        try {
            socket = DatagramSocket(LISTENING_PORT)
            socket?.soTimeout = SOCKET_TIMEOUT_MS
            Log.d(TAG, "Started listening on UDP port $LISTENING_PORT")
            val buffer = ByteArray(256)

            while (_isRunning.value && socket != null) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet) ?: break

                    val senderAddress = packet.address?.hostAddress ?: "Unknown source"
                    val commandData = String(packet.data, 0, packet.length)

                    val (action, payload) = parseCommand(commandData.trim())
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.TAIWAN).format(Date())
                    val logMessage = "[$timestamp] Received [$action] from [$senderAddress]"

                    Log.d(TAG, "$logMessage, Payload: $payload")

                    mainHandler.post {
                        val currentLogs = _logs.value.toMutableList()
                        currentLogs.add(logMessage)
                        if (currentLogs.size > 100) currentLogs.removeAt(0)
                        _logs.value = currentLogs
                    }

                    updateNotification("Last [$action] from: $senderAddress")

                    when (action) {
                        "WAKE" -> {
                            if (isValidMac(payload)) {
                                val result = sendMagicPacketIPv6(payload)
                                Log.d(TAG, "WoL send result: $result")
                            } else {
                                Log.e(TAG, "Invalid MAC: $payload")
                            }
                        }
                        "SHUTDOWN" -> {
                            if (isValidIpv4(payload)) {
                                Thread { sendCommandToPC("shutdown", payload) }.start()
                            } else {
                                Log.e(TAG, "Invalid IP: $payload")
                            }
                        }
                        "REBOOT" -> {
                            if (isValidIpv4(payload)) {
                                Thread { sendCommandToPC("reboot", payload) }.start()
                            } else {
                                Log.e(TAG, "Invalid IP: $payload")
                            }
                        }
                        "SLEEP" -> {
                            if (isValidIpv4(payload)) {
                                Thread { sendCommandToPC("sleep", payload) }.start()
                            } else {
                                Log.e(TAG, "Invalid IP: $payload")
                            }
                        }
                        "HIBERNATE" -> {
                            if (isValidIpv4(payload)) {
                                Thread { sendCommandToPC("hibernate", payload) }.start()
                            } else {
                                Log.e(TAG, "Invalid IP: $payload")
                            }
                        }
                        "MAC" -> {
                            val result = sendMagicPacketIPv6(payload)
                            Log.d(TAG, "WoL send result: $result")
                        }
                        else -> {
                            Log.w(TAG, "Unknown action: $action")
                        }
                    }

                } catch (timeout: SocketTimeoutException) {
                } catch (e: Exception) {
                    Log.e(TAG, "Error while listening or socket was closed: ${e.message}", e)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not bind to port $LISTENING_PORT", e)
        } finally {
            socket?.close()
            socket = null
            Log.d(TAG, "Listener thread finished")
        }
    }

    private fun parseCommand(data: String): Pair<String, String> {
        return if (data.contains(':')) {
            val parts = data.split(':', limit = 2)
            parts[0].uppercase() to parts[1]
        } else {
            "MAC" to data
        }
    }

    private fun isValidMac(mac: String): Boolean {
        val regex = "^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$".toRegex()
        return regex.matches(mac)
    }

    private fun isValidIpv4(ip: String): Boolean {
        val regex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$".toRegex()
        return regex.matches(ip)
    }

    private fun sendMagicPacketIPv6(macAddress: String): String {
        try {
            val macBytes = getMacBytes(macAddress) ?: return "Invalid MAC address format"
            val magicPacket = ByteArray(102).apply {
                (0..5).forEach { this[it] = 0xFF.toByte() }
                for (i in 1..16) {
                    macBytes.copyInto(this, i * 6)
                }
            }

            val broadcastAddr = "255.255.255.255"  // Change to your LAN broadcast address, e.g., 192.168.1.255
            val packet = DatagramPacket(magicPacket, magicPacket.size, InetAddress.getByName(broadcastAddr), 9)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(packet)
            }
            return "Magic Packet broadcasted to $broadcastAddr:9"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Magic Packet", e)
            return "Send failed: ${e.message}"
        }
    }

    private fun getMacBytes(macStr: String): ByteArray? {
        val bytes = ByteArray(6)
        val hex = macStr.split(':', '-')
        if (hex.size != 6) return null
        try {
            for (i in 0..5) { bytes[i] = hex[i].toInt(16).toByte() }
        } catch (e: NumberFormatException) { return null }
        return bytes
    }

    private fun sendCommandToPC(command: String, pcIp: String): String {
        try {
            val packet = DatagramPacket(command.toByteArray(), command.length, InetAddress.getByName(pcIp), PC_COMMAND_PORT)
            DatagramSocket().use { socket -> socket.send(packet) }
            return "Command '$command' sent to $pcIp:$PC_COMMAND_PORT"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command", e)
            return "Send failed: ${e.message}"
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Remote Helper Service")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(newContentText: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(newContentText))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (_isRunning.value) {
            _isRunning.value = false
            socket?.close()
            if (::serverThread.isInitialized) {
                serverThread.interrupt()
            }
            wakeLock?.release()
            mainHandler.removeCallbacksAndMessages(null)
            Log.d(TAG, "Service stopped")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "WoL Listener Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Persistent notification for the WoL Helper Service"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }
}