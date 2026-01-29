package com.example.wakeonlanhomephone

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
                    Intent(this, MqttWolService::class.java).also { intent ->
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
                    Intent(this, MqttWolService::class.java).also { intent ->
                        stopService(intent)
                    }
                },
                configManager = configManager
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
    configManager: MqttConfigManager
) {
    var currentScreen by remember { mutableStateOf(Screen.Monitor) }

    // Service State
    val isServiceRunning by wolService?.isRunning?.collectAsState() ?: remember { mutableStateOf(false) }
    // Use AppLogger for logs instead of getting them from the service instance
    val logs by AppLogger.logs.collectAsState()

    MaterialTheme {
        when (currentScreen) {
            Screen.Settings -> {
                SettingsScreen(
                    configManager = configManager,
                    onSave = {
                        // After save, go to connection animation
                        currentScreen = Screen.ConnectionStatus
                    }
                )
            }
            Screen.ConnectionStatus -> {
                ConnectionStatusScreen(
                    onDone = { currentScreen = Screen.Monitor }
                )
            }
            Screen.Monitor -> {
                MonitorScreen(
                    logs = logs,
                    isRunning = isServiceRunning,
                    onStartService = onStartService,
                    onStopService = onStopService,
                    onToSettings = { currentScreen = Screen.Settings } // Navigate to Level 2
                )
            }
        }
    }
}

enum class Screen {
    Settings,
    Monitor,
    ConnectionStatus
}

@Composable
fun ConnectionStatusScreen(onDone: () -> Unit) {
    val connectionState by AppGlobalState.connectionState.collectAsState()
    val lastError by AppGlobalState.lastErrorMessage.collectAsState()
    val brokerUrl by AppGlobalState.brokerUrl.collectAsState()
    
    val backgroundColor = Color(0xFF11161C)
    val textColor = Color.White
    val accentColor = Color(0xFF6ea2f5)
    val errorColor = Color(0xFFCF6679)
    val successColor = Color(0xFF03DAC5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        
        Spacer(Modifier.weight(1f))

        // Status Text
        Text(
            text = when(connectionState) {
                MqttConnectionState.CONNECTED -> "MQTT Broker Connected!"
                MqttConnectionState.FAILED -> "Connection Failed"
                MqttConnectionState.CONNECTING -> "Connecting..."
                else -> "Initializing..."
            },
            style = MaterialTheme.typography.headlineMedium,
            color = textColor,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))
        
        if (brokerUrl.isNotEmpty()) {
             Text(text = brokerUrl, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        }
        
        Spacer(Modifier.height(48.dp))
        
        // Animation Area
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
             // Icon 1: Phone
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Phone",
                tint = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(Modifier.width(24.dp))
            
            // Center Piece: Animation / Check / Error
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                AnimatedContent(
                    targetState = connectionState,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                    }, label = "ConnectionStateAnimation"
                ) { targetState ->
                    when (targetState) {
                         MqttConnectionState.CONNECTING -> {
                             // Simple loading indicator would be good, or just a dashed line
                             // For now, let's just make the checkmark gray/faded or pulsing
                             // Or a CircularProgressIndicator
                             CircularProgressIndicator(color = accentColor, modifier = Modifier.size(32.dp))
                         }
                         MqttConnectionState.CONNECTED -> {
                             // Checkmark
                              Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = "Success",
                                tint = successColor,
                                modifier = Modifier.size(48.dp)
                            )
                         }
                         MqttConnectionState.FAILED -> {
                             // Error X
                             Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = errorColor,
                                modifier = Modifier.size(48.dp)
                             )
                         }
                         else -> {}
                    }
                }
            }

            Spacer(Modifier.width(24.dp))

            // Icon 2: Server
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Server",
                tint = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
        }

         // Error Message Display
        AnimatedContent(
            targetState = lastError,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ErrorDetail"
        ) { error ->
            if (error != null && connectionState == MqttConnectionState.FAILED) {
                 Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 32.dp)) {
                    Text("Error Details:", color = errorColor, fontWeight = FontWeight.Bold)
                    Text(error, color = errorColor, textAlign = TextAlign.Center)
                 }
            }
        }
        
        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = RoundedCornerShape(25.dp)
        ) {
            Text("Done", color = Color.White)
        }
        
        Spacer(Modifier.height(32.dp))
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    configManager: MqttConfigManager,
    onSave: () -> Unit
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
                title = { Text("Edit Broker", color = textColor) },
                navigationIcon = {
                     IconButton(onClick = { onSave() }) { // Using onSave as back for now or add explicit back
                         // Icon not strictly available without deps, using simple text "<"
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
                    Text("Done", color = Color.White)
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
        Divider(color = Color(0xFF2C333A))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    logs: List<String>,
    isRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onToSettings: () -> Unit
) {
    val listState = rememberLazyListState()
    val backgroundColor = Color(0xFF11161C)
    val textColor = Color.White
    val cardBorderColor = Color(0xFF404855)
    val accentColor = Color(0xFF6ea2f5)

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Monitor", color = textColor) },
                actions = {
                    IconButton(onClick = onToSettings) {
                        Text("⚙\uFE0F", color = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            
            // Service Status Card
            SectionCard(cardBorderColor) {
               Column(modifier = Modifier.padding(16.dp)) {
                   Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Service Status:", style = MaterialTheme.typography.titleMedium, color = textColor)
                        Text(
                            if (isRunning) "RUNNING" else "STOPPED",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) accentColor else Color.Red
                        )
                   }
                   Spacer(Modifier.height(16.dp))
                   Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onStartService, 
                            enabled = !isRunning, 
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, disabledContainerColor = Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) { Text("Start", color = Color.White) }
                        
                        Button(
                            onClick = onStopService, 
                            enabled = isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679), disabledContainerColor = Color.Gray),
                            modifier = Modifier.weight(1f)
                        ) { Text("Stop", color = Color.White) }
                   }
               }
            }
            
            Spacer(Modifier.height(24.dp))

            Text("Live Logs", style = MaterialTheme.typography.titleLarge, color = textColor, modifier = Modifier.align(Alignment.Start))
            Divider(color = cardBorderColor, modifier = Modifier.padding(vertical = 8.dp))
            
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No logs yet...", color = Color.Gray)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().weight(1f)) {
                    items(logs.reversed()) { log ->
                        Text(log, color = textColor, modifier = Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
                        Divider(color = cardBorderColor, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}