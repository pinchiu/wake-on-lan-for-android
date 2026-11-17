package com.example.wakeonlanhomephone

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.wakeonlanhomephone.ui.components.*
import com.example.wakeonlanhomephone.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private var wolService by mutableStateOf<WolListenerService?>(null)
    private var isBound by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as WolListenerService.LocalBinder
            wolService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            wolService = null
            isBound = false
        }
    }

    @Suppress("DEPRECATION")
    private fun applyStartupWindowBackground() {
        val dark = when (ThemePreference(this).mode) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        val background = if (dark) 0xFF121214.toInt() else 0xFFF2F2F7.toInt()
        window.setBackgroundDrawable(ColorDrawable(background))
        window.statusBarColor = background
        window.navigationBarColor = if (dark) 0xFF1C1C1E.toInt() else 0xFFE9E9EF.toInt()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !dark
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyStartupWindowBackground()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        setContent {
            val configManager = remember { MqttConfigManager(this) }
            val deviceManager = remember { DeviceManager(this) }

            // Load initial devices to state
            LaunchedEffect(Unit) {
                AppGlobalState.updateDevices(deviceManager.getDevices())
            }

            var isServiceRunning by remember { mutableStateOf(false) }
            LaunchedEffect(wolService) {
                wolService?.isRunning?.collect { running ->
                    isServiceRunning = running
                } ?: run {
                    isServiceRunning = false
                }
            }

            MainScreen(
                isServiceRunning = isServiceRunning,
                isBound = isBound,
                onStartService = {
                    Intent(this, WolListenerService::class.java).also { intent ->
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        if (!isBound) {
                            bindService(intent, connection, android.content.Context.BIND_AUTO_CREATE)
                        }
                    }
                },
                onStopService = {
                    Intent(this, WolListenerService::class.java).also { intent ->
                        if (isBound) {
                            unbindService(connection)
                            isBound = false
                            wolService = null
                        }
                        stopService(intent)
                    }
                },
                onStartMqtt = {
                    Intent(this, MqttWolService::class.java).also { intent ->
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }
                },
                onStopMqtt = {
                    Intent(this, MqttWolService::class.java).also { intent ->
                        stopService(intent)
                    }
                },
                onRestartMqttService = {
                    Intent(this, MqttWolService::class.java).also { intent ->
                        stopService(intent)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                startForegroundService(intent)
                            } else {
                                startService(intent)
                            }
                        }, 300)
                    }
                },
                configManager = configManager,
                deviceManager = deviceManager
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isBound) {
            Intent(this, WolListenerService::class.java).also { intent ->
                bindService(intent, connection, 0)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
            wolService = null
        }
    }
}



