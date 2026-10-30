package com.example.wakeonlanhomephone

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.example.wakeonlanhomephone.ui.theme.*
import com.example.wakeonlanhomephone.ui.components.*
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackroundDark)
            ) {
                // Main content area
                Crossfade(targetState = currentTab, label = "TabSwitch") { tab ->
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
                                        val dispatcher = PcActionDispatcher()
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
                                        val dispatcher = PcActionDispatcher()
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

    val backgroundColor = Color(0xFF11161C)
    val cardBorderColor = Color(0xFF404855)
    val textColor = Color.White
    val labelColor = Color(0xFFAAB2BB)
    val accentColor = Color(0xFF6ea2f5)

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("MQTT Settings", color = textColor) },
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
                            Text("Protocol", color = labelColor, style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = protocol == "TCP", onClick = { protocol = "TCP" }, colors = RadioButtonDefaults.colors(selectedColor = accentColor, unselectedColor = labelColor))
                                Text("TCP", color = textColor, modifier = Modifier.padding(end = 8.dp))
                                RadioButton(selected = protocol == "WebSocket", onClick = { protocol = "WebSocket" }, colors = RadioButtonDefaults.colors(selectedColor = accentColor, unselectedColor = labelColor))
                                Text("WS", color = textColor)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                             Text("SSL/TLS", color = labelColor, style = MaterialTheme.typography.labelSmall)
                             Switch(checked = useSsl, onCheckedChange = { useSsl = it }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor))
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
                        Text("Auto Connect", color = textColor)
                        Switch(checked = autoConnect, onCheckedChange = { autoConnect = it }, colors = SwitchDefaults.colors(checkedThumbColor = accentColor))
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
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text("Save & Connect", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SectionCard(borderColor: Color, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title, color = Color.White, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
        ) {
            content()
        }
    }
}

