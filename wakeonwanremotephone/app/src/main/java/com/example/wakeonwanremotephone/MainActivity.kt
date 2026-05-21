package com.example.wakeonwanremotephone

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet6Address
import java.util.Locale
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Info

// Custom Colors
val DeepBlack = Color(0xFF050505)
val Charcoal = Color(0xFF121212)
val GlassWhite = Color(0x1AFFFFFF)
val NeonBlue = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00FF91)
val NeonPurple = Color(0xFFD500F9)
val NeonRed = Color(0xFFFF1744)
val NeonOrange = Color(0xFFFF9100)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltimateTheme {
                UltimateRemoteScreen()
            }
        }
    }
}

// Constants for saving settings
private const val PREFS_NAME = "RemoteControlPrefs"
private const val KEY_IPV6 = "helperIpv6Address"
private const val KEY_MAC = "computerMacAddress"
private const val KEY_IPV4 = "computerLocalIpv4"
private const val KEY_LAN_MODE = "localLanMode"

@Composable
fun UltimateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DeepBlack,
            surface = Charcoal,
            primary = NeonBlue,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun UltimateRemoteScreen() {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // State
    var helperIpv6Address by remember { mutableStateOf(sharedPrefs.getString(KEY_IPV6, "") ?: "") }
    var computerMacAddress by remember { mutableStateOf(sharedPrefs.getString(KEY_MAC, "") ?: "") }
    var computerLocalIpv4 by remember { mutableStateOf(sharedPrefs.getString(KEY_IPV4, "") ?: "") }
    var localLanMode by remember { mutableStateOf(sharedPrefs.getBoolean(KEY_LAN_MODE, false)) }

    var statusMessage by remember { mutableStateOf("") }
    var isStatusError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    fun sendCommand(command: String) {
        // Save current fields to shared preferences immediately
        sharedPrefs.edit().apply {
            putString(KEY_IPV6, helperIpv6Address.trim())
            putString(KEY_MAC, computerMacAddress.trim())
            putString(KEY_IPV4, computerLocalIpv4.trim())
            apply()
        }
        isLoading = true
        statusMessage = "Sending..."
        isStatusError = false
        coroutineScope.launch {
            // Fake delay for UI feedback feeling
            delay(300)
            val result = if (localLanMode) {
                if (command.startsWith("WAKE:")) {
                    val mac = command.substringAfter("WAKE:")
                    sendLocalMagicPacket(mac)
                } else {
                    val action = command.substringBefore(":")
                    val ip = command.substringAfter(":")
                    sendDirectCommandToPC(ip, action.lowercase(Locale.ROOT))
                }
            } else {
                sendUdpCommand(helperIpv6Address, command)
            }
            statusMessage = result
            isStatusError = result.startsWith("Send failed") || result.startsWith("Address") ||
                    result.startsWith("LAN WoL failed") || result.startsWith("Direct send failed")
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepBlack, Color(0xFF1A1A1A))
                )
            )
    ) {
        // Background Ambient Glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-50).dp)
                .blur(100.dp)
                .background(NeonBlue.copy(alpha = 0.15f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 50.dp)
                .blur(100.dp)
                .background(NeonPurple.copy(alpha = 0.15f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ULTIMATE",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "REMOTE",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(if (isLoading) NeonOrange else NeonGreen, CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = { showAboutDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Configuration Section
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "CONFIGURATION",
                        color = NeonBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lan,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Local LAN Mode",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Direct WoL broadcast & command",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = localLanMode,
                            onCheckedChange = {
                                localLanMode = it
                                sharedPrefs.edit().putBoolean(KEY_LAN_MODE, it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonGreen,
                                checkedTrackColor = NeonGreen.copy(alpha = 0.3f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    NeonTextField(
                        value = helperIpv6Address,
                        onValueChange = { helperIpv6Address = it },
                        label = "Helper IPv6",
                        icon = Icons.Default.Dns,
                        modifier = Modifier.onFocusChanged {
                            if (!it.isFocused) {
                                sharedPrefs.edit().putString(KEY_IPV6, helperIpv6Address.trim()).apply()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeonTextField(
                        value = computerMacAddress,
                        onValueChange = { computerMacAddress = it },
                        label = "Target MAC",
                        icon = Icons.Default.Lan,
                        modifier = Modifier.onFocusChanged {
                            if (!it.isFocused) {
                                sharedPrefs.edit().putString(KEY_MAC, computerMacAddress.trim()).apply()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeonTextField(
                        value = computerLocalIpv4,
                        onValueChange = { computerLocalIpv4 = it },
                        label = "Target IPv4",
                        icon = Icons.Default.Computer,
                        modifier = Modifier.onFocusChanged {
                            if (!it.isFocused) {
                                sharedPrefs.edit().putString(KEY_IPV4, computerLocalIpv4.trim()).apply()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Actions Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CommandButton(
                            text = "WAKE",
                            icon = Icons.Rounded.Bolt,
                            color = NeonGreen,
                            onClick = { sendCommand("WAKE:${computerMacAddress.trim()}") },
                            enabled = !isLoading
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CommandButton(
                            text = "SLEEP",
                            icon = Icons.Rounded.Bedtime,
                            color = NeonBlue,
                            onClick = { sendCommand("SLEEP:${computerLocalIpv4.trim()}") },
                            enabled = !isLoading
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CommandButton(
                            text = "REBOOT",
                            icon = Icons.Rounded.Refresh,
                            color = NeonOrange,
                            onClick = { sendCommand("REBOOT:${computerLocalIpv4.trim()}") },
                            enabled = !isLoading
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CommandButton(
                            text = "HIBERNATE",
                            icon = Icons.Rounded.AcUnit,
                            color = Color(0xFF00B0FF),
                            onClick = { sendCommand("HIBERNATE:${computerLocalIpv4.trim()}") },
                            enabled = !isLoading
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CommandButton(
                            text = "SHUTDOWN",
                            icon = Icons.Rounded.PowerSettingsNew,
                            color = NeonRed,
                            onClick = { sendCommand("SHUTDOWN:${computerLocalIpv4.trim()}") },
                            enabled = !isLoading
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Status Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        color = if (isStatusError) NeonRed else NeonGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // About & Update Dialog
        if (showAboutDialog) {
            Dialog(onDismissRequest = { showAboutDialog = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                    color = Charcoal.copy(alpha = 0.95f),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "關於與更新",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            "遠端遙控喚醒端",
                            color = NeonBlue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val versionName = remember {
                            try {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
                            } catch (e: Exception) {
                                "1.0"
                            }
                        }
                        Text(
                            "目前版本: v$versionName",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val updateManager = remember { UpdateManager(context) }
                        var isChecking by remember { mutableStateOf(false) }
                        var updateAvailable by remember { mutableStateOf<String?>(null) }
                        var updateMessage by remember { mutableStateOf<String?>(null) }
                        
                        updateMessage?.let { message ->
                            Text(
                                message,
                                color = if (updateAvailable != null) NeonGreen else Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        
                        if (updateAvailable != null) {
                            Button(
                                onClick = {
                                    updateManager.downloadAndInstall(updateAvailable!!)
                                    Toast.makeText(context, "正在下載更新...", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.2f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                Text("下載並安裝更新", color = NeonGreen, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    isChecking = true
                                    updateMessage = "正在檢查更新..."
                                    updateManager.checkForUpdate(versionName) { hasUpdate, downloadUrl, tag ->
                                        isChecking = false
                                        if (hasUpdate && downloadUrl != null) {
                                            updateAvailable = downloadUrl
                                            updateMessage = "偵測到新版本：${tag ?: ""}"
                                        } else {
                                            updateMessage = "已是最新版本"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue.copy(alpha = 0.2f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isChecking,
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = NeonBlue,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("檢查更新", color = NeonBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pinchiu/wake-on-lan-for-android/releases"))
                                context.startActivity(intent)
                            }
                        ) {
                            Text("在 GitHub 上查看", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        TextButton(
                            onClick = { showAboutDialog = false }
                        ) {
                            Text("關閉", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GlassWhite)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
    ) {
        content()
    }
}

@Composable
fun NeonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.7f)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = NeonBlue) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonBlue,
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = NeonBlue,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun CommandButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "buttonScale")

    Button(
        onClick = {
            pressed = true
            onClick()
            // Reset pressed state after a short delay to simulate click
            // In a real app, this would be handled better by interaction source
        },
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .aspectRatio(1.2f)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(GlassWhite)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
    // Reset pressed state helper
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(100)
            pressed = false
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
            
            // Check for IPv6 format to avoid common errors if possible, 
            // but for now just trusting the input as per original app
            
            DatagramSocket().use { socket ->
                socket.send(packet)
            }
            "Command sent: $command"
        } catch (e: Exception) {
            "Send failed: ${e.message}"
        }
    }
}

private suspend fun sendLocalMagicPacket(macAddress: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val macBytes = getMacBytes(macAddress) ?: return@withContext "Invalid MAC address format"
            val magicPacket = ByteArray(102).apply {
                (0..5).forEach { this[it] = 0xFF.toByte() }
                for (i in 1..16) {
                    macBytes.copyInto(this, i * 6)
                }
            }

            val broadcastAddr = "255.255.255.255"
            val packet = DatagramPacket(magicPacket, magicPacket.size, InetAddress.getByName(broadcastAddr), 9)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.send(packet)
            }
            "Magic Packet broadcasted directly on LAN"
        } catch (e: Exception) {
            "LAN WoL failed: ${e.message}"
        }
    }
}

private fun getMacBytes(macStr: String): ByteArray? {
    val bytes = ByteArray(6)
    val hex = macStr.split(':', '-')
    if (hex.size != 6) return null
    try {
        for (i in 0..5) { bytes[i] = hex[i].toInt(16).toByte() }
    } catch (e: NumberFormatException) { return null }
    return bytes
}

private suspend fun sendDirectCommandToPC(pcIp: String, command: String): String {
    return withContext(Dispatchers.IO) {
        if (pcIp.isBlank() || command.isBlank()) {
            return@withContext "PC IP or command cannot be empty!"
        }
        try {
            val commandBytes = command.toByteArray()
            val packet = DatagramPacket(commandBytes, commandBytes.size, InetAddress.getByName(pcIp), 9877)
            DatagramSocket().use { socket -> socket.send(packet) }
            "Direct command '$command' sent to PC"
        } catch (e: Exception) {
            "Direct send failed: ${e.message}"
        }
    }
}