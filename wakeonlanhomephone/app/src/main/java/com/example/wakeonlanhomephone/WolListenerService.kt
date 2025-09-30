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
            Log.d(TAG, "服務啟動")

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("正在監聽喚醒指令..."))

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
            Log.d(TAG, "開始在 UDP 埠 $LISTENING_PORT 上監聽")
            val buffer = ByteArray(256)

            while (_isRunning.value && socket != null) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet) ?: break

                    val senderAddress = packet.address?.hostAddress ?: "未知來源"
                    val commandData = String(packet.data, 0, packet.length)

                    val (action, payload) = parseCommand(commandData.trim())
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.TAIWAN).format(Date())
                    val logMessage = "[$timestamp] 收到 [$senderAddress] [$action]"

                    Log.d(TAG, "$logMessage, 內容: $payload")

                    mainHandler.post {
                        val currentLogs = _logs.value.toMutableList()
                        currentLogs.add(logMessage)
                        if (currentLogs.size > 100) currentLogs.removeAt(0)
                        _logs.value = currentLogs
                    }

                    updateNotification("最後 [$action] 來自: $senderAddress")

                    when (action) {
                        "WAKE" -> {
                            if (isValidMac(payload)) {
                                val result = sendMagicPacketIPv6(payload)
                                Log.d(TAG, "WoL 發送結果: $result")
                            } else {
                                Log.e(TAG, "無效 MAC: $payload")
                            }
                        }
                        "SHUTDOWN" -> {
                            if (isValidIpv4(payload)) {
                                Thread { sendCommandToPC("shutdown", payload) }.start()
                            } else {
                                Log.e(TAG, "無效 IP: $payload")
                            }
                        }
                        "REBOOT" -> {
                            if (isValidIpv4(payload)) {
                                Thread { sendCommandToPC("reboot", payload) }.start()
                            } else {
                                Log.e(TAG, "無效 IP: $payload")
                            }
                        }
                        "SLEEP" -> {
                            if (isValidIpv4(payload)) {
                                Thread { sendCommandToPC("sleep", payload) }.start()
                            } else {
                                Log.e(TAG, "無效 IP: $payload")
                            }
                        }
                        "HIBERNATE" -> {
                            if (isValidIpv4(payload)) {
                                Thread { sendCommandToPC("hibernate", payload) }.start()
                            } else {
                                Log.e(TAG, "無效 IP: $payload")
                            }
                        }
                        "MAC" -> {
                            val result = sendMagicPacketIPv6(payload)
                            Log.d(TAG, "WoL 發送結果: $result")
                        }
                        else -> {
                            Log.w(TAG, "未知動作: $action")
                        }
                    }

                } catch (timeout: SocketTimeoutException) {
                } catch (e: Exception) {
                    Log.e(TAG, "監聽時發生錯誤，或被強制關閉: ${e.message}", e)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "無法綁定監聽埠 $LISTENING_PORT", e)
        } finally {
            socket?.close()
            socket = null
            Log.d(TAG, "監聽執行緒結束")
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
            val macBytes = getMacBytes(macAddress) ?: return "MAC 位址格式錯誤"
            val magicPacket = ByteArray(102).apply {
                (0..5).forEach { this[it] = 0xFF.toByte() }
                for (i in 1..16) {
                    macBytes.copyInto(this, i * 6)
                }
            }

            val broadcastAddr = "255.255.255.255"  // 請改為您的 LAN broadcast, 如 192.168.1.255
            val packet = DatagramPacket(magicPacket, magicPacket.size, InetAddress.getByName(broadcastAddr), 9)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(packet)
            }
            return "Magic Packet 已 broadcast 到 $broadcastAddr:9"
        } catch (e: Exception) {
            Log.e(TAG, "發送 Magic Packet 失敗", e)
            return "發送失敗: ${e.message}"
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
            return "命令 '$command' 已發送至 $pcIp:$PC_COMMAND_PORT"
        } catch (e: Exception) {
            Log.e(TAG, "發送命令失敗", e)
            return "發送失敗: ${e.message}"
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("遠端助手服務")
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
            Log.d(TAG, "服務停止")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "WoL Listener Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "WoL 助手服務的常駐通知"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }
}