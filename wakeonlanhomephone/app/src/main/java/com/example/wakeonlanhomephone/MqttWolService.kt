package com.example.wakeonlanhomephone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3ConnAckException
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * MQTT transport adapter: keeps one broker connection alive and forwards command payloads
 * to [PcActionDispatcher].
 *
 * Threading: all connection state is owned by the main thread. HiveMQ listeners hop onto it via
 * [mainHandler]; each client carries a generation number so events from a replaced client are
 * ignored. Commands run on [serviceScope] (IO) so the MQTT event loop is never blocked.
 */
class MqttWolService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mqttClient: Mqtt3AsyncClient? = null
    private var activeConfig: MqttConfig? = null
    private var activeDevices: List<MqttDevice> = emptyList()
    @Volatile private var generation = 0
    private var isConnecting = false
    private var wasConnected = false
    private var reconnectAttempt = 0
    private var reconnectPending = false
    private var destroyed = false
    private val pendingPublishes = ArrayDeque<Pair<String, String>>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var connectWakeLock: PowerManager.WakeLock? = null

    // Read from the HiveMQ callback thread.
    @Volatile private var commandTopic: String = ""
    @Volatile private var deviceSubscriptions: List<MqttDevice> = emptyList()

    private val reconnectRunnable = Runnable {
        reconnectPending = false
        activeConfig?.let { connect(it, activeDevices) }
    }

    private val dispatcher by lazy {
        PcActionDispatcher(defaultMacProvider = { MqttConfigManager(this).getConfig().targetMac })
    }

    private fun localized(resId: Int, vararg args: Any): String {
        return AppLocale.wrap(this).getString(resId, *args)
    }

    companion object {
        const val TAG = "MqttWolService"
        const val NOTIFICATION_CHANNEL_ID = "MqttWolServiceChannel"
        const val NOTIFICATION_ID = 2
        const val ACTION_PUBLISH = "com.example.wakeonlanhomephone.action.PUBLISH"
        const val EXTRA_TOPIC = "com.example.wakeonlanhomephone.extra.TOPIC"
        const val EXTRA_MESSAGE = "com.example.wakeonlanhomephone.extra.MESSAGE"

        private const val CONNECT_WAKE_LOCK_MS = 30_000L
        private const val COMMAND_WAKE_LOCK_MS = 10_000L
        private const val MAX_PENDING_PUBLISHES = 20
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(localized(R.string.service_mqtt_initializing)))
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PUBLISH) {
            val topic = intent.getStringExtra(EXTRA_TOPIC).orEmpty()
            val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
            ensureConnected()
            if (topic.isNotEmpty()) publishMessage(topic, message)
            return START_STICKY
        }

        Log.d(TAG, "Service Started")
        AppLogger.log("MQTT Service Started")
        ensureConnected()
        return START_STICKY
    }

    /** Connects unless an identical, healthy (connected or connecting) client already exists. */
    private fun ensureConnected() {
        val config = MqttConfigManager(this).getConfig()
        val devices = DeviceManager(this).getDevices()
        AppGlobalState.updateDevices(devices)

        val client = mqttClient
        val sameSetup = config == activeConfig && devices == activeDevices
        if (client != null && sameSetup && (client.state.isConnected || isConnecting)) {
            if (client.state.isConnected) AppGlobalState.updateState(MqttConnectionState.CONNECTED)
            Log.d(TAG, "Already connected/connecting with the current configuration")
            return
        }
        reconnectAttempt = 0
        connect(config, devices)
    }

    private fun connect(config: MqttConfig, devices: List<MqttDevice>) {
        cancelScheduledReconnect()
        teardownClient()
        activeConfig = config
        activeDevices = devices
        wasConnected = false

        val endpoint = MqttEndpoint.parse(config.host, config.port, config.useSsl, config.protocol)
        if (endpoint == null) {
            Log.e(TAG, "Host is empty, skipping connection")
            reportFailure(localized(R.string.mqtt_error_host_missing))
            return
        }
        val topic = config.topic.trim()
        if (!MqttTopics.isValidFilter(topic)) {
            reportFailure(localized(R.string.mqtt_error_invalid_topic, topic))
            return
        }
        commandTopic = topic
        deviceSubscriptions = devices.filter { device ->
            MqttTopics.isValidFilter(device.topic).also { valid ->
                if (!valid) AppLogger.log("MQTT: Skipping device '${device.name}' (invalid topic '${device.topic}')")
            }
        }

        val clientGeneration = ++generation
        isConnecting = true
        AppLogger.log("MQTT: Connecting to ${endpoint.displayUrl}")
        AppGlobalState.updateState(MqttConnectionState.CONNECTING, url = endpoint.displayUrl)
        acquireConnectWakeLock()

        val timeoutSeconds = config.timeout.coerceIn(5, 120).toLong()
        val clientHolder = arrayOfNulls<Mqtt3AsyncClient>(1)
        val transport = MqttClient.builder()
            .useMqttVersion3()
            .identifier(config.clientId.trim().ifEmpty { MqttReconnectPolicy.generateClientId() })
            .transportConfig()
            .serverHost(endpoint.host)
            .serverPort(endpoint.port)
            .socketConnectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .mqttConnectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        if (endpoint.useSsl) transport.sslWithDefaultConfig()
        if (endpoint.useWebSocket) {
            transport.webSocketConfig().serverPath(endpoint.webSocketPath).applyWebSocketConfig()
        }
        val client = transport.applyTransportConfig()
            .addConnectedListener {
                mainHandler.post {
                    if (clientGeneration == generation) {
                        onConnected(endpoint)
                    } else {
                        // A replaced client finished connecting late: close it so it cannot
                        // fight the current one for the same client identifier.
                        clientHolder[0]?.disconnect()
                    }
                }
            }
            .addDisconnectedListener { context ->
                val cause = context.cause
                mainHandler.post {
                    if (clientGeneration == generation) onDisconnected(cause)
                }
            }
            .buildAsync()
        clientHolder[0] = client
        mqttClient = client

        // Registered before connecting so retained/early messages are never missed. Global
        // routing (instead of per-subscribe callbacks) keeps working across re-subscriptions.
        // The generation check drops in-flight messages from a client we already replaced.
        client.publishes(MqttGlobalPublishFilter.ALL) { publish ->
            if (clientGeneration == generation) handleIncoming(publish)
        }

        val connect = client.connectWith()
            .cleanSession(true)
            .keepAlive(config.keepAlive.coerceIn(10, 3600))
        if (config.username.isNotEmpty()) {
            val auth = connect.simpleAuth().username(config.username)
            if (config.password.isNotEmpty()) {
                auth.password(config.password.toByteArray(StandardCharsets.UTF_8))
            }
            auth.applySimpleAuth()
        }
        // Failures are reported once, by the disconnected listener.
        connect.send().whenComplete { _, _ -> mainHandler.post { releaseConnectWakeLock() } }
    }

    private fun onConnected(endpoint: MqttEndpoint) {
        isConnecting = false
        wasConnected = true
        reconnectAttempt = 0
        releaseConnectWakeLock()
        Log.d(TAG, "Connected to ${endpoint.displayUrl}")
        AppLogger.log("MQTT: Connected to broker (${endpoint.displayUrl})")
        AppLogger.log("MQTT: Listening on topic '$commandTopic'")
        AppGlobalState.updateState(MqttConnectionState.CONNECTED, url = endpoint.displayUrl)
        updateNotification(localized(R.string.service_mqtt_connected_listening, commandTopic))

        // Clean session: the broker forgets subscriptions on every disconnect, so subscribe on
        // every (re)connect.
        subscribe(commandTopic, isCommandTopic = true)
        deviceSubscriptions.map { it.topic }.distinct()
            .filter { it != commandTopic }
            .forEach { subscribe(it, isCommandTopic = false) }

        flushPendingPublishes()
    }

    private fun onDisconnected(cause: Throwable?) {
        val connectAttemptFailed = isConnecting && !wasConnected
        isConnecting = false
        releaseConnectWakeLock()
        val message = cause?.let(::formatErrorMessage) ?: "Connection closed"
        Log.w(TAG, "MQTT Disconnected: $message", cause)
        AppLogger.log(if (connectAttemptFailed) "MQTT: Connection Failed ($message)" else "MQTT: Disconnected ($message)")

        val config = activeConfig
        if (destroyed || config == null || !config.autoConnect || isFatal(cause)) {
            reportFailure(message, connectAttemptFailed)
            return
        }

        val delaySeconds = MqttReconnectPolicy.delaySeconds(reconnectAttempt++)
        val status = localized(R.string.service_mqtt_reconnecting, message, delaySeconds.toInt())
        AppLogger.log("MQTT: Reconnecting in ${delaySeconds}s")
        AppGlobalState.updateState(
            if (connectAttemptFailed) MqttConnectionState.FAILED else MqttConnectionState.DISCONNECTED,
            error = status
        )
        updateNotification(status)
        reconnectPending = true
        mainHandler.postDelayed(reconnectRunnable, TimeUnit.SECONDS.toMillis(delaySeconds))
    }

    /** Credential / identifier rejections will not fix themselves by retrying. */
    private fun isFatal(cause: Throwable?): Boolean {
        val connAck = cause as? Mqtt3ConnAckException ?: return false
        return connAck.mqttMessage.returnCode in setOf(
            Mqtt3ConnAckReturnCode.BAD_USER_NAME_OR_PASSWORD,
            Mqtt3ConnAckReturnCode.NOT_AUTHORIZED,
            Mqtt3ConnAckReturnCode.IDENTIFIER_REJECTED,
            Mqtt3ConnAckReturnCode.UNSUPPORTED_PROTOCOL_VERSION
        )
    }

    private fun reportFailure(message: String, failed: Boolean = true) {
        AppGlobalState.updateState(
            if (failed) MqttConnectionState.FAILED else MqttConnectionState.DISCONNECTED,
            error = message
        )
        updateNotification(localized(R.string.service_mqtt_connection_failed, message))
    }

    private fun subscribe(topic: String, isCommandTopic: Boolean) {
        val client = mqttClient ?: return
        val clientGeneration = generation
        client.subscribeWith()
            .topicFilter(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .send()
            .whenComplete { subAck, throwable ->
                mainHandler.post {
                    if (clientGeneration != generation) return@post
                    when {
                        throwable != null -> {
                            val errorMsg = formatErrorMessage(throwable)
                            Log.e(TAG, "Subscription to $topic failed: $errorMsg", throwable)
                            AppLogger.log("MQTT: Subscription to '$topic' failed ($errorMsg)")
                            if (isCommandTopic) {
                                AppGlobalState.updateState(
                                    MqttConnectionState.FAILED,
                                    error = localized(R.string.mqtt_subscribe_failed, errorMsg)
                                )
                            }
                        }
                        subAck != null && subAck.returnCodes.any { it.isError } -> {
                            val reason = localized(R.string.mqtt_subscribe_rejected, topic)
                            Log.e(TAG, reason)
                            AppLogger.log("MQTT: $reason")
                            if (isCommandTopic) {
                                AppGlobalState.updateState(MqttConnectionState.FAILED, error = reason)
                                updateNotification(reason)
                            }
                        }
                        else -> Log.d(TAG, "Subscribed to $topic")
                    }
                }
            }
    }

    /** Runs on the HiveMQ callback thread; must not block. */
    private fun handleIncoming(publish: Mqtt3Publish) {
        val topic = publish.topic.toString()
        val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8).trim()

        // Device status filters win over the command filter. A wildcard command topic can
        // overlap a status topic (e.g. home/# and home/pc/status); treating "online"/"ON"
        // as a command would wake the PC every time it reports its state.
        val statusDevices = deviceSubscriptions.filter { MqttTopics.matches(it.topic, topic) }
        if (statusDevices.isNotEmpty()) {
            statusDevices.forEach { device ->
                Log.d(TAG, "Device ${device.name} status: $payload")
                when (payload) {
                    device.onlinePayload -> AppGlobalState.updateDeviceStatus(device.id, true)
                    device.offlinePayload -> AppGlobalState.updateDeviceStatus(device.id, false)
                }
            }
            return
        }

        if (MqttTopics.matches(commandTopic, topic)) {
            handleCommand(topic, payload, publish.isRetain)
        }
    }

    private fun handleCommand(topic: String, payload: String, retained: Boolean) {
        if (retained) {
            // A retained command is replayed on every (re)connect; executing it would e.g.
            // shut the PC down again each time the phone reconnects.
            AppLogger.log("MQTT: Ignored retained message on '$topic' (publish commands with retain=false)")
            return
        }
        if (payload.isEmpty()) {
            AppLogger.log("MQTT: Ignored empty message on '$topic'")
            return
        }
        Log.d(TAG, "Received command on $topic: $payload")
        serviceScope.launch {
            val wakeLock = newWakeLock("MqttWolService::Command")
            wakeLock?.acquire(COMMAND_WAKE_LOCK_MS)
            try {
                val result = dispatcher.dispatch(payload, "MQTT")
                val text = when (result) {
                    is PcActionResult.Success -> result.message
                    is PcActionResult.Failure -> result.error
                }
                mainHandler.post { if (!destroyed) updateNotification(text) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle MQTT command", e)
                AppLogger.log("MQTT: Command '$payload' failed (${e.message})")
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
        }
    }

    private fun publishMessage(topic: String, message: String) {
        val client = mqttClient
        if (client == null || !client.state.isConnected) {
            if (pendingPublishes.size >= MAX_PENDING_PUBLISHES) pendingPublishes.removeFirst()
            pendingPublishes.addLast(topic to message)
            AppLogger.log("MQTT not connected; queued message for $topic")
            return
        }
        client.publishWith()
            .topic(topic)
            .qos(MqttQos.AT_LEAST_ONCE)
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

    private fun flushPendingPublishes() {
        val queued = pendingPublishes.toList()
        pendingPublishes.clear()
        // publishMessage re-queues if the connection dropped meanwhile; no loop.
        queued.forEach { (topic, message) -> publishMessage(topic, message) }
    }

    private fun cancelScheduledReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectPending = false
    }

    private fun teardownClient() {
        val client = mqttClient ?: return
        mqttClient = null
        generation++ // Ignore all further events from the old client.
        isConnecting = false
        try {
            client.disconnect().whenComplete { _, _ -> }
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting MQTT", e)
        }
    }

    /** Retry immediately when connectivity returns instead of waiting out the back-off. */
    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post {
                    if (!destroyed && reconnectPending) {
                        AppLogger.log("MQTT: Network available, reconnecting now")
                        reconnectAttempt = 0
                        cancelScheduledReconnect()
                        activeConfig?.let { connect(it, activeDevices) }
                    }
                }
            }
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "Cannot register network callback", e)
        }
    }

    private fun newWakeLock(tag: String): PowerManager.WakeLock? = try {
        (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
            .apply { setReferenceCounted(false) }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create WakeLock", e)
        null
    }

    private fun acquireConnectWakeLock() {
        val wakeLock = connectWakeLock ?: newWakeLock("MqttWolService::Connect").also { connectWakeLock = it }
        try {
            wakeLock?.acquire(CONNECT_WAKE_LOCK_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    private fun releaseConnectWakeLock() {
        try {
            if (connectWakeLock?.isHeld == true) connectWakeLock?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }

    private fun formatErrorMessage(throwable: Throwable): String {
        val raw = throwable.message ?: ""
        val causeMsg = throwable.cause?.message ?: ""
        val full = "$raw $causeMsg ${throwable.javaClass.simpleName} ${throwable.cause?.javaClass?.simpleName.orEmpty()}".trim()
        return when {
            full.contains("BAD_USER_NAME_OR_PASSWORD", ignoreCase = true) ->
                localized(R.string.mqtt_error_auth)
            full.contains("NOT_AUTHORIZED", ignoreCase = true) ->
                localized(R.string.mqtt_error_not_authorized)
            full.contains("IDENTIFIER_REJECTED", ignoreCase = true) ->
                localized(R.string.mqtt_error_identifier_rejected)
            full.contains("SSL", ignoreCase = true) || full.contains("handshake", ignoreCase = true) || full.contains("Certificate", ignoreCase = true) ->
                localized(R.string.mqtt_error_ssl)
            full.contains("UnknownHost", ignoreCase = true) ->
                localized(R.string.mqtt_error_unknown_host)
            full.contains("Connection refused", ignoreCase = true) || full.contains("ConnectException", ignoreCase = true) ->
                localized(R.string.mqtt_error_refused)
            full.contains("timeout", ignoreCase = true) || full.contains("timed out", ignoreCase = true) ->
                localized(R.string.mqtt_error_timeout)
            raw.isNotEmpty() -> raw
            causeMsg.isNotEmpty() -> causeMsg
            else -> localized(R.string.mqtt_error_unknown)
        }
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(localized(R.string.service_mqtt_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
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
                localized(R.string.service_mqtt_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        destroyed = true
        cancelScheduledReconnect()
        networkCallback?.let { callback ->
            try {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot unregister network callback", e)
            }
        }
        networkCallback = null
        teardownClient()
        serviceScope.cancel()
        releaseConnectWakeLock()
        AppGlobalState.updateState(MqttConnectionState.DISCONNECTED)
        try {
            stopForeground(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }
        super.onDestroy()
    }
}
