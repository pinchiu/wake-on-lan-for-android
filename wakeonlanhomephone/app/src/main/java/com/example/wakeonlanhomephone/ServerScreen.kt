package com.example.wakeonlanhomephone

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.wakeonlanhomephone.ui.theme.*
import com.example.wakeonlanhomephone.ui.components.*
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ServerScreen(
    isRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    logs: List<String>,
    onClearLogs: () -> Unit = {},
    onSettings: () -> Unit,
    deviceProfileManager: DeviceProfileManager? = null,
    onWakeComputer: ((DeviceProfile) -> Unit)? = null,
    onPowerCommand: ((DeviceProfile, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val profileManager = remember(deviceProfileManager) { deviceProfileManager ?: DeviceProfileManager(context) }
    var devices by remember { mutableStateOf(profileManager.getDevices()) }
    var selectedDeviceId by remember { mutableStateOf(profileManager.getSelectedDeviceId()) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<DeviceProfile?>(null) }

    val refreshDevices = {
        devices = profileManager.getDevices()
        selectedDeviceId = profileManager.getSelectedDeviceId()
    }

    val statusColor = if (isRunning) NeonGreen else Slate500
    val ipv6Addresses = remember { getDeviceIPv6Addresses() }
    val displayedLogs = remember(logs) { logs.asReversed().take(10) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackroundDark)
    ) {
        // Background grid pattern (simulated with subtle lines)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NeonBlue.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 800f
                    )
                )
        )
        
        // Ambient glow at top
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-100).dp)
                .size(500.dp)
                .blur(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PrimaryBlue.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 56.dp, start = 24.dp, end = 24.dp, bottom = 160.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier
                            .size(32.dp)
                            .blur(if (isRunning) 2.dp else 0.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "WOL SYSTEM",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 4.sp
                    )
                    Text(
                        "PREMIUM INTERFACE ACTIVE",
                        color = CyberCyan.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
            }

            // Status Circle
            item {
                Box(
                    modifier = Modifier.size(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing glow background
                    if (isRunning) {
                        PulsingGlow(
                            color = CyberCyan,
                            size = 260.dp
                        )
                    }
                    
                    // Multiple spinning border rings for complexity
                    if (isRunning) {
                        SpinningBorderRing(
                            color = CyberCyan,
                            size = 210.dp,
                            strokeWidth = 1.dp
                        )
                        SpinningBorderRing(
                            color = CyberPink,
                            size = 180.dp,
                            strokeWidth = 2.dp,
                            modifier = Modifier.rotate(180f)
                        )
                    }
                    
                    // Main circle with glass effect
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .clip(CircleShape)
                            .background(SurfaceGlass.copy(alpha = 0.8f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (isRunning) "ONLINE" else "OFFLINE",
                                color = if (isRunning) CyberCyan else Slate500,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            
                            // Online status with ping dot
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isRunning) {
                                    AnimatedPingDot(color = CyberCyan, size = 6.dp)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Slate500, CircleShape)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isRunning) "SECURE" else "INACTIVE",
                                    color = if (isRunning) CyberCyan.copy(alpha = 0.7f) else Slate500,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            
            // IPv6 Address Pills
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (ipv6Addresses.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(
                                    PrimaryBlue.copy(alpha = 0.1f),
                                    RoundedCornerShape(50)
                                )
                                .border(
                                    1.dp,
                                    PrimaryBlue.copy(alpha = 0.2f),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = NeonBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "No IPv6 Available",
                                    color = NeonBlue.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        val context = LocalContext.current
                        
                        ipv6Addresses.forEach { ip ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        PrimaryBlue.copy(alpha = 0.1f),
                                        RoundedCornerShape(50)
                                    )
                                    .border(
                                        1.dp,
                                        PrimaryBlue.copy(alpha = 0.2f),
                                        RoundedCornerShape(50)
                                    )
                                    .clickable {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(ip))
                                        android.widget.Toast.makeText(context, "已複製位址", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = NeonBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        ip,
                                        color = NeonBlue.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Target Computers Management & Instant Controls Card
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "TARGET COMPUTERS (${devices.size})",
                                color = CyberCyan,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        IconButton(
                            onClick = { showAddDeviceDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Computer",
                                tint = NeonGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (devices.isEmpty()) {
                        Text(
                            "尚未新增目標電腦，請點擊右上角 + 新增",
                            color = Slate400,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        // Chips row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(devices) { device ->
                                val isSelected = device.id == selectedDeviceId
                                val borderColor = if (isSelected) NeonGreen else GlassBorder
                                val bgColor = if (isSelected) NeonGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                        .clickable {
                                            selectedDeviceId = device.id
                                            profileManager.setSelectedDeviceId(device.id)
                                            refreshDevices()
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
                                                text = device.name,
                                                color = if (isSelected) Color.White else Slate300,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = device.mac,
                                                color = if (isSelected) CyberCyan else Slate500,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { deviceToEdit = device },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = if (isSelected) NeonGreen else Slate400,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Direct LAN Wake Button
                        val selectedDevice = devices.find { it.id == selectedDeviceId } ?: devices.firstOrNull()
                        if (selectedDevice != null) {
                            Button(
                                onClick = { onWakeComputer?.invoke(selectedDevice) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreen.copy(alpha = 0.2f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonGreen),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            "WAKE ${selectedDevice.name.uppercase()}",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            "內網廣播 WoL Magic Packet (${selectedDevice.mac})",
                                            color = NeonGreen.copy(alpha = 0.85f),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            // Optional Power Controls if IP is set
                            if (selectedDevice.ip.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onPowerCommand?.invoke(selectedDevice, "shutdown") },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.15f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("關機", color = NeonRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { onPowerCommand?.invoke(selectedDevice, "reboot") },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberYellow.copy(alpha = 0.15f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberYellow.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("重啟", color = CyberYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { onPowerCommand?.invoke(selectedDevice, "sleep") },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue.copy(alpha = 0.15f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("睡眠", color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Control Buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Start Button
                    ControlButton(
                        onClick = onStartService,
                        label = "INITIALIZE",
                        subtitle = "TCP:9876 SERVICE",
                        accentColor = CyberCyan,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Stop Button
                    ControlButton(
                        onClick = onStopService,
                        label = "TERMINATE",
                        subtitle = "STOP SERVICE",
                        accentColor = NeonRed,
                        isStop = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Logs Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Real-Time Log",
                        color = Slate400,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Clear Log",
                        color = NeonBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onClearLogs() }
                    )
                }
            }
            
            // Log Items
            itemsIndexed(
                items = displayedLogs,
                key = { index, log -> "$log-$index" }
            ) { _, log ->
                LogItem(log = log)
            }
        }

        if (showAddDeviceDialog) {
            AddComputerDialog(
                onDismiss = { showAddDeviceDialog = false },
                onConfirm = { name, mac, ip ->
                    profileManager.addDevice(name, mac, ip)
                    refreshDevices()
                    showAddDeviceDialog = false
                }
            )
        }

        deviceToEdit?.let { device ->
            EditComputerDialog(
                device = device,
                canDelete = devices.size > 1,
                onDismiss = { deviceToEdit = null },
                onSave = { name, mac, ip ->
                    profileManager.updateDevice(device.copy(name = name, mac = mac, ip = ip))
                    refreshDevices()
                    deviceToEdit = null
                },
                onDelete = {
                    profileManager.deleteDevice(device.id)
                    refreshDevices()
                    deviceToEdit = null
                }
            )
        }
    }
}

@Composable
fun ControlButton(
    onClick: () -> Unit,
    label: String,
    subtitle: String,
    accentColor: Color,
    isStop: Boolean = false,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.1f)
        ),
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isStop) {
                    // Stop icon (square)
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(accentColor, RoundedCornerShape(2.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(10.dp))
            
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(Modifier.height(2.dp))
            
            Text(
                subtitle,
                color = Slate400,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun LogItem(log: String) {
    val parsed = remember(log) {
        val hasTimestamp = log.startsWith("[") && log.contains("]")
        val timestamp = if (hasTimestamp) log.substring(1, log.indexOf("]")) else ""
        val message = if (hasTimestamp) {
            val idx = log.indexOf("]")
            if (idx + 1 < log.length && log[idx + 1] == ' ') {
                log.substring(idx + 2)
            } else {
                log.substring(idx + 1)
            }
        } else {
            log
        }
        
        val fromText = if (message.contains(":")) {
            message.substringBefore(":").trim()
        } else {
            "System"
        }
        val detailText = if (message.contains(":")) {
            message.substringAfter(":").trim()
        } else {
            message
        }
        
        Triple(timestamp, fromText, detailText)
    }
    
    val (timestamp, fromText, detailText) = parsed
    
    val statusInfo = remember(fromText, detailText) {
        val lowerMessage = detailText.lowercase()
        val lowerFrom = fromText.lowercase()
        
        when {
            lowerFrom.contains("mqtt") && lowerMessage.contains("connected") -> {
                "狀態：MQTT 已連線" to NeonGreen
            }
            lowerFrom.contains("mqtt") && (lowerMessage.contains("disconnected") || lowerMessage.contains("closed")) -> {
                "狀態：MQTT 已中斷" to NeonRed
            }
            lowerFrom.contains("mqtt") && lowerMessage.contains("connecting") -> {
                "狀態：MQTT 連線中" to CyberYellow
            }
            lowerFrom.contains("mqtt") && (lowerMessage.contains("failed") || lowerMessage.contains("error")) -> {
                "狀態：MQTT 連線失敗" to NeonRed
            }
            lowerMessage.contains("error") || lowerMessage.contains("failed") -> {
                "狀態：錯誤" to NeonRed
            }
            lowerMessage.contains("listening on") || lowerMessage.contains("started") || lowerMessage.contains("connected") -> {
                "狀態：系統就緒" to NeonGreen
            }
            lowerMessage.contains("received") -> {
                "狀態：收到指令" to CyberCyan
            }
            lowerMessage.contains("action") || lowerMessage.contains("published to") || lowerMessage.contains("sent") -> {
                "狀態：指令已執行" to NeonBlue
            }
            else -> {
                "狀態：系統日誌" to Slate400
            }
        }
    }
    val (statusText, statusColor) = statusInfo

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceGlass,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // From address
                Row {
                    Text(
                        "FROM:",
                        color = Slate500,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    fromText,
                    color = statusColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Message and time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Log Entry",
                        color = Slate400,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        timestamp,
                        color = Slate500,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                Text(
                    detailText,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Status indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Get device IPv6 addresses
 */
private fun getDeviceIPv6Addresses(): List<String> {
    val list = mutableListOf<String>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == true) {
                    // Found IPv6 address, clean it up
                    val ipv6 = address.hostAddress?.split("%")?.get(0) ?: continue
                    if (!ipv6.startsWith("fe80")) { // Skip link-local
                        if (!list.contains(ipv6)) {
                            list.add(ipv6)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

@Composable
fun AddComputerDialog(
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
                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
            color = Navy900.copy(alpha = 0.95f),
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("電腦名稱 (例: 臥室主機)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("網卡 MAC (例: AA:BB:CC:DD:EE:FF)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("區域網路 IPv4 (可選，供關機/重啟)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = Slate400)
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
fun EditComputerDialog(
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
                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
            color = Navy900.copy(alpha = 0.95f),
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("電腦名稱") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("網卡 MAC") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("區域網路 IPv4 (可選)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                            Text("取消", color = Slate400)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (mac.isNotBlank()) {
                                    onSave(name.ifBlank { "主機" }, mac.trim(), ip.trim())
                                }
                            },
                            enabled = mac.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.25f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("儲存", color = CyberCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

