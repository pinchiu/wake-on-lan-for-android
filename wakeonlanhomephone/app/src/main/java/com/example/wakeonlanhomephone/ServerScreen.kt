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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.wakeonlanhomephone.ui.theme.*
import com.example.wakeonlanhomephone.ui.components.*
import java.net.NetworkInterface
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
    val noMacLabel = stringResource(R.string.server_no_mac)
    val profileManager = remember(deviceProfileManager) { deviceProfileManager ?: DeviceProfileManager(context) }
    var devices by remember { mutableStateOf(profileManager.getDevices()) }
    var selectedDeviceId by remember { mutableStateOf(profileManager.getSelectedDeviceId()) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<DeviceProfile?>(null) }
    var confirmPowerAction by remember { mutableStateOf<String?>(null) }
    var confirmPowerDevice by remember { mutableStateOf<DeviceProfile?>(null) }

    val refreshDevices = {
        devices = profileManager.getDevices()
        selectedDeviceId = profileManager.getSelectedDeviceId()
    }

    val ipv6Addresses = remember { getDeviceIPv6Addresses() }
    val displayedLogs = remember(logs) { logs.asReversed().take(12) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBgPrimary)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.server_title),
                            color = Slate50,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.server_subtitle),
                            color = Slate400,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Live Status Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isRunning) AccentEmeraldDim else DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isRunning) AccentEmeraldBorder else BorderSubtle
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isRunning) {
                                AnimatedPingDot(color = AccentEmerald, size = 6.dp)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Slate500, CircleShape)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isRunning) stringResource(R.string.server_status_listening) else stringResource(R.string.server_status_stopped),
                                color = if (isRunning) AccentEmerald else Slate400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Server Status & IPv6 Gateway Card
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isRunning) AccentEmeraldDim else DarkSurfaceElevated,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isRunning) AccentEmeraldBorder else BorderSubtle,
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = if (isRunning) AccentEmerald else Slate400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.server_tcp_service),
                                    color = Slate50,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isRunning) stringResource(R.string.server_tcp_running) else stringResource(R.string.server_tcp_stopped),
                                    color = if (isRunning) AccentEmerald else Slate400,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Toggle Action Button
                        Button(
                            onClick = { if (isRunning) onStopService() else onStartService() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) SemanticDangerDim else AccentEmeraldDim
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isRunning) SemanticDangerBorder else AccentEmeraldBorder
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isRunning) stringResource(R.string.server_stop_service) else stringResource(R.string.server_start_service),
                                color = if (isRunning) SemanticDanger else AccentEmerald,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = BorderSubtle)
                    Spacer(Modifier.height(14.dp))

                    // IPv6 Address Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.server_ipv6_title),
                            color = Slate400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = stringResource(R.string.server_tap_to_copy),
                            color = Slate500,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (ipv6Addresses.isEmpty()) {
                        Text(
                            text = stringResource(R.string.server_no_ipv6),
                            color = Slate500,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ipv6Addresses.forEach { ip ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(ip))
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_ip_copied, ip),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                    color = DarkSurfaceElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ip,
                                            color = Slate200,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = stringResource(R.string.content_copy),
                                            tint = Slate400,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Target Computers Management Card
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.server_targets_title),
                                color = Slate50,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DarkSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Text(
                                    text = stringResource(R.string.server_device_count, devices.size),
                                    color = Slate400,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showAddDeviceDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.content_add_target),
                                tint = AccentEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (devices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.server_no_devices),
                            color = Slate500,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        // Device Selection Chips Row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(devices) { device ->
                                val isSelected = device.id == selectedDeviceId
                                val borderColor = if (isSelected) AccentEmeraldBorder else BorderSubtle
                                val bgColor = if (isSelected) AccentEmeraldDim else DarkSurfaceElevated

                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedDeviceId = device.id
                                            profileManager.setSelectedDeviceId(device.id)
                                            refreshDevices()
                                        },
                                    color = bgColor,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = device.name,
                                                color = if (isSelected) Slate50 else Slate300,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = device.mac.ifEmpty { noMacLabel },
                                                color = if (isSelected) AccentEmerald else Slate500,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { deviceToEdit = device },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = stringResource(R.string.action_edit),
                                                tint = if (isSelected) AccentEmerald else Slate500,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Primary Action: Wake Selected Device
                        val selectedDevice = devices.find { it.id == selectedDeviceId } ?: devices.firstOrNull()
                        if (selectedDevice != null) {
                            MasterWakeButton(
                                enabled = selectedDevice.mac.isNotBlank(),
                                deviceName = selectedDevice.name,
                                deviceMac = selectedDevice.mac,
                                onClick = { onWakeComputer?.invoke(selectedDevice) }
                            )

                            // Secondary Power Actions (if IP configured)
                            if (selectedDevice.ip.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            confirmPowerAction = "sleep"
                                            confirmPowerDevice = selectedDevice
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SemanticWarningDim),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SemanticWarningDim),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(stringResource(R.string.power_sleep), color = SemanticWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            confirmPowerAction = "reboot"
                                            confirmPowerDevice = selectedDevice
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SemanticInfoDim),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SemanticInfo.copy(alpha = 0.35f)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(stringResource(R.string.power_reboot), color = SemanticInfo, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            confirmPowerAction = "shutdown"
                                            confirmPowerDevice = selectedDevice
                                        },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SemanticDangerDim),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SemanticDangerBorder),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(stringResource(R.string.power_shutdown), color = SemanticDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Real-Time System Log Header (Terminal HUD)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(SemanticDanger, CircleShape))
                        Box(modifier = Modifier.size(8.dp).background(SemanticWarning, CircleShape))
                        Box(modifier = Modifier.size(8.dp).background(AccentEmerald, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.server_logs_title),
                            color = Slate300,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = stringResource(R.string.server_logs_clear),
                        color = AccentEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onClearLogs() }
                    )
                }
            }
            
            // Log Items
            if (displayedLogs.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.server_logs_empty),
                        color = Slate500,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                itemsIndexed(
                    items = displayedLogs,
                    key = { index, log -> "$log-$index" }
                ) { _, log ->
                    LogItem(log = log)
                }
            }
        }

        // Add Computer Dialog
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

        // Edit Computer Dialog
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

        // Confirmation Dialog for Power Actions
        if (confirmPowerAction != null && confirmPowerDevice != null) {
            val isDestructive = confirmPowerAction == "shutdown"
            val actionName = when (confirmPowerAction) {
                "shutdown" -> stringResource(R.string.power_action_shutdown)
                "reboot" -> stringResource(R.string.power_action_reboot)
                "sleep" -> stringResource(R.string.power_action_sleep)
                else -> confirmPowerAction!!.uppercase()
            }
            ConfirmPowerDialog(
                actionName = actionName,
                targetName = confirmPowerDevice!!.name,
                targetIp = confirmPowerDevice!!.ip,
                isDestructive = isDestructive,
                onConfirm = {
                    val act = confirmPowerAction
                    val dev = confirmPowerDevice
                    confirmPowerAction = null
                    confirmPowerDevice = null
                    if (act != null && dev != null) {
                        onPowerCommand?.invoke(dev, act)
                    }
                },
                onDismiss = {
                    confirmPowerAction = null
                    confirmPowerDevice = null
                }
            )
        }
    }
}

