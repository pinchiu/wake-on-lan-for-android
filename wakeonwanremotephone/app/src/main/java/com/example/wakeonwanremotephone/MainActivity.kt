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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import java.net.Socket
import java.net.InetSocketAddress
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
    val deviceManager = remember { DeviceProfileManager(context) }
    var devices by remember { mutableStateOf(deviceManager.getDevices()) }
    var selectedDeviceId by remember { mutableStateOf(deviceManager.getSelectedDeviceId()) }
    val currentSelectedDevice = devices.find { it.id == selectedDeviceId } ?: devices.firstOrNull()

    // State
    var helperIpv6Address by remember { mutableStateOf(deviceManager.getHelperIpv6()) }
    var computerMacAddress by remember { mutableStateOf(currentSelectedDevice?.mac ?: "") }
    var computerLocalIpv4 by remember { mutableStateOf(currentSelectedDevice?.ip ?: "") }
    var localLanMode by remember { mutableStateOf(deviceManager.isLocalLanMode()) }

    var statusMessage by remember { mutableStateOf("") }
    var isStatusError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var showEditDeviceDialog by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<DeviceProfile?>(null) }

    val coroutineScope = rememberCoroutineScope()

    fun syncCurrentDevice(mac: String = computerMacAddress, ip: String = computerLocalIpv4) {
        val dev = devices.find { it.id == selectedDeviceId }
        if (dev != null) {
            val updated = dev.copy(mac = mac.trim(), ip = ip.trim())
            deviceManager.updateDevice(updated)
            devices = deviceManager.getDevices()
        }
    }

    fun sendCommand(command: String) {
        syncCurrentDevice()
        deviceManager.setHelperIpv6(helperIpv6Address)
        deviceManager.setLocalLanMode(localLanMode)
        isLoading = true
        statusMessage = "Sending..."
        isStatusError = false
        coroutineScope.launch {
            // Fake delay for UI feedback feeling
            delay(300)
            val result = if (localLanMode) {
                if (command.startsWith("WAKE:")) {
                    val mac = command.substringAfter("WAKE:")
                    RemoteNetworkUtil.sendLocalMagicPacket(mac)
                } else {
                    val action = command.substringBefore(":")
                    val ip = command.substringAfter(":")
                    RemoteNetworkUtil.sendDirectCommandToPC(ip, action.lowercase(Locale.ROOT))
                }
            } else {
                RemoteNetworkUtil.sendTcpCommand(helperIpv6Address, command)
            }
            statusMessage = result
            isStatusError = result.startsWith("Send failed") || result.startsWith("Address") ||
                    result.startsWith("LAN WoL failed") || result.startsWith("Direct send failed") ||
                    result.startsWith("ERROR:")
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

            // Target Computers Selector Section
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = NeonBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "TARGET COMPUTERS (${devices.size})",
                                color = NeonBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        IconButton(
                            onClick = { showAddDeviceDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Computer",
                                tint = NeonGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (devices.isEmpty()) {
                        Text(
                            "尚未新增電腦，點擊右上角 + 新增",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(devices) { device ->
                                val isSelected = device.id == selectedDeviceId
                                val borderColor = if (isSelected) NeonGreen else Color.White.copy(alpha = 0.15f)
                                val bgColor = if (isSelected) NeonGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedDeviceId = device.id
                                            deviceManager.setSelectedDeviceId(device.id)
                                            computerMacAddress = device.mac
                                            computerLocalIpv4 = device.ip
                                        },
                                    color = bgColor,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                device.name,
                                                color = if (isSelected) NeonGreen else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                device.mac.ifEmpty { "無 MAC" },
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 10.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                deviceToEdit = device
                                                showEditDeviceDialog = true
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                                deviceManager.setLocalLanMode(it)
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
                                deviceManager.setHelperIpv6(helperIpv6Address)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeonTextField(
                        value = computerMacAddress,
                        onValueChange = {
                            computerMacAddress = it
                            syncCurrentDevice(mac = it)
                        },
                        label = "Target MAC",
                        icon = Icons.Default.Lan,
                        modifier = Modifier.onFocusChanged {
                            if (!it.isFocused) {
                                syncCurrentDevice()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeonTextField(
                        value = computerLocalIpv4,
                        onValueChange = {
                            computerLocalIpv4 = it
                            syncCurrentDevice(ip = it)
                        },
                        label = "Target IPv4",
                        icon = Icons.Default.Computer,
                        modifier = Modifier.onFocusChanged {
                            if (!it.isFocused) {
                                syncCurrentDevice()
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

        if (showAddDeviceDialog) {
            AddDeviceDialog(
                onDismiss = { showAddDeviceDialog = false },
                onConfirm = { name, mac, ip ->
                    val newDev = deviceManager.addDevice(name, mac, ip)
                    devices = deviceManager.getDevices()
                    selectedDeviceId = newDev.id
                    computerMacAddress = newDev.mac
                    computerLocalIpv4 = newDev.ip
                    showAddDeviceDialog = false
                }
            )
        }

        if (showEditDeviceDialog && deviceToEdit != null) {
            EditDeviceDialog(
                device = deviceToEdit!!,
                canDelete = devices.size > 1,
                onDismiss = {
                    showEditDeviceDialog = false
                    deviceToEdit = null
                },
                onSave = { name, mac, ip ->
                    val updated = deviceToEdit!!.copy(name = name, mac = mac, ip = ip)
                    deviceManager.updateDevice(updated)
                    devices = deviceManager.getDevices()
                    if (selectedDeviceId == updated.id) {
                        computerMacAddress = updated.mac
                        computerLocalIpv4 = updated.ip
                    }
                    showEditDeviceDialog = false
                    deviceToEdit = null
                },
                onDelete = {
                    val idToDelete = deviceToEdit!!.id
                    deviceManager.deleteDevice(idToDelete)
                    devices = deviceManager.getDevices()
                    selectedDeviceId = deviceManager.getSelectedDeviceId()
                    val nextDev = devices.find { it.id == selectedDeviceId }
                    computerMacAddress = nextDev?.mac ?: ""
                    computerLocalIpv4 = nextDev?.ip ?: ""
                    showEditDeviceDialog = false
                    deviceToEdit = null
                }
            )
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

@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, mac: String, ip: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
            color = Charcoal.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    "新增目標電腦",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                NeonTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "電腦名稱 (例: 臥室主機)",
                    icon = Icons.Default.Computer
                )
                Spacer(modifier = Modifier.height(10.dp))
                NeonTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = "網卡 MAC (例: AA:BB:CC:DD:EE:FF)",
                    icon = Icons.Default.Lan
                )
                Spacer(modifier = Modifier.height(10.dp))
                NeonTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = "區域網路 IPv4 (可選)",
                    icon = Icons.Default.Dns
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (mac.isNotBlank()) {
                                onConfirm(name.ifBlank { "新主機" }, mac.trim(), ip.trim())
                            }
                        },
                        enabled = mac.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen.copy(alpha = 0.25f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("新增", color = NeonGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EditDeviceDialog(
    device: DeviceProfile,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, mac: String, ip: String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(device.name) }
    var mac by remember { mutableStateOf(device.mac) }
    var ip by remember { mutableStateOf(device.ip) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
            color = Charcoal.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    "編輯目標電腦",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                NeonTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "電腦名稱",
                    icon = Icons.Default.Computer
                )
                Spacer(modifier = Modifier.height(10.dp))
                NeonTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = "網卡 MAC",
                    icon = Icons.Default.Lan
                )
                Spacer(modifier = Modifier.height(10.dp))
                NeonTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = "區域網路 IPv4 (可選)",
                    icon = Icons.Default.Dns
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = NeonRed)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("取消", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (mac.isNotBlank()) {
                                    onSave(name.ifBlank { "主機" }, mac.trim(), ip.trim())
                                }
                            },
                            enabled = mac.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue.copy(alpha = 0.25f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("儲存", color = NeonBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}