package com.example.wakeonlanhomephone

import android.app.*
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
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

    private val dispatcher by lazy {
        PcActionDispatcher(
            defaultMacProvider = { MqttConfigManager(this).getConfig().targetMac }
        )
    }



    private lateinit var serverThread: Thread
    private var wakeLock: PowerManager.WakeLock? = null
    private var serverSocket: ServerSocket? = null
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
            AppLogger.clear()
            Log.d(TAG, "Service started")

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("Listening for wake-up commands (TCP)..."))

            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WoLHelper::CpuWakeLock")
            wakeLock?.acquire()

            serverThread = Thread { listener() }
            serverThread.start()
        }
        return START_STICKY
    }

    private fun listener() {
        try {
            try {
                serverSocket = ServerSocket(LISTENING_PORT, 50, InetAddress.getByName("::"))
                Log.d(TAG, "ServerSocket bound to IPv6 wildcard [::] successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to bind to IPv6 wildcard, falling back to default", e)
                serverSocket = ServerSocket(LISTENING_PORT)
            }
            serverSocket?.soTimeout = SOCKET_TIMEOUT_MS
            Log.d(TAG, "Started listening on TCP port $LISTENING_PORT")
            
            // Log listening addresses to AppLogger for user visibility
            logListeningAddresses()

            while (_isRunning.value && serverSocket != null && !serverSocket!!.isClosed) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    Thread {
                        handleClientConnection(clientSocket)
                    }.start()
                } catch (timeout: SocketTimeoutException) {
                    // Normal timeout to check _isRunning condition
                } catch (e: Exception) {
                    if (_isRunning.value) {
                        Log.e(TAG, "Error while accepting TCP connection: ${e.message}", e)
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not bind to port $LISTENING_PORT", e)
            AppLogger.log("Error: Could not bind to port $LISTENING_PORT (${e.message})")
        } finally {
            try {
                serverSocket?.close()
            } catch (_: Exception) {}
            serverSocket = null
            Log.d(TAG, "Listener thread finished")
        }
    }

    private fun handleClientConnection(clientSocket: Socket) {
        try {
            clientSocket.soTimeout = 5000
            val senderAddress = clientSocket.inetAddress?.hostAddress ?: "Unknown source"
            val reader = clientSocket.getInputStream().bufferedReader()
            val commandData = reader.readLine()?.trim() ?: ""

            if (commandData.isNotEmpty()) {
                val actionResult = runBlocking {
                    dispatcher.dispatch(commandData, "TCP:$senderAddress")
                }
                val response = actionResult.toProtocolString()
                updateNotification("Last: $response from $senderAddress")

                val writer = clientSocket.getOutputStream().bufferedWriter()
                writer.write(response + "\n")
                writer.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling TCP client connection: ${e.message}", e)
        } finally {
            try {
                clientSocket.close()
            } catch (_: Exception) {}
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

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        if (_isRunning.value) {
            _isRunning.value = false
        }
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        if (::serverThread.isInitialized) {
            serverThread.interrupt()
        }
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        mainHandler.removeCallbacksAndMessages(null)
        try {
            stopForeground(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }
        Log.d(TAG, "Service stopped")
        super.onDestroy()
    }

    private fun logListeningAddresses() {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress) {
                        val ip = addr.hostAddress
                        // Filter out generic IPv6 scope IDs if confusing, but usually good to show
                        if (addr is Inet6Address) {
                             AppLogger.log("Listening on [IPv6]: $ip port $LISTENING_PORT")
                        } else if (addr is Inet4Address) {
                             AppLogger.log("Listening on [IPv4]: $ip port $LISTENING_PORT")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve network interfaces", e)
            AppLogger.log("Error: Could not determine local IP addresses")
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