@Composable
fun LogItem(log: String) {
    val systemSource = stringResource(R.string.log_source_system)
    val mqttConnectedLabel = stringResource(R.string.log_status_mqtt_connected)
    val mqttDisconnectedLabel = stringResource(R.string.log_status_mqtt_disconnected)
    val mqttConnectingLabel = stringResource(R.string.log_status_mqtt_connecting)
    val connectionErrorLabel = stringResource(R.string.log_status_connection_error)
    val serviceReadyLabel = stringResource(R.string.log_status_service_ready)
    val commandReceivedLabel = stringResource(R.string.log_status_command_received)
    val commandExecutedLabel = stringResource(R.string.log_status_command_executed)
    val systemMessageLabel = stringResource(R.string.log_status_system_message)

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
            systemSource
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
                mqttConnectedLabel to AccentEmerald
            }
            lowerFrom.contains("mqtt") && (lowerMessage.contains("disconnected") || lowerMessage.contains("closed")) -> {
                mqttDisconnectedLabel to SemanticDanger
            }
            lowerFrom.contains("mqtt") && lowerMessage.contains("connecting") -> {
                mqttConnectingLabel to SemanticWarning
            }
            lowerMessage.contains("error") || lowerMessage.contains("failed") -> {
                connectionErrorLabel to SemanticDanger
            }
            lowerMessage.contains("listening") || lowerMessage.contains("started") || lowerMessage.contains("connected") -> {
                serviceReadyLabel to AccentEmerald
            }
            lowerMessage.contains("received") -> {
                commandReceivedLabel to SemanticInfo
            }
            lowerMessage.contains("action") || lowerMessage.contains("published") || lowerMessage.contains("sent") -> {
                commandExecutedLabel to AccentEmerald
            }
            else -> {
                systemMessageLabel to Slate400
            }
        }
    }
    val (statusText, statusColor) = statusInfo

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Status indicator line
            Box(
                modifier = Modifier
                    .size(4.dp, 36.dp)
                    .background(statusColor, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fromText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = timestamp,
                        color = Slate500,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detailText,
                    color = Slate100,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
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
    val newHostName = stringResource(R.string.default_device_new_host)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, BorderStandard, RoundedCornerShape(16.dp)),
            color = DarkSurfaceElevated,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_add_target_title),
                    color = Slate50,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_name_with_example)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentEmerald,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = AccentEmerald,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate50
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text(stringResource(R.string.dialog_mac_with_example)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentEmerald,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = AccentEmerald,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate50
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text(stringResource(R.string.dialog_ip_optional_power)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentEmerald,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = AccentEmerald,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate50
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel), color = Slate400)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (mac.isNotBlank()) {
                                onConfirm(name.ifBlank { newHostName }, mac.trim(), ip.trim())
                            }
                        },
                        enabled = mac.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.action_add), color = Color.Black, fontWeight = FontWeight.Bold)
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
    val fallbackHostName = stringResource(R.string.default_device_host)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, BorderStandard, RoundedCornerShape(16.dp)),
            color = DarkSurfaceElevated,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_edit_target_title),
                    color = Slate50,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentEmerald,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = AccentEmerald,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate50
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text(stringResource(R.string.dialog_mac)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentEmerald,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = AccentEmerald,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate50
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text(stringResource(R.string.dialog_ip_optional)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentEmerald,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = AccentEmerald,
                        unfocusedLabelColor = Slate400,
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate50
                    ),
                    shape = RoundedCornerShape(10.dp),
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
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = SemanticDanger)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel), color = Slate400)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (mac.isNotBlank()) {
                                    onSave(name.ifBlank { fallbackHostName }, mac.trim(), ip.trim())
                                }
                            },
                            enabled = mac.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.action_save), color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
