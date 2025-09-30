package com.example.wakeonwanremotephone // Please make sure the package name is consistent with your project

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

// Constants for saving settings
private const val PREFS_NAME = "RemoteControlPrefs"
private const val KEY_IPV6 = "helperIpv6Address"
private const val KEY_MAC = "computerMacAddress"
private const val KEY_IPV4 = "computerLocalIpv4"

@Composable
fun RemoteControlScreen() {
    // Get context to access SharedPreferences
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Load initial values from SharedPreferences
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
                Text("Ultimate Remote Controller", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = helperIpv6Address,
                    onValueChange = {
                        helperIpv6Address = it
                        // When the value changes, save it to SharedPreferences
                        sharedPrefs.edit().putString(KEY_IPV6, it).apply()
                    },
                    label = { Text("Helper phone's IPv6 address") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = computerMacAddress,
                    onValueChange = {
                        computerMacAddress = it
                        // When the value changes, save it to SharedPreferences
                        sharedPrefs.edit().putString(KEY_MAC, it).apply()
                    },
                    label = { Text("Computer's MAC address (for Wake-on-LAN)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = computerLocalIpv4,
                    onValueChange = {
                        computerLocalIpv4 = it
                        // When the value changes, save it to SharedPreferences
                        sharedPrefs.edit().putString(KEY_IPV4, it).apply()
                    },
                    label = { Text("Computer's local network IPv4 (for shutdown/reboot)") },
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
                            Text("Wake")
                        }
                        Button(
                            onClick = { sendCommand("REBOOT:${computerLocalIpv4.trim()}") },
                            enabled = helperIpv6Address.isNotBlank() && computerLocalIpv4.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Reboot")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { sendCommand("SLEEP:${computerLocalIpv4.trim()}") },
                            enabled = helperIpv6Address.isNotBlank() && computerLocalIpv4.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Sleep")
                        }
                        Button(
                            onClick = { sendCommand("HIBERNATE:${computerLocalIpv4.trim()}") },
                            enabled = helperIpv6Address.isNotBlank() && computerLocalIpv4.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Hibernate")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { sendCommand("SHUTDOWN:${computerLocalIpv4.trim()}") },
                            enabled = helperIpv6Address.isNotBlank() && computerLocalIpv4.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Shutdown")
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
            return@withContext "Address or command cannot be empty!"
        }
        try {
            val serverAddr = Inet6Address.getByName(host)
            val commandBytes = command.toByteArray()
            val packet = DatagramPacket(commandBytes, commandBytes.size, serverAddr, 9876)
            DatagramSocket().use { socket ->
                socket.send(packet)
            }
            "Command sent: $command"
        } catch (e: Exception) {
            "Send failed: ${e.message}"
        }
    }
}