@Composable
fun MainScreen(
    isServiceRunning: Boolean,
    isBound: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onStartMqtt: () -> Unit,
    onStopMqtt: () -> Unit,
    onRestartMqttService: () -> Unit,
    configManager: MqttConfigManager,
    deviceManager: DeviceManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val deviceProfileManager = remember { DeviceProfileManager(context) }
    var currentTab by rememberSaveable { mutableStateOf(Tab.Server) }
    var showAddDevice by remember { mutableStateOf(false) } 
    var deviceToEdit by remember { mutableStateOf<MqttDevice?>(null) } 

    // Service & Data State

    val logs by AppLogger.logs.collectAsState()
    val devices by AppGlobalState.devices.collectAsState()
    val deviceStatuses by AppGlobalState.deviceStatuses.collectAsState()
    val brokerUrl by AppGlobalState.brokerUrl.collectAsState()
    val mqttState by AppGlobalState.connectionState.collectAsState()

    WakeOnLanTheme {
        FrostBackground(modifier = Modifier.fillMaxSize()) {
            if (showAddDevice) {
            AddDeviceScreen(
                configManager = configManager,
                deviceToEdit = deviceToEdit,
                onCancel = { 
                    showAddDevice = false
                    deviceToEdit = null
                },
                onSave = { newDevice ->
                    if (deviceToEdit != null) {
                        deviceManager.updateDevice(newDevice)
                    } else {
                        deviceManager.addDevice(newDevice)
                    }
                    onRestartMqttService()
                    showAddDevice = false
                    deviceToEdit = null
                }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main content area
                Crossfade(
                    targetState = currentTab,
                    animationSpec = tween(220),
                    label = "TabSwitch"
                ) { tab ->
                    when (tab) {
                        Tab.Server -> {
                            ServerScreen(
                                isRunning = isServiceRunning,
                                onStartService = {
                                    onStartService()
                                    onStartMqtt()
                                },
                                onStopService = {
                                    onStopService()
                                    onStopMqtt()
                                },
                                logs = logs,
                                onClearLogs = { AppLogger.clear() },
                                onSettings = { currentTab = Tab.Settings },
                                deviceProfileManager = deviceProfileManager,
                                onWakeComputer = { device ->
                                    coroutineScope.launch {
                                        val dispatcher = PcActionDispatcher(
                                            pcSecretProvider = { MqttConfigManager(context).getConfig().pcSecret }
                                        )
                                        val result = dispatcher.dispatch("WAKE:${device.mac}", "HomePhoneUI")
                                        if (result.isSuccess) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_wake_broadcast, device.name, device.mac),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(
                                                    R.string.toast_send_failed,
                                                    (result as PcActionResult.Failure).error
                                                ),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                onPowerCommand = { device, action ->
                                    coroutineScope.launch {
                                        val dispatcher = PcActionDispatcher(
                                            pcSecretProvider = {
                                                val currentConfig = MqttConfigManager(context).getConfig()
                                                device.pcSecret.ifBlank { currentConfig.pcSecret }
                                            }
                                        )
                                        val result = dispatcher.dispatch("${action.uppercase()}:${device.ip}", "HomePhoneUI")
                                        if (result.isSuccess) {
                                            Toast.makeText(
                                                context,
                                                context.getString(
                                                    R.string.toast_power_command_sent,
                                                    action,
                                                    device.name,
                                                    device.ip
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(
                                                    R.string.toast_send_failed,
                                                    (result as PcActionResult.Failure).error
                                                ),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            )
                        }
                        Tab.Connections -> {
                            ConnectionsScreen(
                                devices = devices,
                                deviceStatuses = deviceStatuses,
                                mqttState = mqttState,
                                onAddDevice = { showAddDevice = true },
                                onDeleteDevice = { 
                                    deviceManager.removeDevice(it)
                                    onRestartMqttService()
                                },
                                onEditDevice = { device ->
                                    deviceToEdit = device
                                    showAddDevice = true
                                },
                                onToggleDevice = { device ->
                                    val isOnline = deviceStatuses[device.id] ?: false
                                    val payload = if (isOnline) device.offlinePayload else device.onlinePayload
                                    
                                    Intent(context, MqttWolService::class.java).also { intent ->
                                        intent.action = MqttWolService.ACTION_PUBLISH
                                        intent.putExtra(MqttWolService.EXTRA_TOPIC, device.topic)
                                        intent.putExtra(MqttWolService.EXTRA_MESSAGE, payload)
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            context.startForegroundService(intent)
                                        } else {
                                            context.startService(intent)
                                        }
                                    }
                                    val action = if (isOnline) R.string.toast_device_disconnecting else R.string.toast_device_connecting
                                    Toast.makeText(context, context.getString(action), Toast.LENGTH_SHORT).show()
                                },
                                onSettings = { currentTab = Tab.Settings },
                                brokerUrl = brokerUrl
                            )
                        }
                        Tab.Settings -> {
                            NewSettingsScreen(
                                configManager = configManager,
                                mqttState = mqttState,
                                onSave = { onRestartMqttService() }
                            )
                        }
                    }
                }
                
                // Floating navigation bar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
                    GlassNavBar {
                        // Server tab
                        GlassNavItem(
                            selected = currentTab == Tab.Server,
                            onClick = { currentTab = Tab.Server },
                            icon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = stringResource(R.string.tab_server),
                            selectedColor = AccentEmerald
                        )
                        
                        // MQTT tab
                        GlassNavItem(
                            selected = currentTab == Tab.Connections,
                            onClick = { currentTab = Tab.Connections },
                            icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = "MQTT",
                            selectedColor = AccentEmerald
                        )
                        
                        // Settings tab
                        GlassNavItem(
                            selected = currentTab == Tab.Settings,
                            onClick = { currentTab = Tab.Settings },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            label = stringResource(R.string.tab_settings),
                            selectedColor = AccentEmerald
                        )
                    }
                }
            }
            }
        }
    }
}

enum class Tab {
    Server,
    Connections,
    Settings
}
        


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    configManager: MqttConfigManager,
    onSave: () -> Unit,
    onBack: () -> Unit
) {


    // Config State
    var brokerName by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var clientId by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var targetMac by remember { mutableStateOf("") }
    var useSsl by remember { mutableStateOf(true) }
    var protocol by remember { mutableStateOf("TCP") }
    var timeout by remember { mutableStateOf("30") }
    var keepAlive by remember { mutableStateOf("60") }
    var autoConnect by remember { mutableStateOf(true) }

    // Load config on init
    LaunchedEffect(Unit) {
        val config = configManager.getConfig()
        brokerName = config.brokerName
        host = config.host
        port = config.port.toString()
        clientId = config.clientId
        username = config.username
        password = config.password
        topic = config.topic
        targetMac = config.targetMac
        useSsl = config.useSsl
        protocol = config.protocol
        timeout = config.timeout.toString()
        keepAlive = config.keepAlive.toString()
        autoConnect = config.autoConnect
    }

    val backgroundColor = FrostBg
    val cardBorderColor = FrostBorderStandard
    val textColor = FrostTextPrimary
    val labelColor = FrostTextSecondary
    val accentColor = FrostAccent

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("MQTT Settings", color = textColor, style = FrostHeadline) },
                navigationIcon = {
                     IconButton(onClick = { onBack() }) { 
                         Text("<", color = textColor, style = MaterialTheme.typography.titleLarge)
                     }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(cardBorderColor) {
                    StyledTextField("Name", brokerName, { brokerName = it }, textColor, labelColor)
                    StyledTextField("Client ID", clientId, { clientId = it }, textColor, labelColor)
                }
            }

            item {
                SectionCard(cardBorderColor) {
                    StyledTextField("URL", host, { host = it }, textColor, labelColor)
                    StyledTextField("Port", port, { port = it }, textColor, labelColor)
                    
                    // Protocol & SSL
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Protocol", color = labelColor, style = FrostCaption)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = protocol == "TCP", onClick = { protocol = "TCP" }, colors = RadioButtonDefaults.colors(selectedColor = accentColor, unselectedColor = labelColor))
                                Text("TCP", color = textColor, modifier = Modifier.padding(end = 8.dp))
                                RadioButton(selected = protocol == "WebSocket", onClick = { protocol = "WebSocket" }, colors = RadioButtonDefaults.colors(selectedColor = accentColor, unselectedColor = labelColor))
                                Text("WS", color = textColor)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                             Text("SSL/TLS", color = labelColor, style = FrostCaption)
                             Switch(
                                 checked = useSsl,
                                 onCheckedChange = { useSsl = it },
                                 colors = SwitchDefaults.colors(
                                     checkedThumbColor = Color.White,
                                     checkedTrackColor = accentColor,
                                     uncheckedThumbColor = Color.White,
                                     uncheckedTrackColor = FrostSegmentTrack
                                 )
                             )
                        }
                    }

                     StyledTextField("Connection Timeout", timeout, { timeout = it }, textColor, labelColor)
                     StyledTextField("Keep Alive Interval", keepAlive, { keepAlive = it }, textColor, labelColor)
                }
            }

            item {
                SectionCard(cardBorderColor, title = "Authentication") {
                    StyledTextField("Username", username, { username = it }, textColor, labelColor)
                    StyledTextField("Password", password, { password = it }, textColor, labelColor, isPassword = true)
                }
            }

             item {
                 SectionCard(cardBorderColor) {
                     Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                     ) {
                        Text("Auto Connect", color = textColor, style = FrostBody)
                        Switch(
                            checked = autoConnect,
                            onCheckedChange = { autoConnect = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = FrostSegmentTrack
                            )
                        )
                    }
                     StyledTextField("Topic", topic, { topic = it }, textColor, labelColor)
                     StyledTextField("Target MAC", targetMac, { targetMac = it }, textColor, labelColor)
                 }
             }

            item {
                Button(
                    onClick = {
                        configManager.saveConfig(MqttConfig(
                            brokerName, host, port.toIntOrNull() ?: 8883, clientId, username, password, useSsl, topic, targetMac, protocol,
                            timeout.toIntOrNull() ?: 30, keepAlive.toIntOrNull() ?: 60, autoConnect
                        ))
                        onSave()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = CircleShape
                ) {
                    Text("Save & Connect", color = Color.White, style = FrostCallout, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SectionCard(borderColor: Color, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                color = FrostTextSecondary,
                style = FrostCaption,
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )
        }
        val shape = RoundedCornerShape(22.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, shape, ambientColor = FrostShadow, spotColor = FrostShadow)
                .border(1.dp, borderColor, shape)
                .clip(shape),
            color = FrostSurfaceGlass,
            shape = shape,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun StyledTextField(label: String, value: String, onValueChange: (String) -> Unit, textColor: Color, labelColor: Color, isPassword: Boolean = false) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, color = labelColor, style = FrostCaption)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.weight(1f)
            )
            if (isPassword) {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) stringResource(R.string.password_hide) else stringResource(R.string.password_show),
                        tint = labelColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = FrostBorderSubtle)
    }
}

