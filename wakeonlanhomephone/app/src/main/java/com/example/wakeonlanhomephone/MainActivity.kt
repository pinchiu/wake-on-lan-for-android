package com.example.wakeonlanhomephone

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
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
            println("服務已連接")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            wolService = null
            isBound = false
            println("服務已斷開")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        setContent {
            val isServiceRunning by wolService?.isRunning?.collectAsState() ?: remember { mutableStateOf(false) }
            val logs by wolService?.logs?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

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
                }
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
}