@Composable
fun StyledTextField(label: String, value: String, onValueChange: (String) -> Unit, textColor: Color, labelColor: Color, isPassword: Boolean = false) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, color = labelColor, style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
        HorizontalDivider(color = Color(0xFF2C333A))
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

    val currentConfig = remember { configManager.getConfig() }
    var brokerHost by remember { mutableStateOf(currentConfig.host) }
    var brokerPort by remember { mutableStateOf(if (currentConfig.port > 0) currentConfig.port.toString() else if (currentConfig.useSsl) "8883" else "1883") }
    var brokerUser by remember { mutableStateOf(currentConfig.username) }
    var brokerPass by remember { mutableStateOf(currentConfig.password) }
    var brokerTopic by remember { mutableStateOf(currentConfig.topic) }
    var useSsl by remember { mutableStateOf(currentConfig.useSsl) }
    var autoConnect by remember { mutableStateOf(currentConfig.autoConnect) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val lastError by AppGlobalState.lastErrorMessage.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBgPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 12.dp, start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_title),
                        color = Slate50,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        color = Slate400,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
            ) {
                // MQTT Broker Configuration Section
                item {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_mqtt_panel_title),
                                color = Slate50,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when (mqttState) {
                                    MqttConnectionState.CONNECTED -> AccentEmeraldDim
                                    MqttConnectionState.CONNECTING -> SemanticWarningDim
                                    MqttConnectionState.FAILED -> SemanticDangerDim
                                    MqttConnectionState.DISCONNECTED -> DarkSurfaceElevated
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when (mqttState) {
                                        MqttConnectionState.CONNECTED -> AccentEmeraldBorder
                                        MqttConnectionState.CONNECTING -> SemanticWarning.copy(alpha = 0.4f)
                                        MqttConnectionState.FAILED -> SemanticDangerBorder
                                        MqttConnectionState.DISCONNECTED -> BorderSubtle
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                when (mqttState) {
                                                    MqttConnectionState.CONNECTED -> AccentEmerald
                                                    MqttConnectionState.CONNECTING -> SemanticWarning
                                                    MqttConnectionState.FAILED -> SemanticDanger
                                                    MqttConnectionState.DISCONNECTED -> Slate500
                                                },
                                                CircleShape
                                            )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = when (mqttState) {
                                            MqttConnectionState.CONNECTED -> stringResource(R.string.mqtt_status_connected)
                                            MqttConnectionState.CONNECTING -> stringResource(R.string.mqtt_status_connecting)
                                            MqttConnectionState.FAILED -> stringResource(R.string.mqtt_status_failed)
                                            MqttConnectionState.DISCONNECTED -> stringResource(R.string.mqtt_status_disconnected)
                                        },
                                        color = when (mqttState) {
                                            MqttConnectionState.CONNECTED -> AccentEmerald
                                            MqttConnectionState.CONNECTING -> SemanticWarning
                                            MqttConnectionState.FAILED -> SemanticDanger
                                            MqttConnectionState.DISCONNECTED -> Slate400
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (mqttState == MqttConnectionState.FAILED && !lastError.isNullOrEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SemanticDangerDim,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SemanticDangerBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_connection_error, lastError ?: ""),
                                    color = SemanticDanger,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Host & Port
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Broker Host / URL", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            OutlinedTextField(
                                value = brokerHost,
                                onValueChange = { brokerHost = it },
                                placeholder = { Text("io.adafruit.com", color = Slate500, fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Slate50,
                                    unfocusedTextColor = Slate50,
                                    focusedBorderColor = AccentEmerald,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedContainerColor = DarkSurfaceElevated,
                                    unfocusedContainerColor = DarkSurfaceElevated
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Port", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                OutlinedTextField(
                                    value = brokerPort,
                                    onValueChange = { brokerPort = it },
                                    placeholder = { Text(if (useSsl) "8883" else "1883", color = Slate500, fontSize = 13.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Slate50,
                                        unfocusedTextColor = Slate50,
                                        focusedBorderColor = AccentEmerald,
                                        unfocusedBorderColor = BorderSubtle,
                                        focusedContainerColor = DarkSurfaceElevated,
                                        unfocusedContainerColor = DarkSurfaceElevated
                                    ),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_ssl_encryption), color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Switch(
                                        checked = useSsl,
                                        onCheckedChange = { checked ->
                                            useSsl = checked
                                            if (checked && brokerPort == "1883") brokerPort = "8883"
                                            else if (!checked && brokerPort == "8883") brokerPort = "1883"
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = AccentEmerald,
                                            checkedTrackColor = AccentEmeraldDim
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (useSsl) "SSL" else "TCP", color = if (useSsl) AccentEmerald else Slate400, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Username
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.settings_username_label), color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            OutlinedTextField(
                                value = brokerUser,
                                onValueChange = { brokerUser = it },
                                placeholder = { Text("username", color = Slate500, fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Slate50,
                                    unfocusedTextColor = Slate50,
                                    focusedBorderColor = AccentEmerald,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedContainerColor = DarkSurfaceElevated,
                                    unfocusedContainerColor = DarkSurfaceElevated
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Password / AIO Key
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Password (Adafruit AIO Key)", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            OutlinedTextField(
                                value = brokerPass,
                                onValueChange = { brokerPass = it },
                                placeholder = { Text("aio_xxxxxxxxxxxxxxxxxxxxxxxx", color = Slate500, fontSize = 13.sp) },
                                singleLine = true,
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = if (isPasswordVisible) stringResource(R.string.password_hide) else stringResource(R.string.password_show),
                                            tint = if (isPasswordVisible) AccentEmerald else Slate400,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Slate50,
                                    unfocusedTextColor = Slate50,
                                    focusedBorderColor = AccentEmerald,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedContainerColor = DarkSurfaceElevated,
                                    unfocusedContainerColor = DarkSurfaceElevated
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Topic
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.settings_topic_label), color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            OutlinedTextField(
                                value = brokerTopic,
                                onValueChange = { brokerTopic = it },
                                placeholder = { Text("username/feeds/feed-name", color = Slate500, fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Slate50,
                                    unfocusedTextColor = Slate50,
                                    focusedBorderColor = AccentEmerald,
                                    unfocusedBorderColor = BorderSubtle,
                                    focusedContainerColor = DarkSurfaceElevated,
                                    unfocusedContainerColor = DarkSurfaceElevated
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Auto Connect
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.settings_auto_connect), color = Slate300, fontSize = 13.sp)
                            Switch(
                                checked = autoConnect,
                                onCheckedChange = { autoConnect = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AccentEmerald,
                                    checkedTrackColor = AccentEmeraldDim
                                )
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Save Button
                        Button(
                            onClick = {
                                val parsedPort = brokerPort.toIntOrNull() ?: if (useSsl) 8883 else 1883
                                val cleanHost = brokerHost
                                    .replace(Regex("^[a-zA-Z]+://"), "")
                                    .substringBefore(":")
                                    .substringBefore("/")
                                    .trim()

                                configManager.saveConfig(
                                    currentConfig.copy(
                                        host = cleanHost,
                                        port = parsedPort,
                                        username = brokerUser.trim(),
                                        password = brokerPass.trim(),
                                        useSsl = useSsl,
                                        topic = brokerTopic.trim().ifEmpty { currentConfig.topic },
                                        autoConnect = autoConnect
                                    )
                                )
                                onSave()
                                Toast.makeText(context, context.getString(R.string.toast_mqtt_saved), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                        ) {
                            Icon(Icons.Default.Done, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_save_reconnect), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                // App Update Section
                item {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.about_title),
                                    color = Slate50,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = DarkSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Text(
                                        text = "v$versionName",
                                        color = AccentEmerald,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            Text(
                                text = stringResource(R.string.about_app_title),
                                color = Slate400,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            
                            updateMessage?.let { message ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    message,
                                    color = if (updateAvailable != null) AccentEmerald else Slate300,
                                    fontSize = 12.sp
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
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.update_install_button), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            } else {
                                Button(
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
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                    enabled = !isChecking
                                ) {
                                    if (isChecking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = AccentEmerald,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(stringResource(R.string.update_check_button), color = Slate200, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(10.dp))
                            
                            TextButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pinchiu/wake-on-lan-for-android/releases"))
                                    context.startActivity(intent)
                                }
                            ) {
                                Text(stringResource(R.string.about_view_releases), color = Slate400, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Language Selection Section
                item {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.settings_language_title),
                                color = Slate50,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.settings_language_description),
                                color = Slate400,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(12.dp))

                            val currentLanguage = AppLocale.currentTag(context)
                            LanguageOption(
                                label = stringResource(R.string.language_system),
                                selected = currentLanguage == AppLocale.TAG_SYSTEM,
                                onClick = { AppLocale.set(context, AppLocale.TAG_SYSTEM) }
                            )
                            Spacer(Modifier.height(8.dp))
                            LanguageOption(
                                label = stringResource(R.string.language_chinese_traditional),
                                selected = currentLanguage == AppLocale.TAG_TRADITIONAL_CHINESE,
                                onClick = { AppLocale.set(context, AppLocale.TAG_TRADITIONAL_CHINESE) }
                            )
                            Spacer(Modifier.height(8.dp))
                            LanguageOption(
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
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) AccentEmeraldBorder else BorderSubtle, RoundedCornerShape(10.dp))
            .clickable { if (!selected) onClick() },
        color = if (selected) AccentEmeraldDim else DarkSurfaceElevated,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (selected) AccentEmerald else Slate200,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    tint = AccentEmerald,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
