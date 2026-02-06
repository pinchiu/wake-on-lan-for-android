package com.example.wakeonlanhomephone

import android.content.ComponentName
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
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

            MainScreen(
                wolService = wolService,
                isBound = isBound,
                onStartService = {
                    Intent(this, WolListenerService::class.java).also { intent ->
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }
                },
                onStopService = {
                    Intent(this, WolListenerService::class.java).also { intent ->
                        stopService(intent)
                        if (isBound) {
                            unbindService(connection)
                            isBound = false
                            wolService = null
                        }
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
                        // Small delay or just start immediately? Android services handle partial restarts fine usually.
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }
                },
                configManager = configManager,
                deviceManager = deviceManager
            )
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, WolListenerService::class.java).also { intent ->
            bindService(intent, connection, android.content.Context.BIND_AUTO_CREATE)
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
    wolService: WolListenerService?,
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
    var currentTab by remember { mutableStateOf(Tab.Server) }
    var showAddDevice by remember { mutableStateOf(false) } 
    var deviceToEdit by remember { mutableStateOf<MqttDevice?>(null) } 

    // Service & Data State
    val isServiceRunning by wolService?.isRunning?.collectAsState() ?: remember { mutableStateOf(false) }
    val logs by AppLogger.logs.collectAsState()
    val devices by AppGlobalState.devices.collectAsState()
    val deviceStatuses by AppGlobalState.deviceStatuses.collectAsState()
    val brokerUrl by AppGlobalState.brokerUrl.collectAsState()
    val mqttState by AppGlobalState.connectionState.collectAsState()

    WakeOnLanTheme {
        if (showAddDevice) {
            AddDeviceScreen(
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
                                onSettings = { currentTab = Tab.Settings }
                            )
                        }
                        Tab.Connections -> {
                            ConnectionsScreen(
                                devices = devices,
                                deviceStatuses = deviceStatuses,
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
                                    val action = if(isOnline) "Disconnecting" else "Connecting"
                                    Toast.makeText(context, "$action...", Toast.LENGTH_SHORT).show()
                                },
                                onSettings = { currentTab = Tab.Settings },
                                brokerUrl = brokerUrl
                            )
                        }
                        Tab.Settings -> {
                            NewSettingsScreen(
                                configManager = configManager,
                                onSave = { onRestartMqttService() }
                            )
                        }
                    }
                }
                
                // Floating glassmorphic navigation bar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                        color = SurfaceGlass.copy(alpha = 0.8f),
                        tonalElevation = 0.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Top gradient line
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                NeonGreen.copy(alpha = 0.6f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Server tab
                                GlassNavItem(
                                    selected = currentTab == Tab.Server,
                                    onClick = { currentTab = Tab.Server },
                                    icon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(26.dp)) },
                                    label = "Server",
                                    selectedColor = NeonGreen
                                )
                                
                                // MQTT tab
                                GlassNavItem(
                                    selected = currentTab == Tab.Connections,
                                    onClick = { currentTab = Tab.Connections },
                                    icon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(26.dp)) },
                                    label = "MQTT",
                                    selectedColor = NeonBlue
                                )
                                
                                // Settings tab
                                GlassNavItem(
                                    selected = currentTab == Tab.Settings,
                                    onClick = { currentTab = Tab.Settings },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(26.dp)) },
                                    label = "Settings",
                                    selectedColor = SettingsPrimary
                                )
                            }
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
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, color = labelColor, style = MaterialTheme.typography.labelSmall)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        HorizontalDivider(color = Color(0xFF2C333A))
    }
}

/**
 * New glassmorphic Settings screen with cyberpunk styling
 */
@Composable
fun NewSettingsScreen(
    configManager: MqttConfigManager,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val updateManager = remember { UpdateManager(context) }
    
    // Get actual app version
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
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeepNavy)
    ) {
        // Background gradient blobs
        Box(
            modifier = Modifier
                .offset(x = (-50).dp, y = (-50).dp)
                .size(500.dp)
                .blur(120.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            SettingsPrimary.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .size(400.dp)
                .blur(100.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            PurpleAccent.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundDeepNavy.copy(alpha = 0.7f))
                    .padding(top = 48.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Settings",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
            ) {
                // App Update Section
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                        color = SurfaceGlass.copy(alpha = 0.4f),
                        tonalElevation = 0.dp
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Top highlight line
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = 0.2f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Icon with glow
                                Box(contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .blur(16.dp)
                                            .background(SettingsPrimary.copy(alpha = 0.3f), CircleShape)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = SettingsPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                
                                Spacer(Modifier.height(16.dp))
                                
                                Text(
                                    "App Update",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(Modifier.height(4.dp))
                                
                                Text(
                                    "Version $versionName",
                                    color = Slate400,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                
                                // Update status message
                                updateMessage?.let { message ->
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        message,
                                        color = if (updateAvailable != null) NeonGreen else Slate400,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                
                                Spacer(Modifier.height(16.dp))
                                
                                // Check for updates / Download button
                                if (updateAvailable != null) {
                                    // Update available - show download button
                                    Button(
                                        onClick = {
                                            updateManager.downloadAndInstall(updateAvailable!!)
                                            Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonGreen.copy(alpha = 0.2f)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            NeonGreen.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = NeonGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Download Update",
                                            color = NeonGreen,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                } else {
                                    // Check for updates button
                                    Button(
                                        onClick = {
                                            isChecking = true
                                            updateMessage = "Checking..."
                                            updateManager.checkForUpdate(versionName) { hasUpdate, downloadUrl ->
                                                isChecking = false
                                                if (hasUpdate && downloadUrl != null) {
                                                    updateAvailable = downloadUrl
                                                    updateMessage = "New version available!"
                                                } else {
                                                    updateMessage = "You're up to date!"
                                                }
                                            }
                                        },
                                        modifier = Modifier.height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SettingsPrimary.copy(alpha = 0.1f)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            SettingsPrimary.copy(alpha = 0.4f)
                                        ),
                                        enabled = !isChecking
                                    ) {
                                        if (isChecking) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = SettingsPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Done,
                                                contentDescription = null,
                                                tint = SettingsPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (isChecking) "Checking..." else "Check for Updates",
                                            color = SettingsPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                
                                Spacer(Modifier.height(12.dp))
                                
                                // GitHub link
                                TextButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pinchiu/wake-on-lan-for-android/releases"))
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Text(
                                        "View on GitHub",
                                        color = Slate400,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
