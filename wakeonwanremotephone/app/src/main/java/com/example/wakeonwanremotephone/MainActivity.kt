package com.example.wakeonwanremotephone // 請確認 package 名稱與您的專案一致

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RemoteControlScreen()
        }
    }
}

// 用於儲存設定的常數
private const val PREFS_NAME = "RemoteControlPrefs"
private const val KEY_IPV6 = "helperIpv6Address"
private const val KEY_MAC = "computerMacAddress"
private const val KEY_IPV4 = "computerLocalIpv4"

@Composable
fun RemoteControlScreen() {
    // 獲取 context 以存取 SharedPreferences
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 從 SharedPreferences 載入初始值
    var helperIpv6Address by remember { mutableStateOf(sharedPrefs.getString(KEY_IPV6, "") ?: "") }
    var computerMacAddress by remember { mutableStateOf(sharedPrefs.getString(KEY_MAC, "") ?: "") }
    var computerLocalIpv4 by remember { mutableStateOf(sharedPrefs.getString(KEY_IPV4, "") ?: "") }

    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    fun sendCommand(command: String) {
        isLoading = true
        statusMessage = ""
        coroutineScope.launch {
            val result = sendUdpCommand(helperIpv6Address, command)
            statusMessage = result
            isLoading = false
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("終極遠端控制器", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = helperIpv6Address,
                    onValueChange = {
                        helperIpv6Address = it
                        // 當數值變更時，儲存到 SharedPreferences
                        sharedPrefs.edit().putString(KEY_IPV6, it).apply()
                    },
                    label = { Text("助手手機的 IPv6 位址") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = computerMacAddress,
                    onValueChange = {
                        computerMacAddress = it
                        // 當數值變更時，儲存到 SharedPreferences
                        sharedPrefs.edit().putString(KEY_MAC, it).apply()
                    },
                    label = { Text("電腦的 MAC 位址 (用於喚醒)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = computerLocalIpv4,
                    onValueChange = {
                        computerLocalIpv4 = it
                        // 當數值變更時，儲存到 SharedPreferences
                        sharedPrefs.edit().putString(KEY_IPV4, it).apply()
                    },
                    label = { Text("電腦的區域網路 IPv4 (用於關機/重啟)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(32.dp))

                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { sendCommand("WAKE:${computerMacAddress.trim()}") },
                            enabled = helperIpv6Address.isNotBlank() && computerMacAddress.isNotBlank()
                        ) {
                            Text("喚醒")
                        }
                        Button(
                            onClick = { sendCommand("REBOOT:${computerLocalIpv4.trim()}") },
                            enabled = helperIpv6Address.isNotBlank() && computerLocalIpv4.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("重啟")
                        }
                        Button(
                            onClick = { sendCommand("SHUTDOWN:${computerLocalIpv4.trim()}") },
                            enabled = helperIpv6Address.isNotBlank() && computerLocalIpv4.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("關機")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(statusMessage, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
            }
        }
    }
}

private suspend fun sendUdpCommand(host: String, command: String): String {
    return withContext(Dispatchers.IO) {
        if (host.isBlank() || command.isBlank()) {
            return@withContext "位址或指令不得為空！"
        }
        try {
            val serverAddr = Inet6Address.getByName(host)
            val commandBytes = command.toByteArray()
            val packet = DatagramPacket(commandBytes, commandBytes.size, serverAddr, 9876)
            DatagramSocket().use { socket ->
                socket.send(packet)
            }
            "指令已發送: $command"
        } catch (e: Exception) {
            "發送失敗: ${e.message}"
        }
    }
}

