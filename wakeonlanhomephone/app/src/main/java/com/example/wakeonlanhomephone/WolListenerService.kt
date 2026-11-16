package com.example.wakeonlanhomephone

import android.app.*
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.*
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class WolListenerService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): WolListenerService = this@WolListenerService
    }
    private val binder = LocalBinder()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val dispatcher by lazy {
        PcActionDispatcher(
            defaultMacProvider = { MqttConfigManager(this).getConfig().targetMac },
            pcSecretProvider = { MqttConfigManager(this).getConfig().pcSecret }
        )
    }

    private val replayGuard = ReplayGuard()

    private val activeClientSockets: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

    private val workerPool = ThreadPoolExecutor(
        WORKER_THREADS,
        WORKER_THREADS,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(WORKER_QUEUE_CAPACITY),
        { runnable ->
            Thread(runnable, "WolTcpWorker").apply { isDaemon = true }
        },
        ThreadPoolExecutor.AbortPolicy()
    )

    private fun localized(resId: Int, vararg args: Any): String {
        return AppLocale.wrap(this).getString(resId, *args)
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
        private const val CLIENT_TIMEOUT_MS = 5000
        private const val WORKER_THREADS = 4
        private const val WORKER_QUEUE_CAPACITY = 16
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
            startForeground(NOTIFICATION_ID, createNotification(localized(R.string.service_wol_initializing)))

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
                    activeClientSockets.add(clientSocket)
                    try {
                        workerPool.execute {
                            handleClientConnection(clientSocket)
                        }
                    } catch (rejected: RejectedExecutionException) {
                        activeClientSockets.remove(clientSocket)
                        closeQuietly(clientSocket)
                        Log.w(TAG, "Rejected TCP connection: worker pool and queue are full")
                        AppLogger.log("TCP: connection rejected (server busy)")
                    }
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

    private sealed class LineRead {
        data class Line(val text: String) : LineRead()
        object Empty : LineRead()
        object TooLong : LineRead()
    }

    /**
     * Reads one line without buffering more than [maxBytes]. Returns [LineRead.TooLong] as soon
     * as the limit is crossed; the caller closes the socket, so an attacker cannot grow memory.
     */
    private fun readLimitedLine(input: InputStream, maxBytes: Int): LineRead {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value == -1) {
                return if (buffer.size() == 0) {
                    LineRead.Empty
                } else {
                    LineRead.Line(String(buffer.toByteArray(), StandardCharsets.UTF_8))
                }
            }
            if (value == '\n'.code || value == '\r'.code) {
                return if (buffer.size() == 0) {
                    LineRead.Empty
                } else {
                    LineRead.Line(String(buffer.toByteArray(), StandardCharsets.UTF_8))
                }
            }
            if (buffer.size() >= maxBytes) return LineRead.TooLong
            buffer.write(value)
        }
    }

    private fun handleClientConnection(clientSocket: Socket) {
        try {
            clientSocket.soTimeout = CLIENT_TIMEOUT_MS
            val senderAddress = try {
                clientSocket.inetAddress?.hostAddress
            } catch (_: Exception) {
                null
            } ?: localized(R.string.service_wol_unknown_source)

            val read = readLimitedLine(clientSocket.getInputStream(), CommandAuth.MAX_ENVELOPE_BYTES)
            val response = when (read) {
                is LineRead.TooLong -> {
                    AppLogger.log("TCP: auth rejected (envelope too long) from $senderAddress")
                    Log.w(TAG, "Auth rejected from $senderAddress: envelope too long")
                    CommandAuth.WIRE_ERROR_AUTH_REQUIRED
                }
                is LineRead.Empty -> {
                    AppLogger.log("TCP: auth rejected (missing envelope) from $senderAddress")
                    Log.w(TAG, "Auth rejected from $senderAddress: missing envelope")
                    CommandAuth.WIRE_ERROR_AUTH_REQUIRED
                }
                is LineRead.Line -> handleAuthenticatedLine(read.text, senderAddress)
            }

            updateNotification(localized(R.string.service_wol_last_command, response, senderAddress))

            val writer = clientSocket.getOutputStream().bufferedWriter()
            writer.write(response + "\n")
            writer.flush()
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "TCP client timed out after ${CLIENT_TIMEOUT_MS}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling TCP client connection: ${e.message}", e)
        } finally {
            activeClientSockets.remove(clientSocket)
            closeQuietly(clientSocket)
        }
    }

    private fun handleAuthenticatedLine(envelope: String, senderAddress: String): String {
        val secret = MqttConfigManager(this).getConfig().helperSecret
        val auth = CommandAuth.verify(envelope, secret, replayGuard = replayGuard)
        if (auth != AuthResult.Ok) {
            AppLogger.log("TCP: auth rejected (${CommandAuth.reasonFor(auth)}) from $senderAddress")
            Log.w(TAG, "Auth rejected from $senderAddress: ${CommandAuth.reasonFor(auth)}")
            return CommandAuth.errorFor(auth) ?: CommandAuth.WIRE_ERROR_AUTH_REQUIRED
        }

        // Envelope already verified; dispatch only the legacy command inside it.
        val command = CommandAuth.commandOf(envelope)
        if (command == null) {
            AppLogger.log("TCP: auth rejected (missing command) from $senderAddress")
            return CommandAuth.WIRE_ERROR_AUTH_REQUIRED
        }
        return runBlocking {
            dispatcher.dispatch(command, "TCP:$senderAddress").toProtocolString()
        }
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(localized(R.string.service_wol_notification_title))
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
            try {
                serverThread.join(500)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        // Stop workers, then close every socket that is still queued or in flight.
        workerPool.shutdownNow()
        activeClientSockets.forEach { closeQuietly(it) }
        activeClientSockets.clear()

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
                localized(R.string.service_wol_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = localized(R.string.service_wol_channel_description)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }
}