/**
 * Refined Settings screen with precision dark hardware styling
 */
@Composable
fun NewSettingsScreen(
    configManager: MqttConfigManager,
    mqttState: MqttConnectionState,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val updateManager = remember { UpdateManager(context) }
    
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    
    var isChecking by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf<String?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    val themePreference = remember { ThemePreference(context) }
    val currentTheme = themePreference.mode

    val currentConfig = remember { configManager.getConfig() }
    var brokerHost by remember { mutableStateOf(currentConfig.host) }
    var brokerPort by remember { mutableStateOf(if (currentConfig.port > 0) currentConfig.port.toString() else if (currentConfig.useSsl) "8883" else "1883") }
    var brokerUser by remember { mutableStateOf(currentConfig.username) }
    var brokerPass by remember { mutableStateOf(currentConfig.password) }
    var brokerTopic by remember { mutableStateOf(currentConfig.topic) }
    var useSsl by remember { mutableStateOf(currentConfig.useSsl) }
    var autoConnect by remember { mutableStateOf(currentConfig.autoConnect) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var helperSecret by remember { mutableStateOf(currentConfig.helperSecret) }
    var pcSecret by remember { mutableStateOf(currentConfig.pcSecret) }
    var helperSecretVisible by remember { mutableStateOf(false) }
    var pcSecretVisible by remember { mutableStateOf(false) }
    val lastError by AppGlobalState.lastErrorMessage.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Large iOS-style title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = FrostLargeTitle,
                color = FrostTextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = FrostCaption,
                color = FrostTextSecondary
            )
        }

        // Grouped content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)
        ) {
            item {
                Column {
                    SectionHeader(stringResource(R.string.settings_mqtt_panel_title))
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(FrostAccentDim, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = FrostAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "MQTT",
                                        style = FrostCallout,
                                        color = FrostTextPrimary
                                    )
                                    Text(
                                        text = brokerHost.ifBlank { "—" },
                                        style = FrostCaption,
                                        color = FrostTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            MqttStatusPill(mqttState)
                        }

                        if (mqttState == MqttConnectionState.FAILED && !lastError.isNullOrEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = FrostDangerDim,
                                border = androidx.compose.foundation.BorderStroke(1.dp, FrostDangerBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_connection_error, lastError ?: ""),
                                    color = FrostTextPrimary,
                                    style = FrostCaption,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = FrostBorderSubtle)
                        Spacer(Modifier.height(14.dp))

                        SettingsField(
                            label = "Broker Host / URL",
                            value = brokerHost,
                            onValueChange = { brokerHost = it },
                            placeholder = "io.adafruit.com"
                        )

                        Spacer(Modifier.height(14.dp))

                        SettingsField(
                            label = "Port",
                            value = brokerPort,
                            onValueChange = { brokerPort = it },
                            placeholder = if (useSsl) "8883" else "1883"
                        )

                        Spacer(Modifier.height(10.dp))

                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_ssl_encryption),
                            checked = useSsl,
                            onCheckedChange = { checked ->
                                useSsl = checked
                                if (checked && brokerPort == "1883") brokerPort = "8883"
                                else if (!checked && brokerPort == "8883") brokerPort = "1883"
                            }
                        )

                        SettingsDivider()

                        SettingsField(
                            label = stringResource(R.string.settings_username_label),
                            value = brokerUser,
                            onValueChange = { brokerUser = it },
                            placeholder = "username"
                        )

                        SettingsDivider()

                        SettingsField(
                            label = "Password (Adafruit AIO Key)",
                            value = brokerPass,
                            onValueChange = { brokerPass = it },
                            placeholder = "aio_xxxxxxxxxxxxxxxxxxxxxxxx",
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) stringResource(R.string.password_hide) else stringResource(R.string.password_show),
                                        tint = if (isPasswordVisible) FrostAccent else FrostTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )

                        SettingsDivider()

                        SettingsField(
                            label = stringResource(R.string.settings_topic_label),
                            value = brokerTopic,
                            onValueChange = { brokerTopic = it },
                            placeholder = "username/feeds/feed-name"
                        )

                        SettingsDivider()

                        SettingsSwitchRow(
                            label = stringResource(R.string.settings_auto_connect),
                            checked = autoConnect,
                            onCheckedChange = { autoConnect = it }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val parsedPort = brokerPort.toIntOrNull() ?: if (useSsl) 8883 else 1883
                        // Keep scheme/port/path/IPv6; MqttEndpoint parses it.
                        val cleanHost = brokerHost.trim()

                        configManager.saveConfig(
                            currentConfig.copy(
                                host = cleanHost,
                                port = parsedPort,
                                username = brokerUser.trim(),
                                password = brokerPass.trim(),
                                useSsl = useSsl,
                                topic = brokerTopic.trim().ifEmpty { currentConfig.topic },
                                autoConnect = autoConnect,
                                helperSecret = helperSecret.trim(),
                                pcSecret = pcSecret.trim()
                            )
                        )
                        onSave()
                        Toast.makeText(context, context.getString(R.string.toast_mqtt_saved), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrostAccent,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Done, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.settings_save_reconnect),
                        color = Color.White,
                        style = FrostCallout,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Security / shared secrets
            item {
                Column {
                    SectionHeader(stringResource(R.string.settings_security_title))
                    Text(
                        text = stringResource(R.string.settings_security_description),
                        color = FrostTextSecondary,
                        style = FrostCaption,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                    )
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsField(
                                label = stringResource(R.string.settings_helper_secret_label),
                                value = helperSecret,
                                onValueChange = { helperSecret = it },
                                placeholder = stringResource(R.string.settings_helper_secret_label),
                                visualTransformation = if (helperSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { helperSecretVisible = !helperSecretVisible }) {
                                        Icon(
                                            imageVector = if (helperSecretVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = if (helperSecretVisible) stringResource(R.string.password_hide) else stringResource(R.string.password_show),
                                            tint = FrostTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    helperSecret = CommandAuth.generateSecret()
                                    helperSecretVisible = true
                                }) {
                                    Text(stringResource(R.string.security_generate), color = FrostAccent, style = FrostCaption)
                                }
                                TextButton(
                                    enabled = helperSecret.isNotBlank(),
                                    onClick = {
                                        clipboard?.setPrimaryClip(
                                            android.content.ClipData.newPlainText(
                                                context.getString(R.string.settings_helper_secret_label),
                                                helperSecret
                                            )
                                        )
                                        Toast.makeText(context, context.getString(R.string.toast_secret_copied), Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(stringResource(R.string.security_copy), color = FrostTextSecondary, style = FrostCaption)
                                }
                            }

                            SettingsField(
                                label = stringResource(R.string.settings_pc_secret_label),
                                value = pcSecret,
                                onValueChange = { pcSecret = it },
                                placeholder = stringResource(R.string.settings_pc_secret_label),
                                visualTransformation = if (pcSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { pcSecretVisible = !pcSecretVisible }) {
                                        Icon(
                                            imageVector = if (pcSecretVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = if (pcSecretVisible) stringResource(R.string.password_hide) else stringResource(R.string.password_show),
                                            tint = FrostTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    val clip = clipboard?.primaryClip
                                    val pasted = if (clip != null && clip.itemCount > 0) {
                                        clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
                                    } else {
                                        ""
                                    }
                                    if (pasted.isNotBlank()) pcSecret = pasted.trim()
                                }) {
                                    Text(stringResource(R.string.security_paste), color = FrostTextSecondary, style = FrostCaption)
                                }
                            }
                            if (pcSecret.isBlank()) {
                                Text(
                                    text = stringResource(R.string.security_pc_secret_missing_hint),
                                    color = FrostWarning,
                                    style = FrostCaption
                                )
                            }
                        }
                    }
                }
            }

            // About / updates
            item {
                Column {
                    SectionHeader(stringResource(R.string.about_title))
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.about_app_title),
                                style = FrostBody,
                                color = FrostTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            Surface(
                                shape = CircleShape,
                                color = FrostAccentDim
                            ) {
                                Text(
                                    text = "v$versionName",
                                    color = FrostTextPrimary,
                                    style = FrostMicro,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        updateMessage?.let { message ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = message,
                                color = if (updateAvailable != null) FrostTextPrimary else FrostTextSecondary,
                                style = FrostCaption
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        if (updateAvailable != null) {
                            Button(
                                onClick = {
                                    updateAvailable?.let { url ->
                                        updateManager.downloadAndInstall(url)
                                        Toast.makeText(context, context.getString(R.string.toast_downloading), Toast.LENGTH_SHORT).show()
                                        updateAvailable = null
                                        updateMessage = context.getString(R.string.update_downloading_message)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FrostAccent,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.update_install_button),
                                    color = Color.White,
                                    style = FrostCaption,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isChecking = true
                                    updateMessage = context.getString(R.string.update_checking)
                                    updateManager.checkForUpdate(versionName) { hasUpdate, downloadUrl, tag ->
                                        isChecking = false
                                        if (hasUpdate && downloadUrl != null) {
                                            updateAvailable = downloadUrl
                                            updateMessage = context.getString(R.string.update_available, tag ?: "")
                                        } else {
                                            updateMessage = context.getString(R.string.update_latest)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(1.dp, FrostBorderStandard),
                                enabled = !isChecking,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = FrostTextPrimary
                                )
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = FrostAccent,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.update_check_button),
                                        color = FrostTextPrimary,
                                        style = FrostCaption,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pinchiu/wake-on-lan-for-android/releases"))
                                context.startActivity(intent)
                            }
                        ) {

                            Text(
                                stringResource(R.string.about_view_releases),
                                color = FrostAccent,
                                style = FrostCaption
                            )
                        }
                    }
                }
            }

            // Appearance
            item {
                Column {
                    SectionHeader(stringResource(R.string.settings_appearance_title))
                    Text(
                        text = stringResource(R.string.settings_appearance_description),
                        color = FrostTextSecondary,
                        style = FrostCaption,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                    )
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        SettingsChoiceRow(
                            label = stringResource(R.string.theme_system),
                            selected = currentTheme == AppThemeMode.SYSTEM,
                            onClick = {
                                themePreference.mode = AppThemeMode.SYSTEM
                                (context as? Activity)?.recreate()
                            }
                        )
                        HorizontalDivider(color = FrostBorderSubtle)
                        SettingsChoiceRow(
                            label = stringResource(R.string.theme_light),
                            selected = currentTheme == AppThemeMode.LIGHT,
                            onClick = {
                                themePreference.mode = AppThemeMode.LIGHT
                                (context as? Activity)?.recreate()
                            }
                        )
                        HorizontalDivider(color = FrostBorderSubtle)
                        SettingsChoiceRow(
                            label = stringResource(R.string.theme_dark),
                            selected = currentTheme == AppThemeMode.DARK,
                            onClick = {
                                themePreference.mode = AppThemeMode.DARK
                                (context as? Activity)?.recreate()
                            }
                        )
                    }
                }
            }

            // Language
            item {
                Column {
                    SectionHeader(stringResource(R.string.settings_language_title))
                    Text(
                        text = stringResource(R.string.settings_language_description),
                        color = FrostTextSecondary,
                        style = FrostCaption,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                    )
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        val currentLanguage = AppLocale.currentTag(context)
                        SettingsChoiceRow(
                            label = stringResource(R.string.language_system),
                            selected = currentLanguage == AppLocale.TAG_SYSTEM,
                            onClick = { AppLocale.set(context, AppLocale.TAG_SYSTEM) }
                        )
                        HorizontalDivider(color = FrostBorderSubtle)
                        SettingsChoiceRow(
                            label = stringResource(R.string.language_chinese_traditional),
                            selected = currentLanguage == AppLocale.TAG_TRADITIONAL_CHINESE,
                            onClick = { AppLocale.set(context, AppLocale.TAG_TRADITIONAL_CHINESE) }
                        )
                        HorizontalDivider(color = FrostBorderSubtle)
                        SettingsChoiceRow(
                            label = stringResource(R.string.language_english),
                            selected = currentLanguage == AppLocale.TAG_ENGLISH,
                            onClick = { AppLocale.set(context, AppLocale.TAG_ENGLISH) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = FrostCaption,
        color = FrostTextSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsDivider() {
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = FrostBorderSubtle)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = FrostTextSecondary, style = FrostCaption)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = FrostTextTertiary, style = FrostCaption) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = FrostTextPrimary,
                unfocusedTextColor = FrostTextPrimary,
                focusedBorderColor = FrostAccentBorder,
                unfocusedBorderColor = FrostBorderSubtle,
                focusedContainerColor = FrostSurfaceElevated,
                unfocusedContainerColor = FrostSurfaceElevated,
                cursorColor = FrostAccent,
                focusedPlaceholderColor = FrostTextTertiary,
                unfocusedPlaceholderColor = FrostTextTertiary
            ),
            textStyle = FrostBody.copy(fontSize = 15.sp),
            shape = RoundedCornerShape(14.dp),
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = FrostTextPrimary,
            style = FrostBody.copy(fontSize = 15.sp),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FrostAccent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = FrostSegmentTrack
            )
        )
    }
}

@Composable
private fun MqttStatusPill(state: MqttConnectionState) {
    val accent = when (state) {
        MqttConnectionState.CONNECTED -> FrostSuccess
        MqttConnectionState.CONNECTING -> FrostWarning
        MqttConnectionState.FAILED -> FrostDanger
        MqttConnectionState.DISCONNECTED -> FrostTextTertiary
    }
    val background = when (state) {
        MqttConnectionState.CONNECTED -> FrostSuccessDim
        MqttConnectionState.CONNECTING -> FrostWarningDim
        MqttConnectionState.FAILED -> FrostDangerDim
        MqttConnectionState.DISCONNECTED -> Color.White.copy(alpha = 0.46f)
    }
    val label = when (state) {
        MqttConnectionState.CONNECTED -> R.string.mqtt_status_connected
        MqttConnectionState.CONNECTING -> R.string.mqtt_status_connecting
        MqttConnectionState.FAILED -> R.string.mqtt_status_failed
        MqttConnectionState.DISCONNECTED -> R.string.mqtt_status_disconnected
    }

    Surface(
        shape = CircleShape,
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state == MqttConnectionState.CONNECTED || state == MqttConnectionState.CONNECTING) {
                AnimatedPingDot(color = accent, size = 6.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accent, CircleShape)
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(label),
                color = if (state == MqttConnectionState.DISCONNECTED) FrostTextSecondary else FrostTextPrimary,
                style = FrostMicro
            )
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { if (!selected) onClick() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = FrostTextPrimary,
            style = FrostBody.copy(fontSize = 15.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = null,
                tint = FrostAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
