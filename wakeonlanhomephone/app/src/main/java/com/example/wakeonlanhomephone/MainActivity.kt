package com.example.wakeonlanhomephone // 請確認 package 名稱與您的專案一致

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =================================================================================
// 專案設定提醒 (與之前相同)
// =================================================================================
// 1. 在 AndroidManifest.xml 中加入權限：
//    <uses-permission android:name="android.permission.INTERNET" />
//    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
//    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
//    <uses-permission android:name="android.permission.WAKE_LOCK" />
//    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
// 2. 在 AndroidManifest.xml 的 <application> 標籤內註冊 Service：
//    <service android:name=".WolListenerService" />
// =================================================================================

class MainActivity : ComponentActivity() {

    // ==================== 程式碼修正 ====================
    // 將 wolService 本身變成一個 Compose 狀態物件。
    // 這樣當它從 null 變為服務實例時，UI 就會自動重繪。
    private var wolService by mutableStateOf<WolListenerService?>(null)
    // ===============================================

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as WolListenerService.LocalBinder
            wolService = binder.getService() // 賦值給狀態物件，觸發 UI 更新
            Log.d("MainActivity", "服務已連接")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            wolService = null // 清空狀態物件，觸發 UI 更新
            Log.d("MainActivity", "服務已斷開")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        setContent {
            // ==================== 程式碼修正 ====================
            // 現在直接從 wolService 狀態物件來衍生其他狀態，更簡潔可靠
            val isServiceRunning by wolService?.isRunning?.collectAsState() ?: remember { mutableStateOf(false) }
            val logs by wolService?.logs?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
            // ===============================================

            HelperAppScreen(
                logs = logs,
                isRunning = isServiceRunning,
                onStartService = {
                    Intent(this, WolListenerService::class.java).also { intent ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        // 啟動後立刻綁定，確保連接
                        bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    }
                },
                onStopService = {
                    Intent(this, WolListenerService::class.java).also { intent ->
                        // 解除綁定後再停止服務
                        if (wolService != null) {
                            unbindService(connection)
                            wolService = null
                        }
                        stopService(intent)
                    }
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // 首次進入 App 時也嘗試綁定，以便同步已在運行的服務狀態
        Intent(this, WolListenerService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // App 退至背景時，如果服務已綁定則解除
        if (wolService != null) {
            unbindService(connection)
            wolService = null
        }
    }
}

@Composable
fun HelperAppScreen(
    logs: List<String>,
    isRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val listState = rememberLazyListState()

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("遠端控制助手", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("服務狀態：", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isRunning) "執行中" else "已停止",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onStartService, enabled = !isRunning) { Text("啟動服務") }
                Button(onClick = onStopService, enabled = isRunning) { Text("停止服務") }
            }
            Spacer(Modifier.height(24.dp))

            Text("即時日誌", style = MaterialTheme.typography.titleLarge)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("尚未收到任何指令")
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(logs) { log ->
                        Text(log, modifier = Modifier.padding(vertical = 4.dp))
                        Divider()
                    }
                }
            }
        }
    }
}


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

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "WolListenerChannel"
        const val NOTIFICATION_ID = 1
        const val LISTENING_PORT = 9876
        const val TAG = "WolListenerService"
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!_isRunning.value) {
            _isRunning.value = true
            Log.d(TAG, "服務啟動")

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("正在監聽喚醒指令..."))

            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WoLHelper::CpuWakeLock")
            wakeLock?.acquire()

            serverThread = Thread { listener() }
            serverThread.start()
        }
        return START_STICKY
    }

    private fun listener() {
        DatagramSocket(LISTENING_PORT).use { socket ->
            Log.d(TAG, "開始在 UDP 埠 $LISTENING_PORT 上監聽")
            val buffer = ByteArray(256)
            while (_isRunning.value) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val senderAddress = packet.address?.hostAddress ?: "未知來源"
                    val macAddress = String(packet.data, 0, packet.length).trim()

                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.TAIWAN).format(Date())
                    val logMessage = "[$timestamp] 收到 [$senderAddress] 指令，目標 MAC: $macAddress"

                    Log.d(TAG, logMessage)

                    val currentLogs = _logs.value.toMutableList()
                    currentLogs.add(0, logMessage)
                    _logs.value = currentLogs

                    updateNotification("最後指令來自: $senderAddress")

                    Thread {
                        val result = sendMagicPacketIPv6(macAddress)
                        Log.d(TAG, "發送結果: $result")
                    }.start()

                } catch (e: Exception) {
                    if (_isRunning.value) Log.e(TAG, "監聽時發生錯誤", e)
                }
            }
        }
        Log.d(TAG, "監聽結束")
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("遠端助手服務")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(newContentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(newContentText))
    }

    private fun sendMagicPacketIPv6(macAddress: String): String {
        try {
            val macBytes = getMacBytes(macAddress) ?: return "MAC 位址格式錯誤"
            val magicPacket = ByteArray(102).apply {
                (0..5).forEach { this[it] = 0xFF.toByte() }
                (1..16).forEach { i -> macBytes.copyInto(this, i * 6) }
            }
            val multicastAddress = Inet6Address.getByName("ff02::1")
            val packet = DatagramPacket(magicPacket, magicPacket.size, multicastAddress, 9)
            DatagramSocket().use { socket -> socket.send(packet) }
            return "Magic Packet 已多點傳播到 ff02::1"
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

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        if (this::serverThread.isInitialized) {
            serverThread.interrupt()
        }
        wakeLock?.release()
        Log.d(TAG, "服務停止")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "WoL Listener Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }
}

