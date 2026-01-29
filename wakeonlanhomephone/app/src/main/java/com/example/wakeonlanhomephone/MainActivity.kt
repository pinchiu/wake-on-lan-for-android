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
                        // Save config is handled inside SettingsScreen
                        currentScreen = Screen.Monitor
                    }
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
    Monitor
}

@Composable
fun SettingsScreen(
    configManager: MqttConfigManager,
    onSave: () -> Unit
) {
    // Config State
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var targetMac by remember { mutableStateOf("") }
    var useSsl by remember { mutableStateOf(true) }

    // Load config on init
    LaunchedEffect(Unit) {
        val config = configManager.getConfig()
        host = config.host
        port = config.port.toString()
        username = config.username
        password = config.password
        topic = config.topic
        targetMac = config.targetMac
        useSsl = config.useSsl
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("MQTT 設定 (Level 2)", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Topic (e.g. poochen/feeds/pc-power)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = targetMac, onValueChange = { targetMac = it }, label = { Text("Target MAC (for WAKE command)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useSsl, onCheckedChange = { useSsl = it })
            Text("Enable SSL/TLS")
        }
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = {
                configManager.saveConfig(MqttConfig(host, port.toIntOrNull() ?: 8883, username, password, useSsl, topic, targetMac))
                onSave()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("儲存並返回")
        }
    }
}

@Composable
fun MonitorScreen(
    logs: List<String>,
    isRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onToSettings: () -> Unit
) {
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Bar with Settings Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
             IconButton(onClick = onToSettings) {
                // Using a standard icon or simple text if icon not available immediately
                // To keep it simple without adding deps, using a Text button or default icon
                 Text("⚙\uFE0F") // Gear emoji as simple icon
            }
        }
        
        Text("監控介面", style = MaterialTheme.typography.headlineMedium)
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
        Divider(modifier = Modifier)
        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("尚未收到任何指令")
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(logs.reversed()) { log ->
                    Text(log, modifier = Modifier.padding(vertical = 4.dp))
                    Divider()
                }
            }
        }
    }
}